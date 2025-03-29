package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.pages.course.view.data.OverlayData
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * 分离 [CourseItemContent] 和 [CourseItem]，减少耦合
 *
 * @author 985892345
 * @date 2025/3/5
 */
@Stable
data class CourseItemContent(
  val overlap: OverlayData,
) {

  val item: CourseItem = overlap.item

  val key: String
    get() = item.key

  @Composable
  fun Content(modifier: Modifier, timeline: CourseTimeline) {
    item.CourseItemContent(
      modifier = modifier,
      overlap = overlap,
      timeline = timeline,
    )
  }
}