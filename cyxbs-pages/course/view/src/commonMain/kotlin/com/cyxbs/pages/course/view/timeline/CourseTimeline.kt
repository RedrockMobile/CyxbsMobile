package com.cyxbs.pages.course.view.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.course.view.timeline.data.CourseTimelineData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/10
 */
@Stable
@Serializable
data class CourseTimeline(
  val data: ImmutableList<CourseTimelineData> = DefaultTimeline,
  val beginDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {

  /**
   * 计算 [beginTime] [finalTime] 在整个时间轴上的占比
   * @return Offset(startWeight, endWeight)
   */
  fun calculateBeginFinalWeight(
    beginTime: MinuteTime,
    finalTime: MinuteTime,
  ) : Offset {
    val beginTimeInt = beginTime.minuteOfDay
    val finalTimeInt = finalTime.minuteOfDay
    var startWeight = 0F
    var endWeight = 0F
    var allWeight = 0F
    data.fastForEach {
      allWeight += it.nowWeight
      val startLine = it.startTime.minuteOfDay
      val endLine = it.endTime.minuteOfDay
      if (beginTimeInt >= endLine) {
        startWeight += it.nowWeight
      } else if (beginTimeInt >= startLine) {
        startWeight += (beginTimeInt - startLine) / (endLine - startLine).toFloat() * it.nowWeight
      }
      if (finalTimeInt >= endLine) {
        endWeight += it.nowWeight
      } else if (finalTimeInt >= startLine) {
        endWeight += (finalTimeInt - startLine) / (endLine - startLine).toFloat() * it.nowWeight
      }
    }
    return Offset(
      x = startWeight / allWeight,
      y = endWeight / allWeight,
    )
  }
}

/**
 * 课程时间轴布局
 * @param timelineWidth 时间轴宽度
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param verticalScrollState 垂直滚动状态
 * @param scrollPaddingBottom 滚轴底部 padding
 * @param content 时间轴内容
 */
@Composable
fun CourseTimeline.Content(
  modifier: Modifier = Modifier,
  timelineWidth: Dp = 40.dp,
  enableDrawNowTimeLine: Boolean = false,
  verticalScrollState: ScrollState = rememberScrollState(),
  scrollPaddingBottom: Dp = 12.dp,
  content: @Composable () -> Unit
) {
  CourseScrollCompose(
    timeline = this,
    modifier = modifier.fillMaxSize(),
    verticalScrollState = verticalScrollState,
    scrollPaddingBottom = scrollPaddingBottom,
  ) {
    Column(
      modifier = Modifier.width(timelineWidth).fillMaxHeight()
        .drawNowTimeLine(enable = enableDrawNowTimeLine, timeline = this)
    ) {
      data.fastForEach {
        it.apply { Content() }
      }
    }
    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      content()
    }
  }
}


// 绘制当前时间线
@Composable
private fun Modifier.drawNowTimeLine(
  enable: Boolean,
  timeline: CourseTimeline,
): Modifier {
  if (!enable) return this
  val nowTimeState = remember { mutableStateOf(MinuteTime.now()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1.minutes)
      nowTimeState.value = nowTimeState.value.plusMinutes(1)
    }
  }
  return this then drawBehind {
    var allWeight = 0F
    var nowWeight = 0F
    val now = nowTimeState.value.minuteOfDay
    timeline.data.fastForEach {
      allWeight += it.nowWeight
      val start = it.startTime.minuteOfDay
      val end = it.endTime.minuteOfDay
      if (now >= end) {
        nowWeight += it.nowWeight
      } else if (now >= start) {
        nowWeight += (now - start) / (end - start).toFloat() * it.nowWeight
      }
    }
    val radius = 3.dp.toPx()
    val y = nowWeight / allWeight * size.height
    drawCircle(
      color = Color.Gray,
      radius = radius,
      center = Offset(x = radius, y = y),
    )
    drawLine(
      color = Color.Gray,
      start = Offset(x = radius, y = y),
      end = Offset(x = size.width, y = y),
      strokeWidth = 1.dp.toPx()
    )
  }
}

