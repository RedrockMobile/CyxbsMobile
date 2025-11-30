package com.cyxbs.components.config.scheme

import com.cyxbs.components.config.service.implOrNull


interface WebViewFactory {
  fun startWebView(url: String)
}

internal actual fun jumpHttp(url: String): Boolean {
  val factory = WebViewFactory::class.implOrNull() ?: return false
  factory.startWebView(url)
  return true
}