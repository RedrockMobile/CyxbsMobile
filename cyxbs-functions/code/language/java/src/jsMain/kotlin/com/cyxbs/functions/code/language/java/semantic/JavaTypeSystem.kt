package com.cyxbs.functions.code.language.java.semantic

/**
 * Java 轻量类型系统使用的类型表达。
 *
 * 该模型只保存编辑器语义所需的信息，不尝试复刻 javac 的所有内部类型。类型名统一保留简单名，
 * 工作区中的 package/import 解析由语义索引负责。
 */
internal sealed interface JavaType {
  /** 普通类、接口、类型变量或基本类型。 */
  data class Named(
    val name: String,
    val arguments: List<JavaType> = emptyList(),
  ) : JavaType

  /** Java 数组；多维数组通过嵌套 [Array] 表示。 */
  data class Array(val component: JavaType) : JavaType

  /** 通配符；无边界时两个 bound 均为 null。 */
  data class Wildcard(
    val upperBound: JavaType? = null,
    val lowerBound: JavaType? = null,
  ) : JavaType

  /** null 字面量，只能赋给引用类型。 */
  data object Null : JavaType

  /** 源码不完整或表达式超出轻量推断范围。 */
  data object Unknown : JavaType
}

/** Java 泛型形参及其上界；没有显式上界时等价于 Object。 */
internal data class JavaTypeParameter(
  val name: String,
  val upperBounds: List<JavaType> = emptyList(),
)

/** 一个可参与重载选择的方法或构造器签名。 */
internal data class JavaCallableCandidate<T>(
  val value: T,
  val typeParameters: List<JavaTypeParameter>,
  val parameterTypes: List<JavaType>,
  val returnType: JavaType?,
  val isVararg: Boolean = false,
)

/** 重载选择成功后得到的声明、泛型代换和实际返回类型。 */
internal data class JavaCallableMatch<T>(
  val value: T,
  val returnType: JavaType?,
  val substitutions: Map<String, JavaType>,
)

/**
 * 解析 Java 类型源码。
 *
 * 支持限定名、嵌套泛型、数组、可变参数以及通配符；源码不完整时返回 [JavaType.Unknown]。
 */
internal fun parseJavaType(source: String): JavaType {
  val parser = JavaTypeParser(source)
  return parser.parseType().takeIf { parser.isAtEnd() } ?: JavaType.Unknown
}

/** 解析泛型形参列表。 */
internal fun parseJavaTypeParameters(source: String): List<JavaTypeParameter> {
  val body = source.trim().removePrefix("<").removeSuffix(">")
  if (body.isBlank()) return emptyList()
  return body.splitJavaTopLevel(',').mapNotNull { declaration ->
    val text = declaration.trim()
    val name = text.takeWhile(Char::isJavaTypeIdentifierPart)
    if (name.isEmpty()) return@mapNotNull null
    val boundsSource = text.drop(name.length).trim()
      .removePrefix("extends")
      .trim()
    JavaTypeParameter(
      name = name,
      upperBounds = boundsSource
        .takeIf(String::isNotEmpty)
        ?.splitJavaTopLevel('&')
        .orEmpty()
        .map(::parseJavaType)
        .filterNot { type -> type == JavaType.Unknown },
    )
  }
}

/** 使用泛型形参映射递归替换类型。 */
internal fun JavaType.substitute(substitutions: Map<String, JavaType>): JavaType {
  return when (this) {
    is JavaType.Named -> substitutions[name]?.takeIf { arguments.isEmpty() }
      ?: copy(arguments = arguments.map { argument -> argument.substitute(substitutions) })
    is JavaType.Array -> JavaType.Array(component.substitute(substitutions))
    is JavaType.Wildcard -> copy(
      upperBound = upperBound?.substitute(substitutions),
      lowerBound = lowerBound?.substitute(substitutions),
    )
    JavaType.Null,
    JavaType.Unknown,
    -> this
  }
}

/** 把通配符转换为读取表达式时可保证的类型。 */
internal fun JavaType.readableType(): JavaType {
  return when (this) {
    is JavaType.Wildcard -> upperBound?.readableType() ?: JavaType.Named("Object")
    else -> this
  }
}

/** 返回便于缓存键、签名比较和测试断言使用的稳定类型文本。 */
internal fun JavaType.render(): String {
  return when (this) {
    is JavaType.Named -> buildString {
      append(name)
      if (arguments.isNotEmpty()) {
        append('<')
        append(arguments.joinToString(",") { argument -> argument.render() })
        append('>')
      }
    }
    is JavaType.Array -> component.render() + "[]"
    is JavaType.Wildcard -> when {
      upperBound != null -> "? extends " + upperBound.render()
      lowerBound != null -> "? super " + lowerBound.render()
      else -> "?"
    }
    JavaType.Null -> "null"
    JavaType.Unknown -> "<unknown>"
  }
}

/**
 * Java 类型关系计算器。
 *
 * [directSuperTypes] 由工作区层提供自定义类型的直接父类型；内置教学类型关系会自动合并。
 */
internal class JavaTypeRelations(
  private val directSuperTypes: (JavaType.Named) -> List<JavaType.Named> = { emptyList() },
) {

  /** 判断 [actual] 是否可按日常 Java 调用规则传给 [target]。 */
  fun isAssignable(actual: JavaType, target: JavaType): Boolean {
    return conversionCost(actual, target) != null
  }

  /**
   * 返回隐式转换成本，数字越小越优先。
   *
   * 覆盖精确匹配、引用继承、泛型通配符、null、基本类型拓宽和基础装箱/拆箱。
   */
  fun conversionCost(actual: JavaType, target: JavaType): Int? {
    if (actual == JavaType.Unknown || target == JavaType.Unknown) return null
    if (target is JavaType.Wildcard) return wildcardCost(actual, target)
    if (actual == JavaType.Null) return if (target.isReferenceType()) 8 else null
    if (actual == target) return 0
    if (actual is JavaType.Array && target is JavaType.Array) {
      return conversionCost(actual.component, target.component)
    }
    if (actual is JavaType.Array && target is JavaType.Named && target.name == "Object") return 3
    if (actual !is JavaType.Named || target !is JavaType.Named) return null

    primitiveConversionCost(actual.name, target.name)?.let { return it }
    boxedConversionCost(actual, target)?.let { return it }
    return referenceDistance(actual, target)
  }

  /** 在两个候选成本相同时判断左侧参数是否整体更具体。 */
  fun isMoreSpecific(left: List<JavaType>, right: List<JavaType>): Boolean {
    if (left.size != right.size) return false
    var strictlyMoreSpecific = false
    left.zip(right).forEach { (leftType, rightType) ->
      if (!isAssignable(leftType, rightType)) return false
      if (!isAssignable(rightType, leftType)) strictlyMoreSpecific = true
    }
    return strictlyMoreSpecific
  }

  /** 为重复出现的泛型形参选择可安全表达的最近公共类型。 */
  fun commonType(first: JavaType, second: JavaType): JavaType? {
    if (first == second) return first
    if (isAssignable(first, second)) return second
    if (isAssignable(second, first)) return first
    if (first.isReferenceType() && second.isReferenceType()) return JavaType.Named("Object")
    return null
  }

  /** 计算引用继承距离，并在同原始类型时校验泛型实参。 */
  private fun referenceDistance(actual: JavaType.Named, target: JavaType.Named): Int? {
    // 所有非基本 Java 引用类型都可拓宽为 Object；工作区模型无需为每个 class 人工补父类。
    if (target.name == "Object" && actual.name !in JAVA_PRIMITIVE_TYPES) return if (actual.name == "Object") 0 else 1
    val queue = ArrayDeque<Pair<JavaType.Named, Int>>()
    val visited = mutableSetOf<String>()
    queue.addLast(actual to 0)
    while (queue.isNotEmpty()) {
      val (current, distance) = queue.removeFirst()
      if (!visited.add(current.render())) continue
      if (current.name == target.name && argumentsAssignable(current.arguments, target.arguments)) {
        return distance
      }
      allDirectSuperTypes(current).forEach { superType -> queue.addLast(superType to distance + 1) }
    }
    return null
  }

  /** Java 泛型默认不变，仅允许目标通配符放宽单个实参。 */
  private fun argumentsAssignable(actual: List<JavaType>, target: List<JavaType>): Boolean {
    if (target.isEmpty()) return true
    if (actual.size != target.size) return false
    return actual.zip(target).all { (actualArgument, targetArgument) ->
      when (targetArgument) {
        is JavaType.Wildcard -> wildcardCost(actualArgument.readableType(), targetArgument) != null
        else -> actualArgument == targetArgument
      }
    }
  }

  /** 校验 extends/super 通配符的读写边界。 */
  private fun wildcardCost(actual: JavaType, target: JavaType.Wildcard): Int? {
    target.upperBound?.let { upper ->
      return conversionCost(actual.readableType(), upper)?.plus(1)
    }
    target.lowerBound?.let { lower ->
      return conversionCost(lower, actual.readableType())?.plus(1)
    }
    return if (actual.isReferenceType()) 6 else null
  }

  /** 合并内置类型关系和工作区类型关系，并代换内置父类型的泛型形参。 */
  private fun allDirectSuperTypes(type: JavaType.Named): List<JavaType.Named> {
    val builtins = JAVA_BUILTIN_SUPER_TYPES[type.name].orEmpty().map { template ->
      val parameterNames = JAVA_BUILTIN_TYPE_PARAMETERS[type.name].orEmpty()
      val substitutions = parameterNames.zip(type.arguments).toMap()
      template.substitute(substitutions) as JavaType.Named
    }
    return builtins + directSuperTypes(type)
  }
}

/**
 * 根据实参类型选择唯一最佳重载，并推断方法泛型形参。
 *
 * 无法确定实参类型、多个候选同样具体、边界不满足时返回 null。
 */
internal fun <T> resolveJavaOverload(
  candidates: List<JavaCallableCandidate<T>>,
  argumentTypes: List<JavaType>,
  relations: JavaTypeRelations,
): JavaCallableMatch<T>? {
  val applicable = candidates.mapNotNull { candidate ->
    val expandedParameters = candidate.expandParameters(argumentTypes.size) ?: return@mapNotNull null
    val substitutions = linkedMapOf<String, JavaType>()
    val typeParameterNames = candidate.typeParameters.mapTo(mutableSetOf(), JavaTypeParameter::name)
    expandedParameters.zip(argumentTypes).forEach { (parameter, argument) ->
      if (!inferJavaTypeArguments(parameter, argument, typeParameterNames, substitutions, relations)) {
        return@mapNotNull null
      }
    }
    if (!candidate.typeParameters.boundsSatisfied(substitutions, relations)) return@mapNotNull null
    val substitutedParameters = expandedParameters.map { parameter -> parameter.substitute(substitutions) }
    val costs = argumentTypes.zip(substitutedParameters).map { (argument, parameter) ->
      relations.conversionCost(argument, parameter) ?: return@mapNotNull null
    }
    JavaOverloadScore(
      match = JavaCallableMatch(
        value = candidate.value,
        returnType = candidate.returnType?.substitute(substitutions)?.readableType(),
        substitutions = substitutions,
      ),
      parameters = substitutedParameters,
      totalCost = costs.sum(),
      varargPenalty = if (candidate.isVararg) 1 else 0,
    )
  }
  if (applicable.isEmpty()) return null

  val bestCost = applicable.minOf { score -> score.totalCost + score.varargPenalty }
  val best = applicable.filter { score -> score.totalCost + score.varargPenalty == bestCost }
  if (best.size == 1) return best.single().match

  val mostSpecific = best.filter { candidate ->
    best.all { other ->
      candidate === other || relations.isMoreSpecific(candidate.parameters, other.parameters)
    }
  }
  return mostSpecific.singleOrNull()?.match
}

/** 重载选择期间使用的评分。 */
private data class JavaOverloadScore<T>(
  val match: JavaCallableMatch<T>,
  val parameters: List<JavaType>,
  val totalCost: Int,
  val varargPenalty: Int,
)

/** 按实参数量展开普通参数或最后一个可变参数。 */
private fun <T> JavaCallableCandidate<T>.expandParameters(argumentCount: Int): List<JavaType>? {
  if (!isVararg) return parameterTypes.takeIf { it.size == argumentCount }
  if (parameterTypes.isEmpty() || argumentCount < parameterTypes.size - 1) return null
  val fixed = parameterTypes.dropLast(1)
  val elementType = (parameterTypes.last() as? JavaType.Array)?.component ?: return null
  return fixed + List(argumentCount - fixed.size) { elementType }
}

/** 从参数类型和实参类型递归推断方法泛型形参。 */
private fun inferJavaTypeArguments(
  parameter: JavaType,
  argument: JavaType,
  typeParameterNames: Set<String>,
  substitutions: MutableMap<String, JavaType>,
  relations: JavaTypeRelations,
): Boolean {
  if (parameter is JavaType.Named && parameter.name in typeParameterNames && parameter.arguments.isEmpty()) {
    val inferred = argument.readableType()
    val previous = substitutions[parameter.name]
    substitutions[parameter.name] = previous?.let { relations.commonType(it, inferred) ?: return false } ?: inferred
    return true
  }
  if (parameter is JavaType.Array && argument is JavaType.Array) {
    return inferJavaTypeArguments(
      parameter.component,
      argument.component,
      typeParameterNames,
      substitutions,
      relations,
    )
  }
  if (parameter is JavaType.Named && argument is JavaType.Named && parameter.name == argument.name) {
    if (parameter.arguments.size != argument.arguments.size) return parameter.arguments.isEmpty()
    return parameter.arguments.zip(argument.arguments).all { (parameterArgument, argumentArgument) ->
      val effectiveParameter = when (parameterArgument) {
        is JavaType.Wildcard -> parameterArgument.upperBound ?: parameterArgument.lowerBound ?: return@all true
        else -> parameterArgument
      }
      inferJavaTypeArguments(
        effectiveParameter,
        argumentArgument.readableType(),
        typeParameterNames,
        substitutions,
        relations,
      )
    }
  }
  return true
}

/** 校验推断出的泛型实参满足声明上界。 */
private fun List<JavaTypeParameter>.boundsSatisfied(
  substitutions: Map<String, JavaType>,
  relations: JavaTypeRelations,
): Boolean {
  return all { parameter ->
    val inferred = substitutions[parameter.name] ?: return@all true
    parameter.upperBounds.all { bound ->
      relations.isAssignable(inferred, bound.substitute(substitutions))
    }
  }
}

/** 判断类型是否能保存引用或 null。 */
private fun JavaType.isReferenceType(): Boolean {
  return when (this) {
    is JavaType.Array,
    is JavaType.Wildcard,
    -> true
    is JavaType.Named -> name !in JAVA_PRIMITIVE_TYPES
    JavaType.Null -> true
    JavaType.Unknown -> false
  }
}

/** 基本类型拓宽成本；不支持隐式窄化。 */
private fun primitiveConversionCost(actual: String, target: String): Int? {
  if (actual !in JAVA_PRIMITIVE_TYPES || target !in JAVA_PRIMITIVE_TYPES) return null
  if (actual == target) return 0
  val widening = JAVA_PRIMITIVE_WIDENING[actual].orEmpty()
  val index = widening.indexOf(target)
  return index.takeIf { it >= 0 }?.plus(1)
}

/** 基础装箱、拆箱及装箱后引用拓宽成本。 */
private fun boxedConversionCost(actual: JavaType.Named, target: JavaType.Named): Int? {
  val boxed = JAVA_BOXED_TYPES[actual.name]
  if (boxed == target.name) return 3
  val unboxed = JAVA_BOXED_TYPES.entries.firstOrNull { entry -> entry.value == actual.name }?.key
  if (unboxed == target.name) return 3
  if (boxed != null && target.name in setOf("Number", "Object")) return 4
  return null
}

/** Java 类型文本的递归下降解析器。 */
private class JavaTypeParser(private val source: String) {
  private var index = 0

  fun parseType(): JavaType {
    skipWhitespace()
    if (consume('?')) {
      skipWhitespace()
      return when {
        consumeWord("extends") -> JavaType.Wildcard(upperBound = parseType())
        consumeWord("super") -> JavaType.Wildcard(lowerBound = parseType())
        else -> JavaType.Wildcard()
      }
    }

    val qualifiedName = parseQualifiedName()
    if (qualifiedName.isEmpty()) return JavaType.Unknown
    val arguments = if (consume('<')) {
      buildList {
        do {
          add(parseType())
          skipWhitespace()
        } while (consume(','))
        if (!consume('>')) return JavaType.Unknown
      }
    } else {
      emptyList()
    }
    var result: JavaType = JavaType.Named(qualifiedName.substringAfterLast('.'), arguments)
    while (true) {
      skipWhitespace()
      result = when {
        source.startsWith("[]", index) -> {
          index += 2
          JavaType.Array(result)
        }
        source.startsWith("...", index) -> {
          index += 3
          JavaType.Array(result)
        }
        else -> return result
      }
    }
  }

  fun isAtEnd(): Boolean {
    skipWhitespace()
    return index == source.length
  }

  private fun parseQualifiedName(): String {
    skipWhitespace()
    val start = index
    while (index < source.length) {
      val character = source[index]
      if (!character.isJavaTypeIdentifierPart() && character != '.') break
      index += 1
    }
    return source.substring(start, index)
  }

  private fun consume(expected: Char): Boolean {
    skipWhitespace()
    if (source.getOrNull(index) != expected) return false
    index += 1
    return true
  }

  private fun consumeWord(expected: String): Boolean {
    skipWhitespace()
    if (!source.startsWith(expected, index)) return false
    val end = index + expected.length
    if (source.getOrNull(end)?.isJavaTypeIdentifierPart() == true) return false
    index = end
    return true
  }

  private fun skipWhitespace() {
    while (source.getOrNull(index)?.isWhitespace() == true) index += 1
  }
}

/** 按泛型/括号深度切分 Java 源码。 */
internal fun String.splitJavaTopLevel(separator: Char): List<String> {
  val result = mutableListOf<String>()
  var angleDepth = 0
  var parenthesisDepth = 0
  var bracketDepth = 0
  var start = 0
  forEachIndexed { index, character ->
    when (character) {
      '<' -> angleDepth += 1
      '>' -> if (angleDepth > 0) angleDepth -= 1
      '(' -> parenthesisDepth += 1
      ')' -> if (parenthesisDepth > 0) parenthesisDepth -= 1
      '[' -> bracketDepth += 1
      ']' -> if (bracketDepth > 0) bracketDepth -= 1
      separator -> if (angleDepth == 0 && parenthesisDepth == 0 && bracketDepth == 0) {
        result += substring(start, index)
        start = index + 1
      }
    }
  }
  result += substring(start)
  return result
}

private fun Char.isJavaTypeIdentifierPart(): Boolean {
  return isLetterOrDigit() || this == '_' || this == '$'
}

private val JAVA_PRIMITIVE_TYPES = setOf(
  "boolean", "byte", "short", "char", "int", "long", "float", "double", "void",
)

private val JAVA_PRIMITIVE_WIDENING = mapOf(
  "byte" to listOf("short", "int", "long", "float", "double"),
  "short" to listOf("int", "long", "float", "double"),
  "char" to listOf("int", "long", "float", "double"),
  "int" to listOf("long", "float", "double"),
  "long" to listOf("float", "double"),
  "float" to listOf("double"),
)

private val JAVA_BOXED_TYPES = mapOf(
  "boolean" to "Boolean",
  "byte" to "Byte",
  "short" to "Short",
  "char" to "Character",
  "int" to "Integer",
  "long" to "Long",
  "float" to "Float",
  "double" to "Double",
)

private val JAVA_BUILTIN_TYPE_PARAMETERS = mapOf(
  "Iterable" to listOf("E"),
  "Collection" to listOf("E"),
  "List" to listOf("E"),
  "ArrayList" to listOf("E"),
  "Set" to listOf("E"),
  "HashSet" to listOf("E"),
  "Map" to listOf("K", "V"),
  "HashMap" to listOf("K", "V"),
)

private val JAVA_BUILTIN_SUPER_TYPES = mapOf(
  "String" to listOf(JavaType.Named("Object")),
  "StringBuilder" to listOf(JavaType.Named("Object")),
  "Number" to listOf(JavaType.Named("Object")),
  "Byte" to listOf(JavaType.Named("Number")),
  "Short" to listOf(JavaType.Named("Number")),
  "Integer" to listOf(JavaType.Named("Number")),
  "Long" to listOf(JavaType.Named("Number")),
  "Float" to listOf(JavaType.Named("Number")),
  "Double" to listOf(JavaType.Named("Number")),
  "Character" to listOf(JavaType.Named("Object")),
  "Boolean" to listOf(JavaType.Named("Object")),
  "Collection" to listOf(JavaType.Named("Iterable", listOf(JavaType.Named("E")))),
  "List" to listOf(JavaType.Named("Collection", listOf(JavaType.Named("E")))),
  "ArrayList" to listOf(JavaType.Named("List", listOf(JavaType.Named("E")))),
  "Set" to listOf(JavaType.Named("Collection", listOf(JavaType.Named("E")))),
  "HashSet" to listOf(JavaType.Named("Set", listOf(JavaType.Named("E")))),
  "HashMap" to listOf(JavaType.Named("Map", listOf(JavaType.Named("K"), JavaType.Named("V")))),
)
