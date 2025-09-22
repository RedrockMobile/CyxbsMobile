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
  override val content: @Composable (DestinationParcel<HomeArgument>) -> Unit = {
    remember { PlatformHomePage::class.implOrNull() ?: PlatformHomePage }.content(it)
  }
}

/**
 * 由于需要兼容移动端布局，所以 mobileMain 中单独实现移动端的 PlatformHomePage
 */
internal interface PlatformHomePage {
  val content: @Composable (DestinationParcel<HomeArgument>) -> Unit

  // 默认实现
  companion object : PlatformHomePage {
    override val content: @Composable (DestinationParcel<HomeArgument>) -> Unit =
      { AdaptiveHomePage(it) }
  }
}
