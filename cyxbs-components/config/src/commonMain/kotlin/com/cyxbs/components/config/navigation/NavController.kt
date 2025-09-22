package com.cyxbs.components.config.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.serialization.generateRouteWithArgs

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/1
 */

/**
 * 获取 target 对应路由
 */
fun <T : Any> NavController.getRoute(target: T): String {
  return generateRouteWithArgs(
    target,
    graph.findNode(target)!!.arguments.mapValues { it.value.type }
  )
}

fun NavController.navigateSingleTopTo(argument: Any) = navigate(argument) {
  popUpTo( // 如果栈顶不是起点，则弹出栈顶
    this@navigateSingleTopTo.graph.findStartDestination().route!!
  ) {
    saveState = true // 出栈的 BackStack 保存状态
  }
  launchSingleTop = true // 多次点击不会重复创建，类似与 activity 的 singleTop 模式
  restoreState = true // 返回时恢复状态
}
