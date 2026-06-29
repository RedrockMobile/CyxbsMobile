package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * 重复规则编辑器（RFC5545 RRULE）—— **内联三滚轮 + 多选**版，渲染在编辑弹窗下方区域（[EditSubArea.REPEAT]）。
 *
 * 频率用三个联动滚轮：左[仅/每] · 中[N] · 右[次/天/周/月]。
 * - 「仅 1 次」(= 次/仅 任一) 即不重复：中轮锁定为 1，右轮锁到「次」，左轮到「仅」，三者互相联动。
 * - 「每 N 天/周/月」即重复。每周 → 下方多选周几；每月 → 下方多选几号 + 月末倒数（-1=最后一天）。
 * 结束「永不 / 按次数 / 按日期」。改动通过 [onChange] 实时写回 [RecurrenceDraft]。
 *
 * 「每周」未选周几时默认锚点星期、「每月」未选几号时默认锚点日（由 [RecurrenceDraft.toRRule] 补 BY*）。
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
  val once = !draft.isRepeating

  val leftOptions = remember { listOf("仅", "每").toPersistentList() }
  val unitOptions = remember { listOf("次", "天", "周", "月").toPersistentList() } // 0次 1天 2周 3月
  val numberOptions = remember(once) {
    (if (once) listOf("1") else (1..99).map { it.toString() }).toPersistentList()
  }

  val leftLine = remember { Animatable((if (once) 0 else 1).toFloat()) }
  val nLine = remember { Animatable((if (once) 0 else (draft.interval - 1).coerceIn(0, 98)).toFloat()) }
  val unitLine = remember { Animatable((if (once) 0 else freqToUnitIndex(draft.freq)).toFloat()) }
  // 上次三轮取整值，用于判断是哪个轮被拨动，从而做联动。
  val prev = remember { mutableStateOf(Triple(leftLine.value.roundToInt(), nLine.value.roundToInt(), unitLine.value.roundToInt())) }

  Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    // 三联动滚轮：仅/每 · N · 次/天/周/月
    Row(
      modifier = Modifier.fillMaxWidth().height(82.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OneWheel(leftOptions, leftLine, modifier = Modifier.width(28.dp))
      OneWheel(numberOptions, nLine, modifier = Modifier.width(28.dp))
      OneWheel(unitOptions, unitLine, modifier = Modifier.width(28.dp))
    }
    LaunchedEffect(Unit) {
      snapshotFlow { Triple(leftLine.value.roundToInt(), nLine.value.roundToInt(), unitLine.value.roundToInt()) }
        .collect { cur ->
          val (l, n, u) = cur
          val (pl, _, pu) = prev.value
          // 联动：仅 ↔ 次 ↔ 数字1
          when {
            l != pl -> if (l == 0) { unitLine.snapTo(0f); nLine.snapTo(0f) } else if (u == 0) unitLine.snapTo(1f)
            u != pu -> if (u == 0) { leftLine.snapTo(0f); nLine.snapTo(0f) } else if (l == 0) leftLine.snapTo(1f)
            else -> if ((l == 0 || u == 0) && n != 0) nLine.snapTo(0f) // 仅模式数字锁 1
          }
          prev.value = Triple(leftLine.value.roundToInt(), nLine.value.roundToInt(), unitLine.value.roundToInt())
          // 写回 draft
          val lNow = leftLine.value.roundToInt()
          val uNow = unitLine.value.roundToInt()
          val nNow = nLine.value.roundToInt()
          val d = draftS.value
          if (lNow == 0 || uNow == 0) {
            if (d.isRepeating) onChangeS.value(d.copy(freq = RepeatFreqOption.NONE))
          } else {
            onChangeS.value(d.copy(freq = unitIndexToFreq(uNow), interval = (nNow + 1).coerceAtLeast(1)))
          }
        }
    }

    if (draft.isRepeating) {
      // 每周 → 周几多选
      if (draft.freq == RepeatFreqOption.WEEKLY) {
        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel("周几")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          (1..7).forEach { iso ->
            ToggleChip("周${weekNumberToChinese(iso)}", selected = iso in draft.byDay) {
              onChange(draft.copy(byDay = draft.byDay.toggle(iso)))
            }
          }
        }
      }

      // 每月 → 几号多选 + 月末倒数
      if (draft.freq == RepeatFreqOption.MONTHLY) {
        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel("几号")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
          (1..31).forEach { d ->
            DayToggle(d.toString(), selected = d in draft.byMonthDay) {
              onChange(draft.copy(byMonthDay = draft.byMonthDay.toggle(d)))
            }
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SectionLabel("月末倒数")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(-1 to "末", -2 to "倒2", -3 to "倒3", -4 to "倒4", -5 to "倒5").forEach { (v, label) ->
            ToggleChip(label, selected = v in draft.byMonthDay) {
              onChange(draft.copy(byMonthDay = draft.byMonthDay.toggle(v)))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      // 结束：永不 / 按次数 / 按日期
      SectionLabel("结束")
      Row(verticalAlignment = Alignment.CenterVertically) {
        EndOption.entries.forEachIndexed { idx, opt ->
          if (idx > 0) Spacer(modifier = Modifier.width(8.dp))
          ToggleChip(opt.label, selected = draft.endOption == opt.value) { onChange(draft.copy(endOption = opt.value)) }
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
      if (draft.endOption == RepeatEndOption.COUNT) {
        CountStepper(draft.count) { onChange(draft.copy(count = it)) }
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

/** 单位下标(1天/2周/3月) → 频率（0=次，不是频率）。 */
private fun freqToUnitIndex(freq: RepeatFreqOption): Int = when (freq) {
  RepeatFreqOption.DAILY -> 1
  RepeatFreqOption.WEEKLY -> 2
  RepeatFreqOption.MONTHLY -> 3
  else -> 2
}

private fun unitIndexToFreq(idx: Int): RepeatFreqOption = when (idx) {
  1 -> RepeatFreqOption.DAILY
  3 -> RepeatFreqOption.MONTHLY
  else -> RepeatFreqOption.WEEKLY
}

/** 切换列表中某元素的「选中」：存在则移除、不存在则加入并保持升序。 */
private fun List<Int>.toggle(v: Int): List<Int> =
  if (v in this) this - v else (this + v).sorted()

@Composable
private fun SectionLabel(text: String) {
  Text(text = text, fontSize = 12.sp, color = LocalAppColors.current.tvLv3.copy(alpha = 0.7f),
    modifier = Modifier.padding(bottom = 6.dp))
}

/** 结束次数的步进。 */
@Composable
private fun CountStepper(count: Int, onChange: (Int) -> Unit) {
  val colors = LocalAppColors.current
  Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    StepBox("－") { onChange((count - 1).coerceAtLeast(1)) }
    Text("共 $count 次", fontSize = 13.sp, color = colors.tvLv2, modifier = Modifier.padding(horizontal = 14.dp))
    StepBox("＋") { onChange((count + 1).coerceAtMost(999)) }
  }
}

@Composable
private fun StepBox(text: String, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier.size(28.dp)
      .background(colors.positive.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Text(text, fontSize = 16.sp, color = colors.positive) }
}

/** 多选/分段胶囊。 */
@Composable
private fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
      .padding(horizontal = 12.dp, vertical = 5.dp),
  )
}

/** 月内日期小方格（多选）。 */
@Composable
private fun DayToggle(text: String, selected: Boolean, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Box(
    modifier = Modifier
      .size(30.dp)
      .border(1.dp, if (selected) accent else colors.tvLv3.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
      .background(if (selected) accent.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 12.sp, color = if (selected) accent else colors.tvLv3)
  }
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
