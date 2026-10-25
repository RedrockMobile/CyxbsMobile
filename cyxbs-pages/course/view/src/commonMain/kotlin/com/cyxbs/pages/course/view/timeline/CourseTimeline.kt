package com.cyxbs.pages.course.view.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.data.CourseTimelineData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
data class CourseTimeline(
  val data: ImmutableList<CourseTimelineData> = DefaultTimeline,
  val beginDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {

  val linkNodeList = mutableListOf<LinkNode>()

  val scrollState = ScrollState(0)

  // scroll 整个布局的偏移量
  val marginBottom = SnapshotStateMap<String, Int>()

  init {
    var totalInitialWeight = 0F
    data.fastForEachIndexed { i, it ->
      totalInitialWeight += it.initialWeight
      linkNodeList.add(
        LinkNode(
          totalInitialWeight = totalInitialWeight,
          index = i
        )
      )
      if (i != 0) {
        check(data[i - 1].endTime == it.startTime) {
          "时间段不连续, i = $i, i-1: ${data[i - 1]}, i: $it"
        }
      }
    }
  }

  inner class LinkNode(
    val totalInitialWeight: Float,
    val index: Int,
  ) {
    val value = data[index]
    val startTime: MinuteTime
      get() = value.startTime
    val endTime: MinuteTime
      get() = value.endTime
    val totalShowWeightState = derivedStateOfStructure {
      var sum = 0F
      for (i in 0..index) {
        sum += data[i].nowWeight
      }
      sum
    }
  }

  val totalInitialWeight: Float
    get() = linkNodeList.last().totalInitialWeight

  val totalShowWeight: Float
    get() = linkNodeList.last().totalShowWeightState.value

  /**
   * 将 [time] 换算为当前时间轴展开状态下的累计权重。
   *
   * 时间位于时间轴首尾之外时固定到对应边界，避免自定义时间轴未覆盖完整一天时，
   * 二分查找的插入位置落到 [linkNodeList] 尾部之外。
   */
  fun calculateWeight(
    time: MinuteTime
  ): Float {
    val first = linkNodeList.first()
    val last = linkNodeList.last()
    if (time <= first.startTime) return 0F
    if (time >= last.endTime) return totalShowWeight
    val index = linkNodeList.binarySearchBy(time) { it.endTime }
    val weight = if (index >= 0) {
      linkNodeList[index].totalShowWeightState.value
    } else {
      // 二分没找到时返回 -(low + 1)
      // 此时 low 位置表示 > time 的最小索引
      val start = linkNodeList.getOrNull(-index - 2)
      val end = linkNodeList[-index - 1]
      (start?.totalShowWeightState?.value ?: 0F) +
          end.startTime.minutesUntil(time) /
          (end.startTime.minutesUntil(end.endTime)).toFloat() *
          end.value.nowWeight
    }
    return weight
  }

  fun calculateWeightRatio(
    time: MinuteTime
  ): Float {
    return calculateWeight(time) / totalShowWeight
  }

  /**
   * 以固定 [CourseTimelineData.initialWeight] 计算时间轴比例。
   *
   * 该方法专用于 Widget 等外部稳定快照，避免用户展开早晨/中午/夜间导致 `nowWeight` 瞬时变化并被错误持久化。
   */
  fun calculateInitialWeightRatio(time: MinuteTime): Float {
    val first = linkNodeList.first()
    val last = linkNodeList.last()
    if (time <= first.startTime) return 0F
    if (time >= last.endTime) return 1F
    val index = linkNodeList.binarySearchBy(time) { it.endTime }
    val weight = if (index >= 0) {
      linkNodeList[index].totalInitialWeight
    } else {
      val start = linkNodeList.getOrNull(-index - 2)
      val end = linkNodeList[-index - 1]
      (start?.totalInitialWeight ?: 0F) +
        end.startTime.minutesUntil(time) /
          end.startTime.minutesUntil(end.endTime).toFloat() * end.value.initialWeight
    }
    return weight / totalInitialWeight
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
    val beginTime1Weight = calculateWeight(beginTime1)
    val finalTime1Weight = calculateWeight(finalTime1)
    val beginTime2Weight = calculateWeight(beginTime2)
    val finalTime2Weight = calculateWeight(finalTime2)
    if (finalTime2Weight - beginTime2Weight == 0F) return Offset.Zero
    return Offset(
      x = (beginTime1Weight - beginTime2Weight) / (finalTime2Weight - beginTime2Weight),
      y = (finalTime1Weight - beginTime2Weight) / (finalTime2Weight - beginTime2Weight),
    )
  }

  /**
   * 根据相对于课表内容顶部的 [height] 反算对应的 [MinuteTime]。
   *
   * 长按拖动期间指针允许短暂移出课表边界，因此越过顶部或底部时分别固定为时间轴首尾时间；
   * 区间内继续按当前展开权重插值，调用方无需预先裁剪触摸坐标。
   */
  fun calculateMinuteTime(coursePage: LocalCoursePageContext, height: Float): MinuteTime {
    val totalHeight = coursePage.layoutCoordinates.size.height.toFloat()
    if (height <= 0F) return linkNodeList.first().startTime
    if (height >= totalHeight) return linkNodeList.last().endTime
    val index = linkNodeList.binarySearchBy(height.roundToInt()) {
      (it.totalShowWeightState.value / totalShowWeight * totalHeight).roundToInt()
    }
    return if (index >= 0) {
      linkNodeList[index].endTime
    } else {
      val start = linkNodeList.getOrNull(-index - 2)
      val end = linkNodeList[-index - 1]
      val startHeight = (start?.totalShowWeightState?.value ?: 0F) / totalShowWeight * totalHeight
      val endHeight = end.totalShowWeightState.value / totalShowWeight * totalHeight
      val diffMinute = ((height - startHeight) / (endHeight - startHeight) *
          end.startTime.minutesUntil(end.endTime)).roundToInt()
      end.startTime.plusMinutes(diffMinute)
    }
  }
}

/**
 * 课程时间轴布局
 * @param timelineWidth 时间轴宽度
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param scrollPaddingValues 滚轴内部 padding
 * @param content 时间轴内容
 */
@Composable
fun CourseTimeline.Content(
  modifier: Modifier = Modifier,
  timelineWidth: Dp = 40.dp,
  enableDrawNowTimeLine: Boolean = false,
  scrollPaddingValues: PaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp),
  content: @Composable () -> Unit
) {
  CourseScrollCompose(
    timeline = this,
    modifier = modifier.fillMaxSize(),
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
    content()
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
    val y = timeline.calculateWeightRatio(nowTimeState.value) * size.height
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
