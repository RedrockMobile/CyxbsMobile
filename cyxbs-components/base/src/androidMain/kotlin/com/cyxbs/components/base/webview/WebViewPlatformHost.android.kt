package com.cyxbs.components.base.webview

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.utils.extensions.toast
import io.github.multiweb.api.NavigationRequest
import io.github.multiweb.api.NavigationDecision
import io.github.multiweb.api.NavigationPolicy
import io.github.multiweb.api.WebViewConfig
import io.github.multiweb.android.AndroidWebViewCompatibilityExtension
import io.github.multiweb.extension.DownloadRequest
import io.github.multiweb.extension.NativeWebViewBridgeExtension
import io.github.multiweb.extension.NativeWebViewBridgeHost
import io.github.multiweb.extension.NativeWebViewBridgeRequest
import io.github.multiweb.extension.NativeWebViewBridgeResult
import io.github.multiweb.extension.ScriptBridgeOriginPolicy
import io.github.multiweb.extension.WebContextAction
import io.github.multiweb.extension.WebViewExtension
import io.github.multiweb.extension.WebViewInitialization

@Composable
internal actual fun rememberWebViewPlatformHost(
  onFullscreenChanged: (Boolean) -> Unit,
): WebViewPlatformHost {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnFullscreenChanged = rememberUpdatedState(onFullscreenChanged)
  val host = remember(context) {
    AndroidWebViewPlatformHost(
      context = context,
      activity = context.findFragmentActivity(),
      onFullscreenChanged = { enabled -> currentOnFullscreenChanged.value(enabled) },
    )
  }

  DisposableEffect(host, lifecycleOwner) {
    val observer = object : DefaultLifecycleObserver {
      override fun onPause(owner: LifecycleOwner) {
        host.onHostPause()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }
  return host
}

private class AndroidWebViewPlatformHost(
  private val context: Context,
  private val activity: FragmentActivity?,
  private val onFullscreenChanged: (Boolean) -> Unit,
) : WebViewPlatformHost, NativeWebViewBridgeHost {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val sensorExtension = AndroidWebViewSensorExtension(context)
  private val imageSaver = AndroidWebViewImageSaver(activity)
  private val interactionExtension = AndroidWebViewInteractionExtension(::openExternalUrl)
  private val bridgeExtension = NativeWebViewBridgeExtension(
    originPolicy = ScriptBridgeOriginPolicy.UnsafeAnyHttpOrHttps,
    host = this,
    enableLegacyJavaScriptExecution = true,
  )

  override val initialization = WebViewInitialization(
    webViewConfig = WebViewConfig(javaScriptEnabled = true),
    navigationPolicy = AndroidWebViewNavigationPolicy,
    extensions = listOf(
      AndroidWebViewCompatibilityExtension(),
      sensorExtension,
      interactionExtension,
      bridgeExtension,
    ),
  )

  override fun onExternalNavigation(request: NavigationRequest) {
    openExternalUrl(request.url)
  }

  override fun handle(request: NativeWebViewBridgeRequest): NativeWebViewBridgeResult {
    return when (request) {
      is NativeWebViewBridgeRequest.SaveImage -> {
        if (request.url.isBlank()) {
          NativeWebViewBridgeResult.Failure("invalid_image_url")
        } else {
          imageSaver.requestSave(request.url)
          NativeWebViewBridgeResult.Success("pending_user_confirmation")
        }
      }
      is NativeWebViewBridgeRequest.StartSensor -> {
        if (sensorExtension.startSensor(request.sensorId)) {
          NativeWebViewBridgeResult.Success()
        } else {
          NativeWebViewBridgeResult.Failure("unsupported_sensor")
        }
      }
      is NativeWebViewBridgeRequest.Navigate -> {
        if (request.path.isBlank()) {
          NativeWebViewBridgeResult.Failure("invalid_route")
        } else {
          navigateInApp(request.path)
        }
      }
      is NativeWebViewBridgeRequest.ShowMessage -> {
        request.message.takeIf(String::isNotBlank)?.let(::showMessage)
        NativeWebViewBridgeResult.Success()
      }
      is NativeWebViewBridgeRequest.SetFullscreen -> {
        setFullscreen(request.enabled)
        NativeWebViewBridgeResult.Success()
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

  fun onHostPause() {
    sensorExtension.stopSensors()
  }

  private fun navigateInApp(path: String): NativeWebViewBridgeResult {
    return if (AppScheme.jump(path)) {
      NativeWebViewBridgeResult.Success()
    } else {
      NativeWebViewBridgeResult.Failure("unsupported_route")
    }
  }

  private fun setFullscreen(enabled: Boolean) {
    runOnMain {
      onFullscreenChanged(enabled)
      activity?.window?.applyFullscreen(enabled)
    }
  }

  private fun openExternalUrl(url: String) {
    if (url.isBlank()) {
      return
    }
    runOnMain {
      val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (activity == null) {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }
      runCatching {
        (activity ?: context).startActivity(intent)
      }.onFailure {
        showMessage("未安装可处理该链接的应用")
      }
    }
  }

  private fun showMessage(message: String) {
    message.toast()
  }

  private fun runOnMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action()
    } else {
      mainHandler.post(action)
    }
  }
}

private object AndroidWebViewNavigationPolicy : NavigationPolicy {
  override fun decide(request: NavigationRequest): NavigationDecision {
    return when (request.url.toUri().scheme?.lowercase()) {
      "http", "https" -> NavigationDecision.Allow
      else -> NavigationDecision.OpenExternally
    }
  }
}

private class AndroidWebViewInteractionExtension(
  private val openExternalUrl: (String) -> Unit,
) : WebViewExtension {
  override fun onDownloadRequested(request: DownloadRequest) {
    openExternalUrl(request.url)
  }

  override fun onContextAction(action: WebContextAction) {
    if (action is WebContextAction.LinkLongPressed) {
      openExternalUrl(action.url)
    }
  }
}

private fun Window.applyFullscreen(enabled: Boolean) {
  val controller = WindowInsetsControllerCompat(this, decorView)
  if (enabled) {
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
  } else {
    controller.show(WindowInsetsCompat.Type.systemBars())
  }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? {
  return when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
  }
}
