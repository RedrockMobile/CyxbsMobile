package com.cyxbs.pages.schedule.ui.timeline

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证日时间轴只做墙上时间按日切片，不在 UI 层读取平台默认时区或重算重复规则。 */
class ScheduleTimeUtilsTest {
  @Test fun timedOccurrenceIsSlicedAcrossFirstMiddleAndLastDaysWithoutBoundaryDuplication() {
    val occurrence = timed(
      start = MinuteTimeDate(2024, 1, 1, 23, 0),
      durationMinutes = 26 * 60,
    )

    assertSlice(occurrence, Date(2024, 1, 1), 1380, 1440)
    assertSlice(occurrence, Date(2024, 1, 2), 0, 1440)
    assertSlice(occurrence, Date(2024, 1, 3), 0, 60)
    assertTrue(timelineSchedulesForDate(listOf(occurrence), Date(2024, 1, 4)).isEmpty())
  }

  @Test fun timedOccurrenceUsesHalfOpenDayEdges() {
    val endingAtMidnight = timed(MinuteTimeDate(2024, 1, 1, 23, 0), 60)
    val startingAtMidnight = timed(MinuteTimeDate(2024, 1, 2, 0, 0), 60)

    assertTrue(timelineSchedulesForDate(listOf(endingAtMidnight), Date(2024, 1, 2)).isEmpty())
    assertTrue(timelineSchedulesForDate(listOf(startingAtMidnight), Date(2024, 1, 1)).isEmpty())
    assertSlice(startingAtMidnight, Date(2024, 1, 2), 0, 60)
  }

  @Test fun allDayDeadlineAndExplicitUnscheduledFollowTheirOwnDayContracts() {
    val allDay = occurrence(ScheduleTiming.AllDay(Date(2024, 1, 1), durationDays = 3))
    assertSlice(allDay, Date(2024, 1, 1), 0, FULL_DAY_MINUTES)
    assertSlice(allDay, Date(2024, 1, 2), 0, FULL_DAY_MINUTES)
    assertSlice(allDay, Date(2024, 1, 3), 0, FULL_DAY_MINUTES)
    assertTrue(timelineSchedulesForDate(listOf(allDay), Date(2024, 1, 4)).isEmpty())

    val deadline = occurrence(ScheduleTiming.Deadline(MinuteTimeDate(2024, 1, 2, 9, 30), "Asia/Shanghai"))
    assertSlice(deadline, Date(2024, 1, 2), 570, 570, isInterval = false)
    assertTrue(timelineSchedulesForDate(listOf(deadline), Date(2024, 1, 3)).isEmpty())

    val unscheduled = occurrence(ScheduleTiming.Unscheduled)
    assertTrue(timelineSchedulesForDate(listOf(unscheduled), Date(2024, 1, 2)).isEmpty())
    assertSlice(unscheduled, Date(2024, 1, 2), 0, FULL_DAY_MINUTES, includeUnscheduled = true)
  }

  /** 断言一个 occurrence 在指定日期只生成唯一、已裁剪的可见片段。 */
  private fun assertSlice(
    occurrence: ScheduleUiOccurrence,
    date: Date,
    startMin: Int,
    endMin: Int,
    isInterval: Boolean = true,
    includeUnscheduled: Boolean = false,
  ) {
    assertEquals(
      listOf(Triple(startMin, endMin, isInterval)),
      timelineSchedulesForDate(listOf(occurrence), date, includeUnscheduled)
        .map { Triple(it.startMin, it.endMin, it.isInterval) },
    )
  }

  /** 构造不带重复语义的 UI occurrence，测试仅覆盖投影职责。 */
  private fun timed(start: MinuteTimeDate, durationMinutes: Int) =
    occurrence(ScheduleTiming.Timed(start, durationMinutes, "Asia/Shanghai"))

  /** 创建最小 UI occurrence，identity 对切片无影响。 */
  private fun occurrence(timing: ScheduleTiming) = ScheduleUiOccurrence(
    scheduleId = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ab"),
    recurrenceId = null,
    title = "测试日程",
    description = "",
    categoryId = null,
    timing = timing,
    reminders = emptyList(),
    status = OccurrenceStatus.ACTIVE,
    isOverridden = false,
  )
}
