package com.cyxbs.pages.login.api

import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.navigation.NAV_HOME
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.navigation.encodeToUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/2
 */
@Serializable
data class LoginNavArgument(
  @SerialName("targetUrl")
  val targetUrl: String? = null,
) : AppNavArgument {

  companion object {
    /**
     * @param target 登录后跳转的页面 argument 对象，null 表示不跳转，登录成功或 back 就返回
     * @param clearStack 是否清空栈，true 清空栈，false 不清空栈
     */
    fun navigate(
      target: AppNavArgument?,
      clearStack: Boolean,
    ) {
      appCoroutineScope.launch(Dispatchers.Main.immediate) {
        // 使用 Dispatchers.Main.immediate
        // 如果当前就是主线程，则直接执行，否则切换到主线程才执行
        if (clearStack) {
          appNavBackStack.clear()
        }
        val targetUrl = if (clearStack && target == null) {
          // 如果清空栈且 target 为空，则默认跳到首页
          "${AppScheme.SCHEME}://${NAV_HOME}"
        } else {
          target?.encodeToUrl()?.toString()
        }
        LoginNavArgument(targetUrl = targetUrl).navigate()
      }
    }
  }
}