package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.animation.core.Animatable
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * 编辑时间段或者截止时间
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
  // 截止型：原本没有开始时间。提供一个开关在「时间段 / 仅截止」之间切换。
  var deadlineOnly by remember { mutableStateOf(state.outputStartTime == null && state.outputEndTime != null) }

  val startHour = remember { Animatable((startMin0 / 60).toFloat()) }
  val startMinute = remember { Animatable((startMin0 % 60).toFloat()) }
  val endHour = remember { Animatable((endMin0 / 60).toFloat()) }
  val endMinute = remember { Animatable((endMin0 % 60).toFloat()) }
  val hours = remember { (0..23).map { it.toString().padStart(2, '0') }.toPersistentList() }
  val minutes = remember { (0..59).map { it.toString().padStart(2, '0') }.toPersistentList() }

  Column(modifier = Modifier.fillMaxWidth()) {
    // 时间段 / 截止 分段框框切换（沿用 todo cmp 的样式）。
    ScheduleTimeTypeToggle(isInterval = !deadlineOnly, onChange = { interval ->
      deadlineOnly = !interval
      // 模式切换是显式用户事件：立即提交当前滚轮值；collector 不随模式重启，因而不会吞掉本次切换。
      state.applyExplicitTimeModeSelection(
        interval = interval,
        startMinuteOfDay = startHour.value.roundToInt().coerceIn(0, 23) * 60 +
          startMinute.value.roundToInt().coerceIn(0, 59),
        endMinuteOfDay = endHour.value.roundToInt().coerceIn(0, 23) * 60 +
          endMinute.value.roundToInt().coerceIn(0, 59),
      )
    })
    // 去掉「完成」按钮后，滚轮整体下移一点。
    Spacer(modifier = Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth().height(100.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (deadlineOnly) {
        // 截止型只有一个滚轮：居中、占一半宽度，避免背景铺满整行显得太宽。
        WheelPair(hours, minutes, endHour, endMinute, modifier = Modifier.fillMaxWidth(0.5f))
      } else {
        WheelPair(hours, minutes, startHour, startMinute, modifier = Modifier.weight(1f))
        Text("—", modifier = Modifier.padding(horizontal = 8.dp), color = colors.tvLv2)
        WheelPair(hours, minutes, endHour, endMinute, modifier = Modifier.weight(1f))
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

/** 时间段 / 截止 分段框框切换（紧凑胶囊，左对齐）。 */
@Composable
private fun ScheduleTimeTypeToggle(isInterval: Boolean, onChange: (Boolean) -> Unit) {
  Row {
    ToggleChip("时间段", selected = isInterval) { onChange(true) }
    Spacer(modifier = Modifier.width(8.dp))
    ToggleChip("截止", selected = !isInterval) { onChange(false) }
  }
}

@Composable
private fun WheelPair(
  hours: ImmutableList<String>,
  minutes: ImmutableList<String>,
  hourLine: Animatable<Float, *>,
  minuteLine: Animatable<Float, *>,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  @Suppress("UNCHECKED_CAST")
  Row(modifier = modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
    WheelSelectCompose(
      selectedLine = hourLine as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
      options = hours,
      modifier = Modifier.weight(1f).fillMaxHeight(),
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
      selectedLine = minuteLine as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
      options = minutes,
      modifier = Modifier.weight(1f).fillMaxHeight(),
    )
  }
}