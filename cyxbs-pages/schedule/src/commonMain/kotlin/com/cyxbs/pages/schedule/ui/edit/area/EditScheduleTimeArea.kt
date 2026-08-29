package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.schedule.ui.edit.EditScheduleModelState
import com.cyxbs.pages.schedule.ui.edit.ToggleChip
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

private const val MIN_INTERVAL_MINUTES = 30
private const val LAST_MINUTE_OF_DAY = 23 * 60 + 59

/** 当前由哪一端滚轮驱动时间段变化；另一端负责自动补足最短时长。 */
internal enum class ScheduleTimeBoundary {
  START,
  END,
}

/** 区分小时与分钟滚轮，使小时变化时可以先保留另一端的分钟值。 */
internal enum class ScheduleTimeComponent {
  HOUR,
  MINUTE,
}

/** 已满足同日时间段约束的起止分钟。 */
internal data class ScheduleTimeInterval(
  val startMinuteOfDay: Int,
  val endMinuteOfDay: Int,
)

/**
 * 编辑时间段或者时间点。
 *
 * @author 985892345
 * @date 2026/7/5
 */
@Composable
internal fun EditScheduleTimeArea(
  state: EditScheduleModelState,
) {
  val colors = LocalAppColors.current
  val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
  val startMin0 = state.startMinuteOfDay ?: (now.hour * 60 + now.minute)
  val endMin0 = state.endMinuteOfDay ?: ((startMin0 + 60).coerceAtMost(23 * 60 + 59))
  // 时间点型：底层仍使用 Deadline 原子，但产品文案统一为“时间点”，不再暴露旧“截止”概念。
  var deadlineOnly by remember { mutableStateOf(state.outputStartTime == null && state.outputEndTime != null) }

  val startHour = remember { Animatable((startMin0 / 60).toFloat()) }
  val startMinute = remember { Animatable((startMin0 % 60).toFloat()) }
  val endHour = remember { Animatable((endMin0 / 60).toFloat()) }
  val endMinute = remember { Animatable((endMin0 % 60).toFloat()) }
  val hours = remember { (0..23).map { it.toString().padStart(2, '0') }.toPersistentList() }
  val minutes = remember { (0..59).map { it.toString().padStart(2, '0') }.toPersistentList() }
  val coroutineScope = rememberCoroutineScope()
  /** 根据本次操作端补足 30 分钟，并把滚轮与表单状态一次收敛到同一结果。 */
  suspend fun settleInterval(
    boundary: ScheduleTimeBoundary,
    component: ScheduleTimeComponent,
  ) {
    val interval = adjustScheduleTimeInterval(
      startMinuteOfDay = startHour.value.roundToInt() * 60 + startMinute.value.roundToInt(),
      endMinuteOfDay = endHour.value.roundToInt() * 60 + endMinute.value.roundToInt(),
      changedBoundary = boundary,
      changedComponent = component,
    )
    startHour.snapTo((interval.startMinuteOfDay / 60).toFloat())
    startMinute.snapTo((interval.startMinuteOfDay % 60).toFloat())
    endHour.snapTo((interval.endMinuteOfDay / 60).toFloat())
    endMinute.snapTo((interval.endMinuteOfDay % 60).toFloat())
    state.applyExplicitTimeModeSelection(
      interval = true,
      startMinuteOfDay = interval.startMinuteOfDay,
      endMinuteOfDay = interval.endMinuteOfDay,
    )
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    // 时间段 / 时间点分段切换，两种选择分别无损映射到 Timed / Deadline。
    ScheduleTimeTypeToggle(isInterval = !deadlineOnly, onChange = { interval ->
      deadlineOnly = !interval
      if (interval) {
        // 切回时间段也属于显式操作，需要立即补足最短 30 分钟并同步滚轮位置。
        coroutineScope.launch {
          settleInterval(ScheduleTimeBoundary.START, ScheduleTimeComponent.MINUTE)
        }
      } else {
        state.applyExplicitTimeModeSelection(
          interval = false,
          startMinuteOfDay = startHour.value.roundToInt().coerceIn(0, 23) * 60 +
            startMinute.value.roundToInt().coerceIn(0, 59),
          endMinuteOfDay = endHour.value.roundToInt().coerceIn(0, 23) * 60 +
            endMinute.value.roundToInt().coerceIn(0, 59),
        )
      }
    })
    // 去掉「完成」按钮后，滚轮整体下移一点。
    Spacer(modifier = Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth().height(100.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (deadlineOnly) {
        // 时间点只有一个滚轮：居中、占一半宽度，避免背景铺满整行显得太宽。
        WheelPair(hours, minutes, endHour, endMinute, modifier = Modifier.fillMaxWidth(0.5f))
      } else {
        WheelPair(
          hours, minutes, startHour, startMinute,
          modifier = Modifier.weight(1f),
          onHourDragStopped = {
            coroutineScope.launch {
              settleInterval(ScheduleTimeBoundary.START, ScheduleTimeComponent.HOUR)
            }
          },
          onMinuteDragStopped = {
            coroutineScope.launch {
              settleInterval(ScheduleTimeBoundary.START, ScheduleTimeComponent.MINUTE)
            }
          },
        )
        Text("—", modifier = Modifier.padding(horizontal = 8.dp), color = colors.tvLv2)
        WheelPair(
          hours, minutes, endHour, endMinute,
          modifier = Modifier.weight(1f),
          onHourDragStopped = {
            coroutineScope.launch {
              settleInterval(ScheduleTimeBoundary.END, ScheduleTimeComponent.HOUR)
            }
          },
          onMinuteDragStopped = {
            coroutineScope.launch {
              settleInterval(ScheduleTimeBoundary.END, ScheduleTimeComponent.MINUTE)
            }
          },
        )
      }
    }
  }

  // collector 生命周期与区域一致：首次 composition 无条件跳过，避免 Unscheduled/AllDay 被默认 now/end 写回。
  // 模式切换由点击回调显式提交，不再以 deadlineOnly 重启 collector，因此真实切换不会被“首次 emission”吞掉。
  LaunchedEffect(Unit) {
    var firstEmission = true
    snapshotFlow {
      listOf(
        startHour.value.roundToInt(), startMinute.value.roundToInt(),
        endHour.value.roundToInt(), endMinute.value.roundToInt(),
      )
    }.collect {
      if (firstEmission) {
        firstEmission = false
        return@collect
      }
      state.applyExplicitTimeModeSelection(
        interval = !deadlineOnly,
        startMinuteOfDay = startHour.value.roundToInt().coerceIn(0, 23) * 60 +
          startMinute.value.roundToInt().coerceIn(0, 59),
        endMinuteOfDay = endHour.value.roundToInt().coerceIn(0, 23) * 60 +
          endMinute.value.roundToInt().coerceIn(0, 59),
      )
    }
  }
}

/**
 * 按最后操作的一端修正同日时间段。
 *
 * 调整开始小时且越过结束时间时，先只把结束小时抬到相同小时、保留原结束分钟；该结果仍不足 30 分钟才
 * 改为“开始 + 30 分钟”。调整开始分钟直接补足结束端；调整结束端则固定结束值并把开始端最多推到
 * “结束 - 30 分钟”。由于当前编辑器不表达跨日时间段，开始端最晚为 23:29，结束端最早为 00:30。
 */
internal fun adjustScheduleTimeInterval(
  startMinuteOfDay: Int,
  endMinuteOfDay: Int,
  changedBoundary: ScheduleTimeBoundary,
  changedComponent: ScheduleTimeComponent,
): ScheduleTimeInterval = when (changedBoundary) {
  ScheduleTimeBoundary.START -> {
    val start = startMinuteOfDay.coerceIn(0, LAST_MINUTE_OF_DAY - MIN_INTERVAL_MINUTES)
    val currentEnd = endMinuteOfDay.coerceIn(0, LAST_MINUTE_OF_DAY)
    val endAfterHourCorrection = if (
      changedComponent == ScheduleTimeComponent.HOUR && currentEnd < start
    ) {
      start / 60 * 60 + currentEnd % 60
    } else {
      currentEnd
    }
    val end = endAfterHourCorrection
      .coerceAtLeast(start + MIN_INTERVAL_MINUTES)
    ScheduleTimeInterval(start, end)
  }
  ScheduleTimeBoundary.END -> {
    val end = endMinuteOfDay.coerceIn(MIN_INTERVAL_MINUTES, LAST_MINUTE_OF_DAY)
    // 保留用户选择的结束值；若它越过开始端，则把开始端直接拉回到结束前 30 分钟。
    val start = startMinuteOfDay.coerceIn(0, LAST_MINUTE_OF_DAY)
      .coerceAtMost(end - MIN_INTERVAL_MINUTES)
    ScheduleTimeInterval(start, end)
  }
}

/**
 * 提交用户显式的时间模式/滚轮动作；初始化 composition 不调用此函数，因此 Unscheduled/AllDay 保持原样。
 */
internal fun EditScheduleModelState.applyExplicitTimeModeSelection(
  interval: Boolean,
  startMinuteOfDay: Int,
  endMinuteOfDay: Int,
) {
  val date = anchorDate
  isAllDay = false
  isInterval = interval
  endTime = formatScheduleDateTime(
    date.year, date.monthNumber, date.dayOfMonth,
    endMinuteOfDay.coerceIn(0, 23 * 60 + 59) / 60,
    endMinuteOfDay.coerceIn(0, 23 * 60 + 59) % 60,
  )
  startTime = if (interval) {
    formatScheduleDateTime(
      date.year, date.monthNumber, date.dayOfMonth,
      startMinuteOfDay.coerceIn(0, 23 * 60 + 59) / 60,
      startMinuteOfDay.coerceIn(0, 23 * 60 + 59) % 60,
    )
  } else {
    ""
  }
}

/** 时间段 / 时间点分段切换（紧凑胶囊，左对齐）。 */
@Composable
private fun ScheduleTimeTypeToggle(isInterval: Boolean, onChange: (Boolean) -> Unit) {
  Row {
    ToggleChip("时间段", selected = isInterval) { onChange(true) }
    Spacer(modifier = Modifier.width(8.dp))
    ToggleChip("时间点", selected = !isInterval) { onChange(false) }
  }
}

@Composable
private fun WheelPair(
  hours: ImmutableList<String>,
  minutes: ImmutableList<String>,
  hourLine: Animatable<Float, AnimationVector1D>,
  minuteLine: Animatable<Float, AnimationVector1D>,
  modifier: Modifier = Modifier,
  onHourDragStopped: () -> Unit = {},
  onMinuteDragStopped: () -> Unit = {},
) {
  val colors = LocalAppColors.current
  Row(modifier = modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
    WheelSelectCompose(
      selectedLine = hourLine,
      options = hours,
      modifier = Modifier.weight(1f).fillMaxHeight(),
      onDragStopped = onHourDragStopped,
    )
    // 字体中的冒号字形并非相对数字的视觉中心对称，因此自行绘制以保证上下两点严格居中。
    Canvas(modifier = Modifier.width(4.dp).height(14.dp)) {
      val radius = 1.dp.toPx()
      val offsetY = 3.dp.toPx()
      val center = Offset(size.width / 2, size.height / 2)
      drawCircle(
        color = colors.tvLv2,
        radius = radius,
        center = center.copy(y = center.y - offsetY),
      )
      drawCircle(
        color = colors.tvLv2,
        radius = radius,
        center = center.copy(y = center.y + offsetY),
      )
    }
    WheelSelectCompose(
      selectedLine = minuteLine,
      options = minutes,
      modifier = Modifier.weight(1f).fillMaxHeight(),
      onDragStopped = onMinuteDragStopped,
    )
  }
}
