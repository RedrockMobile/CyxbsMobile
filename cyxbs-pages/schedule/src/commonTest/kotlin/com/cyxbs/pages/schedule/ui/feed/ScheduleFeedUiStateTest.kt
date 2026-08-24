package com.cyxbs.pages.schedule.ui.feed

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.ReminderId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCompletion
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 主页 Feed 投影的纯领域测试，不启动 Compose、Room 或网络。 */
class ScheduleFeedUiStateTest {

  /** 超期事项必须进入 Feed，且横条计数不能被最多三张卡片的展示上限截断。 */
  @Test
  fun overdueAndDueSoonCountUsesWholePendingSet() {
    val now = Instant.parse("2026-08-21T08:00:00Z")
    val schedules = listOf(
      schedule("001", "已超期", MinuteTimeDate(2026, 8, 21, 7, 0)),
      schedule("002", "两小时后", MinuteTimeDate(2026, 8, 21, 10, 0)),
      schedule("003", "十二小时后", MinuteTimeDate(2026, 8, 21, 20, 0)),
      schedule("004", "二十四小时后", MinuteTimeDate(2026, 8, 22, 8, 0)),
      schedule("005", "普通事项", MinuteTimeDate(2026, 8, 23, 8, 0)),
    )

    val state = assertIs<ScheduleFeedUiState.Data>(
      projectScheduleFeed(ScheduleSnapshot(schedules = schedules), now, TimeZone.UTC),
    )

    assertEquals(3, state.items.size)
    assertEquals(4, state.urgentCount)
    assertEquals("已超期", state.items.first().title)
    assertTrue(state.items.first().isOverTime)
  }

  /** 没有未完成事项时保持主页空状态，不展示无意义的零数量横条。 */
  @Test
  fun completedOnlySnapshotIsEmpty() {
    val completed = schedule(
      suffix = "010",
      title = "已完成",
      due = MinuteTimeDate(2026, 8, 21, 10, 0),
      completion = ScheduleCompletion.COMPLETED,
    )

    assertEquals(
      ScheduleFeedUiState.Empty,
      projectScheduleFeed(
        ScheduleSnapshot(schedules = listOf(completed)),
        Instant.parse("2026-08-21T08:00:00Z"),
        TimeZone.UTC,
      ),
    )
  }

  /** Feed 与清单页共用端上置顶顺序；超期仍保持最高优先级，置顶项紧随其后。 */
  @Test
  fun pinnedItemUsesTodoOrderingBeforeFeedLimit() {
    val now = Instant.parse("2026-08-21T08:00:00Z")
    val overdue = schedule("020", "已超期", MinuteTimeDate(2026, 8, 21, 7, 0))
    val ordinary = schedule("021", "普通", MinuteTimeDate(2026, 8, 23, 8, 0))
    val pinned = schedule("022", "置顶", MinuteTimeDate(2026, 8, 24, 8, 0))

    val state = assertIs<ScheduleFeedUiState.Data>(
      projectScheduleFeed(
        ScheduleSnapshot(schedules = listOf(overdue, ordinary, pinned)),
        now,
        TimeZone.UTC,
        pinnedIds = listOf(pinned.id),
      ),
    )

    assertEquals(listOf("已超期", "置顶", "普通"), state.items.map { it.title })
    assertTrue(state.items[1].isPinned)
  }

  /** Feed 必须展示与清单页一致的提醒文案，不能只保留截止时间。 */
  @Test
  fun reminderTextIsProjectedForFeedRow() {
    val state = assertIs<ScheduleFeedUiState.Data>(
      projectScheduleFeed(
        snapshot = ScheduleSnapshot(
          schedules = listOf(
            schedule(
              suffix = "030",
              title = "带提醒事项",
              due = MinuteTimeDate(2026, 8, 21, 10, 0),
              reminders = listOf(ScheduleReminder(ReminderId("r1"), 10, ReminderChannel.DEVICE)),
            ),
          ),
        ),
        now = Instant.parse("2026-08-21T08:00:00Z"),
        viewerTimeZone = TimeZone.UTC,
      ),
    )

    assertEquals("提前10分钟提醒", state.items.single().reminderText)
  }

  /** Feed 仅给有时间的事项展示关联入口，并复用课表投射的进程内选择结果。 */
  @Test
  fun courseProjectionStateOnlyAppliesToScheduledItem() {
    val scheduled = schedule("040", "有时间", MinuteTimeDate(2026, 8, 21, 10, 0))
    val unscheduled = schedule("041", "无时间", MinuteTimeDate(2026, 8, 21, 11, 0))
      .copy(timing = ScheduleTiming.Unscheduled)

    val state = assertIs<ScheduleFeedUiState.Data>(
      projectScheduleFeed(
        snapshot = ScheduleSnapshot(schedules = listOf(scheduled, unscheduled)),
        now = Instant.parse("2026-08-21T08:00:00Z"),
        viewerTimeZone = TimeZone.UTC,
        projectedScheduleIds = setOf(scheduled.id, unscheduled.id),
      ),
    )

    val scheduledItem = state.items.first { it.id == scheduled.id }
    val unscheduledItem = state.items.first { it.id == unscheduled.id }
    assertTrue(scheduledItem.canProjectToCourse)
    assertTrue(scheduledItem.isProjectedToCourse)
    assertFalse(unscheduledItem.canProjectToCourse)
  }

  /** 构造满足领域 identity 的最小 Deadline 日程。 */
  private fun schedule(
    suffix: String,
    title: String,
    due: MinuteTimeDate,
    completion: ScheduleCompletion = ScheduleCompletion.PENDING,
    reminders: List<ScheduleReminder> = emptyList(),
  ): Schedule = Schedule(
    id = ScheduleId("019c6f00-0000-7000-8000-000000000$suffix"),
    revision = 1,
    title = title,
    description = "",
    categoryId = null,
    timing = ScheduleTiming.Deadline(due = due, timeZoneId = "UTC"),
    recurrence = null,
    reminders = reminders,
    completion = completion,
    createdAt = Instant.parse("2026-08-21T00:00:00Z"),
    updatedAt = Instant.parse("2026-08-21T00:00:00Z"),
  )
}
