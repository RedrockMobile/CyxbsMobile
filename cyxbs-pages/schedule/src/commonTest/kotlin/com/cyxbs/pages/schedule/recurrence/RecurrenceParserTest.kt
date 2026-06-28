package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceParserTest {

  @Test
  fun serialize_basic() {
    assertEquals(
      "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE",
      RecurrenceParser.serialize(RRule(Freq.WEEKLY, interval = 2, byDay = listOf(3, 1))),
    )
  }

  @Test
  fun parse_basic() {
    assertEquals(
      RRule(Freq.WEEKLY, interval = 2, byDay = listOf(1, 3)),
      RecurrenceParser.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"),
    )
  }

  @Test
  fun parse_tolerates_prefix_and_count() {
    assertEquals(
      RRule(Freq.DAILY, count = 5),
      RecurrenceParser.parse("RRULE:FREQ=DAILY;COUNT=5"),
    )
  }

  @Test
  fun until_round_trip() {
    val rule = RRule(Freq.MONTHLY, until = Date(2026, 12, 31))
    val s = RecurrenceParser.serialize(rule)
    assertEquals("FREQ=MONTHLY;UNTIL=20261231", s)
    assertEquals(rule, RecurrenceParser.parse(s))
  }

  @Test
  fun until_accepts_date_time_form() {
    assertEquals(
      Date(2026, 1, 1),
      RecurrenceParser.parse("FREQ=DAILY;UNTIL=20260101T235959Z").until,
    )
  }

  @Test
  fun negative_month_day_round_trip() {
    val rule = RRule(Freq.MONTHLY, byMonthDay = listOf(-1))
    val s = RecurrenceParser.serialize(rule)
    assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", s)
    assertEquals(rule, RecurrenceParser.parse(s))
  }

  @Test
  fun round_trip_normalized() {
    // byDay 升序归一后，parse∘serialize 恒等
    val rule = RRule(
      freq = Freq.WEEKLY,
      interval = 3,
      byDay = listOf(1, 5),
      until = Date(2027, 6, 1),
    )
    assertEquals(rule, RecurrenceParser.parse(RecurrenceParser.serialize(rule)))
  }

  @Test
  fun serialize_full_lines() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY, byDay = listOf(1)),
      rdate = listOf(Date(2026, 1, 10)),
      exdate = listOf(Date(2026, 1, 17)),
    )
    assertEquals(
      "RRULE:FREQ=WEEKLY;BYDAY=MO\n" +
        "RDATE;VALUE=DATE:20260110\n" +
        "EXDATE;VALUE=DATE:20260117",
      RecurrenceParser.serializeFull(r),
    )
  }
}
