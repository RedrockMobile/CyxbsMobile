package com.cyxbs.components.base.webview

import io.github.multiweb.compose.DesktopWebViewRuntime
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/** 在创建 Compose Desktop 应用前准备 macOS windowed JCEF 所需的 Swing 互操作层。 */
fun prepareDesktopWebViewComposeInterop() {
  DesktopWebViewRuntime.prepareComposeInterop()
}

private val applicationExitHandoff = DesktopApplicationExitHandoff()

/** 在创建任意 Desktop WebView 前初始化当前进程唯一的 JCEF 运行时。 */
fun initializeDesktopWebViewRuntime() {
  val cefApp = CefAppBuilder().apply {
    setInstallDir(File(System.getProperty("user.home"), ".cyxbs/jcef"))
    configureCefUserDataDirectory()
    addJcefArgs("--autoplay-policy=no-user-gesture-required")
    setAppHandler(createDesktopApplicationTerminationHandler())
  }.build()
  DesktopWebViewRuntime.initialize(cefApp)
  // MultiWeb 只允许绑定一次；handoff 自己负责承接启动期和 Hot Reload 回调变化。
  DesktopWebViewRuntime.bindApplicationExit(applicationExitHandoff::onApplicationExit)
}

/** 更新当前 Compose 内容的退出回调；Hot Reload 重建 Composition 时可重复调用。 */
fun bindDesktopWebViewApplicationExit(onApplicationExit: () -> Unit) {
  applicationExitHandoff.bind(onApplicationExit)
}

internal class DesktopApplicationExitHandoff {
  private val callback = AtomicReference<(() -> Unit)?>(null)
  private var pendingExit = false
  private var delivered = false
  private val lock = Any()

  fun bind(onApplicationExit: () -> Unit) {
    val deliverNow = synchronized(lock) {
      callback.set(onApplicationExit)
      pendingExit && !delivered
    }
    if (deliverNow) deliver()
  }

  fun onApplicationExit() {
    synchronized(lock) {
      pendingExit = true
    }
    deliver()
  }

  private fun deliver() {
    val exit = synchronized(lock) {
      val current = callback.get() ?: return
      if (!pendingExit || delivered) return
      delivered = true
      pendingExit = false
      current
    }
    exit()
  }
}

/** 请求退出 Desktop 应用；业务检查通过后，MultiWeb 等待浏览器和 CEF 正常终止。 */
fun requestDesktopWebViewApplicationExit() {
  DesktopApplicationExit.requestExit()
}

internal fun createDesktopApplicationTerminationHandler(
  operatingSystemName: String = System.getProperty("os.name"),
  scheduleOnEdt: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
  requestExit: () -> Unit = ::requestDesktopWebViewApplicationExit,
): MavenCefAppHandlerAdapter = object : MavenCefAppHandlerAdapter() {
  override fun onBeforeTerminate(): Boolean {
    if (!operatingSystemName.startsWith("Mac")) return false
    scheduleOnEdt(requestExit)
    return true
  }
}

/** 将 Chromium 的运行时和 Profile 固定在用户目录，避免污染工程目录或复用其他应用的默认 Profile。 */
private fun CefAppBuilder.configureCefUserDataDirectory() {
  val rootDirectory = File(System.getProperty("user.home"), ".cyxbs/cef-user-data")
//  cefSettings.root_cache_path = rootDirectory.absolutePath // TODO @zengzhaoxing 后续修复 Desktop WebView 接入问题
  cefSettings.cache_path = File(rootDirectory, "default-profile").absolutePath
  cefSettings.persist_session_cookies = true
}
