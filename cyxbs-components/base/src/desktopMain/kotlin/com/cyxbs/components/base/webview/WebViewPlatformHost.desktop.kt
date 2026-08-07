package com.cyxbs.components.base.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.multiweb.api.NavigationRequest
import io.github.multiweb.extension.WebViewInitialization

/** Desktop 当前先使用 MultiWeb 默认初始化，业务桥和系统能力后续按需补齐。 */
@Composable
internal actual fun rememberWebViewPlatformHost(
  onFullscreenChanged: (Boolean) -> Unit,
): WebViewPlatformHost {
  return remember { NoOpWebViewPlatformHost() }
}

private class NoOpWebViewPlatformHost : WebViewPlatformHost {
  override val initialization: WebViewInitialization = WebViewInitialization()

  override fun onExternalNavigation(request: NavigationRequest) = Unit
}
