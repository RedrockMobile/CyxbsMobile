package com.cyxbs.components.base.webview

import io.github.multiweb.compose.DesktopWebViewRuntime
import me.friwi.jcefmaven.CefAppBuilder

/** 在创建任意 Desktop WebView 前初始化当前进程唯一的 JCEF 运行时。 */
fun initializeDesktopWebViewRuntime() {
  DesktopWebViewRuntime.initialize(
    CefAppBuilder().apply {
      addJcefArgs("--autoplay-policy=no-user-gesture-required")
    }.build()
  )
}
