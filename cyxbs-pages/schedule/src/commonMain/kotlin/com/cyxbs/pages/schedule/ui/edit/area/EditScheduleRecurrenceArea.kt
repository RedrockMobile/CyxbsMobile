package com.cyxbs.pages.schedule.ui.edit.area

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.CalendarDateCompose
import com.cyxbs.components.view.calendar.WeekTextCompose
import com.cyxbs.components.view.calendar.month.CalendarMonthCompose
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.schedule.ui.edit.RecurrenceDraft
import com.cyxbs.pages.schedule.ui.edit.RepeatEndOption
import com.cyxbs.pages.schedule.ui.edit.RepeatFreqOption
import com.cyxbs.pages.schedule.ui.edit.ToggleChip
import com.cyxbs.pages.schedule.ui.edit.countUntil
import com.cyxbs.pages.schedule.ui.edit.endDateAtCount
import com.cyxbs.pages.schedule.ui.edit.firstOccurrenceOnOrAfter
import com.cyxbs.pages.schedule.ui.edit.formatInfoDate
import com.cyxbs.pages.schedule.ui.edit.formatWeekOfTerm
import com.cyxbs.pages.schedule.ui.edit.formatWeekday
import com.cyxbs.pages.schedule.ui.edit.weekNumberToChinese
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.roundToInt

/**
 * 重复规则编辑器（RFC5545 RRULE）—— **单位按钮 + 次数滚轮 + 多选**版，渲染在编辑弹窗下方区域（[com.cyxbs.pages.schedule.ui.edit.EditSubArea.REPEAT]）。
 *
 * 频率用「仅一次 / 日 / 周 / 月」按钮切换，只有重复间隔 N 用滚轮调整。
 * - 「仅一次」即不重复，不显示次数滚轮与结束条件。
 * - 「日 / 周 / 月」即「每 N 天/周/月」。每周 → 下方多选周几；每月 → 下方多选几号 + 月末倒数（-1=最后一天）。
 * 结束「永不 / 按次数 / 按日期」。改动通过 [onChange] 实时写回 [RecurrenceDraft]。
 *
 * 「每周」未选周几时默认锚点星期、「每月」未选几号时默认锚点日（由 [com.cyxbs.pages.schedule.ui.edit.toRRule] 补 BY*）。
 */
@Composable
internal fun EditScheduleRecurrenceArea(
  draft: RecurrenceDraft,
  anchorDate: Date,
  firstMonday: Date?,
  onChange: (RecurrenceDraft) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val draftS = rememberUpdatedState(draft)
  val onChangeS = rememberUpdatedState(onChange)
  val selectedUnit = when {
    !draft.isRepeating -> RepeatUnitOption.ONCE
    draft.freq == RepeatFreqOption.DAILY -> RepeatUnitOption.DAY
    draft.freq == RepeatFreqOption.MONTHLY -> RepeatUnitOption.MONTH
    else -> RepeatUnitOption.WEEK
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Row {
      RepeatUnitOption.entries.forEachIndexed { index, option ->
        if (index > 0) Spacer(modifier = Modifier.width(8.dp))
        ToggleChip(
          option.label,
          selected = selectedUnit == option
        ) {
          val d = draftS.value
          onChangeS.value(
            when (option) {
              RepeatUnitOption.ONCE -> d.copy(freq = RepeatFreqOption.NONE)
              RepeatUnitOption.DAY -> d.copy(
                freq = RepeatFreqOption.DAILY,
                interval = d.interval.coerceIn(1, RepeatUnitOption.DAY.maxInterval)
              )

              RepeatUnitOption.WEEK -> d.copy(
                freq = RepeatFreqOption.WEEKLY,
                interval = d.interval.coerceIn(1, RepeatUnitOption.WEEK.maxInterval)
              )

              RepeatUnitOption.MONTH -> d.copy(
                freq = RepeatFreqOption.MONTHLY,
                interval = d.interval.coerceIn(1, RepeatUnitOption.MONTH.maxInterval)
              )
            }
          )
        }
      }
    }

    val intervalMax = selectedUnit.maxInterval
    val numberOptions = remember(intervalMax) { (1..intervalMax).map { it.toString() }.toPersistentList() }
    val nLine = remember(selectedUnit) {
      Animatable((draft.interval - 1).coerceIn(0, intervalMax - 1).toFloat())
    }
    if (draft.isRepeating) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth().height(74.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
          Text("每", fontSize = 16.sp, color = colors.tvLv2)
        }
        OneWheel(numberOptions, nLine, modifier = Modifier.width(28.dp))
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
          Text(selectedUnit.suffix, fontSize = 16.sp, color = colors.tvLv2)
        }
      }
      LaunchedEffect(Unit) {
        snapshotFlow { nLine.value.roundToInt() }
          .collect { n ->
            val d = draftS.value
            if (d.isRepeating) {
              onChangeS.value(d.copy(interval = (n + 1).coerceIn(1, intervalMax)))
            }
          }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth(),
      ) {
        // 每周 → 周几多选
        if (draft.freq == RepeatFreqOption.WEEKLY) {
          SectionLabel("周几")
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            (1..7).forEach { iso ->
              ToggleChip(
                "周${weekNumberToChinese(iso)}",
                selected = iso in draft.byDay
              ) {
                onChange(draft.copy(byDay = draft.byDay.toggle(iso)))
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // 每月 → 几号多选 + 月末倒数
        if (draft.freq == RepeatFreqOption.MONTHLY) {
          SectionLabel("几号")
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            ((1..31) + (-1 downTo -9)).forEach { d ->
              DayToggle(d.toString(), selected = d in draft.byMonthDay) {
                onChange(draft.copy(byMonthDay = draft.byMonthDay.toggle(d)))
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }
      }

      // 结束：永不 / 按次数 / 按日期
      SectionLabel("结束")
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        EndOption.entries.forEach { opt ->
          ToggleChip(
            opt.label,
            selected = draft.endOption == opt.value
          ) { onChange(draft.copy(endOption = opt.value)) }
        }
      }
      if (draft.endOption == RepeatEndOption.COUNT) {
        val countEndDate = draft.endDateAtCount(anchorDate)
        CountStepper(draft.count, endDate = countEndDate, firstMonday = firstMonday) {
          onChange(draft.copy(count = it))
        }
      } else if (draft.endOption == RepeatEndOption.UNTIL) {
        val untilStartDate = draft.firstOccurrenceOnOrAfter(anchorDate)
        val untilDate = (draft.until ?: untilStartDate).coerceAtLeast(untilStartDate)
        LaunchedEffect(untilDate, draft.until) {
          if (draft.until != untilDate) {
            onChangeS.value(draftS.value.copy(until = untilDate))
          }
        }
        val count = draft.countUntil(anchorDate, untilDate)
        Text(
          text = "直到${formatRecurrenceEndDate(untilDate, firstMonday)}，共${count}次",
          fontSize = 13.sp,
          color = colors.tvLv3,
          modifier = Modifier.padding(vertical = 6.dp),
        )
        val calendarState = rememberCalendarState(
          initialClickDate = untilDate,
          startDate = untilStartDate,
          endDate = untilStartDate.plusYears(8).lastDate
        )
        CalendarCompose(
          modifier = Modifier.fillMaxWidth(),
          state = calendarState,
          calendar = {
            calendarState.WeekTextCompose(fontSize = 8.sp)
            calendarState.CalendarMonthCompose { date, show ->
              calendarState.CalendarDateCompose(
                date = date, show = show,
                dayFontSize = 14.sp, lunarFontSize = 9.sp, maxCellHeight = 38.dp,
              )
            }
          },
        )
        LaunchedEffect(Unit) { calendarState.expand() } // 默认展开整月
        LaunchedEffect(Unit) {
          snapshotFlow { calendarState.clickDate }.collect { date ->
            onChange(draft.copy(until = date.coerceAtLeast(untilStartDate)))
          }
        }
      }
    }
  }
}

private enum class RepeatUnitOption(val label: String, val suffix: String, val maxInterval: Int) {
  ONCE("仅一次", "次", 1),
  DAY("日", "天", 365),
  WEEK("周", "周", 52),
  MONTH("月", "月", 12),
}

private enum class EndOption(val label: String, val value: RepeatEndOption) {
  NEVER("永不", RepeatEndOption.NEVER),
  COUNT("按次数", RepeatEndOption.COUNT),
  UNTIL("按日期", RepeatEndOption.UNTIL),
}

private fun formatRecurrenceEndDate(date: Date, firstMonday: Date?): String = buildString {
  append(formatInfoDate(date))
  formatWeekOfTerm(firstMonday, date)?.let {
    append('，').append(it)
    append(formatWeekday(date))
  }
}

/** 切换列表中某元素的「选中」：存在则移除、不存在则加入并保持升序。 */
private fun List<Int>.toggle(v: Int): List<Int> =
  if (v in this) this - v else (this + v).sorted()

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text, fontSize = 12.sp, color = LocalAppColors.current.tvLv3.copy(alpha = 0.7f),
    modifier = Modifier.padding(bottom = 4.dp)
  )
}

/** 结束次数的步进。 */
@Composable
private fun CountStepper(
  count: Int,
  endDate: Date,
  firstMonday: Date?,
  onChange: (Int) -> Unit,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StepBox("－") { onChange((count - 1).coerceAtLeast(1)) }
    Text(
      "共 $count 次",
      fontSize = 13.sp,
      color = colors.tvLv2,
      modifier = Modifier.padding(horizontal = 14.dp)
    )
    StepBox("＋") { onChange((count + 1).coerceAtMost(99)) }
    Text(
      text = "直到${formatRecurrenceEndDate(endDate, firstMonday)}",
      fontSize = 13.sp,
      color = colors.tvLv3,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@Composable
private fun StepBox(text: String, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier.size(24.dp)
      .background(colors.positive.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Text(text, fontSize = 16.sp, color = colors.positive) }
}

/** 月内日期小方格（多选）。 */
@Composable
private fun DayToggle(text: String, selected: Boolean, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Box(
    modifier = Modifier
      .size(30.dp)
      .border(
        1.dp,
        if (selected) accent else colors.tvLv3.copy(alpha = 0.12f),
        RoundedCornerShape(6.dp)
      )
      .background(
        if (selected) accent.copy(alpha = 0.1f) else Color.Transparent,
        RoundedCornerShape(6.dp)
      )
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
  line: Animatable<Float, AnimationVector1D>,
  modifier: Modifier = Modifier,
) {
  WheelSelectCompose(
    selectedLine = line,
    options = options,
    modifier = modifier.fillMaxHeight(),
    textStyle = TextStyle(
      fontSize = 16.sp,
      textAlign = TextAlign.Center,
      color = Color.Black,
    )
  )
}
