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
import java.awt.Desktop
import java.net.URI
import javax.swing.SwingUtilities

/** Desktop WebView 宿主：桥回调统一切回 Swing EDT，避免跨线程修改 Compose 状态。 */
@Composable
internal actual fun rememberWebViewPlatformHost(
  onFullscreenChanged: (Boolean) -> Unit,
): WebViewPlatformHost {
  val currentOnFullscreenChanged = rememberUpdatedState(onFullscreenChanged)
  return remember {
    DesktopWebViewPlatformHost { enabled -> currentOnFullscreenChanged.value(enabled) }
  }
}

private class DesktopWebViewPlatformHost(
  private val onFullscreenChanged: (Boolean) -> Unit,
) : WebViewPlatformHost, NativeWebViewBridgeHost {

  private val bridgeExtension = NativeWebViewBridgeExtension(
    originPolicy = ScriptBridgeOriginPolicy.UnsafeAnyHttpOrHttps,
    host = this,
    enableLegacyJavaScriptExecution = true,
  )

  override val initialization = WebViewInitialization(
    webViewConfig = WebViewConfig(
      javaScriptEnabled = true,
      thirdPartyCookiesEnabled = true,
    ),
    navigationPolicy = DesktopWebViewNavigationPolicy,
    extensions = listOf(bridgeExtension),
  )

  override fun onExternalNavigation(request: NavigationRequest) {
    openExternalUrl(request.url)
  }

  override fun handle(request: NativeWebViewBridgeRequest): NativeWebViewBridgeResult {
    return when (request) {
      is NativeWebViewBridgeRequest.Navigate -> navigateInApp(request.path)
      is NativeWebViewBridgeRequest.ShowMessage -> {
        request.message.takeIf(String::isNotBlank)?.let(::showMessage)
        NativeWebViewBridgeResult.Success()
      }
      is NativeWebViewBridgeRequest.SetFullscreen -> {
        runOnEdt { onFullscreenChanged(request.enabled) }
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
    runOnEdt(argument::navigate)
    return NativeWebViewBridgeResult.Success()
  }

  private fun openExternalUrl(rawUrl: String) {
    val uri = runCatching { URI(rawUrl) }.getOrElse {
      showMessage("无法打开该链接")
      return
    }
    runOnEdt {
      runCatching {
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
        Desktop.getDesktop().browse(uri)
      }.onFailure {
        showMessage("无法打开该链接")
      }
    }
  }

  private fun showMessage(message: String) {
    runOnEdt { message.toast() }
  }

  private fun runOnEdt(action: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
      action()
    } else {
      SwingUtilities.invokeLater(action)
    }
  }
}

private object DesktopWebViewNavigationPolicy : NavigationPolicy {
  override fun decide(request: NavigationRequest): NavigationDecision {
    return when (runCatching { URI(request.url).scheme?.lowercase() }.getOrNull()) {
      "http", "https" -> NavigationDecision.Allow
      else -> NavigationDecision.OpenExternally
    }
  }
}
