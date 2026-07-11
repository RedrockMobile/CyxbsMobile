package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.OccurrenceStatus
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleCalendarProjectionTest {

  private val rangeStart = Date(2026, 7, 1)
  private val rangeEnd = Date(2026, 7, 31)

  @Test
  fun no_date_or_reminder_or_completed_is_not_exported() {
    assertTrue(project(todo(endTime = "", remindMinutes = 10)).isEmpty())
    assertTrue(project(todo(remindMinutes = -1)).isEmpty())
    assertTrue(project(todo(remindMinutes = 10, isDone = 1)).isEmpty())
  }

  @Test
  fun date_only_is_all_day() {
    val event = project(todo(endTime = "2026年7月4日", remindMinutes = 10)).single()
    assertTrue(event.allDay)
    assertEquals(Date(2026, 7, 4), event.date)
  }

  @Test
  fun pure_rule_keeps_rrule() {
    val event = project(
      todo(
        startTime = "2026年7月4日 10:00",
        endTime = "2026年7月4日 11:00",
        remindMinutes = 10,
        recurrence = Recurrence(rrule = RRule(freq = Freq.WEEKLY, byDay = listOf(6))),
      )
    ).single()
    assertEquals("FREQ=WEEKLY;BYDAY=SA", event.rrule)
    assertEquals("1:series", event.key)
  }

  @Test
  fun completed_occurrence_is_removed_and_override_is_applied() {
    val recurrence = Recurrence(
      rrule = RRule(freq = Freq.DAILY, count = 3),
      overrides = listOf(
        RecurrenceOverride(Date(2026, 7, 5), status = OccurrenceStatus.COMPLETED),
        RecurrenceOverride(
          Date(2026, 7, 6),
          newDate = Date(2026, 7, 8),
          title = "改期",
        ),
      ),
    )
    val events = project(
      todo(
        startTime = "2026年7月4日 10:00",
        endTime = "2026年7月4日 11:00",
        remindMinutes = 10,
        recurrence = recurrence,
      )
    )
    assertEquals(listOf(Date(2026, 7, 4), Date(2026, 7, 8)), events.map { it.date })
    assertEquals("改期", events.last().title)
  }

  private fun project(todo: ScheduleEntity) =
    ScheduleCalendarProjectionFactory.create(todo, rangeStart, rangeEnd)

  private fun todo(
    startTime: String = "",
    endTime: String = "2026年7月4日 10:00",
    remindMinutes: Int = 10,
    isDone: Int = 0,
    recurrence: Recurrence? = null,
  ) = ScheduleEntity(
    todoId = 1,
    title = "测试",
    startTime = startTime,
    endTime = endTime,
    remindMinutes = remindMinutes,
    isDone = isDone,
    recurrence = recurrence,
    lastModifyTime = 0,
  )
}
