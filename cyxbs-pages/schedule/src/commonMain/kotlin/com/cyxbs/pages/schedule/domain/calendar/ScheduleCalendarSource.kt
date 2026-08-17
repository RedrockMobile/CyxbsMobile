package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException

/**
 * 提供给平台日历适配器的领域快照。
 *
 * 此处只暴露日程与例外事实，不定义平台 URI、权限、托管日历或对账策略；这些具有平台副作用的决策必须
 * 留在具体适配层，避免 common 领域模型提前绑定 CalendarContract 或 EventKit。
 */
data class ScheduleCalendarSource(
  val schedules: List<Schedule>,
  val exceptions: List<ScheduleOccurrenceException>,
)

/**
 * 供上层读取当前日历领域快照的边界，不让 common 代码依赖平台日历 API。
 *
 * @return 调用时刻的日程与例外快照；是否缓存由实现决定。
 */
fun interface ScheduleCalendarSourceProvider {
  fun currentSource(): ScheduleCalendarSource
}
