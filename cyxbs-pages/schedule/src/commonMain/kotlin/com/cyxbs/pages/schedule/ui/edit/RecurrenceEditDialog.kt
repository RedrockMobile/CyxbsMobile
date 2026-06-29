package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.schedule.ui.dialog.ScheduleCalendarPickerDialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.roundToInt

/**
 * 重复规则编辑器（RFC5545 RRULE）—— **内联滚轮**版，渲染在编辑弹窗下方区域（[EditSubArea.REPEAT]）。
 *
 * 形态（按需求简化）：「不重复 / 重复」分段 → 重复时用滚轮设「每 N 天/周/月」(不支持年) →
 * 结束「永不 / 按次数(滚轮) / 按日期(日历)」。改动通过 [onChange] **实时写回** [RecurrenceDraft]，← 返回即生效。
 *
 * 说明：「每周」默认落在锚点星期、「每月」默认落在锚点日（由 [RecurrenceDraft.toRRule] 用锚点补 BY*），
 * 故不再提供星期/月日多选。年频率(YEARLY)在数据模型仍保留以兼容旧数据，但编辑器不暴露。
 */
@Composable
internal fun RecurrenceEditInline(
  draft: RecurrenceDraft,
  onChange: (RecurrenceDraft) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val draftS = rememberUpdatedState(draft)
  val onChangeS = rememberUpdatedState(onChange)
  var showUntilPicker by remember { mutableStateOf(false) }

  val numbers = remember { (1..99).map { it.toString() }.toPersistentList() }
  val units = remember { listOf("天", "周", "月").toPersistentList() }
  val counts = remember { (1..99).map { it.toString() }.toPersistentList() }
  val intervalLine = remember { Animatable((draft.interval - 1).coerceIn(0, 98).toFloat()) }
  val unitLine = remember { Animatable(freqToUnitIndex(draft.freq).toFloat()) }
  val countLine = remember { Animatable((draft.count - 1).coerceIn(0, 98).toFloat()) }

  Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    // 不重复 / 重复
    Row {
      RepeatChip("不重复", selected = !draft.isRepeating) { onChange(draft.copy(freq = RepeatFreqOption.NONE)) }
      Spacer(modifier = Modifier.width(8.dp))
      RepeatChip("重复", selected = draft.isRepeating) {
        if (!draft.isRepeating) onChange(draft.copy(freq = unitIndexToFreq(unitLine.value.roundToInt())))
      }
    }

    if (draft.isRepeating) {
      Spacer(modifier = Modifier.height(8.dp))
      // 一行滚轮：每 [N] [天/周/月]（按次数时同一行追加「共 [N] 次」，避免再占一行放不下）
      Row(
        modifier = Modifier.fillMaxWidth().height(82.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("每", fontSize = 13.sp, color = colors.tvLv2, modifier = Modifier.padding(end = 4.dp))
        OneWheel(numbers, intervalLine, modifier = Modifier.width(44.dp))
        OneWheel(units, unitLine, modifier = Modifier.width(48.dp))
        if (draft.endOption == RepeatEndOption.COUNT) {
          Text("共", fontSize = 13.sp, color = colors.tvLv2, modifier = Modifier.padding(horizontal = 4.dp))
          OneWheel(counts, countLine, modifier = Modifier.width(44.dp))
          Text("次", fontSize = 13.sp, color = colors.tvLv2, modifier = Modifier.padding(start = 4.dp))
        }
      }
      // 滚轮值实时写回 freq/interval
      LaunchedEffect(Unit) {
        snapshotFlow { intervalLine.value.roundToInt() to unitLine.value.roundToInt() }.collect { (i, u) ->
          val d = draftS.value
          if (d.isRepeating) onChangeS.value(d.copy(interval = (i + 1).coerceAtLeast(1), freq = unitIndexToFreq(u)))
        }
      }
      if (draft.endOption == RepeatEndOption.COUNT) {
        LaunchedEffect(Unit) {
          snapshotFlow { countLine.value.roundToInt() }.collect { onChangeS.value(draftS.value.copy(count = (it + 1).coerceAtLeast(1))) }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      // 结束：永不 / 按次数 / 按日期
      Row(verticalAlignment = Alignment.CenterVertically) {
        EndOption.entries.forEachIndexed { idx, opt ->
          if (idx > 0) Spacer(modifier = Modifier.width(8.dp))
          RepeatChip(opt.label, selected = draft.endOption == opt.value) { onChange(draft.copy(endOption = opt.value)) }
        }
        if (draft.endOption == RepeatEndOption.UNTIL) {
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = draft.until?.let { "$it" } ?: "选择日期",
            fontSize = 13.sp,
            color = if (draft.until == null) colors.tvLv3.copy(alpha = 0.5f) else colors.positive,
            modifier = Modifier.clickable { showUntilPicker = true }.padding(vertical = 6.dp),
          )
        }
      }
    }
  }

  // UNTIL 日期选择（复用日历选择器，只取日期部分）
  ScheduleCalendarPickerDialog(
    show = showUntilPicker,
    onDismiss = { showUntilPicker = false },
    onConfirm = { year, month, day, _, _ ->
      onChange(draft.copy(until = runCatching { Date(year, month, day) }.getOrNull()))
      showUntilPicker = false
    },
  )
}

private enum class EndOption(val label: String, val value: RepeatEndOption) {
  NEVER("永不", RepeatEndOption.NEVER),
  COUNT("按次数", RepeatEndOption.COUNT),
  UNTIL("按日期", RepeatEndOption.UNTIL),
}

/** 单位下标(0天/1周/2月) ↔ 频率。年(YEARLY)不在编辑器内，默认归到周。 */
private fun freqToUnitIndex(freq: RepeatFreqOption): Int = when (freq) {
  RepeatFreqOption.DAILY -> 0
  RepeatFreqOption.WEEKLY -> 1
  RepeatFreqOption.MONTHLY -> 2
  else -> 1
}

private fun unitIndexToFreq(idx: Int): RepeatFreqOption = when (idx) {
  0 -> RepeatFreqOption.DAILY
  2 -> RepeatFreqOption.MONTHLY
  else -> RepeatFreqOption.WEEKLY
}

/** 紧凑分段胶囊。 */
@Composable
private fun RepeatChip(text: String, selected: Boolean, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Text(
    text = text,
    fontSize = 13.sp,
    textAlign = TextAlign.Center,
    color = if (selected) accent else colors.tvLv3.copy(alpha = 0.6f),
    modifier = Modifier
      .border(1.dp, if (selected) accent else colors.tvLv3.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
      .background(if (selected) accent.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 5.dp),
  )
}

/** 单列滚轮（无背景带）。 */
@Composable
private fun OneWheel(
  options: ImmutableList<String>,
  line: Animatable<Float, *>,
  modifier: Modifier = Modifier,
) {
  @Suppress("UNCHECKED_CAST")
  WheelSelectCompose(
    selectedLine = line as Animatable<Float, AnimationVector1D>,
    options = options,
    modifier = modifier.fillMaxHeight(),
  )
}
