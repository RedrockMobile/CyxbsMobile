package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticType
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticTypeParameterDeclaration
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSymbolId
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaTypeRelations

/**
 * 非 poly 实参对 callable 类型参数的部分推断结果。
 *
 * [Success.mayRemainUnresolved] 只表示当前参数尚未提供约束；已经收集到但互相冲突、或违反
 * 上界的约束必须返回 [Conflict]，调用方不能把它误当成“稍后可由 diamond 补齐”。
 */
internal sealed interface JavaPartialGenericInferenceResult {
  data class Success(
    val substitutions: Map<JavaSymbolId, JavaSemanticType>,
    val mayRemainUnresolved: Boolean,
  ) : JavaPartialGenericInferenceResult

  data object Conflict : JavaPartialGenericInferenceResult
}

/**
 * 阶段 1 方法与 diamond 构造器共用的保守泛型实参推断器。
 *
 * 本类只消费 formal/actual 约束对，不直接读取 AST 上下文；调用方既可以传入实参约束，也可以把
 * callable 返回类型与赋值、return 或调用位置的目标类型追加为约束。这样推断算法无需感知语法节点，
 * 同时能覆盖 Java 8 常用 target typing。无法唯一收敛、违反上界或需要复杂 capture/F-bound 求解时
 * 返回 null。
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
    return when (val result = inferPartial(
      typeParameters,
      formalParameterTypes,
      actualArgumentTypes,
      explicitTypeArguments,
      fixedSubstitutions,
    )) {
      JavaPartialGenericInferenceResult.Conflict -> null
      is JavaPartialGenericInferenceResult.Success ->
        result.substitutions.takeUnless { result.mayRemainUnresolved }
    }
  }

  /**
   * 从已经完成普通分析的实参收集部分约束。
   *
   * 没有触及的类型变量允许保持未决，供调用参数中的 poly expression 后续使用；一旦现有
   * 实参已经产生矛盾约束或违反可判定上界，则立即返回 [JavaPartialGenericInferenceResult.Conflict]。
   */
  fun inferPartial(
    typeParameters: List<JavaSymbolId>,
    formalParameterTypes: List<JavaSemanticType>,
    actualArgumentTypes: List<JavaSemanticType>,
    explicitTypeArguments: List<JavaSemanticType> = emptyList(),
    fixedSubstitutions: Map<JavaSymbolId, JavaSemanticType> = emptyMap(),
  ): JavaPartialGenericInferenceResult {
    if (formalParameterTypes.size != actualArgumentTypes.size) {
      return JavaPartialGenericInferenceResult.Conflict
    }
    if (explicitTypeArguments.isNotEmpty()) {
      if (explicitTypeArguments.size != typeParameters.size) {
        return JavaPartialGenericInferenceResult.Conflict
      }
      val substitutions = typeParameters.zip(explicitTypeArguments).toMap()
      return if (satisfiesBounds(substitutions, fixedSubstitutions)) {
        JavaPartialGenericInferenceResult.Success(substitutions, mayRemainUnresolved = false)
      } else {
        JavaPartialGenericInferenceResult.Conflict
      }
    }

    val candidates = linkedMapOf<JavaSymbolId, JavaSemanticType>()
    formalParameterTypes.zip(actualArgumentTypes).forEach { (formal, actual) ->
      if (!collect(formal, actual, typeParameters.toSet(), candidates)) {
        return JavaPartialGenericInferenceResult.Conflict
      }
    }
    if (!satisfiesKnownBounds(candidates, fixedSubstitutions, typeParameters.toSet())) {
      return JavaPartialGenericInferenceResult.Conflict
    }
    return JavaPartialGenericInferenceResult.Success(
      candidates,
      mayRemainUnresolved = typeParameters.any { it !in candidates },
    )
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

  /**
   * 部分推断只校验已经能完全代换的上界。
   *
   * 例如 `T extends Number` 且 T 已推为 String 时立即判冲突；`T extends U` 但 U 尚无约束时
   * 保留候选，避免把“尚不可判定”误报成失败。
   */
  private fun satisfiesKnownBounds(
    substitutions: Map<JavaSymbolId, JavaSemanticType>,
    fixedSubstitutions: Map<JavaSymbolId, JavaSemanticType>,
    inferable: Set<JavaSymbolId>,
  ): Boolean {
    val allSubstitutions = fixedSubstitutions + substitutions
    return substitutions.all { (symbol, actual) ->
      val declaration = typeParameterDeclarations[symbol] ?: return false
      declaration.upperBounds.all { bound ->
        val substitutedBound = relations.substitute(bound, allSubstitutions) ?: return false
        substitutedBound.containsAnyTypeVariable(inferable - substitutions.keys) ||
          relations.isAssignmentCompatible(actual, substitutedBound)
      }
    }
  }

  /** 判断类型中是否仍含本轮尚未推断的 callable 类型变量。 */
  private fun JavaSemanticType.containsAnyTypeVariable(symbols: Set<JavaSymbolId>): Boolean = when (this) {
    is JavaSemanticType.TypeVariable -> symbol in symbols
    is JavaSemanticType.Declared -> arguments.any { it.containsAnyTypeVariable(symbols) }
    is JavaSemanticType.Array -> componentType.containsAnyTypeVariable(symbols)
    is JavaSemanticType.Wildcard ->
      upperBound?.containsAnyTypeVariable(symbols) == true ||
        lowerBound?.containsAnyTypeVariable(symbols) == true
    else -> false
  }
}
