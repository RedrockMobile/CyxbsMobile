package com.cyxbs.functions.code.js.teaching

import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleMessage
import com.cyxbs.functions.code.js.program.JsPolicyViolationException
import com.cyxbs.functions.code.js.program.JsProgramClient
import com.cyxbs.functions.code.js.program.JsSourceVerificationException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.storage.InMemoryJsProgramStorage
import kotlinx.coroutines.CancellationException

/**
 * 教学编辑器执行一次 JavaScript 后得到的结果。
 *
 * @param value JavaScript 最后一个表达式映射到 Kotlin 后的值。
 * @param consoleMessages 本次执行通过 `console.*` 或 `print` 产生的有序输出。
 */
data class JsTeachingCodeResult(
  val value: Any?,
  val consoleMessages: List<JsConsoleMessage>,
)

/**
 * 仅供本地编程教学使用的 JavaScript 执行入口。
 *
 * 实现使用教学策略和进程内存储，不得把该接口用于执行远端动态下发的内部脚本。
 */
interface JsTeachingCodeRunner {

  /**
   * 编译并执行用户输入的单文件 JavaScript。
   *
   * @param code 待执行的 JavaScript 源码，按普通 Script 语义编译。
   * @return 表达式结果与控制台输出。
   * @throws JsPolicyViolationException 源码体积或教学能力不满足教学策略。
   * @throws JsSourceVerificationException 本地源码校验器拒绝当前源码。
   * @throws JsRuntimeException JavaScript Runtime 初始化、编译、执行或中断失败。
   * @throws CancellationException 执行所在协程被取消。
   */
  @Throws(
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    JsRuntimeException::class,
    CancellationException::class,
  )
  suspend fun execute(code: String): JsTeachingCodeResult

  /**
   * 编译并执行一个本地多文件 ES Module 工作区。
   *
   * [entryFile] 及其静态、动态导入都从 [files] 中解析，不允许 Runtime 访问工作区外的文件。
   * 该入口适合编辑器工程运行；Module 的求值结果通常为空，教学输出应优先通过 `console.*` 收集。
   *
   * @param files 相对模块名到 JavaScript 源码的映射。
   * @param entryFile 作为本次运行入口的模块名，必须存在于 [files]。
   * @return Module 求值结果与控制台输出。
   * @throws IllegalArgumentException 文件集合为空、入口不存在或模块名非法。
   * @throws JsPolicyViolationException 源码体积或教学能力不满足教学策略。
   * @throws JsSourceVerificationException 本地源码校验器拒绝当前源码。
   * @throws JsRuntimeException JavaScript Runtime 初始化、编译、依赖解析、执行或中断失败。
   * @throws CancellationException 执行所在协程被取消。
   */
  @Throws(
    IllegalArgumentException::class,
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    JsRuntimeException::class,
    CancellationException::class,
  )
  suspend fun executeModule(
    files: Map<String, String>,
    entryFile: String,
  ): JsTeachingCodeResult

  companion object {

    /**
     * 创建一个独立的教学 Runner。
     *
     * 每个实例只在进程内保存教学源码，并固定禁止 Runtime 实现读写持久化字节码缓存。
     *
     * @param runtimeFactory 教学代码使用的 JavaScript Runtime 工厂。
     */
    fun create(runtimeFactory: JsRuntimeFactory): JsTeachingCodeRunner {
      return DefaultJsTeachingCodeRunner(
        JsProgramClient(
          sourceStore = InMemoryJsProgramStorage(),
          runtimeFactory = runtimeFactory,
          allowBytecodeCache = false,
        ),
      )
    }
  }
}
