package com.cyxbs.pages.course.view.overlap

import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItem

/**
 * 重叠的数据
 *
 * @author 985892345
 * @date 2025/2/15
 */
data class OverlayData(
  val item: CourseItem,
  val itemId: Int,
  val showBeginTime: MinuteTime,
  val showFinalTime: MinuteTime,
)