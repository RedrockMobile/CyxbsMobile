package com.cyxbs.components.base.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.utils.extensions.toast
import io.github.multiweb.api.NavigationDecision
import io.github.multiweb.api.NavigationPolicy
import io.github.multiweb.api.NavigationRequest
import io.github.multiweb.api.WebViewConfig
import io.github.multiweb.extension.NativeWebViewBridgeExtension
import io.github.multiweb.extension.NativeWebViewBridgeHost
import io.github.multiweb.extension.NativeWebViewBridgeRequest
import io.github.multiweb.extension.NativeWebViewBridgeResult
import io.github.multiweb.extension.ScriptBridgeOriginPolicy
import io.github.multiweb.extension.WebViewInitialization
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** iOS WebView 宿主：复用旧网页桥，同时将平台无法完成的请求明确返回失败。 */
@Composable
internal actual fun rememberWebViewPlatformHost(
  onFullscreenChanged: (Boolean) -> Unit,
): WebViewPlatformHost {
  val currentOnFullscreenChanged = rememberUpdatedState(onFullscreenChanged)
  return remember {
    IosWebViewPlatformHost { enabled -> currentOnFullscreenChanged.value(enabled) }
  }
}

private class IosWebViewPlatformHost(
  private val onFullscreenChanged: (Boolean) -> Unit,
) : WebViewPlatformHost, NativeWebViewBridgeHost {

  private val bridgeExtension = NativeWebViewBridgeExtension(
    originPolicy = ScriptBridgeOriginPolicy.UnsafeAnyHttpOrHttps,
    host = this,
    enableLegacyJavaScriptExecution = true,
  )

  override val initialization = WebViewInitialization(
    webViewConfig = WebViewConfig(javaScriptEnabled = true),
    navigationPolicy = IosWebViewNavigationPolicy,
    extensions = listOf(bridgeExtension),
  )

  override fun onExternalNavigation(request: NavigationRequest) {
    openExternalUrl(request.url)
  }

  override fun handle(request: NativeWebViewBridgeRequest): NativeWebViewBridgeResult {
    return when (request) {
      is NativeWebViewBridgeRequest.Navigate -> navigateInApp(request.path)
      is NativeWebViewBridgeRequest.ShowMessage -> {
        request.message.takeIf(String::isNotBlank)?.toast()
        NativeWebViewBridgeResult.Success()
      }
      is NativeWebViewBridgeRequest.SetFullscreen -> {
        onFullscreenChanged(request.enabled)
        NativeWebViewBridgeResult.Success()
      }
      is NativeWebViewBridgeRequest.SaveImage -> {
        NativeWebViewBridgeResult.Failure("unsupported_save_image")
      }
      is NativeWebViewBridgeRequest.StartSensor -> {
        NativeWebViewBridgeResult.Failure("unsupported_sensor")
      }
      is NativeWebViewBridgeRequest.SetPageLoadScript,
      is NativeWebViewBridgeRequest.ExecuteJavaScript,
      NativeWebViewBridgeRequest.GetStudentId,
      NativeWebViewBridgeRequest.GetDarkThemeEnabled,
      NativeWebViewBridgeRequest.GetSystemBarInsets,
      NativeWebViewBridgeRequest.GetToken,
      -> NativeWebViewBridgeResult.Failure("unsupported_legacy_bridge_method")
    }
  }

  private fun navigateInApp(path: String): NativeWebViewBridgeResult {
    val argument = AppNavArgument.decodeFromUrl(path)
      ?: AppNavArgument.decodeFromRoute(path)
      ?: return NativeWebViewBridgeResult.Failure("unsupported_route")
    argument.navigate()
    return NativeWebViewBridgeResult.Success()
  }

  private fun openExternalUrl(rawUrl: String) {
    val url = NSURL.URLWithString(rawUrl) ?: run {
      "无法打开该链接".toast()
      return
    }
    if (UIApplication.sharedApplication.canOpenURL(url)) {
      UIApplication.sharedApplication.openURL(url)
    } else {
      "未安装可处理该链接的应用".toast()
    }
  }
}

private object IosWebViewNavigationPolicy : NavigationPolicy {
  override fun decide(request: NavigationRequest): NavigationDecision {
    return when (NSURL.URLWithString(request.url)?.scheme?.lowercase()) {
      "http", "https" -> NavigationDecision.Allow
      else -> NavigationDecision.OpenExternally
    }
  }
}
