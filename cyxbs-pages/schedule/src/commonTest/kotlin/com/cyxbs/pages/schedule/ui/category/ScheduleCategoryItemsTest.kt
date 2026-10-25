package com.cyxbs.pages.schedule.ui.category

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 分组全量页的普通日程分区与排序测试，不启动 Compose、Room 或网络。 */
class ScheduleCategoryItemsTest {

  /**
   * 普通日程以今天及未来的首次有效实例升序展示；没有未来实例的系列取最后一次历史实例倒序展示。
   * 被取消的单次不能成为系列代表项，24 小时内的普通日程仍应显示临期标签。
   */
  @Test
  fun ordinarySchedulesUseNextAndLastEffectiveOccurrenceForTwoSections() {
    val now = Instant.parse("2026-08-17T08:00:00Z")
    val dueSoonRecurring = schedule(
      suffix = "001",
      title = "今日重复日程",
      timing = timed(day = 17, hour = 12),
      recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.DAILY,
        end = RecurrenceEnd.Count(2),
      ),
    )
    val cancelledTodayRecurring = schedule(
      suffix = "002",
      title = "今日已取消的重复日程",
      timing = timed(day = 16, hour = 10),
      recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.DAILY,
        end = RecurrenceEnd.Count(4),
      ),
    )
    val futureSingle = schedule(
      suffix = "003",
      title = "明日单次日程",
      timing = timed(day = 18, hour = 9),
    )
    val expiredSingle = schedule(
      suffix = "004",
      title = "昨日单次日程",
      timing = timed(day = 16, hour = 16),
    )
    val expiredRecurring = schedule(
      suffix = "005",
      title = "历史重复日程",
      timing = timed(day = 10, hour = 9),
      recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.DAILY,
        end = RecurrenceEnd.Count(3),
      ),
    )
    val cancelledToday = ScheduleOccurrenceAdjustment(
      scheduleId = cancelledTodayRecurring.id,
      recurrenceId = RecurrenceId(
        originalDateTime = MinuteTimeDate(2026, 8, 17, 10, 0),
        timeZoneId = "UTC",
        allDay = false,
      ),
      revision = 1,
      status = OccurrenceStatus.CANCELLED,
      patch = null,
      createdAt = now,
      updatedAt = now,
    )

    val result = projectScheduleCategoryItems(
      snapshot = ScheduleSnapshot(
        schedules = listOf(
          expiredRecurring,
          futureSingle,
          cancelledTodayRecurring,
          expiredSingle,
          dueSoonRecurring,
        ),
        occurrenceAdjustments = listOf(cancelledToday),
      ),
      categoryId = null,
      now = now,
      viewerTimeZone = TimeZone.UTC,
      pinnedIds = emptyList(),
    )

    assertEquals(
      listOf("今日重复日程", "明日单次日程", "今日已取消的重复日程"),
      result.schedules.upcoming.map { it.schedule.title },
    )
    assertEquals(
      listOf("昨日单次日程", "历史重复日程"),
      result.schedules.expired.map { it.schedule.title },
    )
    assertTrue(result.schedules.upcoming.first().isDueSoon)
    assertEquals("8月18日 10:00–11:00", result.schedules.upcoming.last().timeText)
    assertFalse(result.schedules.expired.any { it.isOverdue || it.isDueSoon })
  }

  /** 构造不带完成态的普通日程；这类数据在清单 UI 中不展示完成圆圈。 */
  private fun schedule(
    suffix: String,
    title: String,
    timing: ScheduleTiming,
    recurrence: RecurrenceRule? = null,
  ): Schedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000$suffix"),
    revision = 1,
    title = title,
    description = "",
    categoryId = null,
    timing = timing,
    recurrence = recurrence,
    reminder = null,
    todoState = null,
    createdAt = Instant.parse("2026-08-10T00:00:00Z"),
    updatedAt = Instant.parse("2026-08-10T00:00:00Z"),
    kind = ScheduleKind.AFFAIR,
    linkedToCourse = true,
  )

  /** 事务领域只允许正时长时间段，测试统一使用一小时。 */
  private fun timed(day: Int, hour: Int): ScheduleTiming.Timed =
    ScheduleTiming.Timed(
      start = MinuteTimeDate(2026, 8, day, hour, 0),
      durationMinutes = 60,
      timeZoneId = "UTC",
    )
}
