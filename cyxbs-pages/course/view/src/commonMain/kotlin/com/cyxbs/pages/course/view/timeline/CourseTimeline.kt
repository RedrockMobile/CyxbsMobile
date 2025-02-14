package com.cyxbs.pages.course.view.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
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
import com.cyxbs.pages.course.view.timeline.data.CourseTimelineData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
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
class CourseTimeline(
  val startMinuteTime: MinuteTime = DefaultTimelineStartMinuteTime,
  val data: ImmutableList<CourseTimelineData> = DefaultTimeline,
) {

  fun getItemWhichDate(startTimeDate: MinuteTimeDate): Date {
    return if (startTimeDate.time >= startMinuteTime) {
      startTimeDate.date
    } else {
      startTimeDate.date.minusDays(1)
    }
  }

  /**
   * 计算 [beginTime] [finalTime] 在整个时间轴上的占比
   * @return Offset(startWeight, endWeight)
   */
  fun calculateBeginFinalWeight(
    beginTime: MinuteTime,
    finalTime: MinuteTime,
  ) : Offset {
    return calculateBeginFinalWeightInternal(
      beginTimeInt = beginTime.let {
        if (it < startMinuteTime) (24 + it.hour) * 60 + it.minute
        else it.hour * 60 + it.minute
      },
      finalTimeInt = finalTime.let {
        if (it <= startMinuteTime) (24 + it.hour) * 60 + it.minute
        else it.hour * 60 + it.minute
      }
    )
  }

  private fun calculateBeginFinalWeightInternal(
    beginTimeInt: Int,
    finalTimeInt: Int,
  ): Offset {
    var startWeight = 0F
    var endWeight = 0F
    var allWeight = 0F
    data.fastForEach {
      allWeight += it.nowWeight
      val startLine = if (it.startTime < startMinuteTime) {
        (24 + it.startTime.hour) * 60 + it.startTime.minute
      } else it.startTime.hour * 60 + it.startTime.minute
      val endLine = if (it.endTime <= startMinuteTime) {
        (24 + it.endTime.hour) * 60 + it.endTime.minute
      } else it.endTime.hour * 60 + it.endTime.minute
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
      y = endWeight,
    )
  }
}

/**
 * 课程时间轴
 * @param timelineWidth 时间轴宽度
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param verticalScrollState 垂直滚动状态
 * @param scrollPaddingBottom 滚轴底部 padding
 * @param content 时间轴内容
 */
@Composable
fun CourseTimeline.Content(
  modifier: Modifier = Modifier.fillMaxSize(),
  timelineWidth: Dp = 36.dp,
  enableDrawNowTimeLine: Boolean = false,
  verticalScrollState: ScrollState = rememberScrollState(),
  scrollPaddingBottom: Dp = 0.dp,
  content: @Composable (ScrollState) -> Unit
) {
  CourseScrollCompose(
    timeline = this,
    modifier = modifier,
    verticalScrollState = verticalScrollState,
    scrollPaddingBottom = scrollPaddingBottom,
  ) { scrollState ->
    Column(
      modifier = Modifier.width(timelineWidth)
        .drawNowTimeLine(enable = enableDrawNowTimeLine, timeline = this)
    ) {
      data.fastForEach {
        it.apply { Content() }
      }
    }
    content(scrollState)
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
    val now = nowTimeState.value.let {
      if (it < timeline.startMinuteTime) (24 + it.hour) * 60 + it.minute
      else it.hour * 60 + it.minute
    }
    timeline.data.fastForEach {
      allWeight += it.nowWeight
      val start = if (it.startTime < timeline.startMinuteTime) {
        (24 + it.startTime.hour) * 60 + it.startTime.minute
      } else it.startTime.hour * 60 + it.startTime.minute
      val end = if (it.endTime <= timeline.startMinuteTime) {
        (24 + it.endTime.hour) * 60 + it.endTime.minute
      } else it.endTime.hour * 60 + it.endTime.minute
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

