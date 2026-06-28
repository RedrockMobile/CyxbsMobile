package com.cyxbs.pages.schedule.data.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleMutationsTest {

  private fun entity(recurrence: Recurrence?) = ScheduleEntity(
    todoId = 1L, title = "t", lastModifyTime = 0L, recurrence = recurrence,
  )

  @Test
  fun is_recurring() {
    assertTrue(ScheduleMutations.isRecurring(entity(Recurrence(RRule(Freq.DAILY)))))
    assertTrue(ScheduleMutations.isRecurring(entity(Recurrence(rdate = listOf(Date(2026, 1, 1))))))
    assertFalse(ScheduleMutations.isRecurring(entity(Recurrence())))
    assertFalse(ScheduleMutations.isRecurring(entity(null)))
  }

  @Test
  fun add_exdate_appends_and_dedupes() {
    val e = entity(Recurrence(RRule(Freq.WEEKLY)))
    val day = Date(2026, 1, 12)
    val once = ScheduleMutations.addExdate(e, day)
    assertEquals(listOf(day), once.recurrence!!.exdate)
    // 幂等
    val twice = ScheduleMutations.addExdate(once, day)
    assertEquals(listOf(day), twice.recurrence!!.exdate)
  }

  @Test
  fun apply_override_replaces_same_recurrence_id() {
    val e = entity(Recurrence(RRule(Freq.WEEKLY)))
    val day = Date(2026, 1, 12)
    val first = ScheduleMutations.applyOverride(e, RecurrenceOverride(recurrenceId = day, title = "A"))
    val second = ScheduleMutations.applyOverride(first, RecurrenceOverride(recurrenceId = day, title = "B"))
    assertEquals(1, second.recurrence!!.overrides.size)
    assertEquals("B", second.recurrence!!.overrides.first().title)
  }

  @Test
  fun truncate_before_sets_until_clears_count_and_drops_tail() {
    val e = entity(
      Recurrence(
        rrule = RRule(Freq.WEEKLY, count = 10),
        rdate = listOf(Date(2026, 1, 1), Date(2026, 3, 1)),
        exdate = listOf(Date(2026, 1, 2), Date(2026, 3, 2)),
        overrides = listOf(
          RecurrenceOverride(recurrenceId = Date(2026, 1, 3)),
          RecurrenceOverride(recurrenceId = Date(2026, 3, 3)),
        ),
      )
    )
    val cut = ScheduleMutations.truncateBefore(e, Date(2026, 2, 1))
    val rec = cut.recurrence!!
    assertEquals(Date(2026, 1, 31), rec.rrule!!.until) // 前一天
    assertNull(rec.rrule!!.count)
    assertEquals(listOf(Date(2026, 1, 1)), rec.rdate) // 丢弃 3/1
    assertEquals(listOf(Date(2026, 1, 2)), rec.exdate) // 丢弃 3/2
    assertEquals(listOf(Date(2026, 1, 3)), rec.overrides.map { it.recurrenceId }) // 丢弃 3/3
  }
}
