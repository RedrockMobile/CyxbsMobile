package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.ui.edit.formatClock
import com.cyxbs.pages.schedule.ui.edit.formatInfoDate
import org.jetbrains.compose.resources.painterResource

/**
 * 重复设置中的单次调整列表。
 *
 * 只展示会改变系列内容或可见性的单次调整：单次修改显示“原时间 → 当前时间”，单次删除显示
 * “原时间 → 已删除”。完成态本身不属于调整，不单独出现在这里；右侧删除按钮用于删除调整并恢复系列继承。
 */
@Composable
internal fun OccurrenceAdjustmentList(
  schedule: Schedule,
  occurrenceAdjustments: List<ScheduleOccurrenceAdjustment>,
  onRestore: (ScheduleOccurrenceAdjustment) -> Unit,
  modifier: Modifier = Modifier,
) {
  val adjustments = remember(schedule.id, occurrenceAdjustments) {
    occurrenceAdjustments
      .filter { adjustment ->
        adjustment.scheduleId == schedule.id &&
          (adjustment.status == OccurrenceStatus.CANCELLED || adjustment.patch.hasContentChanges())
      }
      .sortedBy { it.recurrenceId.originalDateTime }
  }
  if (adjustments.isEmpty()) return

  val colors = LocalAppColors.current
  var expanded by remember(schedule.id) { mutableStateOf(false) }
  Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(28.dp)
        .clickable { expanded = !expanded },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "单次调整（${adjustments.size}）",
        fontSize = 11.sp,
        color = colors.tvLv3.copy(alpha = 0.7F),
      )
      Spacer(modifier = Modifier.weight(1F))
      Text(
        text = if (expanded) "收起" else "查看",
        fontSize = 11.sp,
        color = colors.positive,
      )
    }
    if (!expanded) return@Column
    adjustments.forEachIndexed { index, adjustment ->
      val summary = remember(schedule, adjustment) {
        buildOccurrenceAdjustmentSummary(schedule, adjustment)
      }
      Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = summary,
          fontSize = 12.sp,
          color = colors.tvLv2,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1F),
        )
        IconButton(
          onClick = { onRestore(adjustment) },
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            painter = painterResource(ConfigRes.configIcRestore()),
            contentDescription = "还原此次调整",
            tint = colors.positive,
            modifier = Modifier.size(16.dp),
          )
        }
      }
      if (index != adjustments.lastIndex) {
        Divider(color = colors.tvLv3.copy(alpha = 0.08F))
      }
    }
  }
}

/** 单行摘要优先表达时间变化，并用简短后缀提示同一次发生中并存的内容修改。 */
private fun buildOccurrenceAdjustmentSummary(
  schedule: Schedule,
  adjustment: ScheduleOccurrenceAdjustment,
): String {
  // 由引擎按当前系列锚点还原“未应用单次调整”的发生时间；整个系列移动过时不能直接照抄 recurrenceId。
  val originalTiming = RecurrenceEngine.resolveOccurrenceByIdentity(
    schedule = schedule,
    occurrenceAdjustments = emptyList(),
    recurrenceId = adjustment.recurrenceId,
  )?.timing ?: schedule.timing.at(adjustment.recurrenceId)
  val originalLabel = originalTiming.toAdjustmentTimeLabel()
  if (adjustment.status == OccurrenceStatus.CANCELLED) {
    return "$originalLabel → 已删除"
  }

  val patch = adjustment.patch
  val hasTimingChanges = patch != null && (
    patch.date !is FieldPatch.Inherit || patch.time !is FieldPatch.Inherit
    )
  val currentTiming = if (hasTimingChanges) {
    RecurrenceEngine.resolveOccurrenceByIdentity(schedule, listOf(adjustment), adjustment.recurrenceId)?.timing
  } else {
    null
  }
  val hasOtherChanges = patch.hasNonTimingChanges()
  return when {
    currentTiming != null -> buildString {
      append(originalLabel).append(" → ").append(currentTiming.toAdjustmentTimeLabel())
      if (hasOtherChanges) append(" · 含内容修改")
    }
    hasOtherChanges -> "$originalLabel · 内容已修改"
    else -> originalLabel
  }
}

/** recurrence identity 保存的是规则生成前的原始时间，按父系列类型补回持续时间与全天语义。 */
private fun ScheduleTiming.at(recurrenceId: RecurrenceId): ScheduleTiming = when (this) {
  is ScheduleTiming.Timed -> copy(start = recurrenceId.originalDateTime)
  is ScheduleTiming.Deadline -> copy(due = recurrenceId.originalDateTime)
  is ScheduleTiming.AllDay -> copy(date = recurrenceId.originalDateTime.date)
  ScheduleTiming.Unscheduled -> ScheduleTiming.Unscheduled
}

/** 将四种时间语义格式化为足以区分单次调整的紧凑日期时间。 */
private fun ScheduleTiming.toAdjustmentTimeLabel(): String = when (this) {
  is ScheduleTiming.Timed -> {
    val end = start.plusMinutes(durationMinutes)
    if (end.date == start.date) {
      "${formatInfoDate(start.date)} ${formatClock(start.minuteOfDay)}-${formatClock(end.minuteOfDay)}"
    } else {
      "${formatInfoDate(start.date)} ${formatClock(start.minuteOfDay)}-" +
        "${formatInfoDate(end.date)} ${formatClock(end.minuteOfDay)}"
    }
  }
  is ScheduleTiming.Deadline -> "${formatInfoDate(due.date)} ${formatClock(due.minuteOfDay)}"
  is ScheduleTiming.AllDay -> "${formatInfoDate(date)} 全天"
  ScheduleTiming.Unscheduled -> "未设置时间"
}

private fun OccurrencePatch?.hasContentChanges(): Boolean =
  this != null && this != OccurrencePatch()

private fun OccurrencePatch?.hasNonTimingChanges(): Boolean = this != null && (
  title !is FieldPatch.Inherit ||
    description !is FieldPatch.Inherit ||
    categoryId !is FieldPatch.Inherit ||
    reminder !is FieldPatch.Inherit
  )
