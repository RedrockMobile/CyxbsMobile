package com.cyxbs.pages.course.view.decoration.impl

import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import kotlinx.coroutines.CoroutineScope

/**
 * 把 AFFAIR 来源的时间段日程渲染为课表事务 Item。
 *
 * 事务与 TODO 时间段拥有各自的 ItemHierarchy；Manager 可以独立排列其初始层级，后续也能分别扩展
 * 事务交互，而无需在 [ScheduleTodoTimedPageDecoration] 内判断业务来源。
 */
class ScheduleAffairPageDecoration(
  courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  platformItemFactory: PlatformScheduleItemFactory,
) : ScheduleTimedKindPageDecoration(
  courseFrame = courseFrame,
  coroutineScope = coroutineScope,
  platformItemFactory = platformItemFactory,
  kind = ScheduleOccurrenceKind.AFFAIR,
  segmentType = "affair-timed",
)
