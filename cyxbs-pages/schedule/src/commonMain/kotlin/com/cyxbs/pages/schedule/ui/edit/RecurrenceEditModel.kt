package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule

/** 重复编辑器可选择的频率；[NONE] 只表示 UI 中“不重复”，不会生成 RRULE。 */
enum class RepeatFreqOption { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }
/** 重复结束方式，分别映射为无限、次数和包含截止日当天的领域结束条件。 */
enum class RepeatEndOption { NEVER, COUNT, UNTIL }

/**
 * 面向表单控件的重复规则草稿，字段限定为 Schedule v2 支持的 RFC 5545 子集。
 * 单实例例外刻意不进入本模型：规则编辑只描述系列，移动、完成与取消由 occurrence exception 单独保存。
 */
data class RecurrenceDraft(
  val freq: RepeatFreqOption = RepeatFreqOption.NONE,
  val interval: Int = 1,
  val byDay: List<Int> = emptyList(),
  val byMonthDay: List<Int> = emptyList(),
  val endOption: RepeatEndOption = RepeatEndOption.NEVER,
  val count: Int = 3,
  val until: Date? = null,
) {
  val isRepeating: Boolean get() = freq != RepeatFreqOption.NONE
}

/**
 * 将表单草稿转换为领域重复规则；[anchor] 用于补齐用户未显式选择的周几、月日和月份。
 *
 * [RepeatFreqOption.NONE] 返回 null；interval/count 至少为 1，UNTIL 以截止日期表达，日期级规则天然包含当天。
 */
fun RecurrenceDraft.toRecurrenceRule(anchor: Date): RecurrenceRule? {
  val frequency = when (freq) {
    RepeatFreqOption.NONE -> return null
    RepeatFreqOption.DAILY -> RecurrenceFrequency.DAILY
    RepeatFreqOption.WEEKLY -> RecurrenceFrequency.WEEKLY
    RepeatFreqOption.MONTHLY -> RecurrenceFrequency.MONTHLY
    RepeatFreqOption.YEARLY -> RecurrenceFrequency.YEARLY
  }
  return RecurrenceRule(
    frequency = frequency,
    interval = interval.coerceAtLeast(1),
    byWeekDays = if (frequency == RecurrenceFrequency.WEEKLY) {
      byDay.ifEmpty { listOf(anchor.dayOfWeekNumber) }.mapNotNull(IsoWeekDay::fromIsoNumber).toSet()
    } else emptySet(),
    byMonthDays = when (frequency) {
      RecurrenceFrequency.MONTHLY -> byMonthDay.ifEmpty { listOf(anchor.dayOfMonth) }.toSet()
      RecurrenceFrequency.YEARLY -> setOf(anchor.dayOfMonth)
      else -> emptySet()
    },
    byMonths = if (frequency == RecurrenceFrequency.YEARLY) setOf(anchor.monthNumber) else emptySet(),
    end = when (endOption) {
      RepeatEndOption.NEVER -> RecurrenceEnd.Never
      RepeatEndOption.COUNT -> RecurrenceEnd.Count(count.coerceAtLeast(1))
      RepeatEndOption.UNTIL -> RecurrenceEnd.Until(until ?: anchor)
    },
  )
}

/** 将领域规则无损映射回编辑草稿；空规则恢复为“不重复”的默认表单。 */
fun RecurrenceRule?.toDraft(): RecurrenceDraft {
  val rule = this ?: return RecurrenceDraft()
  return RecurrenceDraft(
    freq = when (rule.frequency) {
      RecurrenceFrequency.DAILY -> RepeatFreqOption.DAILY
      RecurrenceFrequency.WEEKLY -> RepeatFreqOption.WEEKLY
      RecurrenceFrequency.MONTHLY -> RepeatFreqOption.MONTHLY
      RecurrenceFrequency.YEARLY -> RepeatFreqOption.YEARLY
    },
    interval = rule.interval,
    byDay = rule.byWeekDays.map { it.isoNumber },
    byMonthDay = rule.byMonthDays.toList(),
    endOption = when (rule.end) {
      RecurrenceEnd.Never -> RepeatEndOption.NEVER
      is RecurrenceEnd.Count -> RepeatEndOption.COUNT
      is RecurrenceEnd.Until -> RepeatEndOption.UNTIL
    },
    count = (rule.end as? RecurrenceEnd.Count)?.value ?: 3,
    until = (rule.end as? RecurrenceEnd.Until)?.date,
  )
}

/**
 * 计算编辑器文案中截至 [until] 的预览次数。该结果只用于联动次数/日期控件，不参与保存后的业务展开。
 */
fun RecurrenceDraft.countUntil(anchor: Date, until: Date): Int = previewDates(anchor, 100_000)
  .takeWhile { it <= until }.size

fun RecurrenceDraft.firstOccurrenceOnOrAfter(anchor: Date): Date = previewDates(anchor, 1).firstOrNull() ?: anchor
fun RecurrenceDraft.endDateAtCount(anchor: Date, count: Int = this.count): Date =
  previewDates(anchor, count.coerceAtLeast(1)).lastOrNull() ?: anchor

/**
 * 为编辑器摘要做有上限的逐日预览，最多扫描约一百年并受 [limit] 限制，防止异常规则无限循环。
 * 这不是业务重复引擎：Feed、时间轴和课表必须使用 `RecurrenceEngine` 展开，不能依赖此预览保证语义。
 */
private fun RecurrenceDraft.previewDates(anchor: Date, limit: Int): List<Date> {
  val rule = toRecurrenceRule(anchor) ?: return emptyList()
  val result = ArrayList<Date>()
  var date = anchor
  repeat(36_600) {
    val days = anchor.daysUntil(date).toLong()
    val months = (date.year - anchor.year) * 12 + date.monthNumber - anchor.monthNumber
    val matches = when (rule.frequency) {
      RecurrenceFrequency.DAILY -> days % rule.interval == 0L
      RecurrenceFrequency.WEEKLY -> days.floorDiv(7) % rule.interval == 0L &&
        IsoWeekDay.fromIsoNumber(date.dayOfWeekNumber) in rule.byWeekDays
      RecurrenceFrequency.MONTHLY -> months % rule.interval == 0 && date.dayOfMonth in rule.byMonthDays
      RecurrenceFrequency.YEARLY -> (date.year - anchor.year) % rule.interval == 0 &&
        date.monthNumber in rule.byMonths && date.dayOfMonth in rule.byMonthDays
    }
    if (matches) {
      result += date
      if (result.size >= limit) return result
    }
    date = date.plusDays(1)
  }
  return result
}

fun buildRecurrenceLabels(rule: RecurrenceRule?): List<String> {
  rule ?: return emptyList()
  val n = rule.interval
  val first = when (rule.frequency) {
    RecurrenceFrequency.DAILY -> if (n == 1) "每天" else "每${n}天"
    RecurrenceFrequency.WEEKLY -> (if (n == 1) "每周" else "每${n}周") +
      rule.byWeekDays.sortedBy { it.isoNumber }.joinToString("、") { "周${weekNumberToChinese(it.isoNumber)}" }
    RecurrenceFrequency.MONTHLY -> (if (n == 1) "每月" else "每${n}月") +
      rule.byMonthDays.sorted().joinToString("、") { "${it}日" }
    RecurrenceFrequency.YEARLY -> (if (n == 1) "每年" else "每${n}年") +
      "${rule.byMonths.firstOrNull() ?: ""}月${rule.byMonthDays.firstOrNull() ?: ""}日"
  }
  return buildList {
    add(first)
    when (val end = rule.end) {
      RecurrenceEnd.Never -> Unit
      is RecurrenceEnd.Count -> add("共${end.value}次")
      is RecurrenceEnd.Until -> add("至${end.date}")
    }
  }
}

fun recurrenceSummary(rule: RecurrenceRule?): String = buildRecurrenceLabels(rule).joinToString(" · ").ifEmpty { "不重复" }
fun weekNumberToChinese(iso: Int): String = when (iso) { 1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"; else -> "" }
