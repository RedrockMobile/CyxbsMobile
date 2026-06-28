package com.cyxbs.pages.schedule.data.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleOccurrencesTest {

  @Test
  fun parse_full_datetime() {
    assertEquals(
      MinuteTimeDate(2024, 1, 15, 10, 30),
      ScheduleOccurrences.parseDateTime("2024年1月15日 10:30"),
    )
  }

  @Test
  fun parse_without_time_defaults_zero() {
    assertEquals(
      MinuteTimeDate(2024, 1, 15, 0, 0),
      ScheduleOccurrences.parseDateTime("2024年1月15日"),
    )
  }

  @Test
  fun parse_blank_or_invalid_null() {
    assertNull(ScheduleOccurrences.parseDateTime(null))
    assertNull(ScheduleOccurrences.parseDateTime(""))
    assertNull(ScheduleOccurrences.parseDateTime("无"))
  }

  @Test
  fun format_round_trip() {
    val mtd = MinuteTimeDate(2026, 3, 5, 8, 5)
    val s = ScheduleOccurrences.formatDateTime(mtd)
    assertEquals("2026年3月5日 08:05", s)
    assertEquals(mtd, ScheduleOccurrences.parseDateTime(s))
  }

  @Test
  fun anchor_deadline_has_null_start() {
    val todo = ScheduleEntity(
      todoId = 1L, title = "t", lastModifyTime = 0L,
      startTime = null, endTime = "2026年1月5日 09:00",
    )
    val (date, start, end) = ScheduleOccurrences.anchor(todo)!!
    assertEquals(Date(2026, 1, 5), date)
    assertNull(start)
    assertEquals(MinuteTime(9, 0), end)
  }

  @Test
  fun anchor_interval_has_start() {
    val todo = ScheduleEntity(
      todoId = 1L, title = "t", lastModifyTime = 0L,
      startTime = "2026年1月5日 08:00", endTime = "2026年1月5日 09:00",
    )
    val (date, start, end) = ScheduleOccurrences.anchor(todo)!!
    assertEquals(Date(2026, 1, 5), date)
    assertEquals(MinuteTime(8, 0), start)
    assertEquals(MinuteTime(9, 0), end)
  }

  @Test
  fun anchor_null_when_no_end_time() {
    val todo = ScheduleEntity(todoId = 1L, title = "t", lastModifyTime = 0L, endTime = "")
    assertNull(ScheduleOccurrences.anchor(todo))
  }

  @Test
  fun expand_weekly_entity() {
    val todo = ScheduleEntity(
      todoId = 1L, title = "t", lastModifyTime = 0L,
      startTime = "2026年1月5日 08:00", endTime = "2026年1月5日 09:00",
      recurrence = Recurrence(RRule(Freq.WEEKLY)),
    )
    val dates = ScheduleOccurrences
      .expandInRange(todo, Date(2026, 1, 1), Date(2026, 1, 31))
      .map { it.date }
    assertEquals(
      listOf(Date(2026, 1, 5), Date(2026, 1, 12), Date(2026, 1, 19), Date(2026, 1, 26)),
      dates,
    )
  }

  @Test
  fun expand_empty_when_unscheduled() {
    val todo = ScheduleEntity(todoId = 1L, title = "t", lastModifyTime = 0L, endTime = "")
    assertEquals(emptyList(), ScheduleOccurrences.expandInRange(todo, Date(2026, 1, 1), Date(2026, 12, 31)))
  }
}
