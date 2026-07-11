package com.cyxbs.pages.schedule.data.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.OccurrenceStatus
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

  // 验证 isRecurring：有 rrule 或 rdate 判为重复，空 recurrence / null 判为单次
  @Test
  fun is_recurring() {
    assertTrue(ScheduleMutations.isRecurring(entity(Recurrence(RRule(Freq.DAILY)))))
    assertTrue(ScheduleMutations.isRecurring(entity(Recurrence(rdate = listOf(Date(2026, 1, 1))))))
    assertFalse(ScheduleMutations.isRecurring(entity(Recurrence())))
    assertFalse(ScheduleMutations.isRecurring(entity(null)))
  }

  // 验证 addExdate：把某次加入 EXDATE，且重复加入同一天保持幂等（不重复追加）
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

  // 验证 applyOverride：同一 recurrenceId 的 override 是替换而非追加（按 id 去重）
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
  fun complete_occurrence_preserves_existing_override() {
    val day = Date(2026, 1, 12)
    val e = entity(
      Recurrence(
        rrule = RRule(Freq.WEEKLY),
        overrides = listOf(RecurrenceOverride(recurrenceId = day, title = "改后标题")),
      )
    )
    val completed = ScheduleMutations.completeOccurrence(e, day)
    val override = completed.recurrence!!.overrides.single()
    assertEquals("改后标题", override.title)
    assertEquals(OccurrenceStatus.COMPLETED, override.status)
  }

  @Test
  fun apply_override_preserves_existing_status_when_patch_omits_it() {
    val day = Date(2026, 1, 12)
    val completed = ScheduleMutations.completeOccurrence(entity(Recurrence(RRule(Freq.WEEKLY))), day)
    val edited = ScheduleMutations.applyOverride(
      completed,
      RecurrenceOverride(recurrenceId = day, detail = "新备注"),
    )
    assertEquals(OccurrenceStatus.COMPLETED, edited.recurrence!!.overrides.single().status)
  }

  // 验证 truncateBefore（此次及后续）：UNTIL 设为前一天、清空 count，并丢弃截断点之后的 rdate/exdate/override
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
