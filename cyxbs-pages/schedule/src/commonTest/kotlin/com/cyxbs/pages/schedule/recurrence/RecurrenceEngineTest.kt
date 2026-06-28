package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RecurrenceEngine 展开逻辑单测。
 *
 * 关键日期参考：2026-01-05 是周一（dayOfWeekNumber=1）。
 */
class RecurrenceEngineTest {

  private val start = MinuteTime(8, 0)
  private val end = MinuteTime(9, 0)
  private val mon = Date(2026, 1, 5) // 周一

  private fun expand(
    recurrence: Recurrence,
    anchor: Date,
    rangeStart: Date,
    rangeEnd: Date,
  ): List<Date> = RecurrenceEngine.expandInRange(
    recurrence, anchor, start, end, rangeStart, rangeEnd,
  ).map { it.date }

  @Test
  fun daily_interval() {
    val r = Recurrence(RRule(Freq.DAILY, interval = 3))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 15))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 8), Date(2026, 1, 11), Date(2026, 1, 14)), dates)
  }

  @Test
  fun weekly_default_interval2() {
    val r = Recurrence(RRule(Freq.WEEKLY, interval = 2))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 2, 28))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 19), Date(2026, 2, 2), Date(2026, 2, 16)), dates)
  }

  @Test
  fun weekly_byday_multiple() {
    val r = Recurrence(RRule(Freq.WEEKLY, byDay = listOf(1, 3))) // 周一、周三
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 20))
    assertEquals(
      listOf(
        Date(2026, 1, 5), Date(2026, 1, 7),
        Date(2026, 1, 12), Date(2026, 1, 14),
        Date(2026, 1, 19),
      ),
      dates,
    )
  }

  @Test
  fun monthly_default_skips_short_months() {
    // 锚定 1 月 31 日：只在有 31 天的月份出现
    val r = Recurrence(RRule(Freq.MONTHLY))
    val dates = expand(r, Date(2026, 1, 31), Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(
      listOf(1, 3, 5, 7, 8, 10, 12).map { Date(2026, it, 31) },
      dates,
    )
  }

  @Test
  fun monthly_negative_month_day_last_day() {
    val r = Recurrence(RRule(Freq.MONTHLY, byMonthDay = listOf(-1))) // 每月最后一天
    val dates = expand(r, Date(2026, 1, 15), Date(2026, 1, 1), Date(2026, 4, 30))
    assertEquals(
      listOf(Date(2026, 1, 31), Date(2026, 2, 28), Date(2026, 3, 31), Date(2026, 4, 30)),
      dates,
    )
  }

  @Test
  fun yearly_leap_day_only_leap_years() {
    val r = Recurrence(RRule(Freq.YEARLY))
    val dates = expand(r, Date(2024, 2, 29), Date(2024, 1, 1), Date(2032, 12, 31))
    assertEquals(listOf(Date(2024, 2, 29), Date(2028, 2, 29), Date(2032, 2, 29)), dates)
  }

  @Test
  fun until_is_inclusive() {
    val r = Recurrence(RRule(Freq.WEEKLY, until = Date(2026, 1, 19)))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 12), Date(2026, 1, 19)), dates)
  }

  @Test
  fun count_limits_occurrences() {
    val r = Recurrence(RRule(Freq.DAILY, count = 3))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 6), Date(2026, 1, 7)), dates)
  }

  @Test
  fun count_includes_exdate_removed_occurrences() {
    // count=3 在 EXDATE 之前计数；删掉中间一个后剩 2 个
    val r = Recurrence(
      rrule = RRule(Freq.DAILY, count = 3),
      exdate = listOf(Date(2026, 1, 6)),
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 7)), dates)
  }

  @Test
  fun rdate_merges_and_dedupes() {
    val r = Recurrence(
      rrule = RRule(Freq.DAILY, count = 2), // 1/5, 1/6
      rdate = listOf(Date(2026, 1, 6), Date(2026, 1, 10)), // 1/6 与规则重复
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 6), Date(2026, 1, 10)), dates)
  }

  @Test
  fun override_moves_single_occurrence() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY),
      overrides = listOf(RecurrenceOverride(recurrenceId = Date(2026, 1, 12), newDate = Date(2026, 1, 13))),
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 20))
    assertTrue(Date(2026, 1, 12) !in dates)
    assertTrue(Date(2026, 1, 13) in dates)
    // 校验 isOverridden 与 recurrenceId
    val occ = RecurrenceEngine.expandInRange(r, mon, start, end, Date(2026, 1, 1), Date(2026, 1, 20))
      .first { it.date == Date(2026, 1, 13) }
    assertTrue(occ.isOverridden)
    assertEquals(Date(2026, 1, 12), occ.recurrenceId)
  }

  @Test
  fun override_cancel_removes_occurrence() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY),
      overrides = listOf(RecurrenceOverride(recurrenceId = Date(2026, 1, 12), cancelled = true)),
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 20))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 19)), dates)
  }

  @Test
  fun single_event_uses_anchor() {
    val r = Recurrence()
    assertEquals(listOf(mon), expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 31)))
    // 区间外则为空
    assertEquals(emptyList(), expand(r, mon, Date(2026, 2, 1), Date(2026, 2, 28)))
  }

  @Test
  fun deadline_type_keeps_null_start() {
    val occ = RecurrenceEngine.expandInRange(
      Recurrence(), mon, null, end, Date(2026, 1, 1), Date(2026, 1, 31),
    )
    assertEquals(1, occ.size)
    assertEquals(null, occ.first().start)
  }

  @Test
  fun next_from_skips_past_and_exdate() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY),
      exdate = listOf(Date(2026, 1, 12)),
    )
    val next = RecurrenceEngine.nextFrom(
      r, mon, start, end, from = MinuteTimeDate(2026, 1, 8, 8, 0),
    )
    // 1/5 已过、1/12 被 EXDATE，下一次应是 1/19
    assertEquals(Date(2026, 1, 19), next?.date)
  }
}
