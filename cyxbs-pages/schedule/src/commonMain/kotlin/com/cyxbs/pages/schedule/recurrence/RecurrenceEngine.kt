package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate

/**
 * RFC5545 子集的重复规则展开引擎（commonMain 唯一事实源）。
 *
 * 设计要点：
 * - 按可见日期区间「有界展开」，课表只展开当前可见周；
 * - 中国无 DST，时区计算可简化（按本地日期/分钟直接运算）；
 * - 无限规则（无 UNTIL/COUNT）由 rangeEnd 截断，并设周期硬上限防御脏数据；
 * - DTSTART(anchor) 始终视为一个 occurrence（可被 EXDATE/override 剔除）。
 *
 * 不依赖任何外部 RRULE 库；完全建立在 [Date]/[MinuteTime] 之上。
 */
object RecurrenceEngine {

  /** 周期迭代硬上限，防御无 UNTIL/COUNT 且区间异常时的死循环。 */
  private const val MAX_PERIODS = 100_000

  /**
   * 把 [recurrence] 展开为 [rangeStart, rangeEnd]（含端点）内的所有 occurrence。
   *
   * @param anchorDate DTSTART 的日期
   * @param anchorStart 开始时间；null 表示截止型
   * @param anchorEnd 结束时间（截止型时即该时刻）
   */
  fun expandInRange(
    recurrence: Recurrence,
    anchorDate: Date,
    anchorStart: MinuteTime?,
    anchorEnd: MinuteTime,
    rangeStart: Date,
    rangeEnd: Date,
  ): List<Occurrence> {
    if (rangeEnd < rangeStart) return emptyList()

    // 1. 生成基础日期集合：
    //    - 有 RRULE：occurrence 完全由规则决定（锚点匹配规则时自然包含，不匹配则不强行加入）；
    //    - 无 RRULE：DTSTART 本身即一个 occurrence（单次/纯 RDATE 场景）。
    //    再并入 RDATE。
    val base = LinkedHashSet<Date>()
    if (recurrence.rrule != null) {
      base.addAll(ruleDates(recurrence.rrule, anchorDate, upperBound = rangeEnd))
    } else {
      base.add(anchorDate)
    }
    base.addAll(recurrence.rdate)

    // 2. EXDATE 剔除（按原始日期键）
    base.removeAll(recurrence.exdate.toHashSet())

    // 3. override 改写 + 区间过滤
    val overrideMap = recurrence.overrides.associateBy { it.recurrenceId }
    val result = ArrayList<Occurrence>()
    for (date in base) {
      val ov = overrideMap[date]
      if (ov?.cancelled == true) continue
      val effectiveDate = ov?.newDate ?: date
      if (effectiveDate < rangeStart || effectiveDate > rangeEnd) continue
      result.add(
        Occurrence(
          date = effectiveDate,
          start = ov?.newStart ?: anchorStart,
          end = ov?.newEnd ?: anchorEnd,
          isOverridden = ov != null,
          recurrenceId = date,
        )
      )
    }
    // 4. 按生效日期 + 开始时间排序
    result.sortWith(compareBy({ it.date }, { it.start ?: it.end }))
    return result
  }

  /**
   * 取 [from]（含）之后的下一次 occurrence，用于「写入系统日历的下一闹钟点」等。
   * 在 [from] 起 [horizonYears] 年内查找，无则返回 null。
   */
  fun nextFrom(
    recurrence: Recurrence,
    anchorDate: Date,
    anchorStart: MinuteTime?,
    anchorEnd: MinuteTime,
    from: MinuteTimeDate,
    horizonYears: Int = 5,
  ): Occurrence? {
    val rangeEnd = from.date.plusYears(horizonYears)
    val occurrences = expandInRange(recurrence, anchorDate, anchorStart, anchorEnd, from.date, rangeEnd)
    return occurrences
      .filter { MinuteTimeDate(it.date, it.start ?: it.end) >= from }
      .minByOrNull { MinuteTimeDate(it.date, it.start ?: it.end) }
  }

  // ---------------- RRULE 展开 ----------------

  /**
   * 生成 RRULE 在 [anchor, upperBound] 内的日期（升序），并应用 COUNT/UNTIL。
   * - COUNT：取前 count 个（再丢弃超出 upperBound 的尾部）；
   * - UNTIL：取 <= until 且 <= upperBound；
   * - 无界：取 <= upperBound。
   */
  private fun ruleDates(rule: RRule, anchor: Date, upperBound: Date): List<Date> {
    val seq = ruleSequence(rule, anchor).filter { it >= anchor }
    return when {
      rule.count != null -> seq.take(rule.count).takeWhile { it <= upperBound }.toList()
      rule.until != null -> seq.takeWhile { it <= rule.until && it <= upperBound }.toList()
      else -> seq.takeWhile { it <= upperBound }.toList()
    }
  }

  /** 升序产出 RRULE 各次发生（不含 COUNT/UNTIL/上界，由调用方裁剪）；带周期硬上限。 */
  private fun ruleSequence(rule: RRule, anchor: Date): Sequence<Date> = sequence {
    var period = 0
    while (period < MAX_PERIODS) {
      val candidates = candidatesForPeriod(rule, anchor, period)
      for (d in candidates) yield(d)
      period++
    }
  }

  /** 第 period 个周期（0 起）内、符合 BY* 的候选日期，升序。 */
  private fun candidatesForPeriod(rule: RRule, anchor: Date, period: Int): List<Date> {
    return when (rule.freq) {
      Freq.DAILY -> {
        val d = anchor.plusDays(period * rule.interval)
        if (matchesFilters(d, rule)) listOf(d) else emptyList()
      }
      Freq.WEEKLY -> {
        val weekBase = anchor.weekBeginDate.plusWeeks(period * rule.interval)
        val weekdays = (if (rule.byDay.isNotEmpty()) rule.byDay else listOf(anchor.dayOfWeekNumber)).sorted()
        weekdays
          .map { weekBase.plusDays(it - 1) }
          .filter { rule.byMonth.isEmpty() || it.monthNumber in rule.byMonth }
      }
      Freq.MONTHLY -> {
        val totalMonths = (anchor.monthNumber - 1) + period * rule.interval
        val year = anchor.year + totalMonths / 12
        val month = totalMonths % 12 + 1
        if (rule.byMonth.isNotEmpty() && month !in rule.byMonth) emptyList()
        else daysInMonth(rule, anchor, year, month)
      }
      Freq.YEARLY -> {
        val year = anchor.year + period * rule.interval
        val months = (if (rule.byMonth.isNotEmpty()) rule.byMonth else listOf(anchor.monthNumber)).sorted()
        months.flatMap { daysInMonth(rule, anchor, year, it) }
      }
    }
  }

  /** MONTHLY/YEARLY 在某个 (year, month) 内的候选日期，升序。 */
  private fun daysInMonth(rule: RRule, anchor: Date, year: Int, month: Int): List<Date> {
    val len = Date.lengthOfMonth(year, month)
    val days: List<Int> = when {
      rule.byMonthDay.isNotEmpty() ->
        rule.byMonthDay.map { if (it > 0) it else len + it + 1 }.filter { it in 1..len }
      rule.byDay.isNotEmpty() ->
        (1..len).filter { day -> Date(year, month, day).dayOfWeekNumber in rule.byDay }
      else ->
        if (anchor.dayOfMonth <= len) listOf(anchor.dayOfMonth) else emptyList()
    }
    return days.sorted().map { Date(year, month, it) }
  }

  /** DAILY 的 BY* 过滤。 */
  private fun matchesFilters(d: Date, rule: RRule): Boolean {
    if (rule.byMonth.isNotEmpty() && d.monthNumber !in rule.byMonth) return false
    if (rule.byDay.isNotEmpty() && d.dayOfWeekNumber !in rule.byDay) return false
    if (rule.byMonthDay.isNotEmpty()) {
      val len = d.lengthOfMonth
      val ok = rule.byMonthDay.any { (if (it > 0) it else len + it + 1) == d.dayOfMonth }
      if (!ok) return false
    }
    return true
  }
}
