package com.cyxbs.functions.code.js.diagnostic

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import kotlinx.coroutines.CancellationException

/**
 * JavaScript 执行失败的稳定分类。
 *
 * 该分类面向教学编辑器和业务状态层，不直接暴露 QuickJS 的异常类型。QuickJS 无法区分执行超时
 * 与调用方主动中断，因此两者都会映射为 [INTERRUPTED]。
 */
enum class JsDiagnosticKind {
  /** JavaScript 源码不满足语法规则。 */
  SYNTAX_ERROR,

  /** 静态或动态 import 无法解析目标 Module。 */
  MODULE_RESOLUTION_ERROR,

  /** JavaScript 顶层代码或函数在运行时抛出错误。 */
  RUNTIME_ERROR,

  /** JavaScript 执行超时或被调用方主动中断。 */
  INTERRUPTED,

  /** 执行所在协程被取消。 */
  CANCELLED,

  /** Kotlin 宿主能力、存储、校验器或其他非 QuickJS 代码抛出的异常。 */
  HOST_ERROR,
}

/**
 * 可供编辑器和业务 UI 直接消费的 JavaScript 错误诊断。
 *
 * @param kind 稳定错误分类。
 * @param message 去除重复堆栈后的单行错误摘要。
 * @param fileName QuickJS 报告的逻辑文件名，无法定位时为空。
 * @param lineNumber 从 1 开始的源码行号，无法定位时为空。
 * @param columnNumber 从 1 开始的源码列号，无法定位时为空。
 * @param stack JavaScript 原始堆栈；宿主异常或引擎未提供堆栈时为空。
 */
data class JsDiagnostic(
  val kind: JsDiagnosticKind,
  val message: String,
  val fileName: String? = null,
  val lineNumber: Int? = null,
  val columnNumber: Int? = null,
  val stack: String? = null,
)

/**
 * 把执行链抛出的异常转换为稳定的 JavaScript 诊断模型。
 *
 * 该方法只读取异常，不会包装、修改或重新抛出异常。调用方仍可让
 * `JsProgramClient.execute()` 保持原始异常语义，仅在展示错误时调用本方法。
 *
 * 非 [QuickJsException] 无法可靠判断来自哪个宿主组件，统一归为
 * [JsDiagnosticKind.HOST_ERROR]；业务如需细分网络、存储或验签错误，应先按自己的异常类型处理。
 *
 * @return 不持有原始 [Throwable] 的诊断快照。
 */
fun Throwable.toJsDiagnostic(): JsDiagnostic {
  return when (this) {
    is CancellationException -> JsDiagnostic(
      kind = JsDiagnosticKind.CANCELLED,
      message = diagnosticMessage(),
    )

    is QuickJsInterruptedException -> JsDiagnostic(
      kind = JsDiagnosticKind.INTERRUPTED,
      message = quickJsMessageWithoutStack(),
      fileName = fileName,
      lineNumber = lineNumber,
      columnNumber = columnNumber,
      stack = stack,
    )

    is QuickJsException -> JsDiagnostic(
      kind = quickJsDiagnosticKind(),
      message = quickJsMessageWithoutStack(),
      fileName = fileName,
      lineNumber = lineNumber,
      columnNumber = columnNumber,
      stack = stack,
    )

    else -> JsDiagnostic(
      kind = JsDiagnosticKind.HOST_ERROR,
      message = diagnosticMessage(),
    )
  }
}

/**
 * 根据 QuickJS 的标准错误名称和 Module Loader 错误文本确定稳定分类。
 */
private fun QuickJsException.quickJsDiagnosticKind(): JsDiagnosticKind {
  val errorMessage = quickJsMessageWithoutStack()
  return when {
    errorMessage.substringBefore(':') == "SyntaxError" -> JsDiagnosticKind.SYNTAX_ERROR
    errorMessage.contains("could not load module", ignoreCase = true) ->
      JsDiagnosticKind.MODULE_RESOLUTION_ERROR
    else -> JsDiagnosticKind.RUNTIME_ERROR
  }
}

/**
 * QuickJS 会把 JavaScript 堆栈同时附加到 message 和 stack；展示消息时只保留错误正文。
 */
private fun QuickJsException.quickJsMessageWithoutStack(): String {
  val rawMessage = message.orEmpty()
  val rawStack = stack
  val messageWithoutStack = if (rawStack.isNullOrEmpty()) {
    rawMessage
  } else {
    rawMessage.removeSuffix("\n$rawStack")
  }
  return messageWithoutStack.ifBlank { this::class.simpleName ?: "JavaScript execution failed." }
}

/**
 * 返回适合展示的宿主异常摘要。
 *
 * Kotlin/Native 的 QuickJS 桥可能把宿主堆栈追加到异常 message，诊断只保留第一个非空行；
 * 调用方仍可从原始 Throwable 记录完整平台堆栈。
 */
private fun Throwable.diagnosticMessage(): String {
  return message?.lineSequence()?.firstOrNull { it.isNotBlank() }
    ?: this::class.simpleName
    ?: "Unknown host error."
}
