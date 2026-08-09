package com.cyxbs.functions.code.js.quickjs.internal

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import kotlinx.coroutines.delay

/**
 * 为不自带浏览器宿主环境的 QuickJS Runtime 安装基础计时器。
 *
 * JavaScript 回调不会从 Kotlin 线程直接调用；异步宿主函数只负责等待，随后由 QuickJS 的 Promise
 * Job 队列恢复并执行回调，避免跨线程重入 Runtime。
 */
internal class QuickJsTimerBridge(
  private val engine: QuickJs,
) {
  private var sleepFunctionBound = false
  private var installed = false

  /**
   * 在首次业务代码执行前安装 setTimeout 与 clearTimeout。
   *
   * Runtime 契约不允许并发执行，因此初始化无需额外加锁。若调用方在执行前主动绑定了完整的计时器
   * API，bootstrap 会保留其实现。
   */
  suspend fun ensureInstalled() {
    if (installed) return
    if (!sleepFunctionBound) {
      engine.asyncFunction(SLEEP_FUNCTION) { arguments ->
        delay(readDelayMillis(arguments))
        null
      }
      sleepFunctionBound = true
    }
    engine.evaluate<Unit>(
      code = TIMER_BOOTSTRAP,
      filename = TIMER_BOOTSTRAP_FILENAME,
      asModule = false,
    )
    installed = true
  }

  /** 按常见 JavaScript 计时器行为把非法、负数和超大延时收敛到安全毫秒范围。 */
  private fun readDelayMillis(arguments: Array<Any?>): Long {
    val value = (arguments.firstOrNull() as? Number)?.toDouble() ?: return 0L
    if (!value.isFinite() || value <= 0.0) return 0L
    return value.toLong().coerceAtMost(MAX_DELAY_MILLIS)
  }

  private companion object {
    const val SLEEP_FUNCTION = "__cyxbsQuickJsTimerSleep"
    const val TIMER_BOOTSTRAP_FILENAME = "__cyxbs_quickjs_timers__.js"
    const val MAX_DELAY_MILLIS = Int.MAX_VALUE.toLong()

    val TIMER_BOOTSTRAP = """
      (() => {
        if (
          typeof globalThis.setTimeout === "function" &&
          typeof globalThis.clearTimeout === "function"
        ) {
          return;
        }

        const sleep = globalThis.$SLEEP_FUNCTION;
        const activeTimers = new Set();
        let nextTimerId = 1;

        globalThis.setTimeout = (callback, delay = 0, ...args) => {
          if (typeof callback !== "function") {
            throw new TypeError("setTimeout callback must be a function");
          }

          let timerId = nextTimerId;
          while (activeTimers.has(timerId)) {
            timerId = timerId >= 2147483647 ? 1 : timerId + 1;
          }
          nextTimerId = timerId >= 2147483647 ? 1 : timerId + 1;
          activeTimers.add(timerId);

          sleep(delay).then(() => {
            if (!activeTimers.delete(timerId)) return;
            callback(...args);
          });
          return timerId;
        };

        globalThis.clearTimeout = timerId => {
          activeTimers.delete(Number(timerId));
        };
      })();
    """.trimIndent()
  }
}
