package com.cyxbs.pages.schedule.ui.service

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 清单与事务共享 Schedule 后的课表可见性合同测试。 */
class ScheduleCourseVisibilityTest {

  /** TODO 完成后隐藏，AFFAIR 即使关联清单后完成也保留课表身份。 */
  @Test
  fun completionOnlyHidesTodoOrigin() {
    val todo = schedule(ScheduleKind.TODO, ScheduleTodoState.PENDING)
    val affair = schedule(ScheduleKind.AFFAIR, null)

    assertTrue(todo.isVisibleInCourse(OccurrenceStatus.ACTIVE))
    assertFalse(todo.isVisibleInCourse(OccurrenceStatus.COMPLETED))
    assertTrue(affair.isVisibleInCourse(OccurrenceStatus.ACTIVE))
    assertTrue(
      affair.copy(todoState = ScheduleTodoState.COMPLETED)
        .isVisibleInCourse(OccurrenceStatus.COMPLETED),
    )
  }

  /** 未关联与已取消 occurrence 无论来源均不进入课表。 */
  @Test
  fun unlinkedOrCancelledScheduleIsHidden() {
    val affair = schedule(ScheduleKind.AFFAIR, null)
    assertFalse(affair.copy(linkedToCourse = false).isVisibleInCourse(OccurrenceStatus.ACTIVE))
    assertFalse(affair.isVisibleInCourse(OccurrenceStatus.CANCELLED))
  }

  /** 构造满足领域矩阵的最小定时日程。 */
  private fun schedule(kind: ScheduleKind, todoState: ScheduleTodoState?): Schedule = Schedule(
    id = ScheduleId("018f8e2a-7b4c-7abc-8def-0123456789ab"),
    revision = 1,
    title = "课表可见性",
    description = "",
    categoryId = CategoryId("work"),
    timing = ScheduleTiming.Timed(
      start = MinuteTimeDate(2026, 8, 24, 9, 0),
      durationMinutes = 60,
      timeZoneId = "Asia/Shanghai",
    ),
    recurrence = null,
    reminders = emptyList(),
    todoState = todoState,
    createdAt = Instant.fromEpochMilliseconds(1),
    updatedAt = Instant.fromEpochMilliseconds(2),
    kind = kind,
    linkedToCourse = true,
  )
}
