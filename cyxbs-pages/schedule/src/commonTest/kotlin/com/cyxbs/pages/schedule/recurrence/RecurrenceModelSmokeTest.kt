package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 单测设施冒烟测试 + recurrence 数据模型默认值校验。
 *
 * 用于验证 commonTest 单测设施可正常运行；后续 RecurrenceEngine 的完整用例另起文件。
 */
class RecurrenceModelSmokeTest {

  @Test
  fun rrule_defaults() {
    val rule = RRule(freq = Freq.WEEKLY, byDay = listOf(1, 3))
    assertEquals(1, rule.interval)
    assertNull(rule.until)
    assertNull(rule.count)
    assertEquals(listOf(1, 3), rule.byDay)
  }

  @Test
  fun recurrence_defaults_empty() {
    val r = Recurrence()
    assertNull(r.rrule)
    assertTrue(r.rdate.isEmpty())
    assertTrue(r.exdate.isEmpty())
    assertTrue(r.overrides.isEmpty())
  }

  @Test
  fun override_carries_recurrence_id() {
    val day = Date(2026, 6, 28)
    val ov = RecurrenceOverride(recurrenceId = day, cancelled = true)
    assertEquals(day, ov.recurrenceId)
    assertTrue(ov.cancelled)
    assertNull(ov.newDate)
  }
}
