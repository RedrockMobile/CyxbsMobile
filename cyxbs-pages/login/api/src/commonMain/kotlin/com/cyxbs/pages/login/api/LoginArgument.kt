package com.cyxbs.pages.login.api

import androidx.navigation.serialization.generateRouteWithArgs
import com.cyxbs.components.init.MainNavController
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/2
 */
@Serializable
class LoginArgument(
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
      MainNavController.navigate(
        LoginArgument(target)
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