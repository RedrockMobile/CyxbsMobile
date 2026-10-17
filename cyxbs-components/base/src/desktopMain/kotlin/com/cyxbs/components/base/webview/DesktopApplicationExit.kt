package com.cyxbs.components.base.webview

import io.github.multiweb.compose.DesktopWebViewRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.cyxbs.components.log.CyxbsLog

private const val TAG = "DesktopApplicationExit"

/** 业务检查通过后，将资源关闭及最终退出交给 MultiWeb。 */
object DesktopApplicationExit {
  private val coordinator = DesktopApplicationExitCoordinator(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    shutdown = DesktopWebViewRuntime::requestApplicationExit,
  )

  /**
   * 按注册顺序检查，整轮检查默认限时 30 秒。
   * 请求接纳时取快照；之后的注册或注销仅影响下一轮，close 可重复调用且只注销本次注册。
   */
  fun registerInterceptor(interceptor: suspend () -> Boolean): AutoCloseable =
    coordinator.registerInterceptor(interceptor)

  /** 窗口关闭与系统退出共用的入口；Checking/Closing 期间请求合并。 */
  fun requestExit() = coordinator.requestExit()
}

internal class DesktopApplicationExitCoordinator(
  private val scope: CoroutineScope,
  private val timeoutMillis: Long = 30_000,
  private val shutdown: () -> Unit,
) {
  private enum class State { Idle, Checking, Closing }
  private class Registration(val interceptor: suspend () -> Boolean)

  private val lock = Any()
  private val registrations = mutableListOf<Registration>()
  private var state = State.Idle

  fun registerInterceptor(interceptor: suspend () -> Boolean): AutoCloseable {
    val registration = Registration(interceptor)
    synchronized(lock) { registrations += registration }
    return AutoCloseable { synchronized(lock) { registrations.remove(registration) } }
  }

  fun requestExit() {
    // 接纳请求和取快照在同一个锁内，不依赖调用线程或 EDT 调度。
    val snapshot = synchronized(lock) {
      if (state != State.Idle) return
      state = State.Checking
      registrations.toList()
    }
    var shutdownFailed = false
    val job = scope.launch {
      val approved = try {
        withTimeout(timeoutMillis) {
          snapshot.all { it.interceptor() }
        }
      } catch (failure: Throwable) {
        CyxbsLog.w(TAG, "Desktop application exit check failed; keeping application open", failure)
        if (failure is CancellationException) throw failure
        false
      }
      if (approved) {
        synchronized(lock) { state = State.Closing }
        try {
          shutdown()
        } catch (failure: Throwable) {
          synchronized(lock) { shutdownFailed = true }
          CyxbsLog.w(TAG, "Desktop application shutdown failed; allowing exit retry", failure)
          if (failure is CancellationException) throw failure
        }
      }
    }
    // 本轮完成后才恢复 Idle，避免旧回调重置新请求；成功的 Closing 不回退。
    job.invokeOnCompletion {
      synchronized(lock) {
        if (state == State.Checking || shutdownFailed) state = State.Idle
      }
    }
  }
}
