package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.view.day.CourseDayCompose
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.timeline.Content
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/10
 */

/**
 * @param timeline 时间轴
 * @param beginDayOfWeek 周几开始，默认周一
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param verticalScrollState 垂直滚动状态
 * @param items 获取某一天所有 item，如果 item 被完全遮挡，则不会显示
 */
@Composable
fun CoursePageCompose(
  timeline: CourseTimeline,
  modifier: Modifier = Modifier,
  beginDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
  enableDrawNowTimeLine: Boolean = true,
  verticalScrollState: ScrollState = rememberScrollState(),
  items: (dayOfWeek: DayOfWeek) -> ImmutableList<CourseItem>,
) {
  timeline.Content(
    modifier = modifier,
    enableDrawNowTimeLine = enableDrawNowTimeLine,
    verticalScrollState = verticalScrollState,
  ) {
    repeat(7) { index ->
      CourseDayCompose(
        modifier = Modifier.layout { measurable, constraints ->
          val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth / 7, maxHeight = constraints.maxHeight))
          layout(placeable.width, placeable.height) {
            placeable.placeRelative(index * placeable.width, 0)
          }
        },
        timeline = timeline,
        items = { items(DayOfWeek((beginDayOfWeek.ordinal + index) % 7 + 1)) }
      )
    }
  }
}



