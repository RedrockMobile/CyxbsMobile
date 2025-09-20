package com.cyxbs.pages.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.HomeArgument
import com.cyxbs.components.config.navigation.MainDestination
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.login.api.LoginArgument
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

/**
 * 默认的 PlatformHomePage 实现，自适应宽高的主页
 */
@Composable
private fun AdaptiveHomePage(parcel: DestinationParcel<HomeArgument>) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = "退出登录", modifier = Modifier.clickable {
      IAccountEditService::class.impl().onLogout()
      LoginArgument.navigate(HomeArgument, clearStack = true)
    })
  }
}