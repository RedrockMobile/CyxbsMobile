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
   * - 不会覆盖下层 item，但是仍然会受到上层 item 的覆盖
   */
  fun ignoreCoverOther(item: CourseItem): OverlayLock

  interface OverlayLock {
    // 解锁，返回 false 时表示上锁次数仍 > 解锁次数，此时仍会保持被锁状态
    // 有效调用只有第一次，重复调用不会有任何效果
    fun unlock(): Boolean
  }
}