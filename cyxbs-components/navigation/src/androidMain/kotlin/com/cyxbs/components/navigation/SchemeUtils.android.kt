package com.cyxbs.components.navigation

import com.g985892345.provider.manager.KtProvider


interface WebViewFactory {
  fun startWebView(url: String)
}

internal actual fun jumpHttp(url: String): Boolean {
  val factory = KtProvider.implOrNull(WebViewFactory::class) ?: return false
  factory.startWebView(url)
  return true
}