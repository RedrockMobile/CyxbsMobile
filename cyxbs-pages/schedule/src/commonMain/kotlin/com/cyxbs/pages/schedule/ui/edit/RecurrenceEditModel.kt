package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence

/**
 * 重复规则编辑器的「纯逻辑层」：把 UI 友好的 [RecurrenceDraft] 与领域模型 [RRule]/[Recurrence]
 * 互转，并生成展示用的中文标签。全部为 commonMain 纯函数，不依赖 Compose / 平台 API，便于单测。
 *
 * 设计取舍（v1，对齐计划阶段5「频率/星期多选/月日多选/结束条件 → 产出 RRule」）：
 * - 编辑器只产出**单条 RRULE**；RDATE/EXDATE/RECURRENCE-ID 由「完成/删某次/改某次」等占位操作维护，
 *   编辑整条系列时通过 [toRecurrence] 的 base 参数原样保留，避免被基础规则编辑误清空。
 * - 星期(BYDAY)/月日(BYMONTHDAY)/年月日(BYMONTH+BYMONTHDAY) 留空时，[toRRule] 用锚点日期补默认值，
 *   保证「选了每周但没勾星期」时仍是一条合法、符合直觉的规则。
 */

/** 重复频率的 UI 选项：比领域层 [Freq] 多一个「不重复」。 */
enum class RepeatFreqOption { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** 结束条件：永不结束 / 按总次数(COUNT) / 按截止日期(UNTIL)。 */
enum class RepeatEndOption { NEVER, COUNT, UNTIL }

/**
 * 重复规则的可编辑草稿（UI 双向绑定用）。
 *
 * @param freq 频率选项；[RepeatFreqOption.NONE] 表示不重复。
 * @param interval 间隔，最小 1（如每隔一周 = WEEKLY + interval=2）。
 * @param byDay 选中的星期，ISO 1..7（周一=1）；仅 WEEKLY 用。
 * @param byMonthDay 选中的月内日期 1..31；仅 MONTHLY 用。
 * @param endOption 结束条件。
 * @param count 总次数（[RepeatEndOption.COUNT] 时生效），最小 1。
 * @param until 截止日期（[RepeatEndOption.UNTIL] 时生效）。
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
  /** 是否为重复日程。 */
  val isRepeating: Boolean get() = freq != RepeatFreqOption.NONE
}

/** [RepeatFreqOption] → 领域 [Freq]；NONE 返回 null。 */
fun RepeatFreqOption.toFreq(): Freq? = when (this) {
  RepeatFreqOption.NONE -> null
  RepeatFreqOption.DAILY -> Freq.DAILY
  RepeatFreqOption.WEEKLY -> Freq.WEEKLY
  RepeatFreqOption.MONTHLY -> Freq.MONTHLY
  RepeatFreqOption.YEARLY -> Freq.YEARLY
}

/** 领域 [Freq] → [RepeatFreqOption]。 */
fun Freq.toOption(): RepeatFreqOption = when (this) {
  Freq.DAILY -> RepeatFreqOption.DAILY
  Freq.WEEKLY -> RepeatFreqOption.WEEKLY
  Freq.MONTHLY -> RepeatFreqOption.MONTHLY
  Freq.YEARLY -> RepeatFreqOption.YEARLY
}

/**
 * 草稿 → [RRule]；不重复返回 null。
 *
 * BY* 留空时用 [anchor] 补默认：WEEKLY=锚点星期、MONTHLY=锚点日、YEARLY=锚点「月+日」。
 */
fun RecurrenceDraft.toRRule(anchor: Date): RRule? {
  val f = freq.toFreq() ?: return null
  return RRule(
    freq = f,
    interval = interval.coerceAtLeast(1),
    byDay = if (f == Freq.WEEKLY) {
      byDay.ifEmpty { listOf(anchor.dayOfWeekNumber) }.distinct().sorted()
    } else emptyList(),
    byMonthDay = when (f) {
      Freq.MONTHLY -> byMonthDay.ifEmpty { listOf(anchor.dayOfMonth) }.distinct().sorted()
      Freq.YEARLY -> listOf(anchor.dayOfMonth)
      else -> emptyList()
    },
    byMonth = if (f == Freq.YEARLY) listOf(anchor.monthNumber) else emptyList(),
    until = if (endOption == RepeatEndOption.UNTIL) until else null,
    count = if (endOption == RepeatEndOption.COUNT) count.coerceAtLeast(1) else null,
  )
}

/** 已有 [Recurrence] → 草稿（取其 rrule；为空则得到「不重复」草稿）。 */
fun Recurrence?.toDraft(): RecurrenceDraft {
  val r = this?.rrule ?: return RecurrenceDraft()
  return RecurrenceDraft(
    freq = r.freq.toOption(),
    interval = r.interval.coerceAtLeast(1),
    byDay = r.byDay,
    byMonthDay = r.byMonthDay,
    endOption = when {
      r.until != null -> RepeatEndOption.UNTIL
      r.count != null -> RepeatEndOption.COUNT
      else -> RepeatEndOption.NEVER
    },
    count = r.count ?: 10,
    until = r.until,
  )
}

/**
 * 草稿 → [Recurrence]；不重复返回 null。
 *
 * [base] 为编辑前的原 Recurrence：编辑整条系列时原样保留其 rdate/exdate/overrides，
 * 避免改基础规则把「已删的某次 / 已改的某次」一并清掉。
 */
fun RecurrenceDraft.toRecurrence(anchor: Date, base: Recurrence? = null): Recurrence? {
  val rrule = toRRule(anchor) ?: return null
  return Recurrence(
    rrule = rrule,
    rdate = base?.rdate ?: emptyList(),
    exdate = base?.exdate ?: emptyList(),
    overrides = base?.overrides ?: emptyList(),
  )
}

/**
 * 把 [Recurrence] 展开成可显示的 chip 标签（频率块 + 可选结束条件块）。
 *
 * 例：`["每2周 周一、周三", "共10次"]`、`["每月 1日、15日"]`、`["每年 6月28日", "至2026-12-31"]`。
 * 不重复返回空列表。
 */
fun buildRecurrenceLabels(recurrence: Recurrence?): List<String> {
  val r = recurrence?.rrule ?: return emptyList()
  val n = r.interval.coerceAtLeast(1)
  val labels = mutableListOf<String>()
  labels += when (r.freq) {
    Freq.DAILY -> if (n == 1) "每天" else "每${n}天"
    Freq.WEEKLY -> {
      val days = r.byDay.distinct().sorted()
      val daysStr = if (days.size == 7) "全天" else days.joinToString("", "周") { weekNumberToChinese(it) }
      val prefix = if (n == 1) "每周" else "每${n}周"
      if (daysStr.isEmpty()) prefix else if (days.size == 7 && n == 1) "每天" else "$prefix$daysStr"
    }
    Freq.MONTHLY -> {
      val days = formatMonthDayLabels(r.byMonthDay)
      val prefix = if (n == 1) "每月" else "每${n}月"
      if (days.isEmpty()) prefix else "$prefix$days"
    }
    Freq.YEARLY -> {
      val md = if (r.byMonth.isNotEmpty() && r.byMonthDay.isNotEmpty()) {
        "${r.byMonth.first()}月${r.byMonthDay.first()}号"
      } else ""
      val prefix = if (n == 1) "每年" else "每${n}年"
      if (md.isEmpty()) prefix else "$prefix$md"
    }
  }
  when {
    r.until != null -> labels += "至${r.until}"
    r.count != null -> labels += "共${r.count}次" // todo 显示当前是第几次
  }
  return labels
}

/**
 * 月重复的日期标签：连续 2 天及以上压缩为 `A-B`。
 * 例如 `1,2,3,5,6,-1,-3,-4` → `1-3,5-6日,倒1,倒3-4`。
 */
private fun formatMonthDayLabels(days: List<Int>): String {
  if (days.isEmpty()) return ""
  val positives = days.distinct().filter { it > 0 }.sorted()
  val negatives = days.distinct().filter { it < 0 }.map { -it }.sorted() // -1=倒1，-2=倒2...
  val labels = mutableListOf<String>()

  formatMonthDaySegments(positives).takeIf { it.isNotEmpty() }?.let { labels += "${it}日" }
  formatMonthDaySegments(negatives).takeIf { it.isNotEmpty() }?.let { value ->
    labels += value.split(",").joinToString(",") { "倒$it" }
  }
  return labels.joinToString(",")
}

private fun formatMonthDaySegments(days: List<Int>): String {
  val segments = mutableListOf<String>()
  var index = 0
  while (index < days.size) {
    val start = days[index]
    var end = start
    while (index + 1 < days.size && days[index + 1] == end + 1) {
      index += 1
      end = days[index]
    }
    segments += if (end - start >= 1) "${start}-${end}" else "$start"
    index += 1
  }
  return segments.joinToString(",")
}

/** 单行重复摘要，用于入口行右侧展示；不重复返回「不重复」。 */
fun recurrenceSummary(recurrence: Recurrence?): String =
  buildRecurrenceLabels(recurrence).joinToString(" · ").ifEmpty { "不重复" }

/** ISO 星期号 1..7 → 中文「一..日」。 */
fun weekNumberToChinese(iso: Int): String = when (iso) {
  1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"
  else -> ""
}
