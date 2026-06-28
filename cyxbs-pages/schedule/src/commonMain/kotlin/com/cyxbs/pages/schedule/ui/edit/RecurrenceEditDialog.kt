package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleCalendarPickerDialog

/**
 * 重复规则编辑弹窗（RFC5545 RRULE 编辑器）。
 *
 * 自上而下：频率选择（不重复/每天/每周/每月/每年）→ 间隔步进 → 按频率展示星期多选(WEEKLY)
 * 或月日多选(MONTHLY) → 结束条件（永不/按次数/按日期）。确认时把 [RecurrenceDraft] 回传给外层。
 *
 * 不直接产出 RRULE，由外层在保存时结合锚点（[EditScheduleState.anchorDate]）调用
 * [RecurrenceDraft.toRecurrence]，因为 BY* 留空需用锚点补默认。
 */
@Composable
fun RecurrenceEditDialog(
  show: Boolean,
  initial: RecurrenceDraft,
  onDismiss: () -> Unit,
  onConfirm: (RecurrenceDraft) -> Unit,
) {
  if (!show) return
  val colors = LocalAppColors.current
  var draft by remember { mutableStateOf(initial) }
  var showUntilPicker by remember { mutableStateOf(false) }

  ScheduleBottomSheet(show = true, onDismiss = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "取消", fontSize = 16.sp, color = colors.tvLv3.copy(alpha = 0.5f),
          modifier = Modifier.clickable(onClick = onDismiss))
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "设置重复", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.tvLv2)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "确定", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.positive,
          modifier = Modifier.clickable { onConfirm(draft); onDismiss() })
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 频率
      SectionTitle("频率")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FreqOption.entries.forEach { opt ->
          PickChip(text = opt.label, selected = draft.freq == opt.value) { draft = draft.copy(freq = opt.value) }
        }
      }

      if (draft.isRepeating) {
        Spacer(modifier = Modifier.height(16.dp))
        // 间隔步进
        SectionTitle("间隔")
        Row(verticalAlignment = Alignment.CenterVertically) {
          StepperButton("－") { draft = draft.copy(interval = (draft.interval - 1).coerceAtLeast(1)) }
          Text(
            text = "每 ${draft.interval} ${unitOf(draft.freq)}",
            fontSize = 14.sp, color = colors.tvLv2,
            modifier = Modifier.padding(horizontal = 16.dp),
          )
          StepperButton("＋") { draft = draft.copy(interval = (draft.interval + 1).coerceAtMost(99)) }
        }
      }

      // 按周：星期多选
      if (draft.freq == RepeatFreqOption.WEEKLY) {
        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("星期")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          (1..7).forEach { iso ->
            val on = iso in draft.byDay
            PickChip(text = "周${weekNumberToChinese(iso)}", selected = on) {
              draft = draft.copy(byDay = draft.byDay.toggle(iso))
            }
          }
        }
      }

      // 按月：月日多选
      if (draft.freq == RepeatFreqOption.MONTHLY) {
        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("每月日期")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          (1..31).forEach { d ->
            val on = d in draft.byMonthDay
            DayCell(text = d.toString(), selected = on) {
              draft = draft.copy(byMonthDay = draft.byMonthDay.toggle(d))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      // 结束条件
      SectionTitle("结束")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EndOption.entries.forEach { opt ->
          PickChip(text = opt.label, selected = draft.endOption == opt.value) { draft = draft.copy(endOption = opt.value) }
        }
      }
      when (draft.endOption) {
        RepeatEndOption.COUNT -> {
          Spacer(modifier = Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("－") { draft = draft.copy(count = (draft.count - 1).coerceAtLeast(1)) }
            Text(text = "共 ${draft.count} 次", fontSize = 14.sp, color = colors.tvLv2,
              modifier = Modifier.padding(horizontal = 16.dp))
            StepperButton("＋") { draft = draft.copy(count = (draft.count + 1).coerceAtMost(999)) }
          }
        }
        RepeatEndOption.UNTIL -> {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = draft.until?.let { "截止 $it" } ?: "选择截止日期",
            fontSize = 14.sp,
            color = if (draft.until == null) colors.tvLv3.copy(alpha = 0.5f) else colors.positive,
            modifier = Modifier.clickable { showUntilPicker = true }.padding(vertical = 6.dp),
          )
        }
        RepeatEndOption.NEVER -> {}
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }

  // UNTIL 日期选择（复用日历选择器，只取日期部分）
  ScheduleCalendarPickerDialog(
    show = showUntilPicker,
    onDismiss = { showUntilPicker = false },
    onConfirm = { year, month, day, _, _ ->
      draft = draft.copy(until = runCatching { Date(year, month, day) }.getOrNull())
      showUntilPicker = false
    },
  )
}

private enum class FreqOption(val label: String, val value: RepeatFreqOption) {
  NONE("不重复", RepeatFreqOption.NONE),
  DAILY("每天", RepeatFreqOption.DAILY),
  WEEKLY("每周", RepeatFreqOption.WEEKLY),
  MONTHLY("每月", RepeatFreqOption.MONTHLY),
  YEARLY("每年", RepeatFreqOption.YEARLY),
}

private enum class EndOption(val label: String, val value: RepeatEndOption) {
  NEVER("永不", RepeatEndOption.NEVER),
  COUNT("按次数", RepeatEndOption.COUNT),
  UNTIL("按日期", RepeatEndOption.UNTIL),
}

private fun unitOf(freq: RepeatFreqOption): String = when (freq) {
  RepeatFreqOption.DAILY -> "天"
  RepeatFreqOption.WEEKLY -> "周"
  RepeatFreqOption.MONTHLY -> "月"
  RepeatFreqOption.YEARLY -> "年"
  RepeatFreqOption.NONE -> ""
}

/** 切换列表中某元素的「选中」：存在则移除、不存在则加入并保持升序。 */
private fun List<Int>.toggle(v: Int): List<Int> =
  if (v in this) this - v else (this + v).sorted()

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    fontSize = 13.sp,
    color = LocalAppColors.current.tvLv3.copy(alpha = 0.7f),
    modifier = Modifier.padding(bottom = 8.dp),
  )
}

@Composable
private fun PickChip(text: String, selected: Boolean, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Text(
    text = text,
    fontSize = 13.sp,
    textAlign = TextAlign.Center,
    color = if (selected) accent else colors.tvLv3.copy(alpha = 0.6f),
    modifier = Modifier
      .border(1.dp, if (selected) accent else colors.tvLv3.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
      .background(if (selected) accent.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 7.dp),
  )
}

@Composable
private fun DayCell(text: String, selected: Boolean, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  val cellBg = 0xFFE8F1FC.dark(0xFF1F1F1F)
  Box(
    modifier = Modifier
      .size(34.dp)
      .background(if (selected) accent.copy(alpha = 0.15f) else cellBg, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = text, fontSize = 13.sp, color = if (selected) accent else colors.tvLv3)
  }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier
      .size(32.dp)
      .background(colors.positive.copy(alpha = 0.1f), CircleShape)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = text, fontSize = 18.sp, color = colors.positive)
  }
}
