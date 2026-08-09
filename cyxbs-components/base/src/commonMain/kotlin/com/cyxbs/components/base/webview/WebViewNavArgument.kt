package com.cyxbs.components.base.webview

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 通用 WebView 页面参数。
 *
 * 外部模块应直接构造该参数并调用 [navigate]，不要依赖 Android Activity 或 Intent。
 */
@Serializable
data class WebViewNavArgument(
  /** 页面初始地址。 */
  val url: String,
  /** 是否隐藏页面顶部标题栏。 */
  val hideTitle: Boolean = false,
  /** 外部指定的标题；为空时使用网页标题。 */
  val title: String? = null,
  /** 网页没有标题时使用的默认标题。 */
  val defaultTitle: String = DEFAULT_TITLE,
) : AppNavArgument {
  companion object {
    /** 未指定且网页没有标题时显示的标题。 */
    const val DEFAULT_TITLE = "网页"

    /** URL 中传递 [defaultTitle] 时使用的查询参数名。 */
    const val DEFAULT_TITLE_QUERY_PARAMETER = "defaultTitle"
  }
}
