package com.cyxbs.components.base.webview

import android.view.View
import android.view.ViewGroup
import io.github.multiweb.android.AndroidWebViewController
import io.github.multiweb.api.WebViewController
import io.github.multiweb.extension.WebViewControllerLifecycleExtension

/**
 * 对齐旧 WebView 的 Compose 嵌入方式：明确铺满宿主，并使用窗口默认图层渲染 WebGL、Canvas 与视频。
 */
internal class AndroidWebViewDefaultRenderingExtension : WebViewControllerLifecycleExtension {

  override fun onControllerAttached(controller: WebViewController) {
    (controller as? AndroidWebViewController)?.view?.apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
      setLayerType(View.LAYER_TYPE_NONE, null)
    }
  }

  override fun onControllerDisposed() = Unit
}
