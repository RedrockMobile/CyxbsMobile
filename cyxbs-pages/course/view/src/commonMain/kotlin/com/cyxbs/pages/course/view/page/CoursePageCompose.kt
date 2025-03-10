package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachReversed
import com.cyxbs.components.config.time.add
import com.cyxbs.pages.course.view.data.CourseWeekDataPool
import com.cyxbs.pages.course.view.item.CourseItemContent
import com.cyxbs.pages.course.view.timeline.Content
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/10
 */

/**
 * @param timeline 时间轴
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param verticalScrollState 垂直滚动状态
 * @param weekDataPools 当周的数据，越靠前越展示在顶层
 */
@Composable
fun CoursePageCompose(
  timeline: CourseTimeline,
  modifier: Modifier = Modifier,
  enableDrawNowTimeLine: Boolean = true,
  verticalScrollState: ScrollState = rememberScrollState(),
  weekDataPools: List<CourseWeekDataPool>,
) {
  timeline.Content(
    modifier = modifier,
    enableDrawNowTimeLine = enableDrawNowTimeLine,
    verticalScrollState = verticalScrollState,
  ) {
    weekDataPools.fastForEachReversed { weekDataPool ->
      key(weekDataPool.hashCode()) {
        CourseWeekDataContent(
          weekDataPool = weekDataPool,
          timeline = timeline,
        )
      }
    }
  }
}

@Composable
private fun CourseWeekDataContent(weekDataPool: CourseWeekDataPool, timeline: CourseTimeline) {
  repeat(7) { index ->
    val dayOfWeek = timeline.beginDayOfWeek.add(index)
    weekDataPool.get(dayOfWeek).state.value.fastForEach { content ->
      key(content.key) {
        CourseItemContent(
          content = content,
          timeline = timeline,
          index = index,
        )
      }
    }
  }
}

@Composable
private fun CourseItemContent(content: CourseItemContent, timeline: CourseTimeline, index: Int) {
  content.Content(
    modifier = Modifier.layout { measurable, constraints ->
      val placeable = measurable.measure(
        Constraints(
          maxWidth = constraints.maxWidth / 7,
          maxHeight = constraints.maxHeight
        )
      )
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(index * placeable.width, 0)
      }
    },
    timeline = timeline,
  )
}



