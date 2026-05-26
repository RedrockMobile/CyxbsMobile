package com.cyxbs.pages.home.ui

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_HOME
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.notification.api.IHomeNoticeDialogService

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/2
 */
@AppNav(route = NAV_HOME)
class HomeNavDestination : AppNavEntry<HomeNavArgument>() {

  override fun isNeedLogin(argument: HomeNavArgument): Boolean {
    return false // 主页允许未登录打开
  }

  override fun getContentKey(argument: HomeNavArgument): String {
    return "HomeNavArgument" // Home 属于单例页面，应该返回固定值，多次提交，共享页面状态
  }

  @Composable
  override fun Content(argument: HomeNavArgument) {
    PlatformHomePage.HomePageContent(argument)
    IHomeNoticeDialogService.HomeNoticeDialogContent() // 重要通知弹窗，冷启动时就会弹出
  }
}

/**
 * 由于需要兼容移动端布局，所以 mobileMain 中单独实现移动端的 PlatformHomePage
 */
internal interface PlatformHomePage {

  @Composable
  fun HomePageContent(argument: HomeNavArgument)

  // 默认实现
  companion object : PlatformHomePage by PlatformHomePage::class.implOrNull() ?: object : PlatformHomePage {
    @Composable
    override fun HomePageContent(argument: HomeNavArgument) {
      AdaptiveHomePage(argument)
    }
  }
}
