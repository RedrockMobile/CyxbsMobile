package com.cyxbs.pages.course.view.decoration.impl

import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import kotlinx.coroutines.CoroutineScope

/**
 * 把 TODO 来源的时间段日程渲染为普通课表 Item。
 *
 * 与事务的 [ScheduleAffairPageDecoration] 独立注册，使两类数据在进入重叠层级前已经分开；
 * Manager 可以分别控制清单时间段和事务的层级。
 */
class ScheduleTodoTimedPageDecoration(
  courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  platformItemFactory: PlatformScheduleItemFactory,
) : ScheduleTimedKindPageDecoration(
  courseFrame = courseFrame,
  coroutineScope = coroutineScope,
  platformItemFactory = platformItemFactory,
  kind = ScheduleOccurrenceKind.TODO,
  segmentType = "todo-timed",
)
