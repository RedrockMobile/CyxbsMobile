package com.cyxbs.components.base.webview

import androidx.compose.runtime.Composable
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_WEBVIEW

/** 通用 WebView 导航入口。 */
@AppNav(route = NAV_WEBVIEW)
class WebViewNavEntry : AppNavEntry<WebViewNavArgument>() {

  override fun isNeedLogin(argument: WebViewNavArgument): Boolean = false

  @Composable
  override fun Content(argument: WebViewNavArgument) {
    WebViewScreen(argument)
  }
}
