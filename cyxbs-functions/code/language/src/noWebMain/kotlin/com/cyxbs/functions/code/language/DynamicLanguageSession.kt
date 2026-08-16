package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.internal.DynamicExecutableProgramRunner
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

/**
 * 动态语言程序的一次运行请求。
 *
 * [compilation] 对 Java 可通过光标选择 static 方法，对 JavaScript/Python 等脚本语言则选择入口
 * 文件。[arguments] 使用 JSON 值保证各平台和语言之间拥有稳定、可检查的基础值边界。
 */
data class DynamicProgramRunRequest(
  val compilation: DynamicCompilationRequest,
  val arguments: List<JsonElement> = emptyList(),
)

/**
 * 单次用户程序 Runtime 的资源限制。
 *
 * 每次 [DynamicLanguageSession.run] 都创建独立 Runtime，这些限制不会改变高亮、补全和编译
 * Service 所在 Runtime。0 毫秒表示不限制执行时间。
 */
data class DynamicProgramRunOptions(
  val memoryLimitBytes: Long = 32L * 1024L * 1024L,
  val maxStackSizeBytes: Long = 256L * 1024L,
  val evaluationTimeoutMillis: Long = 5_000L,
) {
  init {
    require(maxStackSizeBytes > 0) { "maxStackSizeBytes must be greater than 0." }
    require(evaluationTimeoutMillis >= 0) {
      "evaluationTimeoutMillis must not be negative."
    }
  }
}

/**
 * 动态程序运行结果。
 *
 * [executed] 为 false 表示编译失败，此时不会创建用户代码 Runtime；[diagnostics] 同时保留成功
 * 编译的警告。标准输出和错误输出由独立 Runtime 中的 console bridge 收集。
 */
data class DynamicProgramRunResult(
  val executed: Boolean,
  val returnValue: JsonElement? = null,
  val standardOutput: String = "",
  val standardError: String = "",
  val diagnostics: List<DynamicCompilationDiagnostic> = emptyList(),
)

/**
 * 一个独立动态语言编辑与执行会话。
 *
 * 高亮、补全、编译等能力委托给同一个 npm 语言包 Runtime，共享 parser 和语言配置；[run] 会为
 * 每次用户程序创建另一个短生命周期 Runtime，防止死循环、全局变量和运行时损坏影响编辑能力。
 * 调用方结束编辑时必须调用 [close]。
 */
class DynamicLanguageSession internal constructor(
  private val delegate: DynamicLanguageService,
  runtimeFactoryProvider: () -> JsRuntimeFactory?,
) : DynamicLanguageService by delegate {
  private val programRunner = DynamicExecutableProgramRunner(runtimeFactoryProvider)

  /**
   * 编译并运行动态语言程序。
   *
   * 编译错误作为 [DynamicProgramRunResult] 返回；Runtime 创建、Module 加载或执行失败则抛出
   * [DynamicLanguageExecutionException]。协程取消会原样传播并关闭当前用户代码 Runtime。
   *
   * @param request 工作区、入口和 JSON 参数。
   * @param options 当前执行专属的内存、栈和超时限制。
   * @throws DynamicLanguageExecutionException Runtime 不可用、Module 图无效或执行失败。
   * @throws SerializationException npm 语言 Service 的编译协议不匹配。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    DynamicLanguageExecutionException::class,
    SerializationException::class,
    CancellationException::class,
  )
  suspend fun run(
    request: DynamicProgramRunRequest,
    options: DynamicProgramRunOptions = DynamicProgramRunOptions(),
  ): DynamicProgramRunResult {
    val compilation = compile(request.compilation)
    val hasErrors = compilation.diagnostics.any { diagnostic ->
      diagnostic.severity == DynamicCompilationDiagnosticSeverity.ERROR
    }
    if (hasErrors) {
      return DynamicProgramRunResult(
        executed = false,
        diagnostics = compilation.diagnostics,
      )
    }
    val program = compilation.program ?: throw DynamicLanguageExecutionException(
      "The language package returned neither an executable program nor an error diagnostic.",
    )
    val execution = programRunner.run(
      program = program,
      arguments = request.arguments,
      config = JsRuntimeConfig(
        memoryLimitBytes = options.memoryLimitBytes,
        maxStackSizeBytes = options.maxStackSizeBytes,
        evaluationTimeoutMillis = options.evaluationTimeoutMillis,
      ),
    )
    return DynamicProgramRunResult(
      executed = true,
      returnValue = execution.returnValue,
      standardOutput = execution.standardOutput,
      standardError = execution.standardError,
      diagnostics = compilation.diagnostics,
    )
  }
}

/**
 * 动态语言用户程序执行失败。
 *
 * 该异常隐藏 QuickJS 等具体引擎类型；底层 [JsRuntimeException] 仅通过 cause 保留用于诊断。
 */
class DynamicLanguageExecutionException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
