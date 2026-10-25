package com.cyxbs.pages.home.api

import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.appNavBackStack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 主页的路由参数。[courseItemId] 是桌面小组件的可选定位信息，内部已包含教学周且不携带重叠条目；
 * 主页课表会从当前页面状态重新解析详情。
 */
@Serializable
data class HomeNavArgument(
  @SerialName("page")
  val page: String = "discover", // discover、fairground、mine
  val courseItemId: String? = null,
  /** 每次小组件点击生成的新身份；只用于页面重组，不写入 URL 或保存的导航栈。 */
  @Transient val courseOpenId: Long? = null,
) : AppNavArgument {

  private companion object {
    /** navigate 在 UI 线程消费外部 URL，单调序号用于区分重复点击相同的静态小组件链接。 */
    var nextCourseOpenId = 0L
  }

  /**
   * 栈内已有主页时回到最早的主页并替换参数，保证主页只出现一次。
   * 有效的小组件点击生成新身份，让相同 URL 的再次点击也触发主页重组；冷启动无主页时正常压栈。
   */
  override fun navigate() {
    val itemId = courseItemId?.takeIf { it.isNotBlank() }
    val target = if (itemId != null && courseOpenId == null) {
      copy(courseOpenId = ++nextCourseOpenId)
    } else {
      this
    }
    val existingHomeIndex = appNavBackStack.indexOfFirst { it is HomeNavArgument }
    if (existingHomeIndex >= 0) {
      // 主页是栈内单例：先按正常回退语义移除其上所有页面，再用新参数重建主页。
      while (appNavBackStack.lastIndex > existingHomeIndex) {
        appNavBackStack.last().popBackStack()
      }
      appNavBackStack.last().popBackStack()
      target.navigate()
    } else if (target !== this) {
      target.navigate()
    } else {
      super.navigate()
    }
  }
}
