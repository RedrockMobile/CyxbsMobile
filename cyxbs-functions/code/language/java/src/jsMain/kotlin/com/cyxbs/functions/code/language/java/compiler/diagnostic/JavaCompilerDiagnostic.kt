package com.cyxbs.functions.code.language.java.compiler.diagnostic

import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/** Java 编译诊断严重程度。 */
internal enum class JavaDiagnosticSeverity {
  ERROR,
  WARNING,
}

/** 为主诊断补充相关声明位置或推断原因。 */
internal data class JavaDiagnosticNote(
  val message: String,
  val span: JavaSourceSpan? = null,
)

/**
 * 一条面向 Java 源码的稳定编译诊断。
 *
 * [code] 用于测试与客户端分类，不能直接使用异常类名或 JavaScript 内部错误文本。
 */
internal data class JavaCompilerDiagnostic(
  val code: String,
  val message: String,
  val severity: JavaDiagnosticSeverity,
  val span: JavaSourceSpan? = null,
  val notes: List<JavaDiagnosticNote> = emptyList(),
)

/**
 * 单个编译阶段的结果。
 *
 * 失败时 [value] 必须为空且至少包含一条 ERROR；WARNING 可以与成功结果同时返回。
 */
internal data class JavaCompilerPhaseResult<out T>(
  val value: T?,
  val diagnostics: List<JavaCompilerDiagnostic>,
) {
  val isSuccess: Boolean
    get() = value != null && diagnostics.none { diagnostic ->
      diagnostic.severity == JavaDiagnosticSeverity.ERROR
    }

  init {
    require(value != null || diagnostics.any { diagnostic ->
      diagnostic.severity == JavaDiagnosticSeverity.ERROR
    }) {
      "A Java compiler phase without a value must contain at least one error diagnostic."
    }
  }

  companion object {
    /** 创建带可选警告的成功结果。 */
    fun <T> success(
      value: T,
      diagnostics: List<JavaCompilerDiagnostic> = emptyList(),
    ): JavaCompilerPhaseResult<T> = JavaCompilerPhaseResult(value, diagnostics)

    /** 创建不再向后续阶段传递半成品的失败结果。 */
    fun failure(
      diagnostics: List<JavaCompilerDiagnostic>,
    ): JavaCompilerPhaseResult<Nothing> = JavaCompilerPhaseResult(null, diagnostics)
  }
}
