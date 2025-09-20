package com.cyxbs.components.base.crash

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.cyxbs.components.base.BuildConfig
import com.cyxbs.components.base.crash.CrashActivity.Companion.NetworkApiResult
import com.cyxbs.components.base.pages.SecretActivity
import com.cyxbs.components.utils.extensions.toastLong
import com.g985892345.provider.api.annotation.ImplProvider
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.lang.Thread.UncaughtExceptionHandler

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/25
 */
@ImplProvider // 提供给其他模块使用，比如 ApiGenerator 中的 OkHttp Dispatcher
object CrashMonitor : UncaughtExceptionHandler {

  // 提供给 application 模块向外暴露异常用于上报
  var crashReport: ((Throwable) -> Unit)? = null

  private var lastThrowableTime = 0L

  private val mainThread = Looper.getMainLooper().thread

  fun install() {
    installThreadHandler()
    installRxjavaErrorHandler()
  }

  private fun installThreadHandler() {
    // 这里的 ExceptionHandler 优先级会高于 DefaultUncaughtExceptionHandler
    mainThread.setUncaughtExceptionHandler(this)
    Thread.setDefaultUncaughtExceptionHandler(this)
    Looper.getMainLooper().queue.addIdleHandler {
      // 我们需要确保 DefaultUncaughtExceptionHandler 没有被其他 sdk 覆盖掉
      if (Thread.getDefaultUncaughtExceptionHandler() !== this) {
        Thread.setDefaultUncaughtExceptionHandler(this)
      }
      true
    }
  }

  private fun installRxjavaErrorHandler() {
    RxJavaPlugins.setErrorHandler {
      if (BuildConfig.DEBUG) throw it
    }
  }

  private fun handleOtherThread(throwable: Throwable) {
    // 其他线程不处理
    if (BuildConfig.DEBUG) {
      Log.d("crash", throwable.stackTraceToString())
    }
  }

  private fun handleMainThread(throwable: Throwable) {
    CrashDialog.Builder(
      RuntimeException(
        "触发了一次来自主线程的异常, ${throwable.message}",
        throwable
      )
    ).show()
    lastThrowableTime = SystemClock.elapsedRealtime()
    tryLoop(throwable)
  }

  private fun tryLoop(originThrowable: Throwable) {
    // 主线程崩溃后 loop 会停掉，这里重启 loop
    try {
      Looper.loop()
    } catch (e: Throwable) {
      e.printStackTrace()
      if (SystemClock.elapsedRealtime() - lastThrowableTime < 1000) {
        // 短时间内再次崩溃，则直接打开 CrashActivity
        startCrashActivity(originThrowable)
      } else {
        CrashDialog.Builder(RuntimeException("触发了一次来自主线程的异常, ${e.message}", e)).show()
        lastThrowableTime = SystemClock.elapsedRealtime()
        tryLoop(e)
      }
    }
  }

  override fun uncaughtException(t: Thread, e: Throwable) {
    if (t === mainThread) {
      if (BuildConfig.DEBUG) {
        Log.d("crash", e.stackTraceToString())
        toastLong("触发异常，日志筛选 tag:crash 查看")
      }
      handleMainThread(e)
      crashReport?.invoke(e)
    } else {
      handleOtherThread(e)
    }
  }

  private fun startCrashActivity(throwable: Throwable) {
    CrashActivity.start(
      throwable = throwable,
      netWorkApiResults = if (SecretActivity.sSpPandoraIsOpen) {
        // 如果当前用户值得信任，则可以传递 apiResultList
        CrashNetworkConfigService.apiResultList.mapTo(ArrayList()) {
          // 因为 CrashActivity 是在另一个进程中启动，所以只能以 String 的形式传过去
          NetworkApiResult(it.request.toString(), it.response.toString(), it.throwable)
        }
      } else null
    )
  }
}