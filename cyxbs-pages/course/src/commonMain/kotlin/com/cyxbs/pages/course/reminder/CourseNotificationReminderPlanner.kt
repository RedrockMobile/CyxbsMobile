package com.cyxbs.pages.course.reminder

import com.cyxbs.components.config.time.MinuteTimeDate

/** 待安排的单次课前通知；保留原课程实例及精确到分钟的触发时刻。 */
internal data class CourseNotificationReminder(
  val event: CourseReminderEvent,
  val notifyAt: MinuteTimeDate,
)

/**
 * iOS 通知队列的纯数据规划器，不涉及授权、存储或平台 API。
 *
 * 保守把应用的待发请求总数限制在 64，先给其他业务保留位置，再按时间安排最近的课程。
 * 队列会在应用回到前台、课程或设置发生变化时补充；不承诺应用长期未启动时覆盖整个学期。
 */
internal object CourseNotificationReminderPlanner {
  private const val PENDING_LIMIT = 64
  private const val ADVANCE_MINUTES = 20

  /**
   * 从 [events] 中选择尚未错过提醒时刻的实例，并排除重复身份。
   *
   * [now] 与课程使用相同的本地时区；[otherPendingCount] 是其他业务已占用的请求数。
   * 已到达当前分钟的提醒不再补发，避免开启开关时立即连续发送过期课程。
   */
  fun plan(
    events: List<CourseReminderEvent>,
    now: MinuteTimeDate,
    otherPendingCount: Int,
  ): List<CourseNotificationReminder> = events
    .distinctBy(CourseReminderEvent::stableId)
    .map { CourseNotificationReminder(it, it.start.minusMinutes(ADVANCE_MINUTES)) }
    .filter { it.notifyAt > now }
    .sortedWith(compareBy(CourseNotificationReminder::notifyAt, { it.event.stableId }))
    .take((PENDING_LIMIT - otherPendingCount.coerceIn(0, PENDING_LIMIT)))
}
