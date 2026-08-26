package com.cyxbs.pages.schedule.ui.todo

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.category.ScheduleDefaultCategories
import com.cyxbs.pages.schedule.ui.category.findMissingDefaultScheduleCategory
import com.cyxbs.pages.schedule.ui.category.isFixedScheduleCategory
import com.cyxbs.pages.schedule.ui.category.mergeScheduleCategories
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** 邮子清单投影的纯领域测试，不启动 Compose、Room 或网络。 */
class ScheduleTodoUiStateTest {

  /** 默认候选始终可见；已有同名真实分类会替代候选，避免保存时重复创建。 */
  @Test
  fun defaultCategoriesAreMergedWithoutPretendingToBeRepositoryFacts() {
    val actualStudy = ScheduleCategory(CategoryId("actual-study"), 3, "学习", null, 9)
    val custom = ScheduleCategory(CategoryId("custom"), 2, "项目", null, 4)

    val merged = mergeScheduleCategories(listOf(custom, actualStudy))

    assertEquals(listOf("学习", "生活", "其他", "项目"), merged.map { it.name })
    assertEquals(actualStudy.id, merged.first().id)
    assertEquals(
      ScheduleDefaultCategories[1],
      findMissingDefaultScheduleCategory(
        ScheduleDefaultCategories[1].id,
        actual = listOf(custom, actualStudy),
      ),
    )
    assertEquals(
      null,
      findMissingDefaultScheduleCategory(actualStudy.id, listOf(custom, actualStudy)),
    )
    assertTrue(isFixedScheduleCategory(actualStudy))
    assertTrue(isFixedScheduleCategory(ScheduleDefaultCategories[1]))
    assertFalse(isFixedScheduleCategory(custom))
  }

  /** Deadline 展示时间点，Timed 保留完整起止区间，完成态仍进入独立分区。 */
  @Test
  fun pointRangeAndCompletedAreProjectedWithoutCalendarRelationState() {
    val now = Instant.parse("2026-08-17T08:00:00Z")
    val snapshot = ScheduleSnapshot(
      schedules = listOf(
        schedule(
          suffix = "001",
          title = "时间点事项",
          timing = ScheduleTiming.Deadline(
            due = MinuteTimeDate(2026, 8, 17, 10, 0),
            timeZoneId = "UTC",
          ),
        ),
        schedule(
          suffix = "002",
          title = "预约讨论",
          timing = ScheduleTiming.Timed(
            start = MinuteTimeDate(2026, 8, 17, 9, 0),
            durationMinutes = 60,
            timeZoneId = "UTC",
          ),
        ),
        schedule(
          suffix = "003",
          title = "已完成事项",
          timing = ScheduleTiming.Unscheduled,
          todoState = ScheduleTodoState.COMPLETED,
          updatedAt = now - 2.days,
        ),
        schedule(
          suffix = "005",
          title = "八天前完成事项",
          timing = ScheduleTiming.Unscheduled,
          todoState = ScheduleTodoState.COMPLETED,
          updatedAt = now - 8.days,
        ),
      ),
    )

    val projection = projectScheduleTodo(snapshot, now, TimeZone.UTC)

    assertEquals(2, projection.pending.size)
    assertEquals(1, projection.completed.size)
    assertEquals(2, projection.urgentCount)
    assertEquals(
      "8月17日 10:00",
      projection.pending.first { it.schedule.title == "时间点事项" }.timeText,
    )
    assertEquals(
      "8月17日 09:00–10:00",
      projection.pending.first { it.schedule.title == "预约讨论" }.timeText,
    )
    assertEquals("未设置时间", projection.completed.single().timeText)
    assertEquals("已完成事项", projection.completed.single().schedule.title)
  }

  /** 未完成排序遵循超期、置顶、24 小时临期、普通、无截止时间，超期不会被置顶覆盖。 */
  @Test
  fun pendingItemsUseBusinessPriorityAndTwentyFourHourDueSoonWindow() {
    val now = Instant.parse("2026-08-17T08:00:00Z")
    val overdue = schedule(
      suffix = "010",
      title = "超期",
      timing = ScheduleTiming.Deadline(MinuteTimeDate(2026, 8, 17, 7, 0), "UTC"),
    )
    val pinned = schedule(
      suffix = "011",
      title = "置顶",
      timing = ScheduleTiming.Deadline(MinuteTimeDate(2026, 8, 20, 8, 0), "UTC"),
    )
    val dueSoon = schedule(
      suffix = "012",
      title = "临期",
      timing = ScheduleTiming.Deadline(MinuteTimeDate(2026, 8, 18, 8, 0), "UTC"),
    )
    val normal = schedule(
      suffix = "013",
      title = "普通",
      timing = ScheduleTiming.Deadline(MinuteTimeDate(2026, 8, 18, 8, 1), "UTC"),
    )
    val unscheduled = schedule(
      suffix = "014",
      title = "无截止时间",
      timing = ScheduleTiming.Unscheduled,
    )
    val projection = projectScheduleTodo(
      ScheduleSnapshot(schedules = listOf(unscheduled, normal, dueSoon, pinned, overdue)),
      now,
      TimeZone.UTC,
    )

    val sorted = sortScheduleTodoPending(
      projection.pending,
      // 即便超期项也在置顶 Settings 中，它仍必须排在置顶组之前。
      pinnedIds = listOf(pinned.id, overdue.id),
    )

    assertEquals(listOf("超期", "置顶", "临期", "普通", "无截止时间"), sorted.map { it.schedule.title })
    assertTrue(sorted.first { it.schedule.title == "临期" }.isDueSoon)
    assertFalse(sorted.first { it.schedule.title == "普通" }.isDueSoon)
  }

  /** 重复系列只展示一个可执行实例，避免按一整年规则生成大量清单卡片。 */
  @Test
  fun recurringSeriesProducesOneTodoCard() {
    val now = Instant.parse("2026-08-17T08:00:00Z")
    val recurring = schedule(
      suffix = "004",
      title = "每周例会",
      timing = ScheduleTiming.Timed(
        start = MinuteTimeDate(2026, 8, 17, 12, 0),
        durationMinutes = 60,
        timeZoneId = "UTC",
      ),
      recurrence = RecurrenceRule(
        frequency = RecurrenceFrequency.WEEKLY,
        byWeekDays = setOf(IsoWeekDay.MONDAY),
        end = RecurrenceEnd.Never,
      ),
    )

    val projection = projectScheduleTodo(
      ScheduleSnapshot(schedules = listOf(recurring)),
      now,
      TimeZone.UTC,
    )

    assertEquals(1, projection.pending.size)
    assertEquals(recurring.id, projection.pending.single().schedule.id)
    assertTrue(projection.pending.single().occurrence.recurrenceId != null)
  }

  /** 构造满足领域 identity 的最小测试日程。 */
  private fun schedule(
    suffix: String,
    title: String,
    timing: ScheduleTiming,
    todoState: ScheduleTodoState = ScheduleTodoState.PENDING,
    recurrence: RecurrenceRule? = null,
    updatedAt: Instant = Instant.parse("2026-08-17T00:00:00Z"),
  ): Schedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000$suffix"),
    revision = 1,
    title = title,
    description = "",
    categoryId = null,
    timing = timing,
    recurrence = recurrence,
    reminders = emptyList(),
    todoState = todoState,
    createdAt = Instant.parse("2026-08-17T00:00:00Z"),
    updatedAt = updatedAt,
  )
}
