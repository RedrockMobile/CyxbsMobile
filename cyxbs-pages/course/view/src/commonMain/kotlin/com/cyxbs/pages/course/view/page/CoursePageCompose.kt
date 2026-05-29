package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEachReversed
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.timeline.Content
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/10
 */

// CoursePageCompose 下的一些 context
val LocalCoursePage = compositionLocalOf<LocalCoursePageContext> { error("未初始化") }

/**
 * @param timeline 时间轴
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param decorations 绘制在课表上的装饰物，同时也能拦截触摸事件
 */
@Composable
fun CoursePageCompose(
  page: Int,                    // 课表页数
  timeline: CourseTimeline,     // 左侧时间轴
  timelineWidth: Dp,            // 左侧时间轴宽度
  scrollPaddingValues: PaddingValues, // 滚动区域的内边距
  modifier: Modifier = Modifier,
  enableDrawNowTimeLine: Boolean = true,  // 是否绘制当前时间的线
  decorations: ImmutableList<CoursePageDecoration<*>>,
) {
  // 课表时间轴的绘制
  timeline.Content(
    modifier = modifier,
    timelineWidth = timelineWidth,
    enableDrawNowTimeLine = enableDrawNowTimeLine,
    scrollPaddingValues = scrollPaddingValues,
  ) {
    val courseScroll = LocalCourseScroll.current
    val pageContext = remember {
      LocalCoursePageContext(
        page = page,
        timeline = timeline,
        scrollContext = courseScroll,
      )
    }.apply {
      update(
        timeline = timeline,
        scrollContext = courseScroll,
      )
    }
    Box(
      modifier = Modifier.fillMaxSize().onGloballyPositioned {
        pageContext.layoutCoordinatesFlow.tryEmit(it)
      }
    ) {
      CompositionLocalProvider(
        LocalCoursePage provides pageContext,
      ) {
        decorations.fastForEachReversed { decoration ->
          key(decoration) {
            decoration.CoursePageContent()
          }
        }
      }
    }
  }
}

class LocalCoursePageContext(
  val page: Int, // page 并不一定就是周数，需要通过 AbstractCourseFrame#getWeekNumByPage 才能获取周数
  timeline: CourseTimeline,
  scrollContext: LocalCourseScrollContext, // 滚轴 context
) {

  var timeline: CourseTimeline by mutableStateOf(timeline)
    private set

  var scrollContext: LocalCourseScrollContext by mutableStateOf(scrollContext)
    private set

  val layoutCoordinatesFlow = MutableSharedFlow<LayoutCoordinates>(
    replay = 1,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val layoutCoordinates: LayoutCoordinates
    get() = layoutCoordinatesFlow.replayCache.first()

  fun update(
    timeline: CourseTimeline,
    scrollContext: LocalCourseScrollContext,
  ) {
    this.timeline = timeline
    this.scrollContext = scrollContext
  }
}