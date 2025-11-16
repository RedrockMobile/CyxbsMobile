package com.cyxbs.components.config.scheme

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
object SchemeUtils {

  fun jump(url: String): Boolean {
    var result = jumpHttp(url)
    result = result || jumpHttp(url)
    return result
  }

  private fun jumpCyxbs(url: String): Boolean {
    if (!url.startsWith("cyxbs://")) return false
    appCoroutineScope.launch(Dispatchers.Main.immediate) {
      MainNavController.navigate(deepLink = NavUri(url))
    }
    return true
  }

  private fun jumpHttp(url: String): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    return com.cyxbs.components.config.scheme.jumpHttp(url)
  }
}

internal expect fun jumpHttp(url: String): Boolean