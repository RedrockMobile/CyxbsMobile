package com.cyxbs.pages.home.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.route.MAIN_ENTRY
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.AppNavDisplay
import com.cyxbs.components.navigation.AppSnackbarCompose
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.utils.extensions.launchByLifecycleScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.utils.judge.RedrockNetwork
import com.cyxbs.functions.update.api.IAppUpdateService
import com.g985892345.provider.api.annotation.KClassProvider

/**
 * MainActivity 作为 Compose 容器，有以下约定：
 * - MainActivity 不再只表示主页，只是一个容器，可能会表示其他页面，由 Compose 决定
 * - 不要承接与 UI 相关操作，这些操作该放到 Compose 里面
 * -
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/14 20:49
 */
@KClassProvider(clazz = Activity::class, name = MAIN_ENTRY)
class MainActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    // 还原主题，因为 MainActivity 最开始在 AndroidManifest.xml 设置了闪屏页背景，所以这里需要还原
    setTheme(com.cyxbs.components.config.R.style.ConfigAppTheme)
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        AppNavDisplay()
        AppSnackbarCompose()
        DeepLinkHandler()
      }
    }
    initUpdate()
    initPing()
  }

  /**
   * 在应用根组合中常驻监听页面协议。
   *
   * [DisposableEffect] 会在 [AppNavDisplay] 完成首次组合后执行，避免冷启动时导航栈尚未就绪；
   * 同时它不依附具体页面，因此进入清单、课表等子页面后仍能接收新的 Intent。
   */
  @Composable
  private fun DeepLinkHandler() {
    DisposableEffect(Unit) {
      handleDeepLink(intent)
      val listener = Consumer<Intent>(::handleDeepLink)
      addOnNewIntentListener(listener)
      onDispose { removeOnNewIntentListener(listener) }
    }
  }

  /**
   * 在 Activity 容器层统一处理页面协议。
   *
   * MainActivity 会承载主页之外的导航页面，因此不能把监听绑定到主页 Composable 的生命周期；
   * 否则离开主页后 [onNewIntent] 到达时，原监听已经随组合销毁而移除。
   */
  private fun handleDeepLink(intent: Intent) {
    val url = intent.data ?: return
    runCatching { AppScheme.jump(url.toString()) }
      .onSuccess { handled ->
        if (!handled) {
          // 仅记录 route，避免把 query 中的业务标识带入日志。
          logg("未识别页面协议：${url.scheme}://${url.authority}${url.path.orEmpty()}")
        }
      }.onFailure {
        logg(it.stackTraceToString())
      }
  }

  private fun initUpdate() {
    IAppUpdateService::class.impl().tryNoticeUpdate()
  }

  private fun initPing() {
    launchByLifecycleScope {
      RedrockNetwork.tryPingNetWork()?.onFailure {
        toast("后端服务暂不可用")
      }
    }
  }
}
