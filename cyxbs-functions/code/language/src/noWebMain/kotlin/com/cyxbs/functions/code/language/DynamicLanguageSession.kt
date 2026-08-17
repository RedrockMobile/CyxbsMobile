package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.internal.DynamicExecutableProgramRunner
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
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
 * [standardInput] 是运行前一次性准备的完整输入快照，不会在程序执行期间阻塞等待交互输入。
 */
data class DynamicProgramRunRequest(
  val compilation: DynamicCompilationRequest,
  val arguments: List<JsonElement> = emptyList(),
  val standardInput: String = "",
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
  /**
   * 运行期间接收已接受输出的同步通知；为 null 时仅在运行结束后读取结果中的标准流文本。
   *
   * 回调位于 Runtime binding 边界，调用方必须快速投递，不能阻塞或同步重入当前 Runtime。
   */
  val outputSink: DynamicProgramOutputSink? = null,
  /**
   * 标准输出和标准错误合计允许保留的最大 UTF-8 字节数。
   *
   * 超量文本不会再占用运行结果内存，也不会向 [outputSink] 发送；运行仍会继续，并通过结果中的
   * 截断字段通知调用方。设为 0 可禁用输出保留。
   */
  val maxOutputBytes: Long = DEFAULT_MAX_OUTPUT_BYTES,
  /**
   * 本次运行允许传入的标准输入最大 UTF-8 字节数。
   *
   * 输入在创建用户代码 Runtime 前校验，超限会明确终止运行而不会静默截断。该限制只约束
   * [DynamicProgramRunRequest.standardInput]，不改变入口 [DynamicProgramRunRequest.arguments]。
   */
  val maxInputBytes: Long = DEFAULT_MAX_INPUT_BYTES,
  /**
   * 语言包生成的完整 ES Module 图允许占用的最大 UTF-8 字节数。
   *
   * 该限制在创建用户 Runtime 前执行，防止损坏或不受信任的动态语言包返回超大源码绕过运行时
   * 内存限制。它不约束编辑器中的原始源码，语言包应在编译阶段单独限制输入规模。
   */
  val maxProgramSourceBytes: Long = DEFAULT_MAX_PROGRAM_SOURCE_BYTES,
) {
  init {
    require(maxStackSizeBytes > 0) { "maxStackSizeBytes must be greater than 0." }
    require(evaluationTimeoutMillis >= 0) {
      "evaluationTimeoutMillis must not be negative."
    }
    require(maxOutputBytes >= 0) { "maxOutputBytes must not be negative." }
    require(maxInputBytes >= 0) { "maxInputBytes must not be negative." }
    require(maxProgramSourceBytes >= 0) { "maxProgramSourceBytes must not be negative." }
  }

  companion object {
    /** 默认限制运行输出为 64 KiB，避免教学死循环持续追加文本导致页面和内存失控。 */
    const val DEFAULT_MAX_OUTPUT_BYTES: Long = 64L * 1024L

    /** 默认允许预加载 64 KiB 标准输入，覆盖教学用例并限制复制到 JS Runtime 的文本大小。 */
    const val DEFAULT_MAX_INPUT_BYTES: Long = 64L * 1024L

    /** 默认允许 4 MiB 生成源码，覆盖教学工作区并阻止异常 Module 图占满宿主内存。 */
    const val DEFAULT_MAX_PROGRAM_SOURCE_BYTES: Long = 4L * 1024L * 1024L
  }
}

/** 动态程序输出所属的标准流。 */
enum class DynamicProgramOutputChannel {
  STANDARD_OUTPUT,
  STANDARD_ERROR,
}

/**
 * 一段已脱离具体 JavaScript Runtime 生命周期的用户程序输出。
 *
 * [text] 保留原始换行，不会为了 UI 展示额外补换行；因此 Java `print`、`println` 和未来 Python、
 * JavaScript 的直接输出可以准确区分。
 */
data class DynamicProgramOutputEvent(
  val channel: DynamicProgramOutputChannel,
  val text: String,
)

/**
 * 接收用户程序的增量输出。
 *
 * 回调发生在底层 Runtime 的同步宿主 binding 边界，必须快速返回，不得进行网络、磁盘或其他阻塞操作，
 * 也不得同步重新进入同一个 Runtime。需要切换协程或更新 UI 时，应只把 [DynamicProgramOutputEvent] 投递给
 * 调用方自己的队列；回调异常会中断当前运行，并由执行器统一包装为 [DynamicLanguageExecutionException]，
 * 原始异常会保留在 cause 链中。
 */
fun interface DynamicProgramOutputSink {

  /** 接收本次运行允许保留的一段输出事件。 */
  fun write(event: DynamicProgramOutputEvent)
}

/**
 * 动态程序运行结果。
 *
 * [executed] 为 false 表示编译失败，此时不会创建用户代码 Runtime；[diagnostics] 同时保留成功
 * 编译的警告。标准输出和错误输出由独立 Runtime 中的 console 与宿主输出 bridge 收集。
 */
data class DynamicProgramRunResult(
  val executed: Boolean,
  val returnValue: JsonElement? = null,
  val standardOutput: String = "",
  val standardError: String = "",
  val diagnostics: List<DynamicCompilationDiagnostic> = emptyList(),
  /** 输出是否因 [DynamicProgramRunOptions.maxOutputBytes] 被截断。 */
  val outputTruncated: Boolean = false,
  /** 因输出上限未被保留或通知给 sink 的 UTF-8 字节数。 */
  val droppedOutputBytes: Long = 0,
  /** 语言包返回的本次编译缓存路径和耗时；旧语言包未提供时为空。 */
  val compilationMetrics: DynamicCompilationMetrics? = null,
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
   * @param request 工作区、入口、JSON 参数和运行前预加载的标准输入。
   * @param options 当前执行专属的内存、栈、超时及输入输出大小限制。
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
        compilationMetrics = compilation.metrics,
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
      outputSink = options.outputSink,
      maxOutputBytes = options.maxOutputBytes,
      standardInput = request.standardInput,
      maxInputBytes = options.maxInputBytes,
      maxProgramSourceBytes = options.maxProgramSourceBytes,
    )
    return DynamicProgramRunResult(
      executed = true,
      returnValue = execution.returnValue,
      standardOutput = execution.standardOutput,
      standardError = execution.standardError,
      outputTruncated = execution.outputTruncated,
      droppedOutputBytes = execution.droppedOutputBytes,
      diagnostics = compilation.diagnostics,
      compilationMetrics = compilation.metrics,
    )
  }
}

/** 一帧由生成 JavaScript 位置还原到动态语言源码的位置。 */
data class DynamicProgramSourceFrame(
  val generatedModuleName: String,
  val generatedLine: Int,
  val generatedColumn: Int,
  val sourceLocation: DynamicSourceLocation,
)

/**
 * 动态语言用户程序执行失败。
 *
 * 该异常隐藏 QuickJS 等具体引擎类型；底层 [JsRuntimeException] 仅通过 cause 保留用于诊断。
 * 语言包提供生成源码映射且引擎返回位置时，[sourceFrames] 可直接用于编辑器导航。
 */
class DynamicLanguageExecutionException(
  message: String,
  cause: Throwable? = null,
  /** 按 JavaScript 调用栈顺序排列；语言包未提供映射或引擎未返回位置时为空。 */
  val sourceFrames: List<DynamicProgramSourceFrame> = emptyList(),
) : RuntimeException(message, cause)
