package com.cyxbs.pages.home.ui

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeNavArgument
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_HOME
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.notification.api.IHomeNoticeDialogService
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/2
 */
@ImplProvider(clazz = MainNavDestination::class, name = NAV_HOME)
class HomeNavDestination : MainNavDestination<HomeNavArgument>(HomeNavArgument::class) {

  override val needLogin: Boolean
    get() = false // 主页允许未登录打开

  @Composable
  override fun DestinationContent(parcel: DestinationParcel<HomeNavArgument>) {
    PlatformHomePage.HomePageContent(parcel)
    IHomeNoticeDialogService.HomeNoticeDialogContent() // 重要通知弹窗，冷启动时就会弹出
  }
}

/**
 * 由于需要兼容移动端布局，所以 mobileMain 中单独实现移动端的 PlatformHomePage
 */
internal interface PlatformHomePage {

  @Composable
  fun HomePageContent(parcel: DestinationParcel<HomeNavArgument>)

  // 默认实现
  companion object : PlatformHomePage by PlatformHomePage::class.implOrNull() ?: object : PlatformHomePage {
    @Composable
    override fun HomePageContent(parcel: DestinationParcel<HomeNavArgument>) {
      AdaptiveHomePage(parcel)
    }
  }
}
