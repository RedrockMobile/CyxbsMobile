package com.cyxbs.pages.map.api

import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.appNavBackStack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * @Desc : 地图的跳转参数
 * @Author : zzx
 * @Date : 2025/11/10 14:16
 */

@Serializable
class MapNavArgument(
  @SerialName("placeSearch")
  val placeSearch: String? = null
) : AppNavArgument {

  /**
   * 退出地图页前的清理钩子，由横屏的 sheet 宿主登记（弹出叠在 map 之上的 sheet NavEntry）。
   *
   * 横屏下 PlaceDetail / Search 两个 bottomSheet 是叠在 map 之上的独立 NavEntry，若直接弹 map 会留下
   * 悬空的 sheet entry 甚至弹空返回栈。统一在此先清理 sheet 再弹 map，覆盖所有走 [popBackStack] 的退出路径
   * （SearchCompose / BackIcon / backHandler 等）。
   */
  @Transient
  var beforePop: (() -> Unit)? = null

  override fun popBackStack() {
    beforePop?.invoke()
    // 防止把返回栈弹空（NavDisplay 要求 backStack 非空）
    if (appNavBackStack.size > 1) {
      super.popBackStack()
    }
  }
}