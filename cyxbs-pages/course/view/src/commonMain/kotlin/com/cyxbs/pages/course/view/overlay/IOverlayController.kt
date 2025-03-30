package com.cyxbs.pages.course.view.overlay

import androidx.compose.runtime.compositionLocalOf
import com.cyxbs.pages.course.view.item.CourseItem

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/30
 */

val LocalOverlayController = compositionLocalOf<IOverlayController> {
  error("LocalOverlayController not provided")
}

interface IOverlayController {

  /**
   * 改变该 item 带来的覆盖影响
   * - 设置为 true 后不会覆盖下层 item，但是仍然会受到上层 item 的覆盖
   */
  fun ignoreCoverOther(item: CourseItem, enable: Boolean)
}