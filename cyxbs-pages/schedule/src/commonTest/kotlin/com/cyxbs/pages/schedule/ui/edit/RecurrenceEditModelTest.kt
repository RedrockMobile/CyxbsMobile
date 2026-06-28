package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 重复规则编辑器纯逻辑（[RecurrenceDraft] ↔ [RRule]/[Recurrence] 互转、标签生成）的单测。
 *
 * 锚点统一用 2026-06-28（星期日，ISO=7；6 月；28 日），便于校验「BY* 留空用锚点补默认」的行为。
 */
class RecurrenceEditModelTest {

  private val anchor = Date(2026, 6, 28) // 周日，dayOfWeekNumber=7

  /** 不重复草稿应产出 null RRULE。 */
  @Test
  fun none_produces_null_rrule() {
    assertNull(RecurrenceDraft(freq = RepeatFreqOption.NONE).toRRule(anchor))
  }

  /** 每天 + 间隔：直接映射 FREQ=DAILY，interval 透传。 */
  @Test
  fun daily_maps_interval() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.DAILY, interval = 3).toRRule(anchor)!!
    assertEquals(Freq.DAILY, r.freq)
    assertEquals(3, r.interval)
  }

  /** 每周但没勾星期：用锚点星期(周日=7)补默认 BYDAY。 */
  @Test
  fun weekly_empty_byday_defaults_to_anchor_weekday() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.WEEKLY).toRRule(anchor)!!
    assertEquals(listOf(7), r.byDay)
  }

  /** 每周显式多选星期：去重并升序。 */
  @Test
  fun weekly_explicit_byday_sorted() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.WEEKLY, byDay = listOf(3, 1, 3)).toRRule(anchor)!!
    assertEquals(listOf(1, 3), r.byDay)
  }

  /** 每月没选日期：用锚点日(28)补默认 BYMONTHDAY。 */
  @Test
  fun monthly_empty_bymonthday_defaults_to_anchor_day() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.MONTHLY).toRRule(anchor)!!
    assertEquals(listOf(28), r.byMonthDay)
  }

  /** 每年：BYMONTH=锚点月、BYMONTHDAY=锚点日。 */
  @Test
  fun yearly_uses_anchor_month_and_day() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.YEARLY).toRRule(anchor)!!
    assertEquals(listOf(6), r.byMonth)
    assertEquals(listOf(28), r.byMonthDay)
  }

  /** 结束=按次数：写 COUNT、清 UNTIL。 */
  @Test
  fun end_count_sets_count_only() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.DAILY, endOption = RepeatEndOption.COUNT, count = 5).toRRule(anchor)!!
    assertEquals(5, r.count)
    assertNull(r.until)
  }

  /** 结束=按日期：写 UNTIL、清 COUNT。 */
  @Test
  fun end_until_sets_until_only() {
    val until = Date(2026, 12, 31)
    val r = RecurrenceDraft(freq = RepeatFreqOption.DAILY, endOption = RepeatEndOption.UNTIL, until = until).toRRule(anchor)!!
    assertEquals(until, r.until)
    assertNull(r.count)
  }

  /** interval 非法(0/负)被纠正为最小 1。 */
  @Test
  fun interval_clamped_to_at_least_one() {
    val r = RecurrenceDraft(freq = RepeatFreqOption.DAILY, interval = 0).toRRule(anchor)!!
    assertEquals(1, r.interval)
  }

  /** Recurrence(带 until) → 草稿：endOption 应反解为 UNTIL。 */
  @Test
  fun toDraft_reads_until_as_end_option() {
    val rec = Recurrence(rrule = RRule(freq = Freq.WEEKLY, byDay = listOf(1), until = Date(2026, 12, 31)))
    val draft = rec.toDraft()
    assertEquals(RepeatFreqOption.WEEKLY, draft.freq)
    assertEquals(RepeatEndOption.UNTIL, draft.endOption)
    assertEquals(listOf(1), draft.byDay)
  }

  /** Recurrence(带 count) → 草稿：endOption 应反解为 COUNT。 */
  @Test
  fun toDraft_reads_count_as_end_option() {
    val draft = Recurrence(rrule = RRule(freq = Freq.DAILY, count = 10)).toDraft()
    assertEquals(RepeatEndOption.COUNT, draft.endOption)
    assertEquals(10, draft.count)
  }

  /** null Recurrence → 不重复草稿。 */
  @Test
  fun toDraft_null_is_non_repeating() {
    assertTrue(!(null as Recurrence?).toDraft().isRepeating)
  }

  /** toRecurrence 编辑整条系列时保留原 exdate/overrides（base 透传）。 */
  @Test
  fun toRecurrence_preserves_base_exdate() {
    val base = Recurrence(rrule = RRule(freq = Freq.DAILY), exdate = listOf(Date(2026, 7, 1)))
    val out = RecurrenceDraft(freq = RepeatFreqOption.WEEKLY).toRecurrence(anchor, base)!!
    assertEquals(listOf(Date(2026, 7, 1)), out.exdate)
    assertEquals(Freq.WEEKLY, out.rrule!!.freq)
  }

  /** 标签：每2天。 */
  @Test
  fun labels_daily_interval() {
    val labels = buildRecurrenceLabels(Recurrence(rrule = RRule(freq = Freq.DAILY, interval = 2)))
    assertEquals(listOf("每2天"), labels)
  }

  /** 标签：每周 周一、周三。 */
  @Test
  fun labels_weekly_days() {
    val labels = buildRecurrenceLabels(Recurrence(rrule = RRule(freq = Freq.WEEKLY, byDay = listOf(1, 3))))
    assertEquals(listOf("每周 周一、周三"), labels)
  }

  /** 标签：每月 + 结束次数 两块。 */
  @Test
  fun labels_monthly_with_count() {
    val labels = buildRecurrenceLabels(Recurrence(rrule = RRule(freq = Freq.MONTHLY, byMonthDay = listOf(1, 15), count = 10)))
    assertEquals(listOf("每月 1日、15日", "共10次"), labels)
  }

  /** 摘要：不重复返回「不重复」。 */
  @Test
  fun summary_non_repeating() {
    assertEquals("不重复", recurrenceSummary(null))
  }
}
