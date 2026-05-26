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
 * KSP 编译时会输出每个注册项的 route、Entry、Argument、Provider 和 deeplink 模板。
 * URL 模板统一显示参数的原始 Kotlin 类型（含泛型、`?`）。required 字段用 `{Type}`，optional（有默认值）字段用 `\[Type\]`；
 * object fields 端递归展开复杂类、enum、Map<K, V> 的 value、Collection / Array 的 element，
 * optional 字段名用 `\[name\]` 方括号包裹；自定义带类型形参的类（如 `Wrapper<T>`）会按外层实参替换 `T`：
 * ```text
 * [AppNav] route = test
 * entry: com.xxx.TestNavEntry
 * argument: com.xxx.TestNavArgument
 * deeplink: cyxbs://test?name={String}&what={What}&map=[Map<String, TextInfo>]&status={Status}
 * object fields:
 *   what: What {
 *     age: Int
 *   }
 *   [map]: Map<String, TextInfo> {
 *     value: TextInfo {
 *       text: String
 *     }
 *   }
 *   status: Status {
 *     ACTIVE
 *     INACTIVE
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

    // 仅 kotlin.collections 包下的官方 Map 抽象类型；自定义 Map 实现可能挂自定义序列化规则，不按内置 Map 展开。
    private val MAP_TYPES = setOf(
      "kotlin.collections.Map",
      "kotlin.collections.MutableMap",
    )

    // 仅 kotlin.collections 包下的官方集合抽象类型 + kotlin.Array，理由同上。
    private val COLLECTION_TYPES = setOf(
      "kotlin.collections.Iterable",
      "kotlin.collections.Collection",
      "kotlin.collections.List",
      "kotlin.collections.MutableList",
      "kotlin.collections.Set",
      "kotlin.collections.MutableSet",
      "kotlin.Array",
    )

    // Kotlin 基础类型，URL 模板直接显示类名，object fields 端不展开。
    private val PRIMITIVE_TYPES = setOf(
      "kotlin.String",
      "kotlin.Boolean",
      "kotlin.Byte",
      "kotlin.Short",
      "kotlin.Int",
      "kotlin.Long",
      "kotlin.Float",
      "kotlin.Double",
      "kotlin.Char",
    )
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
    val parameters = primaryConstructor?.parameters.orEmpty().filterNot { it.isTransient() }
    val deeplink = if (parameters.isEmpty()) {
      "cyxbs://$route"
    } else {
      parameters.joinToString(
        separator = "&",
        prefix = "cyxbs://$route?"
      ) { it.buildQueryTemplate() }
    }
    val objectFields = parameters.mapNotNull {
      it.buildObjectFieldTemplate(indent = "  ", typeArgMap = emptyMap(), visited = emptySet())
    }
    return buildString {
      append("deeplink: ").append(deeplink)
      if (objectFields.isNotEmpty()) {
        append('\n').append("object fields:")
        objectFields.forEach { append('\n').append(it) }
      }
    }
  }

  /**
   * URL 模板中显示参数的原始 Kotlin 类型。required 字段用 `{Type}`，optional（有默认值）字段用 `[Type]`，
   * 例如 `name={String}`、`map=[Map<String, TextInfo>]`。
   */
  private fun KSValueParameter.buildQueryTemplate(): String {
    val name = getSerialName()
    val type = type.resolve()
    val (open, close) = if (hasDefault) "[" to "]" else "{" to "}"
    return "$name=$open${type.getTypeDisplayName()}$close"
  }

  /** 如果参数有可展开的内部结构（复杂类 / enum / Map value / 集合 element），则生成换行字段说明，否则返回 null。 */
  private fun KSValueParameter.buildObjectFieldTemplate(
    indent: String,
    typeArgMap: Map<String, KSType>,
    visited: Set<String>,
  ): String? {
    val type = type.resolve()
    if (!type.hasExpandableContent(typeArgMap, visited)) return null
    return type.buildFieldTemplate(getSerialName(), indent, optional = hasDefault, typeArgMap = typeArgMap, visited = visited)
  }

  /**
   * 为一个类型生成 object fields 中的字段说明。
   *
   * 分支顺序：先解析类型形参（替换 T 之类的占位符），然后按 primitive / 基础数组 / enum / Map / Collection /
   * 复杂类 / 普通类型依次匹配。所有递归调用都把 [typeArgMap]（类型形参名 → 实参类型）和 [visited]（已展开的类，
   * 用来防止 self-reference 循环）原样向下传递。[optional] 为 true 时字段名会被 `[name]` 方括号包裹。
   */
  private fun KSType.buildFieldTemplate(
    name: String,
    indent: String,
    optional: Boolean,
    typeArgMap: Map<String, KSType>,
    visited: Set<String>,
  ): String {
    val resolved = applyTypeArgMap(typeArgMap)
    val display = resolved.getTypeDisplayName(typeArgMap)
    val displayName = if (optional) "[$name]" else name
    if (!resolved.hasExpandableContent(typeArgMap, visited)) {
      return "$indent$displayName: $display"
    }
    val declaration = resolved.declaration as? KSClassDeclaration
    // enum：列出所有 entry。
    if (declaration != null && declaration.classKind == ClassKind.ENUM_CLASS) {
      val entries = declaration.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.ENUM_ENTRY }
        .map { it.simpleName.asString() }
        .toList()
      return buildString {
        append(indent).append(displayName).append(": ").append(display).append(" {")
        entries.forEach { append('\n').append(indent).append("  ").append(it) }
        append('\n').append(indent).append("}")
      }
    }
    // Map<K, V>：展开 value 类型。value 自身不是 optional。
    if (resolved.isMapType()) {
      val valueType = resolved.getMapValueType()
        ?: return "$indent$displayName: $display"
      return buildString {
        append(indent).append(displayName).append(": ").append(display).append(" {")
        append('\n').append(valueType.buildFieldTemplate("value", "$indent  ", optional = false, typeArgMap, visited))
        append('\n').append(indent).append("}")
      }
    }
    // Collection / Array：展开 element 类型。element 自身不是 optional。
    if (resolved.isCollectionType()) {
      val element = resolved.getCollectionElementType()
        ?: return "$indent$displayName: $display"
      return buildString {
        append(indent).append(displayName).append(": ").append(display).append(" {")
        append('\n').append(element.buildFieldTemplate("value", "$indent  ", optional = false, typeArgMap, visited))
        append('\n').append(indent).append("}")
      }
    }
    // 普通复杂类：迭代主构造参数，并把当前类的类型实参写入 typeArgMap 供 T 替换使用。
    if (declaration != null) {
      val params = declaration.primaryConstructor?.parameters.orEmpty().filterNot { it.isTransient() }
      if (params.isEmpty()) return "$indent$displayName: $display"
      val nextTypeArgMap = resolved.buildTypeArgMap(typeArgMap)
      val nextVisited = visited + (declaration.qualifiedName?.asString() ?: declaration.simpleName.asString())
      return buildString {
        append(indent).append(displayName).append(": ").append(display).append(" {")
        params.forEach { p ->
          val paramType = p.type.resolve()
          append('\n').append(
            paramType.buildFieldTemplate(
              name = p.getSerialName(),
              indent = "$indent  ",
              optional = p.hasDefault,
              typeArgMap = nextTypeArgMap,
              visited = nextVisited,
            )
          )
        }
        append('\n').append(indent).append("}")
      }
    }
    return "$indent$displayName: $display"
  }

  /**
   * 判断类型是否含可展开的内部结构。判定顺序与 [buildFieldTemplate] 完全对齐：
   * primitive / 基础数组直接 false；enum 永远 true；Map 看 value；Collection 看 element；
   * 普通类看是否有非 @Transient 的主构造参数；circular 情况下 [visited] 命中返回 false 防止死循环。
   */
  private fun KSType.hasExpandableContent(typeArgMap: Map<String, KSType>, visited: Set<String>): Boolean {
    val resolved = applyTypeArgMap(typeArgMap)
    if (resolved.isPrimitiveType() || resolved.isPrimitiveArrayType()) return false
    val decl = resolved.declaration as? KSClassDeclaration ?: return false
    if (decl.classKind == ClassKind.ENUM_CLASS) return true
    if (resolved.isMapType()) {
      val v = resolved.getMapValueType() ?: return false
      return v.hasExpandableContent(typeArgMap, visited)
    }
    if (resolved.isCollectionType()) {
      val e = resolved.getCollectionElementType() ?: return false
      return e.hasExpandableContent(typeArgMap, visited)
    }
    val qname = decl.qualifiedName?.asString() ?: decl.simpleName.asString()
    if (qname in visited) return false
    return decl.primaryConstructor?.parameters.orEmpty().any { !it.isTransient() }
  }

  /** 若当前 KSType 的 declaration 是类型形参，则按名字到 [typeArgMap] 中查找实参类型，递归直到不是形参为止。 */
  private fun KSType.applyTypeArgMap(typeArgMap: Map<String, KSType>): KSType {
    val tp = declaration as? KSTypeParameter ?: return this
    val mapped = typeArgMap[tp.name.asString()] ?: return this
    return mapped.applyTypeArgMap(typeArgMap)
  }

  /**
   * 用当前 KSType 的 `arguments` 与 `declaration.typeParameters` 建立类型形参名 → 实参类型 的映射，
   * 同时保留 [parent] 中外层尚未消化的形参绑定（外层 T 在内层可能继续被引用）。
   */
  private fun KSType.buildTypeArgMap(parent: Map<String, KSType>): Map<String, KSType> {
    val decl = declaration as? KSClassDeclaration ?: return parent
    val params = decl.typeParameters
    if (params.isEmpty()) return parent
    val merged = parent.toMutableMap()
    for (i in params.indices) {
      val argType = arguments.getOrNull(i)?.type?.resolve()?.applyTypeArgMap(parent) ?: continue
      merged[params[i].name.asString()] = argType
    }
    return merged
  }

  /** 返回类型在文档中显示的名称，递归带上泛型实参并替换形参，例如 Map<String, TextInfo>、Wrapper<TextInfo>。 */
  private fun KSType.getTypeDisplayName(typeArgMap: Map<String, KSType> = emptyMap()): String {
    val resolved = applyTypeArgMap(typeArgMap)
    val base = (resolved.declaration as? KSClassDeclaration)?.simpleName?.asString()
      ?: resolved.declaration.simpleName.asString()
    val generic = if (resolved.arguments.isEmpty()) "" else
      resolved.arguments.joinToString(separator = ", ", prefix = "<", postfix = ">") {
        it.type?.resolve()?.getTypeDisplayName(typeArgMap) ?: "?"
      }
    val nullable = if (resolved.isMarkedNullable) "?" else ""
    return "$base$generic$nullable"
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


  /** Kotlin 基础类型：URL 模板直接显示类名，object fields 端不展开。 */
  private fun KSType.isPrimitiveType(): Boolean {
    return declaration.qualifiedName?.asString() in PRIMITIVE_TYPES
  }

  /**
   * Kotlin 基础数组：`kotlin.IntArray`、`kotlin.BooleanArray` 等以 `kotlin.` 开头并以 `Array` 结尾的类型，
   * 排除掉 `kotlin.Array<T>` 自身。它们的元素是基础类型，等同于不可展开。
   */
  private fun KSType.isPrimitiveArrayType(): Boolean {
    val name = declaration.qualifiedName?.asString() ?: return false
    return name.startsWith("kotlin.") && name.endsWith("Array") && name != "kotlin.Array"
  }

  /** 是否是 kotlinx.serialization 中按 JSON 对象编码的 Map。仅匹配 kotlin.collections 包下的 Map / MutableMap。 */
  private fun KSType.isMapType(): Boolean {
    return declaration.qualifiedName?.asString() in MAP_TYPES
  }

  /** 取出 Map<K, V> 的 V 类型，星投影返回 null。 */
  private fun KSType.getMapValueType(): KSType? {
    return arguments.getOrNull(1)?.type?.resolve()
  }

  /** 是否是 Kotlin 官方的可遍历集合 / 数组类型。仅匹配 kotlin.collections 下的抽象集合接口与 kotlin.Array。 */
  private fun KSType.isCollectionType(): Boolean {
    return declaration.qualifiedName?.asString() in COLLECTION_TYPES
  }

  /** 取出 Collection<E> / Array<E> 的 E 类型，星投影返回 null。 */
  private fun KSType.getCollectionElementType(): KSType? {
    return arguments.firstOrNull()?.type?.resolve()
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
