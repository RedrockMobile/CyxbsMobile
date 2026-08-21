package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.schedule.ui.edit.ToggleChip
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.roundToInt

/**
 * 提前提醒：「不提醒 / 提前」分段 + 滚轮设「提前 N 分钟/小时」。
 * [current] 为 remindMinutes（-1 不提醒；整小时存为 N*60）。改动实时回调 [onChoose]。
 *
 * @author 985892345
 * @date 2026/7/5
 */
@Composable
internal fun EditScheduleRemindArea(
  current: Int,
  onChoose: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val onChooseS = rememberUpdatedState(onChoose)
  val isHour = current >= 60 && current % 60 == 0
  val initN = (if (current < 0) 10 else if (isHour) current / 60 else current).coerceIn(1, 59)
  val nLine = remember { Animatable((initN - 1).toFloat()) }
  val unitLine = remember { Animatable((if (isHour) 1 else 0).toFloat()) } // 0=分钟,1=小时
  val numbers = remember { (1..59).map { it.toString() }.toPersistentList() }
  val units = remember { listOf("分钟", "小时").toPersistentList() }

  Column(modifier = modifier.fillMaxWidth()) {
    Row {
      ToggleChip("不提醒", selected = current < 0) { onChoose(-1) }
      Spacer(modifier = Modifier.width(8.dp))
      ToggleChip("提醒", selected = current >= 0) { if (current < 0) onChoose(10) }
    }
    if (current >= 0) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth().height(82.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
          Text("提前", fontSize = 16.sp, color = colors.tvLv2)
        }
        OneWheel(numbers, nLine, modifier = Modifier.width(28.dp))
        OneWheel(units, unitLine, modifier = Modifier.width(40.dp))
      }
      LaunchedEffect(Unit) {
        snapshotFlow { nLine.value.roundToInt() to unitLine.value.roundToInt() }.collect { (n, u) ->
          val v = (n + 1).coerceIn(1, 59)
          onChooseS.value(if (u == 1) v * 60 else v)
        }
      }
    }
  }
}

/** 单列滚轮。 */
@Composable
private fun OneWheel(
  options: kotlinx.collections.immutable.ImmutableList<String>,
  line: Animatable<Float, AnimationVector1D>,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  WheelSelectCompose(
    selectedLine = line,
    options = options,
    modifier = modifier.fillMaxHeight(),
    textStyle = TextStyle(
      fontSize = 16.sp,
      textAlign = TextAlign.Center,
      color = colors.tvLv2,
    )
  )
}
