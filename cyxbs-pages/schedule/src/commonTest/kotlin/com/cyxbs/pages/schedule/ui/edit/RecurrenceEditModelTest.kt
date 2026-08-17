package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.domain.model.*
import kotlin.test.*

/** V2 RRULE editor contract tests. */
class RecurrenceEditModelTest {
  private val anchor = Date(2026, 7, 6)

  @Test fun none_has_no_rule() = assertNull(RecurrenceDraft().toRecurrenceRule(anchor))
  @Test fun weekly_defaults_to_anchor_weekday() {
    val rule = RecurrenceDraft(freq = RepeatFreqOption.WEEKLY).toRecurrenceRule(anchor)!!
    assertEquals(setOf(IsoWeekDay.MONDAY), rule.byWeekDays)
  }
  @Test fun monthly_defaults_to_anchor_day() {
    assertEquals(setOf(6), RecurrenceDraft(freq = RepeatFreqOption.MONTHLY).toRecurrenceRule(anchor)!!.byMonthDays)
  }
  @Test fun count_and_until_map_to_exclusive_end_variants() {
    assertEquals(RecurrenceEnd.Count(4), RecurrenceDraft(
      freq = RepeatFreqOption.DAILY, endOption = RepeatEndOption.COUNT, count = 4,
    ).toRecurrenceRule(anchor)!!.end)
    assertEquals(
      RecurrenceEnd.Until(Date(2026, 7, 9)),
      RecurrenceDraft(
        freq = RepeatFreqOption.DAILY, endOption = RepeatEndOption.UNTIL, until = Date(2026, 7, 9),
      ).toRecurrenceRule(anchor)!!.end,
    )
  }
  @Test fun round_trip_keeps_v2_fields() {
    val rule = RecurrenceRule(
      RecurrenceFrequency.WEEKLY, 2, setOf(IsoWeekDay.MONDAY, IsoWeekDay.WEDNESDAY),
      end = RecurrenceEnd.Count(5),
    )
    assertEquals(rule, rule.toDraft().toRecurrenceRule(anchor))
  }
  @Test fun labels_are_stable() {
    val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, byWeekDays = setOf(IsoWeekDay.MONDAY))
    assertEquals("每周一", recurrenceRowLabel(rule))
    assertTrue(buildRecurrenceLabels(rule).first().contains("周一"))
  }
  @Test fun previews_are_bounded() {
    val draft = RecurrenceDraft(freq = RepeatFreqOption.DAILY)
    assertEquals(3, draft.countUntil(anchor, anchor.plusDays(2)))
    assertEquals(anchor.plusDays(2), draft.endDateAtCount(anchor, 3))
  }
}
