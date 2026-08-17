package com.cyxbs.pages.schedule.domain.model

/**
 * 在调用方给定有界窗口内展开得到的一次日程发生。
 *
 * 非重复日程的 [recurrenceId] 为 `null`；重复日程则始终以规则原始生成的本地墙上时间作为身份。
 * 单次覆盖可以移动 [timing]，但不能随之改变该身份，否则后续完成、取消或再次编辑将无法命中原实例。
 */
data class ScheduleOccurrence(
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId?,
  val timing: ScheduleTiming,
  val title: String,
  val description: String,
  val categoryId: CategoryId?,
  val reminders: List<ScheduleReminder>,
  val status: OccurrenceStatus,
  val isOverridden: Boolean,
)
