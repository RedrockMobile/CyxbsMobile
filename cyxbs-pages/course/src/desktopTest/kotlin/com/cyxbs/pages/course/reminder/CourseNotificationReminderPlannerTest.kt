package com.cyxbs.pages.course.reminder

import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 iOS 提醒队列的时间边界、去重和共享容量，不依赖系统通知权限。 */
class CourseNotificationReminderPlannerTest {

  /** 忽略已到达及过去的提醒，保留未来课程并按实际提醒时间排序。 */
  @Test
  fun skipsExpiredRemindersAndSortsUniqueEvents() {
    val now = MinuteTimeDate(2026, 10, 1, 8, 0)
    val upcoming = event("upcoming", MinuteTimeDate(2026, 10, 1, 8, 21))
    val later = event("later", MinuteTimeDate(2026, 10, 1, 10, 0))
    val reminders = CourseNotificationReminderPlanner.plan(
      listOf(later, upcoming, upcoming, event("expired", MinuteTimeDate(2026, 10, 1, 8, 20))),
      now,
      otherPendingCount = 0,
    )
    assertEquals(listOf("upcoming", "later"), reminders.map { it.event.stableId })
    assertEquals(MinuteTimeDate(2026, 10, 1, 8, 1), reminders.first().notifyAt)
  }

  /** 午夜后的课程也必须正确计算到前一天的提醒日期。 */
  @Test
  fun reminderCanCrossMidnight() {
    val reminders = CourseNotificationReminderPlanner.plan(
      listOf(event("night", MinuteTimeDate(2026, 10, 2, 0, 10))),
      now = MinuteTimeDate(2026, 10, 1, 23, 40),
      otherPendingCount = 0,
    )
    assertEquals(MinuteTimeDate(2026, 10, 1, 23, 50), reminders.single().notifyAt)
  }

  /** 其他业务优先保留槽位；本功能只安排剩余容量内最近的课程。 */
  @Test
  fun reservesCapacityForOtherNotifications() {
    val base = MinuteTimeDate(2026, 10, 1, 8, 0)
    val events = (0 until 100).map { event("$it", base.plusDays(it + 1)) }
    val reminders = CourseNotificationReminderPlanner.plan(events.reversed(), base, otherPendingCount = 4)
    assertEquals(60, reminders.size)
    assertEquals("0", reminders.first().event.stableId)
    assertEquals("59", reminders.last().event.stableId)
    assertTrue(CourseNotificationReminderPlanner.plan(events, base, otherPendingCount = 64).isEmpty())
    assertTrue(CourseNotificationReminderPlanner.plan(events, base, otherPendingCount = 100).isEmpty())
  }

  /** 构造单次课程实例，避免测试需要课程网络或平台存储。 */
  private fun event(id: String, start: MinuteTimeDate) = CourseReminderEvent(
    stableId = id,
    title = "移动应用开发",
    description = "教师：张老师",
    location = "二教A101",
    start = start,
    end = start.plusMinutes(90),
  )
}
