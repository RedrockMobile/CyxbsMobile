package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

  // 验证 FREQ=DAILY + INTERVAL=3：每隔 3 天
  @Test
  fun daily_interval() {
    val r = Recurrence(RRule(Freq.DAILY, interval = 3))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 15))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 8), Date(2026, 1, 11), Date(2026, 1, 14)), dates)
  }

  // 验证 FREQ=WEEKLY + INTERVAL=2（无 BYDAY 时按锚点星期）：每隔两周
  @Test
  fun weekly_default_interval2() {
    val r = Recurrence(RRule(Freq.WEEKLY, interval = 2))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 2, 28))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 19), Date(2026, 2, 2), Date(2026, 2, 16)), dates)
  }

  // 验证 WEEKLY + BYDAY 多个星期（周一、周三）：一周内展开多天
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

  // 验证 MONTHLY 默认（锚点 31 日）：自动跳过没有 31 天的月份
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

  // 验证 MONTHLY + BYMONTHDAY=-1（负数倒数）：每月最后一天，随月份天数变化
  @Test
  fun monthly_negative_month_day_last_day() {
    val r = Recurrence(RRule(Freq.MONTHLY, byMonthDay = listOf(-1))) // 每月最后一天
    val dates = expand(r, Date(2026, 1, 15), Date(2026, 1, 1), Date(2026, 4, 30))
    assertEquals(
      listOf(Date(2026, 1, 31), Date(2026, 2, 28), Date(2026, 3, 31), Date(2026, 4, 30)),
      dates,
    )
  }

  // 验证 YEARLY 锚定闰日 2/29：仅在闰年出现，非闰年跳过
  @Test
  fun yearly_leap_day_only_leap_years() {
    val r = Recurrence(RRule(Freq.YEARLY))
    val dates = expand(r, Date(2024, 2, 29), Date(2024, 1, 1), Date(2032, 12, 31))
    assertEquals(listOf(Date(2024, 2, 29), Date(2028, 2, 29), Date(2032, 2, 29)), dates)
  }

  // 验证 UNTIL 含端点（截止当天仍产出）
  @Test
  fun until_is_inclusive() {
    val r = Recurrence(RRule(Freq.WEEKLY, until = Date(2026, 1, 19)))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 12), Date(2026, 1, 19)), dates)
  }

  // 验证 COUNT 限制总发生次数
  @Test
  fun count_limits_occurrences() {
    val r = Recurrence(RRule(Freq.DAILY, count = 3))
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 6), Date(2026, 1, 7)), dates)
  }

  // 验证 COUNT 在 EXDATE 之前计数：删掉中间一次后，总数仍按原始 count 截断
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

  // 验证 RDATE 与规则结果取并集并去重
  @Test
  fun rdate_merges_and_dedupes() {
    val r = Recurrence(
      rrule = RRule(Freq.DAILY, count = 2), // 1/5, 1/6
      rdate = listOf(Date(2026, 1, 6), Date(2026, 1, 10)), // 1/6 与规则重复
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 6), Date(2026, 1, 10)), dates)
  }

  // 验证 override（仅此次改期）：原日期消失、改后日期出现，且保留 isOverridden / recurrenceId 指向原锚点
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

  // 验证 override 取消（cancelled）：等价于把该次从序列移除
  @Test
  fun override_cancel_removes_occurrence() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY),
      overrides = listOf(RecurrenceOverride(recurrenceId = Date(2026, 1, 12), cancelled = true)),
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 20))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 19)), dates)
  }

  // 验证无 rrule/rdate 的单次日程：以锚点为唯一 occurrence；不在区间内则为空
  @Test
  fun single_event_uses_anchor() {
    val r = Recurrence()
    assertEquals(listOf(mon), expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 31)))
    // 区间外则为空
    assertEquals(emptyList(), expand(r, mon, Date(2026, 2, 1), Date(2026, 2, 28)))
  }

  // 验证截止型（anchorStart 传 null）：occurrence.start 保持 null
  @Test
  fun deadline_type_keeps_null_start() {
    val occ = RecurrenceEngine.expandInRange(
      Recurrence(), mon, null, end, Date(2026, 1, 1), Date(2026, 1, 31),
    )
    assertEquals(1, occ.size)
    assertEquals(null, occ.first().start)
  }

  // 验证 nextFrom：跳过已过去的与被 EXDATE 删除的，返回之后第一次
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

  // 验证 MONTHLY + INTERVAL=2：每隔一个月
  @Test
  fun monthly_interval() {
    val r = Recurrence(RRule(Freq.MONTHLY, interval = 2))
    val dates = expand(r, Date(2026, 1, 15), Date(2026, 1, 1), Date(2026, 7, 31))
    assertEquals(listOf(1, 3, 5, 7).map { Date(2026, it, 15) }, dates)
  }

  // 验证 YEARLY + INTERVAL=2：每隔一年
  @Test
  fun yearly_interval() {
    val r = Recurrence(RRule(Freq.YEARLY, interval = 2))
    val dates = expand(r, Date(2026, 3, 10), Date(2026, 1, 1), Date(2031, 12, 31))
    assertEquals(listOf(Date(2026, 3, 10), Date(2028, 3, 10), Date(2030, 3, 10)), dates)
  }

  // 验证 YEARLY + BYMONTH 多月：每年在指定的多个月各产出一次
  @Test
  fun yearly_bymonth_multiple() {
    val r = Recurrence(RRule(Freq.YEARLY, byMonth = listOf(3, 6)))
    val dates = expand(r, Date(2026, 3, 10), Date(2026, 1, 1), Date(2026, 12, 31))
    assertEquals(listOf(Date(2026, 3, 10), Date(2026, 6, 10)), dates)
  }

  // 验证 MONTHLY + BYMONTHDAY 正数多值（每月 1 号与 15 号）
  @Test
  fun monthly_bymonthday_positive_multiple() {
    val r = Recurrence(RRule(Freq.MONTHLY, byMonthDay = listOf(1, 15)))
    val dates = expand(r, Date(2026, 1, 1), Date(2026, 1, 1), Date(2026, 2, 28))
    assertEquals(
      listOf(Date(2026, 1, 1), Date(2026, 1, 15), Date(2026, 2, 1), Date(2026, 2, 15)),
      dates,
    )
  }

  // 验证 MONTHLY + BYDAY：在每月内按星期展开（每月的每个周一）
  @Test
  fun monthly_byday_expands_weekdays() {
    val r = Recurrence(RRule(Freq.MONTHLY, byDay = listOf(1))) // 每月的每个周一
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 31))
    assertEquals(listOf(5, 12, 19, 26).map { Date(2026, 1, it) }, dates)
  }

  // 验证 rangeStart 截断：区间起点之前的 occurrence 被排除（课表翻页有界展开）
  @Test
  fun range_start_truncates_recurring() {
    val r = Recurrence(RRule(Freq.WEEKLY))
    val dates = expand(r, mon, Date(2026, 1, 19), Date(2026, 2, 2))
    assertEquals(listOf(Date(2026, 1, 19), Date(2026, 1, 26), Date(2026, 2, 2)), dates)
  }

  // 验证 override 把某次改期到区间外：该次在当前区间内既不在原位也不出现
  @Test
  fun override_moving_out_of_range_disappears() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY),
      overrides = listOf(RecurrenceOverride(recurrenceId = Date(2026, 1, 12), newDate = Date(2026, 3, 1))),
    )
    val dates = expand(r, mon, Date(2026, 1, 1), Date(2026, 1, 31))
    assertEquals(listOf(Date(2026, 1, 5), Date(2026, 1, 19), Date(2026, 1, 26)), dates)
  }

  // 验证 nextFrom 在序列已耗尽（COUNT 用完）时返回 null
  @Test
  fun next_from_returns_null_when_exhausted() {
    val r = Recurrence(RRule(Freq.DAILY, count = 1)) // 唯一一次 1/5
    val next = RecurrenceEngine.nextFrom(r, mon, start, end, from = MinuteTimeDate(2026, 1, 6, 0, 0))
    assertNull(next)
  }
}
