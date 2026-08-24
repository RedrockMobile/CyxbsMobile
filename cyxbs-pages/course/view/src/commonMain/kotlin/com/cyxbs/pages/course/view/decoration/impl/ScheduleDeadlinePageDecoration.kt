package com.cyxbs.pages.course.view.decoration.impl

import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.item.impl.CourseScheduleItem
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.course.view.item.impl.ScheduleCourseDecorationItem
import com.cyxbs.pages.course.view.item.impl.ScheduleItemWhatTime
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 把截止时间点渲染为课表最高层的零时长 Item。
 *
 * 调用方必须把该 Decoration 放在 Manager 的第一个位置。这样时间点先参与重叠计算并切开下层时段，
 * 无需 Schedule 模块设置跨层级 zIndex，也不会把真实截止时间伪装成一分钟区间。
 */
class ScheduleDeadlinePageDecoration(
  courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  platformItemFactory: PlatformScheduleItemFactory,
) : SchedulePageDecoration<CourseScheduleItem>(courseFrame, coroutineScope) {

  init {
    scheduleRangeFlow.onEach { range ->
      val items = if (range == null) emptyList() else projectDeadlineRange(range)
      itemHierarchy.reset(
        items.map { item ->
          ScheduleItemWhatTime(item, isDeadline = true, platformItemFactory)
        },
      )
    }.launchIn(coroutineScope)
  }

  /** 转换整个学期内的截止时间点；时间点继续保持零分钟业务区间。 */
  private fun projectDeadlineRange(
    range: ScheduleRange,
  ): List<ScheduleCourseDecorationItem> = range.occurrences.mapNotNull { occurrence ->
    val timing = occurrence.timing as? ScheduleOccurrenceTiming.Deadline ?: return@mapNotNull null
    val date = timing.due.date
    if (date < range.startDate || date >= range.endDateExclusive) {
      return@mapNotNull null
    }
    val page = courseFrame.getPage(date) ?: return@mapNotNull null
    val point = minuteTimeForCourse(timing.due.minuteOfDay, isEnd = false)
    ScheduleCourseDecorationItem(
      stableId = occurrence.courseSegmentIdentity(date, point, point, page, "deadline"),
      occurrence = occurrence,
      page = page,
      dayOfWeek = date.dayOfWeek,
      beginTime = point,
      finalTime = point,
      title = occurrence.title,
      description = "",
    )
  }
}
