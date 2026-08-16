package com.cyxbs.functions.code.language.java.compiler.semantic

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType

/**
 * 阶段 1 使用的纯 Java 类型关系计算器。
 *
 * 本类只依赖已冻结的声明表，以精确 [JavaSymbolId] 判断类型身份，不读取限定名或产生诊断。
 * 损坏模型、raw type、尚未支持的 capture/boxing/vararg 等情况会保守返回 null 或 false，
 * 调用方应在自身阶段把失败转换为对应诊断。
 *
 * [objectSymbol] 必须指向声明表中的内建 java.lang.Object；该符号用于无界通配符擦除和数组
 * 到 Object 的引用拓宽，禁止通过名称猜测根类型。
 */
internal class JavaTypeRelations(
  private val typeDeclarations: Map<JavaSymbolId, JavaSemanticTypeDeclaration>,
  private val typeParameterDeclarations:
    Map<JavaSymbolId, JavaSemanticTypeParameterDeclaration>,
  private val objectSymbol: JavaSymbolId,
) {
  /**
   * 递归代换声明类型、类型变量、数组与通配符中的类型变量。
   *
   * 未出现在 [substitutions] 中的类型变量保持不变，`T -> T` 也视为合法恒等映射；跨变量形成的
   * `T -> U -> T` 环、包含 ERROR 或产生非法通配符时返回 null。
   */
  fun substitute(
    type: JavaSemanticType,
    substitutions: Map<JavaSymbolId, JavaSemanticType>,
  ): JavaSemanticType? {
    return substitute(type, substitutions, mutableSetOf())
  }

  /**
   * 计算 Java 擦除类型。
   *
   * 声明类型擦除后只保留 symbol，其空参数结果仅用于 descriptor/override，不可再参与成员查找；
   * 类型变量使用首个上界，无界通配符使用 [objectSymbol]。raw 输入或递归上界返回 null。
   */
  fun erasure(type: JavaSemanticType): JavaSemanticType? {
    return erasure(type, mutableSetOf())
  }

  /**
   * 将 [source] 沿直接父类链投影成 [targetSymbol] 的参数化声明类型。
   *
   * 每跨越一层都会按当前声明的类型参数代换父类实参。继承环、缺失声明、raw type
   * 或不完整类型参数声明均返回 null，确保损坏模型不会导致无限递归。
   */
  fun asSupertype(
    source: JavaSemanticType.Declared,
    targetSymbol: JavaSymbolId,
  ): JavaSemanticType.Declared? {
    if (typeDeclarations[targetSymbol] == null || !isWellFormedDeclared(source)) return null

    var current = source
    val visited = mutableSetOf<JavaSymbolId>()
    while (visited.add(current.symbol)) {
      if (current.symbol == targetSymbol) return current

      val declaration = typeDeclarations[current.symbol] ?: return null
      val directSuperClass = declaration.directSuperClass ?: return null
      val substitutions = declaration.typeParameters.zip(current.arguments).toMap()
      val substituted = substitute(directSuperClass, substitutions)
        as? JavaSemanticType.Declared ?: return null
      if (!isWellFormedDeclared(substituted)) return null
      current = substituted
    }
    return null
  }

  /**
   * 判断 [source] 是否是 [target] 的子类型。
   *
   * 参数化声明默认 invariant；仅目标类型参数为 ?、? extends 或 ? super 时应用 Java 8
   * 的基础 containment 规则。primitive widening 不属于子类型关系，应使用
   * [assignmentConversion]。
   */
  fun isSubtype(
    source: JavaSemanticType,
    target: JavaSemanticType,
  ): Boolean {
    return isSubtype(source, target, mutableSetOf())
  }

  /**
   * 返回赋值上下文允许的最小转换。
   *
   * 当前只支持 identity、primitive widening、null 到引用类型以及引用拓宽；
   * boxing、unboxing、常量窄化与 vararg 联合留给后续阶段。
   */
  fun assignmentConversion(
    source: JavaSemanticType,
    target: JavaSemanticType,
  ): JavaSemanticConversion? {
    if (!isAssignmentSourceType(source) || !isAssignmentTargetType(target)) return null
    if (source == target) return JavaSemanticConversion.Identity

    if (source is JavaSemanticType.Primitive && target is JavaSemanticType.Primitive) {
      return if (canWidenPrimitive(source.kind, target.kind)) {
        JavaSemanticConversion.PrimitiveWidening(source.kind, target.kind)
      } else {
        null
      }
    }

    if (source == JavaSemanticType.Null && isReferenceType(target)) {
      return JavaSemanticConversion.ReferenceWidening(source, target)
    }

    return if (isReferenceType(source) && isReferenceType(target) && isSubtype(source, target)) {
      JavaSemanticConversion.ReferenceWidening(source, target)
    } else {
      null
    }
  }

  /** 判断赋值上下文是否存在当前阶段支持的转换。 */
  fun isAssignmentCompatible(
    source: JavaSemanticType,
    target: JavaSemanticType,
  ): Boolean {
    return assignmentConversion(source, target) != null
  }

  /** 递归代换实现；[visiting] 防止 T->U、U->T 一类损坏映射。 */
  private fun substitute(
    type: JavaSemanticType,
    substitutions: Map<JavaSymbolId, JavaSemanticType>,
    visiting: MutableSet<JavaSymbolId>,
  ): JavaSemanticType? {
    return when (type) {
      is JavaSemanticType.Primitive,
      JavaSemanticType.Null,
      JavaSemanticType.Void,
      -> type

      JavaSemanticType.Error -> null

      is JavaSemanticType.Array -> {
        substitute(type.componentType, substitutions, visiting)
          ?.let(JavaSemanticType::Array)
      }

      is JavaSemanticType.Declared -> {
        val arguments = type.arguments.map { argument ->
          substitute(argument, substitutions, visiting) ?: return null
        }
        type.copy(arguments = arguments)
      }

      is JavaSemanticType.TypeVariable -> {
        val replacement = substitutions[type.symbol] ?: return type
        // owner 自类型会自然产生 T -> T；它不展开也不构成递归，必须保留原类型变量。
        if (replacement == type) return type
        if (!visiting.add(type.symbol)) return null
        val result = substitute(replacement, substitutions, visiting)
        visiting.remove(type.symbol)
        result
      }

      is JavaSemanticType.Wildcard -> {
        if (type.upperBound != null && type.lowerBound != null) return null
        val upperBound = type.upperBound?.let {
          substitute(it, substitutions, visiting) ?: return null
        }
        val lowerBound = type.lowerBound?.let {
          substitute(it, substitutions, visiting) ?: return null
        }
        JavaSemanticType.Wildcard(upperBound = upperBound, lowerBound = lowerBound)
      }
    }
  }

  /** 类型变量擦除时跟踪正在访问的参数，拒绝无法收敛的递归边界。 */
  private fun erasure(
    type: JavaSemanticType,
    visiting: MutableSet<JavaSymbolId>,
  ): JavaSemanticType? {
    return when (type) {
      is JavaSemanticType.Primitive,
      JavaSemanticType.Void,
      -> type

      JavaSemanticType.Null,
      JavaSemanticType.Error,
      -> null

      is JavaSemanticType.Array -> {
        erasure(type.componentType, visiting)?.let(JavaSemanticType::Array)
      }

      is JavaSemanticType.Declared -> {
        if (!isWellFormedDeclared(type)) null
        else JavaSemanticType.Declared(type.symbol, emptyList())
      }

      is JavaSemanticType.TypeVariable -> {
        if (!visiting.add(type.symbol)) return null
        val declaration = typeParameterDeclarations[type.symbol] ?: return null
        val firstUpperBound = declaration.upperBounds.firstOrNull() ?: return null
        val result = erasure(firstUpperBound, visiting)
        visiting.remove(type.symbol)
        result
      }

      is JavaSemanticType.Wildcard -> {
        if (type.upperBound != null && type.lowerBound != null) return null
        val upperBound = type.upperBound ?: objectTypeOrNull() ?: return null
        erasure(upperBound, visiting)
      }
    }
  }

  /** 带环检测的子类型实现，避免损坏的类型参数上界反复进入自身。 */
  private fun isSubtype(
    source: JavaSemanticType,
    target: JavaSemanticType,
    visiting: MutableSet<TypeRelation>,
  ): Boolean {
    if (!isSubtypeOperand(source) || !isSubtypeOperand(target)) return false
    if (source == target) return true
    val relation = TypeRelation(source, target)
    if (!visiting.add(relation)) return false

    val result = when {
      source == JavaSemanticType.Error || target == JavaSemanticType.Error -> false
      source == JavaSemanticType.Null -> isReferenceType(target)
      source is JavaSemanticType.Declared && target is JavaSemanticType.Declared ->
        isDeclaredSubtype(source, target, visiting)
      source is JavaSemanticType.Array && target is JavaSemanticType.Array ->
        isArrayComponentSubtype(source.componentType, target.componentType, visiting)
      source is JavaSemanticType.Array &&
        target == JavaSemanticType.Declared(objectSymbol, emptyList()) ->
        objectTypeOrNull() != null
      source is JavaSemanticType.TypeVariable -> {
        val declaration = typeParameterDeclarations[source.symbol]
        declaration != null &&
          declaration.upperBounds.any { isSubtype(it, target, visiting) }
      }
      else -> false
    }

    visiting.remove(relation)
    return result
  }

  /** 先投影到相同声明，再按 invariant/目标通配符规则比较实参。 */
  private fun isDeclaredSubtype(
    source: JavaSemanticType.Declared,
    target: JavaSemanticType.Declared,
    visiting: MutableSet<TypeRelation>,
  ): Boolean {
    if (!isWellFormedDeclared(source) || !isWellFormedDeclared(target)) return false
    val projected = asSupertype(source, target.symbol) ?: return false
    return projected.arguments.zip(target.arguments).all { (sourceArgument, targetArgument) ->
      acceptsTypeArgument(sourceArgument, targetArgument, visiting)
    }
  }

  /** 实现目标通配符的基础 containment；源通配符仅传播其已知边界。 */
  private fun acceptsTypeArgument(
    source: JavaSemanticType,
    target: JavaSemanticType,
    visiting: MutableSet<TypeRelation>,
  ): Boolean {
    if (target !is JavaSemanticType.Wildcard) return source == target
    if (target.upperBound == null && target.lowerBound == null) return true
    if (target.upperBound != null && target.lowerBound != null) return false

    target.upperBound?.let { targetUpper ->
      val sourceUpper = when (source) {
        is JavaSemanticType.Wildcard -> {
          if (source.lowerBound != null) return false
          source.upperBound ?: objectTypeOrNull() ?: return false
        }
        else -> source
      }
      return isSubtype(sourceUpper, targetUpper, visiting)
    }

    val targetLower = target.lowerBound ?: return false
    val sourceLower = when (source) {
      is JavaSemanticType.Wildcard -> source.lowerBound ?: return false
      else -> source
    }
    return isSubtype(targetLower, sourceLower, visiting)
  }

  /** Java 数组只对引用 component 协变，primitive component 必须精确相同。 */
  private fun isArrayComponentSubtype(
    source: JavaSemanticType,
    target: JavaSemanticType,
    visiting: MutableSet<TypeRelation>,
  ): Boolean {
    if (source is JavaSemanticType.Primitive || target is JavaSemanticType.Primitive) {
      return source == target
    }
    return isSubtype(source, target, visiting)
  }

  /** 校验声明类型不是 raw，并递归确认类型实参可供本阶段安全使用。 */
  private fun isWellFormedDeclared(type: JavaSemanticType.Declared): Boolean {
    val declaration = typeDeclarations[type.symbol] ?: return false
    if (declaration.typeParameters.size != type.arguments.size) return false
    if (declaration.typeParameters.any { parameter ->
        typeParameterDeclarations[parameter]?.owner != type.symbol
      }
    ) {
      return false
    }
    return type.arguments.all(::isWellFormedTypeArgument)
  }

  /** 类型实参不接受 primitive、void、null 或恢复类型，避免把非法模型当成兼容。 */
  private fun isWellFormedTypeArgument(type: JavaSemanticType): Boolean {
    return when (type) {
      is JavaSemanticType.Declared -> isWellFormedDeclared(type)
      is JavaSemanticType.TypeVariable -> typeParameterDeclarations[type.symbol] != null
      is JavaSemanticType.Array -> isWellFormedArrayComponent(type.componentType)
      is JavaSemanticType.Wildcard -> {
        if (type.upperBound != null && type.lowerBound != null) {
          false
        } else {
          val bound = type.upperBound ?: type.lowerBound
          bound == null || isWellFormedReferenceBound(bound)
        }
      }
      is JavaSemanticType.Primitive,
      JavaSemanticType.Null,
      JavaSemanticType.Void,
      JavaSemanticType.Error,
      -> false
    }
  }

  /** 数组 component 可继续嵌套，但不能是 void、null、wildcard 或恢复类型。 */
  private fun isWellFormedArrayComponent(type: JavaSemanticType): Boolean {
    return when (type) {
      is JavaSemanticType.Primitive -> true
      is JavaSemanticType.Declared -> isWellFormedDeclared(type)
      is JavaSemanticType.TypeVariable -> typeParameterDeclarations[type.symbol] != null
      is JavaSemanticType.Array -> isWellFormedArrayComponent(type.componentType)
      is JavaSemanticType.Wildcard,
      JavaSemanticType.Null,
      JavaSemanticType.Void,
      JavaSemanticType.Error,
      -> false
    }
  }

  /** 判断类型能否承接 null/reference widening，不把 wildcard 当作独立运行时值类型。 */
  private fun isReferenceType(type: JavaSemanticType): Boolean {
    return when (type) {
      is JavaSemanticType.Declared -> isWellFormedDeclared(type)
      is JavaSemanticType.TypeVariable -> typeParameterDeclarations[type.symbol] != null
      is JavaSemanticType.Array -> isWellFormedArrayComponent(type.componentType)
      else -> false
    }
  }

  /** 通配符边界只能是已解析的引用类型，不能继续嵌套 wildcard。 */
  private fun isWellFormedReferenceBound(type: JavaSemanticType): Boolean {
    return when (type) {
      is JavaSemanticType.Declared -> isWellFormedDeclared(type)
      is JavaSemanticType.TypeVariable -> typeParameterDeclarations[type.symbol] != null
      is JavaSemanticType.Array -> isWellFormedArrayComponent(type.componentType)
      else -> false
    }
  }

  /** 子类型关系只接收 proper value type；raw、void、wildcard 和 ERROR 均拒绝。 */
  private fun isSubtypeOperand(type: JavaSemanticType): Boolean {
    return type is JavaSemanticType.Primitive || type == JavaSemanticType.Null ||
      isReferenceType(type)
  }

  /** 赋值源允许 null，但不允许 void、wildcard、raw 或恢复类型。 */
  private fun isAssignmentSourceType(type: JavaSemanticType): Boolean {
    return type == JavaSemanticType.Null || type is JavaSemanticType.Primitive ||
      isReferenceType(type)
  }

  /** 赋值目标必须是 primitive 或 proper reference type，null 不是可声明目标类型。 */
  private fun isAssignmentTargetType(type: JavaSemanticType): Boolean {
    return type is JavaSemanticType.Primitive || isReferenceType(type)
  }

  /** 返回经模型验证的 Object 类型，声明缺失或携带泛型参数视为损坏模型。 */
  private fun objectTypeOrNull(): JavaSemanticType.Declared? {
    val declaration = typeDeclarations[objectSymbol] ?: return null
    if (declaration.typeParameters.isNotEmpty()) return null
    return JavaSemanticType.Declared(objectSymbol, emptyList())
  }

  /** Java 8 赋值上下文允许的 primitive widening 表。 */
  private fun canWidenPrimitive(
    source: JavaAstPrimitiveType,
    target: JavaAstPrimitiveType,
  ): Boolean {
    return when (source) {
      JavaAstPrimitiveType.BYTE ->
        target in setOf(
          JavaAstPrimitiveType.SHORT,
          JavaAstPrimitiveType.INT,
          JavaAstPrimitiveType.LONG,
          JavaAstPrimitiveType.FLOAT,
          JavaAstPrimitiveType.DOUBLE,
        )
      JavaAstPrimitiveType.SHORT ->
        target in setOf(
          JavaAstPrimitiveType.INT,
          JavaAstPrimitiveType.LONG,
          JavaAstPrimitiveType.FLOAT,
          JavaAstPrimitiveType.DOUBLE,
        )
      JavaAstPrimitiveType.CHAR ->
        target in setOf(
          JavaAstPrimitiveType.INT,
          JavaAstPrimitiveType.LONG,
          JavaAstPrimitiveType.FLOAT,
          JavaAstPrimitiveType.DOUBLE,
        )
      JavaAstPrimitiveType.INT ->
        target in setOf(
          JavaAstPrimitiveType.LONG,
          JavaAstPrimitiveType.FLOAT,
          JavaAstPrimitiveType.DOUBLE,
        )
      JavaAstPrimitiveType.LONG ->
        target in setOf(JavaAstPrimitiveType.FLOAT, JavaAstPrimitiveType.DOUBLE)
      JavaAstPrimitiveType.FLOAT -> target == JavaAstPrimitiveType.DOUBLE
      JavaAstPrimitiveType.BOOLEAN,
      JavaAstPrimitiveType.DOUBLE,
      -> false
    }
  }

  /** 一次递归子类型判定中的访问边，使用完整类型避免泛型路径错误去重。 */
  private data class TypeRelation(
    val source: JavaSemanticType,
    val target: JavaSemanticType,
  )
}
