package com.cyxbs.components.base.crash

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import com.cyxbs.components.base.crash.CrashActivity.Companion.NetworkApiResult
import com.cyxbs.components.base.pages.SecretActivity
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.toastLong
import com.g985892345.provider.api.annotation.ImplProvider
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 统一处理线程崩溃与 Compose 协程异常；effects 异常独立展示，不进入 Looper 恢复循环。
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

  private val mainHandler = Handler(Looper.getMainLooper())
  private val composeDialogPending = AtomicBoolean(false)

  /** 在 Application 初始化时安装；窗口工厂必须早于首个 Compose 页面创建。 */
  fun install() {
    installThreadHandler()
    installRxjavaErrorHandler()
    installComposeExceptionHandler()
  }

  /** 保留默认窗口生命周期，只在 effects 上下文中插入一次监督层和异常展示入口。 */
  @OptIn(InternalComposeUiApi::class)
  private fun installComposeExceptionHandler() {
    val handler = CoroutineExceptionHandler { _, throwable ->
      handleComposeException(throwable)
    }
    WindowRecomposerPolicy.setFactory { rootView ->
      // 同时提供 dispatcher 和 frame clock，避免默认入口从右侧 fold 展开并丢掉包装。
      val context = ComposeEffectCoroutineContext(AndroidUiDispatcher.CurrentThread + handler)
      rootView.createLifecycleAwareWindowRecomposer(coroutineContext = context).also {
        if (context.supervisor == null && isDebug()) {
          // 升级 Compose 后若构造顺序发生变化，保留默认行为并明确记录，不能误改其他 Job。
          // 如果后续发现无法有效的找到更好的 hook 点，那可以考虑移除掉这个 compose 异常拦截器
          Log.e("crash", "Compose effects 监督层未安装，请检查 Recomposer 构造顺序")
          toast("Compose effects 监督层未安装，请检查 Recomposer 构造顺序")
        }
      }
    }
  }

  /**
   * 上报并投递原生弹窗；失败的任务已结束，但不能在异常处理器中嵌套 Looper.loop。
   * 可从任意线程调用；无有效前台窗口时仅记录异常，合并同一批待展示的错误。
   */
  private fun handleComposeException(throwable: Throwable) {
    Log.e("crash", "Compose 协程执行失败", throwable)
    runCatching { crashReport?.invoke(throwable) }
      .onFailure { Log.e("crash", "Compose 异常上报失败", it) }
    if (!composeDialogPending.compareAndSet(false, true)) return
    mainHandler.post {
      try {
        val activity = appTopActivity.get()
        if (activity != null && !activity.isFinishing && !activity.isDestroyed
          && activity.window.decorView.isShown
        ) {
          CrashDialog.Builder(
            RuntimeException("Compose 协程执行失败，已终止本次任务，其他界面任务将尝试继续运行", throwable)
          ).show()
        }
      } catch (error: Throwable) {
        // 错误展示不能再次把主线程带入异常恢复循环。
        Log.e("crash", "Compose 异常弹窗展示失败", error)
      } finally {
        composeDialogPending.set(false)
      }
    }
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
      if (isDebug()) throw it
    }
  }

  private fun handleOtherThread(throwable: Throwable) {
    // 其他线程不处理
    if (isDebug()) {
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
      if (isDebug()) {
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
