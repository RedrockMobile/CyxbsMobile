package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 三态作用域两个纯构造函数 [buildFollowingSeries]/[buildOccurrenceOverride] 的单测。
 */
class EditScheduleScopeTest {

  private fun entity(
    title: String = "原标题",
    detail: String = "原备注",
    startTime: String? = null,
    endTime: String = "",
    recurrence: Recurrence? = null,
  ) = ScheduleEntity(
    todoId = 1L, title = title, detail = detail,
    startTime = startTime, endTime = endTime, recurrence = recurrence, lastModifyTime = 0L,
  )

  /** 「此次及后续」新系列：开始/结束的【日期】改写到 occurrenceDate，但保留各自【时分】。 */
  @Test
  fun followingSeries_reanchors_date_keeps_clock() {
    val edited = entity(
      startTime = "2026年6月28日 10:00",
      endTime = "2026年6月28日 11:30",
      recurrence = Recurrence(rrule = RRule(freq = Freq.WEEKLY, byDay = listOf(7))),
    )
    val out = buildFollowingSeries(edited, Date(2026, 7, 5))
    assertEquals("2026年7月5日 10:00", out.startTime)
    assertEquals("2026年7月5日 11:30", out.endTime)
  }

  /** 「此次及后续」新系列：丢弃旧系列的 exdate/overrides（只属于旧系列），但保留 rrule。 */
  @Test
  fun followingSeries_drops_exceptions_keeps_rule() {
    val edited = entity(
      startTime = "2026年6月28日 10:00",
      endTime = "2026年6月28日 11:00",
      recurrence = Recurrence(
        rrule = RRule(freq = Freq.WEEKLY, byDay = listOf(7)),
        exdate = listOf(Date(2026, 7, 12)),
      ),
    )
    val out = buildFollowingSeries(edited, Date(2026, 7, 5))
    assertTrue(out.recurrence!!.exdate.isEmpty())
    assertEquals(Freq.WEEKLY, out.recurrence!!.rrule!!.freq)
  }

  /** 「仅此次」override：日期未变(编辑后仍落在 occurrenceDate)则 newDate 为 null，时间改写进 newStart。 */
  @Test
  fun override_same_date_sets_time_not_newDate() {
    val origin = entity(title = "原标题", startTime = "2026年7月5日 10:00", endTime = "2026年7月5日 11:00")
    val edited = entity(title = "新标题", startTime = "2026年7月5日 10:30", endTime = "2026年7月5日 11:00")
    val ov = buildOccurrenceOverride(Date(2026, 7, 5), edited, origin)
    assertNull(ov.newDate)
    assertEquals(MinuteTime(10, 30), ov.newStart)
    // 标题变了应记录、备注没变应为 null。
    assertEquals("新标题", ov.title)
    assertNull(ov.detail)
    assertEquals(Date(2026, 7, 5), ov.recurrenceId)
  }

  /** 「仅此次」override：把这一次挪到别的日期 → newDate 记录新日期。 */
  @Test
  fun override_moved_date_sets_newDate() {
    val origin = entity(startTime = "2026年7月5日 10:00", endTime = "2026年7月5日 11:00")
    val edited = entity(startTime = "2026年7月6日 10:00", endTime = "2026年7月6日 11:00")
    val ov = buildOccurrenceOverride(Date(2026, 7, 5), edited, origin)
    assertEquals(Date(2026, 7, 6), ov.newDate)
  }
}
