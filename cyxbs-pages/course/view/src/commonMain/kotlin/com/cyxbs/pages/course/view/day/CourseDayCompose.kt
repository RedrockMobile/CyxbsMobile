package com.cyxbs.pages.course.view.day

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.overlap.CourseItemOverlapHelper
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList

/**
 *
 * @param items 获取某一天所有 item，如果 item 被完全遮挡，则不会显示
 */
@Composable
fun CourseDayCompose(
  timeline: CourseTimeline,
  modifier: Modifier = Modifier,
  items: () -> ImmutableList<CourseItem>,
) {
  // 处理 item 重叠
  val overlapHelper = CourseItemOverlapHelper()
  overlapHelper.overlay(items()).fastForEach { overlay ->
    key(overlay.item, overlay.itemId) {
      overlay.item.Content(
        modifier = modifier,
        overlap = overlay,
        timeline = timeline,
      )
    }
  }
}