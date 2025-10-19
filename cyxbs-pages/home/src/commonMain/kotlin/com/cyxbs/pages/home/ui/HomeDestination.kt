package com.cyxbs.pages.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeArgument
import com.cyxbs.components.config.navigation.MainDestination
import com.cyxbs.components.config.service.implOrNull
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/2
 */
@ImplProvider(clazz = MainDestination::class, name = "home")
class HomeDestination : MainDestination<HomeArgument>(HomeArgument::class) {

  @Composable
  override fun DestinationContent(parcel: DestinationParcel<HomeArgument>) {
    remember { PlatformHomePage::class.implOrNull() ?: PlatformHomePage }.HomePageContent(parcel)
  }
}

/**
 * 由于需要兼容移动端布局，所以 mobileMain 中单独实现移动端的 PlatformHomePage
 */
internal interface PlatformHomePage {

  @Composable
  fun HomePageContent(parcel: DestinationParcel<HomeArgument>)

  // 默认实现
  companion object : PlatformHomePage {
    @Composable
    override fun HomePageContent(parcel: DestinationParcel<HomeArgument>) {
      AdaptiveHomePage(parcel)
    }
  }
}
