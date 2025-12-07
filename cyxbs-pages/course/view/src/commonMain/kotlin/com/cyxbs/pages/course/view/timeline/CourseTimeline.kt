package com.cyxbs.pages.course.view.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.timeline.data.CourseTimelineData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
   * 根据开始和结束时间计算比例后进行布局的方法
   * @param parent 父区域的开始和结束时间，如果为 null 则为整个时间轴
   */
  fun createLayoutModifier(
    beginTime: MinuteTime,
    finalTime: MinuteTime,
    parent: Pair<MinuteTime, MinuteTime>? = null,
  ) : Modifier {
    return Modifier.layout { measurable, constraints ->
      val weight =
        if (parent == null) calculateBeginFinalWeight(beginTime, finalTime)
        else calculateRelativeWeight(beginTime, finalTime, parent.first, parent.second)
      val height = (constraints.maxHeight * (weight.y - weight.x)).roundToInt()
      val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth, height)
      )
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, (constraints.maxHeight * weight.x).roundToInt())
      }
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
    var startWeight = 0F
    var endWeight = 0F
    var allWeight = 0F
    data.fastForEach {
      allWeight += it.nowWeight
      if (beginTime >= it.endTime) {
        startWeight += it.nowWeight
      } else if (beginTime >= it.startTime) {
        startWeight += it.startTime.minutesUntil(beginTime) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
      if (finalTime >= it.endTime) {
        endWeight += it.nowWeight
      } else if (finalTime >= it.startTime) {
        endWeight += it.startTime.minutesUntil(finalTime) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
    }
    return Offset(
      x = startWeight / allWeight,
      y = endWeight / allWeight,
    )
  }

  /**
   * 计算 [time] 在整个时间轴上的占比
   */
  fun calculateWeight(
    time: MinuteTime
  ): Float {
    var allWeight = 0F
    var nowWeight = 0F
    data.fastForEach {
      allWeight += it.nowWeight
      if (time >= it.endTime) {
        nowWeight += it.nowWeight
      } else if (time >= it.startTime) {
        nowWeight += it.startTime.minutesUntil(time) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
    }
    return nowWeight / allWeight
  }

  /**
   * 计算 [beginTime1] [finalTime1] 在 [beginTime2] [finalTime2] 上的占比
   * @return Offset(startWeight, endWeight)
   */
  fun calculateRelativeWeight(
    beginTime1: MinuteTime,
    finalTime1: MinuteTime,
    beginTime2: MinuteTime,
    finalTime2: MinuteTime,
  ): Offset {
    var startWeight1 = 0F
    var endWeight1 = 0F
    var startWeight2 = 0F
    var endWeight2 = 0F
    data.fastForEach {
      if (beginTime1 >= it.endTime) {
        startWeight1 += it.nowWeight
      } else if (beginTime1 >= it.startTime) {
        startWeight1 += it.startTime.minutesUntil(beginTime1) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
      if (finalTime1 >= it.endTime) {
        endWeight1 += it.nowWeight
      } else if (finalTime1 >= it.startTime) {
        endWeight1 += it.startTime.minutesUntil(finalTime1) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
      if (beginTime2 >= it.endTime) {
        startWeight2 += it.nowWeight
      } else if (beginTime2 >= it.startTime) {
        startWeight2 += it.startTime.minutesUntil(beginTime2) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
      if (finalTime2 >= it.endTime) {
        endWeight2 += it.nowWeight
      } else if (finalTime2 >= it.startTime) {
        endWeight2 += it.startTime.minutesUntil(finalTime2) / (it.startTime.minutesUntil(it.endTime)).toFloat() * it.nowWeight
      }
    }
    return Offset(
      x = (startWeight1 - startWeight2) / (endWeight2 - startWeight2),
      y = (endWeight1 - startWeight2) / (endWeight2 - startWeight2),
    )
  }

  /**
   * 计算 [height] 在时间轴上的 [MinuteTime]
   */
  fun calculateMinuteTime(scrollContext: LocalCourseScrollContext, height: Float): MinuteTime {
    data.fastForEach {
      scrollContext.timelineCoordinatesMap[it]?.let { coordinates ->
        val y1 = coordinates.positionInParent().y
        val y2 = y1 + coordinates.size.height
        if (height in y1..y2) {
          return it.startTime.plusMinutes(((it.startTime.minutesUntil(it.endTime)) * (height - y1) / (y2 - y1)).roundToInt())
        }
      }
    }
    if (height < scrollContext.timelineCoordinatesMap[data.first()]!!.positionInParent().y) {
      return data.first().startTime
    } else if (height > scrollContext.timelineCoordinatesMap[data.last()]!!.positionInParent().y) {
      return data.last().endTime
    }
    throw IllegalStateException("无法寻找高度对应时间，不应该出现的异常, height=$height, timeline=$data")
  }
}

/**
 * 课程时间轴布局
 * @param timelineWidth 时间轴宽度
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param verticalScrollState 垂直滚动状态
 * @param scrollPaddingValues 滚轴内部 padding
 * @param content 时间轴内容
 */
@Composable
fun CourseTimeline.Content(
  modifier: Modifier = Modifier,
  timelineWidth: Dp = 40.dp,
  enableDrawNowTimeLine: Boolean = false,
  verticalScrollState: ScrollState = rememberScrollState(),
  scrollPaddingValues: PaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp),
  content: @Composable () -> Unit
) {
  CourseScrollCompose(
    timeline = this,
    modifier = modifier.fillMaxSize(),
    verticalScrollState = verticalScrollState,
    scrollPaddingValues = scrollPaddingValues,
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
@Stable
@Composable
private fun Modifier.drawNowTimeLine(
  enable: Boolean,
  timeline: CourseTimeline,
): Modifier {
  if (!enable) return this
  val nowTimeState = remember { mutableStateOf(MinuteTime.now()) }
  LaunchedEffect(Unit) {
    val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    delay(1.minutes - localDateTime.second.seconds)
    while (true) {
      nowTimeState.value = nowTimeState.value.plusMinutes(1)
      delay(1.minutes)
    }
  }
  return this then drawBehind {
    val radius = 3.dp.toPx()
    val start = 2.dp.toPx() + radius
    val end = size.width - 2.dp.toPx()
    val y = timeline.calculateWeight(nowTimeState.value) * size.height
    drawCircle(
      color = Color.Gray,
      radius = radius,
      center = Offset(x = start, y = y),
    )
    drawLine(
      color = Color.Gray,
      start = Offset(x = start, y = y),
      end = Offset(x = end, y = y),
      strokeWidth = 1.dp.toPx()
    )
  }
}

