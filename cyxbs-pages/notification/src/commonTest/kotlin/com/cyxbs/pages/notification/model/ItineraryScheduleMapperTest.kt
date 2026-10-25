package com.cyxbs.pages.notification.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.api.ScheduleExternalRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 没课约单时间段到 Schedule 的映射测试。 */
class ItineraryScheduleMapperTest {

  /** 指定教学周只生成一个绝对时间段，不附加重复规则。 */
  @Test
  fun specifiedWeekCreatesSingleTimedOccurrence() {
    val result = ItineraryScheduleSlot(
      beginLesson = 5,
      day = 2,
      period = 2,
      week = 3,
    ).toScheduleTiming(
      firstMonday = Date(2026, 3, 2),
      maxWeek = 20,
    )

    requireNotNull(result)
    assertEquals(MinuteTimeDate(2026, 3, 17, 14, 0), result.timing.start)
    assertEquals(100, result.timing.durationMinutes)
    assertNull(result.recurrence)
  }

  /** week=0 从第一教学周开始，并按课表最大周数生成每周重复。 */
  @Test
  fun wholeSemesterCreatesWeeklyRecurrence() {
    val result = ItineraryScheduleSlot(
      beginLesson = 1,
      day = 7,
      period = 1,
      week = 0,
    ).toScheduleTiming(
      firstMonday = Date(2026, 3, 2),
      maxWeek = 21,
    )

    requireNotNull(result)
    assertEquals(MinuteTimeDate(2026, 3, 8, 8, 0), result.timing.start)
    assertEquals(45, result.timing.durationMinutes)
    assertEquals(21, assertIs<ScheduleExternalRecurrence.Weekly>(result.recurrence).occurrenceCount)
  }

  /** 午间特殊行按旧课表语义映射为第四节下课到第五节上课。 */
  @Test
  fun noonSpecialRowKeepsLegacyTimeRange() {
    val result = ItineraryScheduleSlot(
      beginLesson = -1,
      day = 1,
      period = 1,
      week = 1,
    ).toScheduleTiming(
      firstMonday = Date(2026, 3, 2),
      maxWeek = 20,
    )

    requireNotNull(result)
    assertEquals(MinuteTimeDate(2026, 3, 2, 11, 55), result.timing.start)
    assertEquals(125, result.timing.durationMinutes)
  }

  /** 缺少学期、非法星期、非法长度和越界节次都必须安全拒绝。 */
  @Test
  fun invalidAcademicSlotIsRejected() {
    val firstMonday = Date(2026, 3, 2)
    assertNull(ItineraryScheduleSlot(1, 1, 1, 1).toScheduleTiming(null, 20))
    assertNull(ItineraryScheduleSlot(1, 0, 1, 1).toScheduleTiming(firstMonday, 20))
    assertNull(ItineraryScheduleSlot(1, 1, 0, 1).toScheduleTiming(firstMonday, 20))
    assertNull(ItineraryScheduleSlot(12, 1, 2, 1).toScheduleTiming(firstMonday, 20))
    assertNull(ItineraryScheduleSlot(1, 1, 1, 0).toScheduleTiming(firstMonday, 0))
  }
}
