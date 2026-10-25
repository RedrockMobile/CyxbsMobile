package com.cyxbs.pages.schedule.ui.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.edit.RecurrenceDraft
import com.cyxbs.pages.schedule.ui.edit.RepeatFreqOption
import com.cyxbs.pages.schedule.ui.edit.previewDatesInRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ScheduleCalendarMarkedDatesTest {

  /** 重复截止日历仅预览当前草稿规则生成的日期。 */
  @Test
  fun marksCurrentRecurrenceDraftDates() {
    val draft = RecurrenceDraft(
      freq = RepeatFreqOption.WEEKLY,
      byDay = listOf(1, 3),
    )

    assertEquals(
      expected = setOf(
        Date(2026, 3, 2),
        Date(2026, 3, 4),
        Date(2026, 3, 9),
        Date(2026, 3, 11),
      ),
      actual = draft.previewDatesInRange(
        anchor = Date(2026, 3, 2),
        startInclusive = Date(2026, 3, 1),
        endExclusive = Date(2026, 3, 12),
      ),
    )
  }

  /** 日期圆点覆盖实际占用日期，未设置日期和窗口外日程不产生标记。 */
  @Test
  fun marksVisibleOccupiedDatesOnly() {
    val snapshot = ScheduleSnapshot(
      schedules = listOf(
        schedule(
          index = 1,
          timing = ScheduleTiming.Timed(
            start = MinuteTimeDate(2026, 3, 2, 23, 30),
            durationMinutes = 90,
            timeZoneId = "Asia/Shanghai",
          ),
        ),
        schedule(
          index = 2,
          timing = ScheduleTiming.Deadline(
            due = MinuteTimeDate(2026, 3, 4, 18, 0),
            timeZoneId = "Asia/Shanghai",
          ),
        ),
        schedule(index = 3, timing = ScheduleTiming.AllDay(Date(2026, 3, 5))),
        schedule(index = 4, timing = ScheduleTiming.Unscheduled),
        schedule(index = 5, timing = ScheduleTiming.AllDay(Date(2026, 3, 9))),
      ),
    )

    assertEquals(
      expected = setOf(
        Date(2026, 3, 2),
        Date(2026, 3, 3),
        Date(2026, 3, 4),
        Date(2026, 3, 5),
      ),
      actual = scheduleCalendarMarkedDates(
        snapshot = snapshot,
        startInclusive = Date(2026, 3, 1),
        endExclusive = Date(2026, 3, 9),
      ),
    )
  }

  private fun schedule(index: Int, timing: ScheduleTiming): Schedule = Schedule(
    id = ScheduleId("00000000-0000-7000-8000-${index.toString().padStart(12, '0')}"),
    revision = 1,
    title = "日程$index",
    description = "",
    categoryId = null,
    timing = timing,
    recurrence = null,
    reminder = null,
    todoState = ScheduleTodoState.PENDING,
    createdAt = Instant.fromEpochMilliseconds(index.toLong()),
    updatedAt = Instant.fromEpochMilliseconds(index.toLong()),
    kind = ScheduleKind.TODO,
  )
}
