package com.cyxbs.components.base.webview

import io.github.multiweb.compose.DesktopWebViewRuntime
import me.friwi.jcefmaven.CefAppBuilder
import java.io.File

/** 在创建 Compose Desktop 应用前准备 macOS windowed JCEF 所需的 Swing 互操作层。 */
fun prepareDesktopWebViewComposeInterop() {
  DesktopWebViewRuntime.prepareComposeInterop()
}

/** 在创建任意 Desktop WebView 前初始化当前进程唯一的 JCEF 运行时。 */
fun initializeDesktopWebViewRuntime() {
  val cefApp = CefAppBuilder().apply {
    setInstallDir(File(System.getProperty("user.home"), ".cyxbs/jcef"))
    configureCefUserDataDirectory()
    addJcefArgs("--autoplay-policy=no-user-gesture-required")
    setAppHandler(DesktopWebViewRuntime.createMacOsTerminationHandler())
  }.build()
  DesktopWebViewRuntime.initialize(cefApp)
}

/** 绑定 Desktop 应用退出回调；CEF 完全终止后才会调用。 */
fun bindDesktopWebViewApplicationExit(onApplicationExit: () -> Unit) {
  DesktopWebViewRuntime.bindApplicationExit(onApplicationExit)
}

/** 请求退出 Desktop 应用；MultiWeb 会等待所有浏览器和 CEF 正常终止。 */
fun requestDesktopWebViewApplicationExit() {
  DesktopWebViewRuntime.requestApplicationExit()
}

/** 将 Chromium 的运行时和 Profile 固定在用户目录，避免污染工程目录或复用其他应用的默认 Profile。 */
private fun CefAppBuilder.configureCefUserDataDirectory() {
  val rootDirectory = File(System.getProperty("user.home"), ".cyxbs/cef-user-data")
  cefSettings.root_cache_path = rootDirectory.absolutePath
  cefSettings.cache_path = File(rootDirectory, "default-profile").absolutePath
  cefSettings.persist_session_cookies = true
}
