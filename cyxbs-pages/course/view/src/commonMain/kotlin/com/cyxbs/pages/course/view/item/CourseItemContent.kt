package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.pages.course.view.data.OverlayData
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * 分离 [CourseItemContent] 和 [CourseItem]，减少耦合
 *
 * @author 985892345
 * @date 2025/3/5
 */
data class CourseItemContent(
  private val overlap: OverlayData,
) {

  private val item: CourseItem = overlap.item

  val key: String
    get() = item.key

  @Composable
  fun Content(modifier: Modifier, timeline: CourseTimeline) {
    item.Content(
      modifier = modifier,
      overlap = overlap,
      timeline = timeline,
    )
  }
}