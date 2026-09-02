package com.cyxbs.components.navigation

import com.cyxbs.components.init.appCoroutineScope
import com.eygraber.uri.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 掌邮的统一处理 scheme 跳转的工具类
 *
 * @author 985892345
 * @date 2025/10/20
 */
object AppScheme {

  /**
   * 应用 nav 统一的 scheme
   */
  const val SCHEME = "cyxbs"

  /**
   * 根据协议跳转页面
   * - 支持 http 协议，跳转到统一的 webView
   * - 支持 cyxbs 页面协议
   */
  fun jump(url: String): Boolean {
    var result = jumpHttp(url)
    result = result || jumpNav(url)
    return result
  }

  private fun jumpNav(url: String): Boolean {
    if (!url.startsWith("${SCHEME}://")) return false
    val argument = AppNavArgument.decodeFromUrl(url) ?: return false
    appCoroutineScope.launch(Dispatchers.Main.immediate) {
      argument.navigate()
    }
    return true
  }

  /**
   * 支持一些参数从 url 的 query 中获取：
   * - hideTitle: 是否隐藏标题栏
   * - title: 如果为 null 则优先使用网页标签页名字
   * - defaultTitle: 如果网页标签页名字为空则使用这个为标题
   */
  private fun jumpHttp(url: String): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    val uri = Uri.parse(url)
    val webViewRoute = Uri.Builder()
      .scheme(SCHEME)
      .authority(NAV_WEBVIEW)
      .appendQueryParameter("url", url)
      .appendQueryParameter(
        "hideTitle",
        uri.getQueryParameter("hideTitle")?.toBooleanStrictOrNull()?.toString() ?: "false",
      )
      .apply {
        uri.getQueryParameter("title")?.let { appendQueryParameter("title", it) }
      }
      .appendQueryParameter(
        "defaultTitle",
        uri.getQueryParameter("defaultTitle") ?: DEFAULT_WEB_VIEW_TITLE,
      )
      .build()
      .toString()
    return jumpNav(webViewRoute)
  }

  private const val DEFAULT_WEB_VIEW_TITLE = ""
}
