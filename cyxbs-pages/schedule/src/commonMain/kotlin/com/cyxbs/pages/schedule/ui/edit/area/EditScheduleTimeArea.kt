package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.schedule.ui.edit.EditScheduleState
import com.cyxbs.pages.schedule.ui.edit.ToggleChip
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime
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
  state: EditScheduleState,
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
    ScheduleTimeTypeToggle(isInterval = !deadlineOnly, onChange = { deadlineOnly = !it })
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

  // 滚轮值（及 截止/时间段 切换）实时写回 state：顶部「返回」← 收起即生效，无需单独「完成」按钮。
  LaunchedEffect(deadlineOnly) {
    snapshotFlow {
      listOf(
        startHour.value.roundToInt(), startMinute.value.roundToInt(),
        endHour.value.roundToInt(), endMinute.value.roundToInt(),
      )
    }.collect {
      val date = state.anchorDate
      val eH = endHour.value.roundToInt().coerceIn(0, 23)
      val eM = endMinute.value.roundToInt().coerceIn(0, 59)
      state.endTime = formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, eH, eM)
      state.startTime = if (deadlineOnly) {
        "" // 截止型：清空开始
      } else {
        val sH = startHour.value.roundToInt().coerceIn(0, 23)
        val sM = startMinute.value.roundToInt().coerceIn(0, 59)
        formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, sH, sM)
      }
    }
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
  hours: kotlinx.collections.immutable.ImmutableList<String>,
  minutes: kotlinx.collections.immutable.ImmutableList<String>,
  hourLine: Animatable<Float, *>,
  minuteLine: Animatable<Float, *>,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  @Suppress("UNCHECKED_CAST")
  Row(modifier = modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
    WheelSelectCompose(
      selectedLine = hourLine as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
      options = hours, modifier = Modifier.weight(1f).fillMaxHeight(),
    )
    Text(":", fontSize = 16.sp, color = colors.tvLv2)
    WheelSelectCompose(
      selectedLine = minuteLine as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
      options = minutes, modifier = Modifier.weight(1f).fillMaxHeight(),
    )
  }
}