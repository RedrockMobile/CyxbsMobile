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
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.security.MessageDigest

/**
 * 为反向 npm 桥生成 JS 强类型代理与端上宿主分发器。
 *
 * 协议身份只由接口限定名和方法名组成；方法增删通过 Runtime capabilities 协商。
 */
class KspNpmJsBridgeProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val isJsTarget: Boolean,
) : SymbolProcessor {
  private val generated = mutableSetOf<String>()

  override fun process(resolver: Resolver): List<KSAnnotated> =
    if (isJsTarget) processJs(resolver) else processHost(resolver)

  private fun processJs(resolver: Resolver): List<KSAnnotated> {
    val deferred = mutableListOf<KSAnnotated>()
    resolver.getSymbolsWithAnnotation(BRIDGE_ANNOTATION).forEach { symbol ->
      if (!symbol.validate()) deferred += symbol
      else generateJs((symbol as? KSClassDeclaration
        ?: invalid("@NpmJsBridge can only annotate an interface.", symbol)).bridgeModel())
    }
    return deferred
  }

  private fun processHost(resolver: Resolver): List<KSAnnotated> {
    val deferred = mutableListOf<KSAnnotated>()
    resolver.getSymbolsWithAnnotation(IMPL_ANNOTATION).forEach { symbol ->
      if (!symbol.validate()) deferred += symbol
      else generateHost((symbol as? KSClassDeclaration
        ?: invalid("@NpmJsBridgeImpl can only annotate a class or object.", symbol)).implModel())
    }
    return deferred
  }

  /** 校验接口，并排除仅用于 JS 兼容回退的默认方法。 */
  private fun KSClassDeclaration.bridgeModel(): BridgeModel {
    val id = qualifiedName?.asString()
      ?: invalid("@NpmJsBridge does not support local interfaces.", this)
    if (classKind != ClassKind.INTERFACE) invalid("@NpmJsBridge must annotate an interface: $id", this)
    if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers ||
      Modifier.INTERNAL in modifiers
    ) invalid("@NpmJsBridge interface must be public: $id", this)
    if (typeParameters.isNotEmpty()) invalid("@NpmJsBridge cannot declare type parameters: $id", this)
    if (getDeclaredProperties().any()) invalid("@NpmJsBridge does not support properties: $id", this)
    val allMethods = getDeclaredFunctions().map { it.methodModel(id) }.toList()
    val duplicate = allMethods.groupBy(MethodModel::name).entries.firstOrNull { it.value.size > 1 }
    if (duplicate != null) {
      invalid("@NpmJsBridge does not support overloaded method '${duplicate.key}': $id", this)
    }
    return BridgeModel(this, id, allMethods.filter(MethodModel::requiresHost))
  }

  private fun KSFunctionDeclaration.methodModel(bridgeId: String): MethodModel {
    val name = simpleName.asString()
    // Kotlin/JS 的 KSP 在 common 声明上不会稳定暴露 SUSPEND modifier；宿主目标会再次扫描同一
    // 协议并做严格校验，而 JS 生成的 override 也会由 Kotlin 编译器兜底校验签名。
    if (!isJsTarget && Modifier.SUSPEND !in modifiers) {
      invalid("@NpmJsBridge method must be suspend: $bridgeId.$name", this)
    }
    if (typeParameters.isNotEmpty() || extensionReceiver != null) {
      invalid("@NpmJsBridge method cannot be generic or an extension: $bridgeId.$name", this)
    }
    val parameters = parameters.map { parameter ->
      if (parameter.hasDefault || parameter.isVararg) {
        invalid("@NpmJsBridge parameters cannot be default or vararg: $bridgeId.$name", parameter)
      }
      ParameterModel(
        parameter.name?.asString()
          ?: invalid("@NpmJsBridge parameter requires a stable name: $bridgeId.$name", parameter),
        parameter.type.resolve(),
      )
    }
    return MethodModel(
      name = name,
      parameters = parameters,
      returnType = returnType?.resolve()
        ?: invalid("@NpmJsBridge method requires a return type: $bridgeId.$name", this),
      // modifiers 只包含源码显式写出的修饰符；接口中省略 abstract 的方法必须读取语义属性，
      // 否则 JS 目标会把所有必需能力误当成默认回退方法。
      requiresHost = isAbstract,
    )
  }

  /** 校验 scope、包名和生成代码可使用的构造方式。 */
  private fun KSClassDeclaration.implModel(): ImplModel {
    val name = qualifiedName?.asString()
      ?: invalid("@NpmJsBridgeImpl does not support local implementations.", this)
    if (classKind != ClassKind.CLASS && classKind != ClassKind.OBJECT) {
      invalid("@NpmJsBridgeImpl must annotate a class or object: $name", this)
    }
    if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers) {
      invalid("@NpmJsBridgeImpl must be visible to generated code: $name", this)
    }
    val bridges = getAllSuperTypes()
      .mapNotNull { it.declaration as? KSClassDeclaration }
      .filter { it.hasAnnotation(BRIDGE_ANNOTATION) }
      .distinctBy { it.qualifiedName?.asString() }
      .toList()
    if (bridges.size != 1) {
      invalid("@NpmJsBridgeImpl must implement exactly one @NpmJsBridge interface: $name", this)
    }
    val annotation = annotations.first { current ->
      current.annotationType.resolve().declaration.qualifiedName?.asString() == IMPL_ANNOTATION
    }
    val scopeText = annotation.argument("packageScope").toString().substringAfterLast('.')
    val scope = when (scopeText) {
      "ALL_PACKAGES" -> Scope.ALL
      "SPECIFIED_PACKAGES" -> Scope.SPECIFIED
      else -> invalid("Unknown @NpmJsBridgeImpl packageScope '$scopeText'.", this)
    }
    val packages = (annotation.argument("packageNames") as? List<*>).orEmpty().map { value ->
      value as? String ?: invalid("@NpmJsBridgeImpl packageNames must contain strings.", this)
    }
    if (packages.distinct().size != packages.size) {
      invalid("@NpmJsBridgeImpl packageNames must be unique.", this)
    }
    packages.forEach { packageName ->
      if (!NPM_PACKAGE.matches(packageName)) {
        invalid("@NpmJsBridgeImpl contains invalid npm package name '$packageName'.", this)
      }
    }
    if (scope == Scope.ALL && packages.isNotEmpty()) {
      invalid("ALL_PACKAGES must not declare packageNames.", this)
    }
    if (scope == Scope.SPECIFIED && packages.isEmpty()) {
      invalid("SPECIFIED_PACKAGES must declare at least one packageName.", this)
    }
    val construction = if (classKind == ClassKind.OBJECT) {
      Construction.OBJECT
    } else {
      val constructorParameters = primaryConstructor?.parameters.orEmpty()
      when {
        constructorParameters.isEmpty() -> Construction.NO_ARG
        constructorParameters.size == 1 &&
          constructorParameters.single().type.resolve().declaration.qualifiedName?.asString() ==
          BRIDGE_CONTEXT.canonicalName -> Construction.CONTEXT
        else -> invalid(
          "@NpmJsBridgeImpl constructor must be empty or accept only NpmJsBridgeContext.",
          this,
        )
      }
    }
    return ImplModel(this, bridges.single().bridgeModel(), scope, packages, construction)
  }

  /** 代理通过接口名派生的顶层属性获取，完全不依赖 JS 顶层静态注册。 */
  private fun generateJs(model: BridgeModel) {
    if (!generated.add("js:${model.id}")) return
    val names = model.names()
    val bridgeType = model.declaration.toClassName()
    val proxy = TypeSpec.objectBuilder(names.proxy)
      .addModifiers(KModifier.INTERNAL)
      .addSuperinterface(bridgeType)
      .addProperty(jsonProperty())
      .apply { model.methods.forEach { addFunction(it.proxyFunction(model.id)) } }
      .build()
    val instance = PropertySpec.builder(names.accessor, bridgeType)
      .getter(FunSpec.getterBuilder().addStatement("return %N", names.proxy).build())
      .addKdoc("当前 Runtime 中由 KSP 生成并按 capabilities 协商的宿主桥代理。\n")
      .build()
    FileSpec.builder(model.declaration.packageName.asString(), names.jsFile)
      .addType(proxy)
      .addProperty(instance)
      .build()
      .writeTo(codeGenerator, false, listOfNotNull(model.declaration.containingFile))
  }

  /** 为实现生成 dispatcher 和由 KtProvider 自动发现的 factory。 */
  private fun generateHost(model: ImplModel) {
    if (!generated.add("host:${model.bridge.id}")) return
    val names = model.bridge.names(model.declaration)
    val bridgeType = model.bridge.declaration.toClassName()
    val implementationType = model.declaration.toClassName()
    val dispatcher = TypeSpec.classBuilder(names.dispatcher)
      .addModifiers(KModifier.INTERNAL)
      .primaryConstructor(FunSpec.constructorBuilder().addParameter("implementation", bridgeType).build())
      .addProperty(
        PropertySpec.builder("implementation", bridgeType, KModifier.PRIVATE)
          .initializer("implementation")
          .build(),
      )
      .addSuperinterface(HOST_DISPATCHER)
      .addStringProperty("bridgeId", model.bridge.id)
      .addMethodsProperty(model.bridge.methods)
      .addProperty(jsonProperty())
      .addFunction(model.bridge.hostInvokeFunction())
      .build()
    val factory = TypeSpec.objectBuilder(names.factory)
      .addModifiers(KModifier.INTERNAL)
      .addAnnotation(
        AnnotationSpec.builder(ImplProvider::class)
          .addMember("clazz = %T::class", HOST_FACTORY)
          .addMember("name = %S", "npm-js-bridge:${model.bridge.id.sha256()}")
          .build(),
      )
      .addSuperinterface(HOST_FACTORY)
      .addStringProperty("bridgeId", model.bridge.id)
      .addMethodsProperty(model.bridge.methods)
      .addProperty(
        PropertySpec.builder("packageScope", PACKAGE_SCOPE, KModifier.OVERRIDE)
          .initializer("%T.%L", PACKAGE_SCOPE, model.scope.enumName)
          .build(),
      )
      .addProperty(
        PropertySpec.builder(
          "packageNames",
          Set::class.asClassName().parameterizedBy(STRING),
          KModifier.OVERRIDE,
        ).initializer(model.packagesCode()).build(),
      )
      .addFunction(
        FunSpec.builder("create")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("context", BRIDGE_CONTEXT)
          .returns(HOST_DISPATCHER)
          .addStatement(
            "return %N(%L)",
            names.dispatcher,
            model.implementationCode(implementationType),
          )
          .build(),
      )
      .build()
    FileSpec.builder(model.declaration.packageName.asString(), names.hostFile)
      .addType(dispatcher)
      .addType(factory)
      .build()
      .writeTo(
        codeGenerator,
        false,
        listOfNotNull(model.declaration.containingFile, model.bridge.declaration.containingFile),
      )
  }

  private fun MethodModel.proxyFunction(bridgeId: String): FunSpec =
    FunSpec.builder(name)
      .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
      .apply {
        this@proxyFunction.parameters.forEach { addParameter(it.name, it.type.toTypeName()) }
        returns(returnType.toTypeName())
        addCode(argumentsJson(this@proxyFunction.parameters))
        addStatement("val resultJson = %T.invoke(%S, %S, argumentsJson)", JS_CLIENT, bridgeId, name)
        if (!returnType.isUnit()) {
          addStatement("return %N.%M(resultJson)", JSON_PROPERTY, DECODE_FROM_STRING)
        }
      }
      .build()

  private fun BridgeModel.hostInvokeFunction(): FunSpec {
    val code = CodeBlock.builder()
      .addStatement("val arguments = %N.parseToJsonElement(argumentsJson).%M", JSON_PROPERTY, JSON_ARRAY)
      .beginControlFlow("return when (methodName)")
    methods.forEach { method ->
      code.beginControlFlow("%S ->", method.name)
        .addStatement(
          "require(arguments.size == %L) { %S }",
          method.parameters.size,
          "Invalid argument count for $id.${method.name}.",
        )
      method.parameters.forEachIndexed { index, parameter ->
        code.addStatement(
          "val %N: %T = %N.%M(arguments[%L])",
          parameter.name,
          parameter.type.toTypeName(),
          JSON_PROPERTY,
          DECODE_FROM_JSON_ELEMENT,
          index,
        )
      }
      val arguments = method.parameters.joinToString(", ") { it.name }
      if (method.returnType.isUnit()) {
        code.addStatement("implementation.%N($arguments)", method.name).addStatement("%S", "null")
      } else {
        code.addStatement("val result = implementation.%N($arguments)", method.name)
          .addStatement("%N.%M(result)", JSON_PROPERTY, ENCODE_TO_STRING)
      }
      code.endControlFlow()
    }
    code.addStatement("else -> error(%S + methodName)", "Unknown npm JavaScript bridge method: ")
      .endControlFlow()
    return FunSpec.builder("invoke")
      .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
      .addParameter("methodName", STRING)
      .addParameter("argumentsJson", STRING)
      .returns(STRING)
      .addCode(code.build())
      .build()
  }

  private fun argumentsJson(parameters: List<ParameterModel>): CodeBlock {
    val code = CodeBlock.builder().add("val argumentsJson = %M {\n", BUILD_JSON_ARRAY).indent()
    parameters.forEach {
      code.addStatement("add(%N.%M(%N))", JSON_PROPERTY, ENCODE_TO_JSON_ELEMENT, it.name)
    }
    return code.unindent().add("}.toString()\n").build()
  }

  private fun jsonProperty(): PropertySpec =
    PropertySpec.builder(JSON_PROPERTY, JSON, KModifier.PRIVATE)
      .addAnnotation(
        AnnotationSpec.builder(OPT_IN)
          .addMember("%T::class", EXPERIMENTAL_SERIALIZATION_API)
          .build(),
      )
      .initializer(
        CodeBlock.builder().add("%T {\n", JSON).indent()
          .addStatement("ignoreUnknownKeys = true")
          .addStatement("encodeDefaults = false")
          .addStatement("explicitNulls = true")
          .unindent().add("}").build(),
      )
      .build()

  private fun TypeSpec.Builder.addStringProperty(name: String, value: String): TypeSpec.Builder =
    addProperty(
      PropertySpec.builder(name, STRING, KModifier.OVERRIDE).initializer("%S", value).build(),
    )

  private fun TypeSpec.Builder.addMethodsProperty(methods: List<MethodModel>): TypeSpec.Builder {
    val code = CodeBlock.builder().add("%M(", SET_OF)
    methods.sortedBy(MethodModel::name).forEachIndexed { index, method ->
      if (index > 0) code.add(", ")
      code.add("%S", method.name)
    }
    return addProperty(
      PropertySpec.builder(
        "methodNames",
        Set::class.asClassName().parameterizedBy(STRING),
        KModifier.OVERRIDE,
      ).initializer(code.add(")").build()).build(),
    )
  }

  private fun ImplModel.packagesCode(): CodeBlock {
    val code = CodeBlock.builder().add("%M(", SET_OF)
    packages.sorted().forEachIndexed { index, value ->
      if (index > 0) code.add(", ")
      code.add("%S", value)
    }
    return code.add(")").build()
  }

  private fun ImplModel.implementationCode(type: ClassName): CodeBlock = when (construction) {
    Construction.OBJECT -> CodeBlock.of("%T", type)
    Construction.NO_ARG -> CodeBlock.of("%T()", type)
    Construction.CONTEXT -> CodeBlock.of("%T(context)", type)
  }

  private fun BridgeModel.names(implementation: KSClassDeclaration? = null): Names {
    val bridgeTail = id.removePrefix(declaration.packageName.asString() + ".").replace('.', '_')
    val implementationTail = implementation?.qualifiedName?.asString()
      ?.removePrefix(implementation.packageName.asString() + ".")
      ?.replace('.', '_')
    val tail = implementationTail ?: bridgeTail
    return Names(
      proxy = "_${bridgeTail}NpmJsBridgeProxy",
      dispatcher = "_${tail}NpmJsBridgeDispatcher",
      factory = "_${tail}NpmJsBridgeFactory",
      jsFile = "_${bridgeTail}NpmJsBridgeJs",
      hostFile = "_${tail}NpmJsBridgeHost",
      accessor = bridgeTail.replaceFirstChar(Char::lowercaseChar),
    )
  }

  private fun KSClassDeclaration.hasAnnotation(name: String): Boolean =
    annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == name }

  private fun KSAnnotation.argument(name: String): Any? =
    arguments.firstOrNull { it.name?.asString() == name }?.value

  private fun KSType.isUnit(): Boolean =
    declaration.qualifiedName?.asString() == UNIT.canonicalName

  private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray())
      .joinToString("") { byte -> "%02x".format(byte) }

  private fun invalid(message: String, node: KSNode): Nothing {
    logger.error(message, node)
    throw IllegalStateException(message)
  }

  private data class BridgeModel(
    val declaration: KSClassDeclaration,
    val id: String,
    val methods: List<MethodModel>,
  )

  private data class MethodModel(
    val name: String,
    val parameters: List<ParameterModel>,
    val returnType: KSType,
    val requiresHost: Boolean,
  )

  private data class ParameterModel(val name: String, val type: KSType)

  private data class ImplModel(
    val declaration: KSClassDeclaration,
    val bridge: BridgeModel,
    val scope: Scope,
    val packages: List<String>,
    val construction: Construction,
  )

  private data class Names(
    val proxy: String,
    val dispatcher: String,
    val factory: String,
    val jsFile: String,
    val hostFile: String,
    val accessor: String,
  )

  private enum class Scope(val enumName: String) {
    ALL("ALL_PACKAGES"),
    SPECIFIED("SPECIFIED_PACKAGES"),
  }

  private enum class Construction { OBJECT, NO_ARG, CONTEXT }

  private companion object {
    const val BRIDGE_ANNOTATION = "com.cyxbs.functions.code.npm.js.bridge.NpmJsBridge"
    const val IMPL_ANNOTATION = "com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeImpl"
    const val JSON_PROPERTY = "bridgeJson"
    val NPM_PACKAGE = Regex("(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*")
    val HOST_FACTORY = ClassName("com.cyxbs.functions.code.npm.bridge", "NpmJsBridgeHostFactory")
    val HOST_DISPATCHER = ClassName(
      "com.cyxbs.functions.code.npm.bridge",
      "NpmJsBridgeHostDispatcher",
    )
    val BRIDGE_CONTEXT = ClassName("com.cyxbs.functions.code.npm.bridge", "NpmJsBridgeContext")
    val PACKAGE_SCOPE = ClassName(
      "com.cyxbs.functions.code.npm.js.bridge",
      "NpmJsBridgePackageScope",
    )
    val JS_CLIENT = ClassName(
      "com.cyxbs.functions.code.npm.js.bridge",
      "NpmJsBridgeJsClient",
    )
    val JSON = ClassName("kotlinx.serialization.json", "Json")
    val EXPERIMENTAL_SERIALIZATION_API = ClassName(
      "kotlinx.serialization",
      "ExperimentalSerializationApi",
    )
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
    val SET_OF = MemberName("kotlin.collections", "setOf")
    val OPT_IN = ClassName("kotlin", "OptIn")
  }
}
