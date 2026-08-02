package com.cyxbs.functions.code.js

import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleMessage
import com.cyxbs.functions.code.js.teaching.DefaultJsTeachingCodeRunner
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import kotlinx.coroutines.CancellationException

/**
 * 教学编辑器执行一次 JavaScript 后得到的结果。
 *
 * @param value JavaScript 最后一个表达式映射到 Kotlin 后的值。
 * @param consoleMessages 本次执行通过 `console.*` 或 `print` 产生的有序输出。
 * @param executableOrigin 入口本次来自源码编译还是进程内字节码缓存。
 */
data class JsTeachingCodeResult(
  val value: Any?,
  val consoleMessages: List<JsConsoleMessage>,
  val executableOrigin: JsExecutableOrigin,
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
   * @return 表达式结果、控制台输出以及可执行产物来源。
   * @throws JsPolicyViolationException 源码体积或教学能力不满足教学策略。
   * @throws JsSourceVerificationException 本地源码校验器拒绝当前源码。
   * @throws QuickJsInterruptedException JavaScript 执行超时或被主动中断。
   * @throws QuickJsException QuickJS 初始化、编译或执行失败。
   * @throws CancellationException 执行所在协程被取消。
   */
  @Throws(
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    QuickJsException::class,
    CancellationException::class,
  )
  suspend fun execute(code: String): JsTeachingCodeResult

  companion object {

    /**
     * 创建一个独立的教学 Runner。
     *
     * 每个实例拥有自己的进程内源码和字节码缓存；需要复用缓存时，业务应复用同一个实例。
     */
    fun create(): JsTeachingCodeRunner = DefaultJsTeachingCodeRunner()
  }
}
