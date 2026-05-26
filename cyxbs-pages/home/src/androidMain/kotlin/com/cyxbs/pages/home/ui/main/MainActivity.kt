package com.cyxbs.pages.home.ui.main

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.route.MAIN_ENTRY
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.AppNavDisplay
import com.cyxbs.components.utils.extensions.launchByLifecycleScope
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
    setContent { AppTheme { AppNavDisplay() } }
    initUpdate()
    initPing()
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