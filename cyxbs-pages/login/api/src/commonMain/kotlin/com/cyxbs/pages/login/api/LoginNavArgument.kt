package com.cyxbs.pages.login.api

import androidx.navigation.serialization.generateRouteWithArgs
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.init.appCoroutineScope
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
class LoginNavArgument(
  @SerialName("targetRoute")
  val targetRoute: String?,
) {

  /**
   * @param target 登录后跳转的页面 argument 对象，null 表示不跳转，登录成功或 back 就返回
   */
  constructor(target: Any?) : this(
    target?.let {
      // 生成对应 route
      generateRouteWithArgs(
        target,
        MainNavController.graph.findNode(target)!!.arguments.mapValues { it.value.type }
      )
    }
  )

  companion object {
    /**
     * @param clearStack 是否清空栈，true 清空栈，false 不清空栈
     * @param target 登录后跳转的页面 argument 对象，null 表示不跳转，登录成功或 back 就返回
     */
    fun navigate(
      target: Any?,
      clearStack: Boolean,
    ) {
      appCoroutineScope.launch(Dispatchers.Main.immediate) {
        // 使用 Dispatchers.Main.immediate
        // 如果当前就是主线程，则直接执行，否则切换到主线程才执行
        MainNavController.navigate(
          LoginNavArgument(target)
        ) {
          if (clearStack) {
            // 清空栈
            popUpTo(0) { inclusive = true }
          }
          launchSingleTop = true
          restoreState = false
        }
      }
    }
  }
}