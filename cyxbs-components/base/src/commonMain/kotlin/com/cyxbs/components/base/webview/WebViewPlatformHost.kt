package com.cyxbs.components.base.webview

import androidx.compose.runtime.Composable
import io.github.multiweb.api.NavigationRequest
import io.github.multiweb.extension.WebViewInitialization

/**
 * WebView 页面所需的平台宿主能力。
 *
 * 页面与 MultiWeb 控制器保持在 commonMain；Activity、Intent、权限和传感器等平台行为由各平台宿主提供。
 */
internal interface WebViewPlatformHost {

  /** 当前平台的 WebView 初始化配置和扩展。 */
  val initialization: WebViewInitialization

  /** 处理导航策略要求交给宿主的地址，例如 qq://。 */
  fun onExternalNavigation(request: NavigationRequest)
}

/** 创建当前平台的 WebView 宿主；Android 实现负责注入业务桥和系统能力。 */
@Composable
internal expect fun rememberWebViewPlatformHost(
  onFullscreenChanged: (Boolean) -> Unit,
): WebViewPlatformHost
