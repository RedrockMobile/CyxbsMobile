package com.cyxbs.compiler.npmjs

import com.g985892345.provider.api.annotation.ImplProvider
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.security.MessageDigest
import kotlin.reflect.KClass

/**
 * 为 commonMain npm Service 接口生成端上代理，并为 Kotlin/JS object 实现生成稳定分发器。
 *
 * 生成类型统一使用 `_` 前缀且保持 internal；业务只应依赖原接口和 NpmJsServiceLoader。
 */
class KspNpmJsServiceProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val isJsTarget: Boolean,
) : SymbolProcessor {

  private val generated = mutableSetOf<String>()

  /** 根据当前编译目标选择端上代理生成或 Kotlin/JS 实现分发。 */
  override fun process(resolver: Resolver): List<KSAnnotated> {
    return if (isJsTarget) processJsImplementations(resolver) else processHostServices(resolver)
  }

  /** 非 Web 目标扫描注解接口，生成代理及 KtProvider 绑定。 */
  private fun processHostServices(resolver: Resolver): List<KSAnnotated> {
    val deferred = mutableListOf<KSAnnotated>()
    resolver.getSymbolsWithAnnotation(SERVICE_ANNOTATION).forEach { symbol ->
      if (!symbol.validate()) {
        deferred += symbol
        return@forEach
      }
      val service = symbol as? KSClassDeclaration
        ?: invalid("@NpmJsService can only annotate an interface.", symbol)
      val model = service.toServiceModel()
      generateHost(model)
    }
    return deferred
  }

  /**
   * JS 目标扫描源码 object，并根据其实现的 @NpmJsService 接口生成分发器。
   *
   * 接口可以来自依赖模块；实现 object 必须位于当前编译模块，保证生成代码能稳定引用。
   */
  private fun processJsImplementations(resolver: Resolver): List<KSAnnotated> {
    resolver.getAllFiles()
      .flatMap { file -> file.declarations.flatMap { it.allClassDeclarations() } }
      .filter { it.classKind == ClassKind.OBJECT }
      .forEach { implementation ->
        val services = implementation.getAllSuperTypes()
          .mapNotNull { it.declaration as? KSClassDeclaration }
          .filter { it.hasAnnotation(SERVICE_ANNOTATION) }
          .distinctBy { it.qualifiedName?.asString() }
          .toList()
        services.forEach { service ->
          if (!service.validate()) return@forEach
          implementation.checkJsImplementation()
          generateJs(service.toServiceModel(), implementation)
        }
      }
    return emptyList()
  }

  /** 校验并提取一个稳定的跨端 Service 协议。 */
  private fun KSClassDeclaration.toServiceModel(): ServiceModel {
    val serviceId = qualifiedName?.asString()
      ?: invalid("@NpmJsService does not support local interfaces.", this)
    if (classKind != ClassKind.INTERFACE) {
      invalid("@NpmJsService must annotate an interface: $serviceId", this)
    }
    if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers ||
      Modifier.INTERNAL in modifiers
    ) {
      invalid("@NpmJsService interface must be public: $serviceId", this)
    }
    if (typeParameters.isNotEmpty()) {
      invalid("@NpmJsService interface cannot declare type parameters: $serviceId", this)
    }
    val directSuperTypes = superTypes.map { it.resolve() }.toList()
    if (directSuperTypes.size != 1 ||
      directSuperTypes.single().declaration.qualifiedName?.asString() != SERVICE_INSTANCE
    ) {
      invalid("@NpmJsService interface must directly extend NpmJsServiceInstance: $serviceId", this)
    }
    if (getDeclaredProperties().any()) {
      invalid("@NpmJsService does not support properties: $serviceId", this)
    }
    val methods = getDeclaredFunctions().map { it.toMethodModel(serviceId) }.toList()
    val duplicated = methods.groupBy(MethodModel::name).entries.firstOrNull { it.value.size > 1 }
    if (duplicated != null) {
      invalid("@NpmJsService does not support overloaded method '${duplicated.key}': $serviceId", this)
    }
    val schema = buildString {
      append(serviceId)
      methods.sortedBy(MethodModel::name).forEach { method ->
        append('|').append(method.name).append('(')
        append(method.parameters.joinToString(",") { it.type.canonicalName() })
        append("):").append(method.returnType.canonicalName())
      }
    }
    return ServiceModel(
      declaration = this,
      serviceId = serviceId,
      schemaHash = schema.sha256(),
      methods = methods,
    )
  }

  /** 方法协议只接受可直接映射为单次 Promise 的 suspend 函数。 */
  private fun KSFunctionDeclaration.toMethodModel(serviceId: String): MethodModel {
    val methodName = simpleName.asString()
    if (methodName == CLOSE_METHOD) {
      invalid("NpmJsServiceInstance.close must not be redeclared: $serviceId", this)
    }
    if (Modifier.SUSPEND !in modifiers) {
      invalid("@NpmJsService method must be suspend: $serviceId.$methodName", this)
    }
    if (typeParameters.isNotEmpty()) {
      invalid("@NpmJsService method cannot declare type parameters: $serviceId.$methodName", this)
    }
    if (extensionReceiver != null) {
      invalid("@NpmJsService does not support extension methods: $serviceId.$methodName", this)
    }
    val methodParameters = parameters.map { parameter ->
      if (parameter.hasDefault) {
        invalid("@NpmJsService parameters cannot have default values: $serviceId.$methodName", parameter)
      }
      if (parameter.isVararg) {
        invalid("@NpmJsService does not support vararg: $serviceId.$methodName", parameter)
      }
      val name = parameter.name?.asString()
        ?: invalid("@NpmJsService parameter must have a stable name: $serviceId.$methodName", parameter)
      MethodParameter(name, parameter.type.resolve())
    }
    val resolvedReturnType = returnType?.resolve()
      ?: invalid("@NpmJsService method must declare a return type: $serviceId.$methodName", this)
    return MethodModel(methodName, methodParameters, resolvedReturnType)
  }

  /** 生成 internal `_Proxy` 与通过 KtProvider 发现的 internal `_Factory`。 */
  private fun generateHost(model: ServiceModel) {
    val names = model.generatedNames()
    val generationKey = "host:${model.serviceId}"
    if (!generated.add(generationKey)) return
    val serviceType = model.declaration.toClassName()
    val sessionParameter = ParameterSpec.builder("session", SESSION).build()
    val proxy = TypeSpec.classBuilder(names.proxy)
      .addModifiers(KModifier.INTERNAL)
      .primaryConstructor(FunSpec.constructorBuilder().addParameter(sessionParameter).build())
      .addProperty(
        PropertySpec.builder("session", SESSION, KModifier.PRIVATE)
          .initializer("session")
          .build(),
      )
      .addSuperinterface(serviceType)
      .apply {
        model.methods.forEach { addFunction(it.hostProxyFunction()) }
        addFunction(
          FunSpec.builder(CLOSE_METHOD)
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addStatement("session.close()")
            .build(),
        )
      }
      .build()

    val serviceKClass = KClass::class.asClassName().parameterizedBy(
      WildcardTypeName.producerOf(serviceType),
    )
    val factory = TypeSpec.classBuilder(names.factory)
      .addModifiers(KModifier.INTERNAL)
      .addAnnotation(
        AnnotationSpec.builder(ImplProvider::class)
          .addMember("clazz = %T::class", PROXY_FACTORY)
          .addMember("name = %S", model.providerName())
          .build(),
      )
      .addSuperinterface(PROXY_FACTORY.parameterizedBy(serviceType))
      .addProperty(
        PropertySpec.builder("serviceClass", serviceKClass, KModifier.OVERRIDE)
          .initializer("%T::class", serviceType)
          .build(),
      )
      .addStringProperty("serviceId", model.serviceId)
      .addStringProperty("schemaHash", model.schemaHash)
      .addFunction(
        FunSpec.builder("create")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("session", SESSION)
          .returns(serviceType)
          .addStatement("return %N(session)", names.proxy)
          .build(),
      )
      .build()

    FileSpec.builder(model.declaration.packageName.asString(), names.hostFile)
      .addType(proxy)
      .addType(factory)
      .build()
      .writeTo(codeGenerator, false, listOfNotNull(model.declaration.containingFile))
  }

  /** 生成 internal `_Dispatcher` 及一个带 `_` 前缀的导出注册点。 */
  private fun generateJs(model: ServiceModel, implementation: KSClassDeclaration) {
    val names = model.generatedNames(implementation)
    val generationKey = "js:${model.serviceId}:${implementation.qualifiedName?.asString()}"
    if (!generated.add(generationKey)) return
    val implementationType = implementation.toClassName()
    val dispatcher = TypeSpec.objectBuilder(names.dispatcher)
      .addModifiers(KModifier.INTERNAL)
      .addSuperinterface(JS_DISPATCHER)
      .addStringProperty("serviceId", model.serviceId)
      .addStringProperty("schemaHash", model.schemaHash)
      .addFunction(model.jsInvokeFunction(implementationType))
      .addFunction(
        FunSpec.builder(CLOSE_METHOD)
          .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
          .addStatement("%T.close()", implementationType)
          .build(),
      )
      .build()

    val registration = PropertySpec.builder(names.registration, Boolean::class)
      .addAnnotation(AnnotationSpec.builder(JS_EXPORT).build())
      .initializer("%T.register(%N)", JS_REGISTRY, names.dispatcher)
      .build()
    FileSpec.builder(implementation.packageName.asString(), names.jsFile)
      .addAnnotation(
        AnnotationSpec.builder(OPT_IN)
          .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
          .addMember("%T::class", EXPERIMENTAL_JS_EXPORT)
          .build(),
      )
      .addType(dispatcher)
      .addProperty(registration)
      .build()
      .writeTo(codeGenerator, false, listOfNotNull(implementation.containingFile))
  }

  /** 生成端上代理方法：参数编码为 JSON 数组，结果按声明返回类型解码。 */
  private fun MethodModel.hostProxyFunction(): FunSpec {
    return FunSpec.builder(name)
      .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
      .apply {
        this@hostProxyFunction.parameters.forEach { parameter ->
          addParameter(parameter.name, parameter.type.toTypeName())
        }
        returns(returnType.toTypeName())
        addCode(buildArgumentsJson(this@hostProxyFunction.parameters))
        addStatement("val resultJson = session.invoke(%S, argumentsJson)", name)
        if (!returnType.isUnit()) {
          addStatement("return %T.%M(resultJson)", JSON, DECODE_FROM_STRING)
        }
      }
      .build()
  }

  /** 生成 Kotlin/JS 分发入口，根据稳定方法名解码、调用 object 并重新编码。 */
  private fun ServiceModel.jsInvokeFunction(implementationType: ClassName): FunSpec {
    val code = CodeBlock.builder()
      .addStatement("val arguments = %T.parseToJsonElement(argumentsJson).%M", JSON, JSON_ARRAY)
      .beginControlFlow("return when (method)")
    methods.forEach { method ->
      code.beginControlFlow("%S ->", method.name)
        .addStatement(
          "require(arguments.size == %L) { %S }",
          method.parameters.size,
          "Invalid argument count for $serviceId.${method.name}.",
        )
      method.parameters.forEachIndexed { index, parameter ->
        code.addStatement(
          "val %N: %T = %T.%M(arguments[%L])",
          parameter.name,
          parameter.type.toTypeName(),
          JSON,
          DECODE_FROM_JSON_ELEMENT,
          index,
        )
      }
      val arguments = method.parameters.joinToString(", ") { it.name }
      if (method.returnType.isUnit()) {
        code.addStatement("%T.%N($arguments)", implementationType, method.name)
          .addStatement("%S", NULL_JSON)
      } else {
        code.addStatement("val result = %T.%N($arguments)", implementationType, method.name)
          .addStatement("%T.%M(result)", JSON, ENCODE_TO_STRING)
      }
      code.endControlFlow()
    }
    code.addStatement("else -> error(%S + method)", "Unknown npm JavaScript Service method: ")
      .endControlFlow()
    return FunSpec.builder("invoke")
      .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
      .addParameter("method", STRING)
      .addParameter("argumentsJson", STRING)
      .returns(STRING)
      .addCode(code.build())
      .build()
  }

  /** 生成 buildJsonArray，并依靠 kotlinx.serialization 的 reified API 校验参数可序列化。 */
  private fun buildArgumentsJson(parameters: List<MethodParameter>): CodeBlock {
    val code = CodeBlock.builder()
      .add("val argumentsJson = %M {\n", BUILD_JSON_ARRAY)
      .indent()
    parameters.forEach { parameter ->
      code.addStatement("add(%T.%M(%N))", JSON, ENCODE_TO_JSON_ELEMENT, parameter.name)
    }
    return code.unindent().add("}.toString()\n").build()
  }

  /** Kotlin/JS 实现必须是生成文件可见的 object，避免构造和实例生命周期产生隐式约定。 */
  private fun KSClassDeclaration.checkJsImplementation() {
    val name = qualifiedName?.asString()
      ?: invalid("npm JavaScript Service implementation cannot be local.", this)
    if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers) {
      invalid("npm JavaScript Service object must be visible to generated code: $name", this)
    }
  }

  /** 递归枚举文件及嵌套类型中的所有 class/object 声明。 */
  private fun KSDeclaration.allClassDeclarations(): Sequence<KSClassDeclaration> = sequence {
    val classDeclaration = this@allClassDeclarations as? KSClassDeclaration ?: return@sequence
    yield(classDeclaration)
    classDeclaration.declarations.forEach { declaration ->
      yieldAll(declaration.allClassDeclarations())
    }
  }

  private fun KSClassDeclaration.hasAnnotation(qualifiedName: String): Boolean {
    return annotations.any {
      it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }
  }

  /** 使用完全限定类型名生成跨平台稳定协议文本。 */
  private fun KSType.canonicalName(): String {
    val declarationName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    val argumentsName = if (arguments.isEmpty()) "" else arguments.joinToString(
      prefix = "<",
      postfix = ">",
    ) { argument ->
      val variance = when (argument.variance) {
        Variance.COVARIANT -> "out "
        Variance.CONTRAVARIANT -> "in "
        Variance.STAR -> "*"
        Variance.INVARIANT -> ""
      }
      if (argument.variance == Variance.STAR) variance
      else variance + (argument.type?.resolve()?.canonicalName() ?: "?")
    }
    val nullable = if (nullability == Nullability.NULLABLE) "?" else ""
    return declarationName + argumentsName + nullable
  }

  private fun KSType.isUnit(): Boolean {
    return declaration.qualifiedName?.asString() == UNIT.canonicalName
  }

  /** 生成名称包含接口/实现嵌套路径，且全部以 `_` 开头。 */
  private fun ServiceModel.generatedNames(
    implementation: KSClassDeclaration? = null,
  ): GeneratedNames {
    val serviceTail = serviceId.removePrefix(declaration.packageName.asString() + ".")
      .replace('.', '_')
    val implementationTail = implementation?.qualifiedName?.asString()
      ?.removePrefix(implementation.packageName.asString() + ".")
      ?.replace('.', '_')
    return GeneratedNames(
      proxy = "_${serviceTail}NpmJsProxy",
      factory = "_${serviceTail}NpmJsFactory",
      dispatcher = "_${implementationTail ?: serviceTail}NpmJsDispatcher",
      registration = "_${implementationTail ?: serviceTail}NpmJsRegistration",
      hostFile = "_${serviceTail}NpmJsHost",
      jsFile = "_${implementationTail ?: serviceTail}NpmJsJs",
    )
  }

  /** 为生成的 override String 属性添加固定初始化值。 */
  private fun TypeSpec.Builder.addStringProperty(name: String, value: String): TypeSpec.Builder {
    return addProperty(
      PropertySpec.builder(name, STRING, KModifier.OVERRIDE)
        .initializer("%S", value)
        .build(),
    )
  }

  private fun String.sha256(): String {
    return MessageDigest.getInstance("SHA-256")
      .digest(toByteArray())
      .joinToString("") { byte -> "%02x".format(byte) }
  }

  /** KtProvider 的 name 仅用于区分多个工厂，不参与运行时 Service 查找。 */
  private fun ServiceModel.providerName(): String {
    return "npm-js-service:${serviceId.sha256()}"
  }

  private fun invalid(message: String, symbol: KSNode): Nothing {
    logger.error(message, symbol)
    throw IllegalStateException(message)
  }

  private data class ServiceModel(
    val declaration: KSClassDeclaration,
    val serviceId: String,
    val schemaHash: String,
    val methods: List<MethodModel>,
  )

  private data class MethodModel(
    val name: String,
    val parameters: List<MethodParameter>,
    val returnType: KSType,
  )

  private data class MethodParameter(
    val name: String,
    val type: KSType,
  )

  private data class GeneratedNames(
    val proxy: String,
    val factory: String,
    val dispatcher: String,
    val registration: String,
    val hostFile: String,
    val jsFile: String,
  )

  private companion object {
    const val SERVICE_ANNOTATION =
      "com.cyxbs.functions.code.npm.api.bridge.NpmJsService"
    const val SERVICE_INSTANCE =
      "com.cyxbs.functions.code.npm.api.bridge.NpmJsServiceInstance"
    const val CLOSE_METHOD = "close"
    const val NULL_JSON = "null"

    val SESSION = ClassName("com.cyxbs.functions.code.npm.service", "NpmJsServiceSession")
    val PROXY_FACTORY = ClassName(
      "com.cyxbs.functions.code.npm.service",
      "NpmJsServiceProxyFactory",
    )
    val JS_DISPATCHER = ClassName(
      "com.cyxbs.functions.code.npm.service",
      "NpmJsServiceJsDispatcher",
    )
    val JS_REGISTRY = ClassName(
      "com.cyxbs.functions.code.npm.service",
      "NpmJsServiceJsRegistry",
    )
    val JSON = ClassName("kotlinx.serialization.json", "Json")
    val BUILD_JSON_ARRAY = MemberName("kotlinx.serialization.json", "buildJsonArray")
    val JSON_ARRAY = MemberName("kotlinx.serialization.json", "jsonArray")
    val ENCODE_TO_JSON_ELEMENT = MemberName(
      "kotlinx.serialization.json",
      "encodeToJsonElement",
    )
    val DECODE_FROM_JSON_ELEMENT = MemberName(
      "kotlinx.serialization.json",
      "decodeFromJsonElement",
    )
    val ENCODE_TO_STRING = MemberName("kotlinx.serialization", "encodeToString")
    val DECODE_FROM_STRING = MemberName("kotlinx.serialization", "decodeFromString")
    val JS_EXPORT = ClassName("kotlin.js", "JsExport")
    val EXPERIMENTAL_JS_EXPORT = ClassName("kotlin.js", "ExperimentalJsExport")
    val OPT_IN = ClassName("kotlin", "OptIn")

  }
}
