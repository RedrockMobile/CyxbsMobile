package com.cyxbs.components.navigation

import androidx.navigation.NavUri
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.init.appCoroutineScope
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
    appCoroutineScope.launch(Dispatchers.Main.immediate) {
      MainNavController.navigate(deepLink = NavUri(url))
    }
    return true
  }

  private fun jumpHttp(url: String): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    return com.cyxbs.components.navigation.jumpHttp(url)
  }
}

internal expect fun jumpHttp(url: String): Boolean