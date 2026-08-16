package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticType
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticTypeParameterDeclaration
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSymbolId
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaTypeRelations

/**
 * 阶段 1 方法与 diamond 构造器共用的保守泛型实参推断器。
 *
 * 仅从实参约束推断，不读取 target type；同一类型变量出现多个候选时只选择已有候选中的
 * 最小公共可赋值类型。无法唯一收敛、违反上界或需要 capture/F-bound 复杂求解时返回 null。
 */
internal class JavaGenericInference(
  private val relations: JavaTypeRelations,
  private val typeParameterDeclarations:
    Map<JavaSymbolId, JavaSemanticTypeParameterDeclaration>,
) {
  /**
   * 推断或校验一组 callable 类型参数。
   *
   * [explicitTypeArguments] 非空时必须与 [typeParameters] 等长；为空时从每个 formal/actual
   * 对递归收集约束。[fixedSubstitutions] 保存 receiver owner 已确定的 class 类型实参，使
   * `C<T>.<U extends T> U id(U)` 的 U 上界能先把 T 代换到实际 receiver 类型。
   * 返回值只包含本次 callable 声明的参数，调用方再用它代换参数和返回类型。
   */
  fun infer(
    typeParameters: List<JavaSymbolId>,
    formalParameterTypes: List<JavaSemanticType>,
    actualArgumentTypes: List<JavaSemanticType>,
    explicitTypeArguments: List<JavaSemanticType> = emptyList(),
    fixedSubstitutions: Map<JavaSymbolId, JavaSemanticType> = emptyMap(),
  ): Map<JavaSymbolId, JavaSemanticType>? {
    if (formalParameterTypes.size != actualArgumentTypes.size) return null
    if (explicitTypeArguments.isNotEmpty()) {
      if (explicitTypeArguments.size != typeParameters.size) return null
      return typeParameters.zip(explicitTypeArguments).toMap()
        .takeIf { satisfiesBounds(it, fixedSubstitutions) }
    }

    val candidates = linkedMapOf<JavaSymbolId, JavaSemanticType>()
    formalParameterTypes.zip(actualArgumentTypes).forEach { (formal, actual) ->
      if (!collect(formal, actual, typeParameters.toSet(), candidates)) return null
    }
    if (typeParameters.any { it !in candidates }) return null
    return candidates.takeIf { satisfiesBounds(it, fixedSubstitutions) }
  }

  /** 递归收集 invariant 参数位置约束；通配符只使用明确的 extends/super 边界。 */
  private fun collect(
    formal: JavaSemanticType,
    actual: JavaSemanticType,
    inferable: Set<JavaSymbolId>,
    candidates: MutableMap<JavaSymbolId, JavaSemanticType>,
  ): Boolean {
    return when {
      formal is JavaSemanticType.TypeVariable && formal.symbol in inferable ->
        merge(formal.symbol, actual, candidates)
      formal is JavaSemanticType.Array && actual is JavaSemanticType.Array ->
        collect(formal.componentType, actual.componentType, inferable, candidates)
      formal is JavaSemanticType.Declared && actual is JavaSemanticType.Declared -> {
        val projected = relations.asSupertype(actual, formal.symbol) ?: return false
        formal.arguments.zip(projected.arguments).all { (nestedFormal, nestedActual) ->
          collect(nestedFormal, nestedActual, inferable, candidates)
        }
      }
      formal is JavaSemanticType.Wildcard && formal.upperBound != null ->
        collect(formal.upperBound, actual, inferable, candidates)
      formal is JavaSemanticType.Wildcard && formal.lowerBound != null ->
        collect(formal.lowerBound, actual, inferable, candidates)
      else -> relations.isAssignmentCompatible(actual, formal)
    }
  }

  /** 多个约束只在其中一个候选可承接另一个时合并，避免猜测不存在的 LUB。 */
  private fun merge(
    symbol: JavaSymbolId,
    candidate: JavaSemanticType,
    candidates: MutableMap<JavaSymbolId, JavaSemanticType>,
  ): Boolean {
    val previous = candidates[symbol]
    if (previous == null) {
      candidates[symbol] = candidate
      return true
    }
    candidates[symbol] = when {
      previous == candidate -> previous
      relations.isSubtype(previous, candidate) -> candidate
      relations.isSubtype(candidate, previous) -> previous
      else -> return false
    }
    return true
  }

  /** 将已经得到的全部实参代换进上界后逐一校验。 */
  private fun satisfiesBounds(
    substitutions: Map<JavaSymbolId, JavaSemanticType>,
    fixedSubstitutions: Map<JavaSymbolId, JavaSemanticType>,
  ): Boolean {
    val allSubstitutions = fixedSubstitutions + substitutions
    return substitutions.all { (symbol, actual) ->
      val declaration = typeParameterDeclarations[symbol] ?: return false
      declaration.upperBounds.all { bound ->
        val substitutedBound = relations.substitute(bound, allSubstitutions) ?: return false
        relations.isAssignmentCompatible(actual, substitutedBound)
      }
    }
  }
}
