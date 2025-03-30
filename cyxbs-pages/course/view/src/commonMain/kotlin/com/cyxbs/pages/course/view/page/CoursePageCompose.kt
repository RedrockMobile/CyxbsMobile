package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.add
import com.cyxbs.pages.course.view.data.CourseWeekDataPool
import com.cyxbs.pages.course.view.item.CourseItemContent
import com.cyxbs.pages.course.view.overlay.LocalOverlayController
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
 * @param decorations 绘制在课表上的装饰物，同时也能拦截触摸事件
 * @param weekDataPool 当周的数据，越靠前越展示在顶层
 */
@Composable
fun CoursePageCompose(
  timeline: CourseTimeline,
  weekDataPool: CourseWeekDataPool,
  modifier: Modifier = Modifier,
  enableDrawNowTimeLine: Boolean = true,
  verticalScrollState: ScrollState = rememberScrollState(),
  decorations: List<CoursePageDecoration> = emptyList(),
) {
  val timelineWidth = 40.dp
  val scrollPaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp)
  Box {
    decorations.fastForEach {
      key(it.hashCode()) { it.OuterCoursePageBottom(timeline, verticalScrollState, weekDataPool, scrollPaddingValues, timelineWidth) }
    }
    timeline.Content(
      modifier = modifier,
      enableDrawNowTimeLine = enableDrawNowTimeLine,
      verticalScrollState = verticalScrollState,
    ) {
      decorations.fastForEach {
        key(it.hashCode()) { it.InnerCoursePageBottom(timeline, verticalScrollState, weekDataPool) }
      }
      CourseWeekDataContent(
        weekDataPool = weekDataPool,
        timeline = timeline,
      )
      decorations.fastForEach {
        key(it.hashCode() + 1) { it.InnerCoursePageTop(timeline, verticalScrollState, weekDataPool) }
      }
    }
    decorations.fastForEach {
      key(it.hashCode() + 1) { it.OuterCoursePageTop(timeline, verticalScrollState, weekDataPool, scrollPaddingValues, timelineWidth) }
    }
  }
}

@Composable
private fun CourseWeekDataContent(weekDataPool: CourseWeekDataPool, timeline: CourseTimeline) {
  repeat(7) { index ->
    val dayOfWeek = timeline.beginDayOfWeek.add(index)
    val dayDataPool = weekDataPool.get(dayOfWeek)
    CompositionLocalProvider(
      LocalOverlayController provides dayDataPool,
    ) {
      dayDataPool.state.value.fastForEach { content ->
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



