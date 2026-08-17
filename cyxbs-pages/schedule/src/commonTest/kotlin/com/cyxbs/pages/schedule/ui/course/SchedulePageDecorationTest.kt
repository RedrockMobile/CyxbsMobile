package com.cyxbs.pages.schedule.ui.course

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek

/** 验证课表装饰层严格遵守整学期页与教学周页的页面合同，并为跨日切片保留独立身份。 */
class SchedulePageDecorationTest {
  private val beginDate = Date(2024, 9, 2)

  @Test fun wholeTermPageHasNoWeekStart() {
    assertNull(scheduleWeekStart(beginDate, DayOfWeek.MONDAY, page = 0))
  }

  @Test fun firstTeachingWeekStartsAtConfiguredFirstDisplayDay() {
    assertEquals(Date(2024, 9, 2), scheduleWeekStart(beginDate, DayOfWeek.MONDAY, page = 1))
  }

  @Test fun laterTeachingWeekUsesOneBasedPageOffset() {
    assertEquals(Date(2024, 9, 9), scheduleWeekStart(beginDate, DayOfWeek.MONDAY, page = 2))
  }

  @Test fun firstTeachingWeekRespectsCustomTimelineBeginDay() {
    assertEquals(Date(2024, 9, 8), scheduleWeekStart(beginDate, DayOfWeek.SUNDAY, page = 1))
  }

  @Test fun fragmentsOnDifferentDatesHaveDifferentHierarchyIdentities() {
    val common = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ab")
    val firstDay = ScheduleCourseItemIdentity(
      scheduleId = common,
      recurrenceId = null,
      date = Date(2024, 9, 2),
      start = MinuteTime(0, 0),
      end = MinuteTime(23, 59),
      page = 1,
    )
    val nextDay = firstDay.copy(date = Date(2024, 9, 3))

    assertNotEquals(firstDay, nextDay)
  }
}
