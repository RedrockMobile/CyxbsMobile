import com.g985892345.provider.api.annotation.ImplProvider
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

/**
 * # AppNav KSP 处理器
 *
 * 扫描所有标记了 `@AppNav(route = "...")` 的 `AppNavEntry<T>` 直接子类，
 * 为每个 Entry 生成一个带 `@ImplProvider` 的 `AppNavCollector<T>` 实现类，
 * 使 `AppNavDisplay` 可以通过 `AppNavCollector::class.allImpl()` 自动发现多模块页面。
 *
 * ## 输入示例
 * ```kotlin
 * @Serializable
 * data class TestNavArgument(val name: String, val what: What): AppNavArgument
 *
 * @AppNav(route = "test")
 * class TestNavEntry : AppNavEntry<TestNavArgument>()
 * ```
 *
 * ## 生成示例
 * ```kotlin
 * @ImplProvider(clazz = AppNavCollector::class, name = "test")
 * object _TestNavEntry_KtProvider : AppNavCollector<TestNavArgument> {
 *   private val navEntryInstance by lazy { TestNavEntry() }
 *   override val navEntry get() = navEntryInstance
 *   override val argumentClazz get() = TestNavArgument::class
 *   override val argumentSerializer get() = serializer<TestNavArgument>()
 * }
 * ```
 *
 * ## 校验规则
 * - `@AppNav` 只能标记普通 `class` 或单例 `object`，普通 `class` Entry 必须能通过无参构造器创建
 * - Entry 必须直接继承 `AppNavEntry<T>`，禁止中间抽象类，便于稳定获取参数类型
 * - `T` 必须是非空、具体、无泛型实参的 `@Serializable` 类
 * - `T` 的类名必须以 `NavArgument` 结尾，用于规范 nav 场景下的参数写法
 * - `T` 必须实现 `com.cyxbs.components.navigation.AppNavArgument` 接口，才能作为 `AppNavBackStack` 的元素
 * - `T` 不需要实现 Navigation3 官方的 `NavKey`
 * - `route` 必须满足：非空、不以 `/` 开头或结尾、不含连续 `/`，每个 segment 仅允许 `[a-zA-Z0-9_-]`
 *
 * ## 终端输出
 * KSP 编译时会输出每个注册项的 route、Entry、Argument、Provider 和 deeplink 模板：
 * ```text
 * [AppNav] route = test
 * entry: com.xxx.TestNavEntry
 * argument: com.xxx.TestNavArgument
 * deeplink: cyxbs://test?name={String}&what={What json}
 * object fields:
 *   what: What {
 *     age: Int
 *   }
 * ```
 */
class KspNavigationSymbolProcess(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) : SymbolProcessor {

  companion object {
    private const val ANNOTATION = "com.cyxbs.components.navigation.AppNav"
    private const val APP_NAV_MODULE_PATH = "appNav.modulePath"
    private const val APP_NAV_ENTRY = "com.cyxbs.components.navigation.AppNavEntry"
    private const val SERIALIZABLE = "kotlinx.serialization.Serializable"
    private const val NAV_ARGUMENT = "com.cyxbs.components.navigation.AppNavArgument"
    private const val SERIAL_NAME = "kotlinx.serialization.SerialName"
    private const val TRANSIENT = "kotlinx.serialization.Transient"
    private val APP_NAV_ENTRY_CLASS = ClassName("com.cyxbs.components.navigation", "AppNavEntry")
    private val APP_NAV_COLLECTOR_CLASS = ClassName("com.cyxbs.components.navigation", "AppNavCollector")
    private val K_SERIALIZER_CLASS = ClassName("kotlinx.serialization", "KSerializer")
    private val SERIALIZER = MemberName("kotlinx.serialization", "serializer")

    // route 强校验：仅允许字母数字、下划线、短横线，多段以 `/` 分隔且不允许首尾或连续 `/`
    private val ROUTE_SEGMENT_REGEX = Regex("[a-zA-Z0-9_-]+")
  }

  private val generated = mutableSetOf<String>()

  /** 扫描所有 @AppNav 注解类，并逐个生成对应的 AppNavCollector provider。 */
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver.getSymbolsWithAnnotation(ANNOTATION)
      .filterIsInstance<KSClassDeclaration>()
      .forEach {
        generate(it)
      }
    return emptyList()
  }

  /** 校验单个 @AppNav Entry，并生成 _Entry_KtProvider 文件。 */
  private fun generate(declaration: KSClassDeclaration) {
    val route = declaration.getAppNavRoute()
    declaration.checkEntryDeclaration()
    val appNavEntryType = declaration.findAppNavEntryType()
    val argumentType = declaration.getArgumentType(appNavEntryType)
    declaration.checkArgumentType(argumentType)

    val packageName = declaration.packageName.asString()
    val providerName = declaration.getProviderName()
    if (!generated.add("$packageName.$providerName")) return
    val argumentDeclaration = argumentType.declaration as KSClassDeclaration
    val deeplinkTemplate = argumentDeclaration.buildDeepLinkTemplate(route)
    val report = AppNavReport(
      modulePath = options[APP_NAV_MODULE_PATH].orEmpty().ifBlank { "unknown" },
      route = route,
      entry = declaration.displayName(),
      argument = argumentDeclaration.displayName(),
      deeplinkTemplate = deeplinkTemplate,
    )
    // 终端会输出 route、Entry、Argument、Provider 和 deeplink 模板，便于检查路由注册结果。
    logger.warn(report.toLogText())
    report.writeMarkdown(declaration)

    val entryClassName = declaration.toClassName()
    val argumentTypeName = argumentType.toTypeName()
    val argumentClassName = argumentDeclaration.toClassName()
    val navEntryTypeName = APP_NAV_ENTRY_CLASS.parameterizedBy(argumentTypeName)
    val collectorTypeName = APP_NAV_COLLECTOR_CLASS.parameterizedBy(argumentTypeName)
    val kClassTypeName = KClass::class.asClassName().parameterizedBy(argumentTypeName)
    val kSerializerTypeName = K_SERIALIZER_CLASS.parameterizedBy(argumentTypeName)

    // 生成 object _Xxx_KtProvider : AppNavCollector<Argument>，并用 @ImplProvider 注册到 KtProvider。
    val providerBuilder = TypeSpec.objectBuilder(providerName)
      .addAnnotation(
        AnnotationSpec.builder(ImplProvider::class)
          .addMember("clazz = %T::class", APP_NAV_COLLECTOR_CLASS)
          .addMember("name = %S", route)
          .build()
      )
      .addSuperinterface(collectorTypeName)
      .addProperty(
        PropertySpec.builder("argumentClazz", kClassTypeName, KModifier.OVERRIDE)
          .getter(
            FunSpec.getterBuilder()
              .addStatement("return %T::class", argumentClassName)
              .build()
          )
          .build()
      )
      .addProperty(
        PropertySpec.builder("argumentSerializer", kSerializerTypeName, KModifier.OVERRIDE)
          .getter(
            FunSpec.getterBuilder()
              .addStatement("return %M<%T>()", SERIALIZER, argumentTypeName)
              .build()
          )
          .build()
      )

    // 普通 class 通过 lazy 延迟创建 Entry，object 则直接返回单例。
    if (declaration.classKind == ClassKind.CLASS) {
      providerBuilder.addProperty(
        PropertySpec.builder("navEntryInstance", entryClassName, KModifier.PRIVATE)
          .delegate(CodeBlock.of("lazy { %T() }", entryClassName))
          .build()
      )
      providerBuilder.addProperty(
        PropertySpec.builder("navEntry", navEntryTypeName, KModifier.OVERRIDE)
          .getter(
            FunSpec.getterBuilder()
              .addStatement("return navEntryInstance")
              .build()
          )
          .build()
      )
    } else {
      providerBuilder.addProperty(
        PropertySpec.builder("navEntry", navEntryTypeName, KModifier.OVERRIDE)
          .getter(
            FunSpec.getterBuilder()
              .addStatement("return %T", entryClassName)
              .build()
          )
          .build()
      )
    }

    // 写入 KSP 生成目录，并绑定来源文件以支持增量编译。
    FileSpec.builder(packageName, providerName)
      .addType(providerBuilder.build())
      .build()
      .writeTo(codeGenerator, false, listOf(declaration.containingFile!!))
  }

  /** 读取并强校验 @AppNav(route)，违反命名规则时直接抛异常。 */
  private fun KSClassDeclaration.getAppNavRoute(): String {
    val annotation = annotations.firstOrNull {
      it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION
    } ?: throw IllegalStateException("找不到 @AppNav 注解，${displayName()}")
    val route = annotation.arguments.firstOrNull { it.name?.asString() == "route" }?.value as? String
      ?: throw IllegalStateException("@AppNav 必须设置 route，${displayName()}")
    validateRoute(route, this)
    return route
  }

  /**
   * 校验 @AppNav 的 route 字符串：
   * - 非空
   * - 不以 `/` 开头或结尾，否则解析回来时 segment 会含空字符串
   * - 不含连续 `/`，否则同样产生空 segment
   * - 每段仅允许 `[a-zA-Z0-9_-]`，避免 URI 解析时被 percent-encoding 或当作分隔符
   *
   * 命名约定与 AppNavArgument.encodeToUrl 的拼装规则严格对齐：首段作 URI authority，其余作 path segment。
   */
  private fun validateRoute(route: String, declaration: KSClassDeclaration) {
    if (route.isBlank()) {
      throw IllegalStateException("@AppNav 的 route 不能为空，${declaration.displayName()}")
    }
    if (route.startsWith('/') || route.endsWith('/')) {
      throw IllegalStateException("@AppNav 的 route 不能以 `/` 开头或结尾：\"$route\"，${declaration.displayName()}")
    }
    val segments = route.split('/')
    if (segments.any { it.isEmpty() }) {
      throw IllegalStateException("@AppNav 的 route 不能包含连续 `/`：\"$route\"，${declaration.displayName()}")
    }
    val invalid = segments.firstOrNull { !ROUTE_SEGMENT_REGEX.matches(it) }
    if (invalid != null) {
      throw IllegalStateException(
        "@AppNav 的 route segment \"$invalid\" 含非法字符，仅允许 [a-zA-Z0-9_-]：\"$route\"，${declaration.displayName()}"
      )
    }
  }

  /** 校验 Entry 本身能被生成代码稳定访问和实例化。 */
  private fun KSClassDeclaration.checkEntryDeclaration() {
    if (classKind != ClassKind.CLASS && classKind != ClassKind.OBJECT) {
      throw IllegalStateException("@AppNav 注解类只能为普通 class 或者单例 object，${displayName()}")
    }
    if (Modifier.ABSTRACT in modifiers) {
      throw IllegalStateException("@AppNav 注解类不能为 abstract，${displayName()}")
    }
    if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers) {
      throw IllegalStateException("@AppNav 注解类不能为 private 或 protected，${displayName()}")
    }
    if (Modifier.INNER in modifiers) {
      throw IllegalStateException("@AppNav 注解类不能为 inner class，${displayName()}")
    }
    if (classKind == ClassKind.CLASS && !hasNoArgConstructor()) {
      throw IllegalStateException("@AppNav 注解 class 必须提供无参构造器，${displayName()}")
    }
  }

  /** 查找直接父类 AppNavEntry，禁止通过中间抽象类继承。 */
  private fun KSClassDeclaration.findAppNavEntryType(): KSType {
    return superTypes
      .map { it.resolve() }
      .firstOrNull { it.declaration.qualifiedName?.asString() == APP_NAV_ENTRY }
      ?: throw IllegalStateException("@AppNav 注解类的必须是 AppNavEntry 的直接子类，禁止存在中间的抽象类，${displayName()}")
  }

  /** 从 AppNavEntry<T> 中取出页面参数类型 T。 */
  private fun KSClassDeclaration.getArgumentType(appNavEntryType: KSType): KSType {
    val argument = appNavEntryType.arguments.singleOrNull()
      ?: throw IllegalStateException("@AppNav 注解类的 AppNavEntry 必须声明一个泛型参数，${displayName()}")
    return argument.type?.resolve()
      ?: throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数不能为星投影，${displayName()}")
  }

  /** 校验参数类型必须是非空、具体、非泛型实参且带 @Serializable 的类。 */
  private fun KSClassDeclaration.checkArgumentType(argumentType: KSType) {
    if (argumentType.isMarkedNullable) {
      throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数不能为可空类型，${displayName()}")
    }
    if (!argumentType.isConcreteType()) {
      throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数必须是具体类型，不能为 T 或星投影，${displayName()}")
    }
    if (argumentType.arguments.isNotEmpty()) {
      throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数暂不支持带泛型实参，请封装成一个具体的 @Serializable 参数类，${displayName()}")
    }
    val argumentDeclaration = argumentType.declaration as? KSClassDeclaration
      ?: throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数必须是具体类，${displayName()}")
    val hasSerializable = argumentDeclaration.annotations.any {
      it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE
    }
    if (!hasSerializable) {
      throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数必须添加 @Serializable，${argumentDeclaration.displayName()} used by ${displayName()}")
    }
    // 强制以 NavArgument 结尾，统一 nav 场景下参数类的写法，方便在跨模块代码中一眼区分。
    val argumentSimpleName = argumentDeclaration.simpleName.asString()
    if (!argumentSimpleName.endsWith("NavArgument")) {
      throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数命名必须以 NavArgument 结尾，用于规范 nav 场景下的参数写法，${argumentDeclaration.displayName()} used by ${displayName()}")
    }
    // 必须实现 NavArgument 接口，才能直接放入 AppNavBackStack 作为栈元素。
    val implementsNavArgument = argumentDeclaration.getAllSuperTypes().any {
      it.declaration.qualifiedName?.asString() == NAV_ARGUMENT
    }
    if (!implementsNavArgument) {
      throw IllegalStateException("@AppNav 注解类的 AppNavEntry 泛型参数必须实现 $NAV_ARGUMENT 接口，${argumentDeclaration.displayName()} used by ${displayName()}")
    }
  }

  /** 判断 class Entry 是否可以用 Entry() 无参创建。 */
  private fun KSClassDeclaration.hasNoArgConstructor(): Boolean {
    return primaryConstructor?.parameters.orEmpty().isEmpty() || getConstructors().any { it.parameters.isEmpty() }
  }

  /** 按 UrlDecoder 的 query 解析规则生成 cyxbs:// deeplink 模板和复杂对象字段说明，会跳过 @Transient 并使用 @SerialName。 */
  private fun KSClassDeclaration.buildDeepLinkTemplate(route: String): String {
    val parameters = primaryConstructor?.parameters.orEmpty()
    val deeplink = if (parameters.isEmpty()) {
      "cyxbs://$route"
    } else {
      parameters.filterNot { it.isTransient() }.joinToString(
        separator = "&",
        prefix = "cyxbs://$route?"
      ) { it.buildQueryTemplate() }
    }
    val objectFields = parameters.filterNot { it.isTransient() }.mapNotNull { it.buildObjectFieldTemplate(indent = "  ") }
    return buildString {
      append("deeplink: ").append(deeplink)
      if (objectFields.isNotEmpty()) {
        append('\n').append("object fields:")
        objectFields.forEach { append('\n').append(it) }
      }
    }
  }

  /** 为一个构造参数生成 query 片段，集合/数组使用重复 key 模板，默认值字段会标记 optional。 */
  private fun KSValueParameter.buildQueryTemplate(): String {
    if (isTransient()) return ""
    val name = getSerialName()
    val type = type.resolve()
    val suffix = getOptionalSuffix()
    return if (type.isRepeatedQueryType()) {
      "$name={${type.getListElementTemplate()}[]$suffix}"
    } else {
      "$name={${type.getUrlValueTemplate()}$suffix}"
    }
  }

  /** 如果参数是复杂对象或复杂对象列表，则生成换行字段说明。 */
  private fun KSValueParameter.buildObjectFieldTemplate(indent: String): String? {
    val name = getSerialName()
    val type = type.resolve()
    val objectType = if (type.isRepeatedQueryType()) type.getRepeatedElementType() else type
    val declaration = objectType?.declaration as? KSClassDeclaration ?: return null
    if (!objectType.isComplexObjectType()) return null
    return buildComplexObjectFieldTemplate(name, declaration, indent)
  }

  /** 递归展开复杂对象主构造参数，生成 object fields 中的字段树。 */
  private fun buildComplexObjectFieldTemplate(name: String, declaration: KSClassDeclaration, indent: String): String {
    val childParameters = declaration.primaryConstructor?.parameters.orEmpty()
    if (childParameters.isEmpty()) return "$indent$name: ${declaration.simpleName.asString()}"
    return buildString {
      append(indent).append(name).append(": ").append(declaration.simpleName.asString()).append(" {")
      childParameters.filterNot { it.isTransient() }.forEach { parameter ->
        append('\n').append(parameter.buildFieldTemplate(indent = "$indent  "))
      }
      append('\n').append(indent).append("}")
    }
  }

  /** 为复杂对象中的一个字段生成字段说明。 */
  private fun KSValueParameter.buildFieldTemplate(indent: String): String {
    val name = getSerialName()
    val type = type.resolve()
    return type.buildFieldTemplate(name, indent, getOptionalSuffix())
  }

  /** 为类型生成字段说明，复杂对象继续递归展开。 */
  private fun KSType.buildFieldTemplate(name: String, indent: String, suffix: String = ""): String {
    if (isRepeatedQueryType()) {
      val elementType = getRepeatedElementType()
      val elementDeclaration = elementType?.declaration as? KSClassDeclaration
      return if (elementType != null && elementDeclaration != null && elementType.isComplexObjectType()) {
        buildComplexObjectFieldTemplate("$name[]", elementDeclaration, indent)
      } else {
        "$indent$name[]: ${getRepeatedElementTemplate()}$suffix"
      }
    }
    val declaration = declaration as? KSClassDeclaration
    return if (declaration != null && isComplexObjectType()) {
      buildComplexObjectFieldTemplate(name, declaration, indent)
    } else {
      "$indent$name: ${getUrlValueTemplate()}$suffix"
    }
  }

  /** 读取字段序列化名称，优先使用 @SerialName，否则使用参数名。 */
  private fun KSValueParameter.getSerialName(): String {
    val serialName = annotations.firstOrNull {
      it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIAL_NAME
    }?.arguments?.firstOrNull { it.name?.asString() == "value" }?.value as? String
    return serialName ?: name?.asString() ?: "arg"
  }

  /** 判断字段是否被 @Transient 忽略序列化，忽略字段不会出现在 deeplink 模板中。 */
  private fun KSValueParameter.isTransient(): Boolean {
    return annotations.any {
      it.annotationType.resolve().declaration.qualifiedName?.asString() == TRANSIENT
    }
  }

  /** 默认值字段在模板中标记为 optional，表示 UrlDecoder 缺省时由 kotlinx.serialization 使用默认值。 */
  private fun KSValueParameter.getOptionalSuffix(): String {
    return if (hasDefault) " optional" else ""
  }

  /** 获取重复 query 类型的元素在 URL 中的占位文本。 */
  private fun KSType.getListElementTemplate(): String {
    return getRepeatedElementTemplate()
  }

  /** 获取类型在 URL 中的占位文本，复杂对象显示为 ClassName json。 */
  private fun KSType.getUrlValueTemplate(): String {
    return when (declaration.qualifiedName?.asString()) {
      "kotlin.String" -> "String"
      "kotlin.Boolean" -> "Boolean"
      "kotlin.Byte" -> "Byte"
      "kotlin.Short" -> "Short"
      "kotlin.Int" -> "Int"
      "kotlin.Long" -> "Long"
      "kotlin.Float" -> "Float"
      "kotlin.Double" -> "Double"
      "kotlin.Char" -> "Char"
      else -> {
        val classDeclaration = declaration as? KSClassDeclaration
        if (classDeclaration?.classKind == ClassKind.ENUM_CLASS) {
          "EnumName"
        } else {
          "${classDeclaration?.simpleName?.asString() ?: "Object"} json"
        }
      }
    }
  }

  /** 判断类型是否是 UrlDecoder 可通过重复 key 表达的集合或数组。 */
  private fun KSType.isRepeatedQueryType(): Boolean {
    return declaration.qualifiedName?.asString() in setOf(
      "kotlin.collections.Iterable",
      "kotlin.collections.Collection",
      "kotlin.collections.List",
      "kotlin.collections.MutableList",
      "kotlin.collections.ArrayList",
      "java.util.ArrayList",
      "java.util.LinkedList",
      "kotlin.collections.Set",
      "kotlin.collections.MutableSet",
      "kotlin.collections.HashSet",
      "kotlin.collections.LinkedHashSet",
      "java.util.HashSet",
      "java.util.LinkedHashSet",
      "kotlin.Array",
      "kotlin.BooleanArray",
      "kotlin.ByteArray",
      "kotlin.ShortArray",
      "kotlin.IntArray",
      "kotlin.LongArray",
      "kotlin.FloatArray",
      "kotlin.DoubleArray",
      "kotlin.CharArray",
    )
  }

  /** 获取集合或数组的元素占位，primitive array 直接映射到对应基础类型。 */
  private fun KSType.getRepeatedElementTemplate(): String {
    return when (declaration.qualifiedName?.asString()) {
      "kotlin.BooleanArray" -> "true|false"
      "kotlin.ByteArray" -> "Byte"
      "kotlin.ShortArray" -> "Short"
      "kotlin.IntArray" -> "Int"
      "kotlin.LongArray" -> "Long"
      "kotlin.FloatArray" -> "Float"
      "kotlin.DoubleArray" -> "Double"
      "kotlin.CharArray" -> "Char"
      else -> arguments.firstOrNull()?.type?.resolve()?.getUrlValueTemplate() ?: "value"
    }
  }

  /** 获取集合或数组的元素类型，primitive array 没有泛型实参，返回 null。 */
  private fun KSType.getRepeatedElementType(): KSType? {
    return arguments.firstOrNull()?.type?.resolve()
  }

  /** 判断类型是否需要用 JSON 字符串传入 URL。 */
  private fun KSType.isComplexObjectType(): Boolean {
    if (isRepeatedQueryType()) return false
    val declaration = declaration as? KSClassDeclaration ?: return false
    return declaration.classKind != ClassKind.ENUM_CLASS && getUrlValueTemplate().endsWith(" json")
  }

  /** 递归判断类型是否已经具体化，禁止 T 和星投影。 */
  private fun KSType.isConcreteType(): Boolean {
    if (declaration is KSTypeParameter) return false
    return arguments.all { it.type?.resolve()?.isConcreteType() == true }
  }

  /** 清理 Gradle module path，使其可以安全作为报告文件名。 */
  private fun String.sanitizeReportFileName(): String {
    return replace(':', '_').trim('_').ifBlank { "unknown" }
  }

  /** 生成带下划线前缀的 provider 类名，避免和业务类混淆。 */
  private fun KSClassDeclaration.getProviderName(): String {
    val qualifiedName = qualifiedName?.asString()
      ?: throw IllegalStateException("@AppNav 注解类不能为局部类，${displayName()}")
    val packagePrefix = packageName.asString().takeIf { it.isNotEmpty() }?.plus(".").orEmpty()
    return "_" + qualifiedName.removePrefix(packagePrefix).replace('.', '_') + "_KtProvider"
  }

  /** 返回用于异常和日志输出的类名。 */
  private fun KSClassDeclaration.displayName(): String {
    return qualifiedName?.asString() ?: simpleName.asString()
  }

  private inner class AppNavReport(
    val modulePath: String,
    val route: String,
    val entry: String,
    val argument: String,
    val deeplinkTemplate: String,
  ) {
    fun writeMarkdown(declaration: KSClassDeclaration) {
      codeGenerator.createNewFileByPath(
        dependencies = Dependencies(aggregating = false, declaration.containingFile!!),
        path = "AppNavReport/${entry.sanitizeReportFileName()}",
        extensionName = "md",
      ).bufferedWriter().use { writer ->
        writer.append(toMarkdown())
      }
    }

    fun toLogText(): String {
      return "[AppNav] route = $route" +
          "\nentry: $entry" +
          "\nargument: $argument" +
          "\n$deeplinkTemplate"
    }

    fun toMarkdown(): String {
      return buildString {
        appendLine("### $route")
        appendLine()
        appendLine("- entry: `$entry`")
        appendLine("- argument: `$argument`")
        appendLine()
        appendLine("```text")
        appendLine(deeplinkTemplate)
        appendLine("```")
      }
    }
  }
}
