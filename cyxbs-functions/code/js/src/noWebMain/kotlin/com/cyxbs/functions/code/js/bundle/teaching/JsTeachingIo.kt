package com.cyxbs.functions.code.js.bundle.teaching

import com.cyxbs.functions.code.js.bundle.JsHostCapability
import com.cyxbs.functions.code.js.runtime.JsAsyncFunctionBridge
import com.cyxbs.functions.code.js.runtime.JsObjectFunctionsBridge
import com.cyxbs.functions.code.js.runtime.JsRuntimeBridge
import com.cyxbs.functions.code.js.runtime.JsSyncFunctionBridge

/**
 * 教学控制台消息级别。
 */
enum class JsConsoleLevel {
  LOG,
  INFO,
  WARN,
  ERROR,
}

/**
 * JavaScript 控制台输出的一条不可变消息。
 *
 * 参数会在引擎 binding 回调期间转换成字符串，避免业务在 Runtime 关闭后持有原生 JS 对象。
 *
 * @param level 输出级别。
 * @param arguments 按调用参数顺序格式化后的文本。
 */
data class JsConsoleMessage(
  val level: JsConsoleLevel,
  val arguments: List<String>,
) {

  /** 适合直接展示在教学控制台中的单行文本。 */
  val text: String
    get() = arguments.joinToString(separator = " ")
}

/**
 * 接收教学 JavaScript 的控制台输出。
 *
 * 回调发生在引擎同步 binding 边界内，使用方必须快速返回，不得阻塞或同步重入当前 Runtime。
 * 若需要写数据库、文件或切换线程，应只在这里投递消息。回调异常不会被捕获，会传回当前执行。
 */
fun interface JsConsoleSink {

  /** 接收一条已经脱离原生 JS 对象生命周期的 [message]。 */
  fun write(message: JsConsoleMessage)
}

/**
 * 向教学脚本提供标准控制台输出。
 *
 * 安装后可使用 `console.log/info/warn/error(...)` 和兼容入门教学的 `print(...)`。该能力不会自行
 * 保存输出，业务通过 [sink] 决定如何追加到 UI、日志或测试断言。
 *
 * @param sink 同步输出接收器。
 * @param id 用于教学策略白名单的稳定能力 ID。
 */
class JsTeachingConsoleCapability(
  private val sink: JsConsoleSink,
  override val id: String = ID,
) : JsHostCapability {
  override val runtimeBridges: List<JsRuntimeBridge> = listOf(
    JsObjectFunctionsBridge(
      name = "console",
      functions = linkedMapOf(
        "log" to outputFunction(JsConsoleLevel.LOG),
        "info" to outputFunction(JsConsoleLevel.INFO),
        "warn" to outputFunction(JsConsoleLevel.WARN),
        "error" to outputFunction(JsConsoleLevel.ERROR),
      ),
    ),
    JsSyncFunctionBridge("print", outputFunction(JsConsoleLevel.LOG)),
  )

  /**
   * 创建一个只在当前 binding 调用期间读取参数的输出函数。
   */
  private fun outputFunction(level: JsConsoleLevel): (Array<Any?>) -> Any? = { args ->
    sink.write(
      JsConsoleMessage(
        level = level,
        arguments = args.map(::formatConsoleArgument),
      ),
    )
  }

  companion object {
    const val ID: String = "teaching.console"

    /**
     * 把引擎已转换的参数变成可安全跨越 Runtime 生命周期的文本。
     */
    private fun formatConsoleArgument(value: Any?): String = when (value) {
      null -> "null"
      Unit -> "undefined"
      is String -> value
      is Array<*>, is Iterable<*>, is Map<*, *> -> formatJsonValue(value)
      else -> value.toString()
    }

    /**
     * 将引擎已转换的普通对象和数组编码为合法 JSON，便于教学控制台稳定展示嵌套数据。
     */
    private fun formatJsonValue(value: Any?): String = when (value) {
      null, Unit -> "null"
      is String -> quoteJsonString(value)
      is Char -> quoteJsonString(value.toString())
      is Boolean -> value.toString()
      is Double -> if (value.isFinite()) value.toString() else "null"
      is Float -> if (value.isFinite()) value.toString() else "null"
      is Number -> value.toString()
      is Array<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
        formatJsonValue(it)
      }
      is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
        formatJsonValue(it)
      }
      is Map<*, *> -> value.entries.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ",",
      ) { entry ->
        "${quoteJsonString(entry.key.toString())}:${formatJsonValue(entry.value)}"
      }
      else -> quoteJsonString(value.toString())
    }

    /** 对 JSON 字符串中的引号、反斜杠和控制字符进行跨平台稳定转义。 */
    private fun quoteJsonString(value: String): String = buildString(value.length + 2) {
      append('"')
      value.forEach { char ->
        when (char) {
          '"' -> append("\\\"")
          '\\' -> append("\\\\")
          '\b' -> append("\\b")
          '\u000C' -> append("\\f")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> if (char.code < 0x20) {
            append("\\u")
            append(char.code.toString(radix = 16).padStart(length = 4, padChar = '0'))
          } else {
            append(char)
          }
        }
      }
      append('"')
    }
  }
}

/**
 * 教学脚本请求一行文本输入的提供方。
 *
 * 实现可以挂起等待 UI 输入。取消 JavaScript 执行所在协程会同时取消等待，并请求底层引擎中断。
 */
fun interface JsLineInput {

  /**
   * 请求一行输入。
   *
   * @param prompt 脚本传入的可选提示文本。
   * @return 用户输入；返回 null 表示输入流结束。
   */
  suspend fun readLine(prompt: String?): String?
}

/**
 * 向教学脚本提供异步 `readLine(prompt?)`。
 *
 * JavaScript 侧必须通过 `await readLine()` 获取结果。等待输入的时间不计入引擎的纯 JS 执行
 * 超时，调用方如需限制整次运行耗时，应对 [com.cyxbs.functions.code.js.program.JsProgramClient.execute]
 * 所在协程使用 `withTimeout` 或主动取消。
 *
 * @param input 实际输入提供方。
 * @param id 用于教学策略白名单的稳定能力 ID。
 */
class JsTeachingInputCapability(
  private val input: JsLineInput,
  override val id: String = ID,
) : JsHostCapability {
  override val runtimeBridges: List<JsRuntimeBridge> = listOf(
    JsAsyncFunctionBridge("readLine") { args ->
      require(args.size <= 1) { "readLine accepts at most one prompt argument." }
      input.readLine(args.firstOrNull()?.toString())
    },
  )

  companion object {
    const val ID: String = "teaching.input"
  }
}
