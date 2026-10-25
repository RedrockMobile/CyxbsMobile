package com.cyxbs.pages.course.view.decoration.impl

import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind

/**
 * 把 TODO 来源的时间段日程渲染为普通课表 Item。
 *
 * 与事务的 [ScheduleAffairPageDecoration] 独立注册，使两类数据在进入重叠层级前已经分开；
 * Manager 可以分别控制清单时间段和事务的层级。
 */
class ScheduleTodoTimedPageDecoration(
  platformItemFactory: PlatformScheduleItemFactory,
) : ScheduleTimedKindPageDecoration(
  platformItemFactory = platformItemFactory,
  kind = ScheduleOccurrenceKind.TODO,
  segmentType = "todo-timed",
)
