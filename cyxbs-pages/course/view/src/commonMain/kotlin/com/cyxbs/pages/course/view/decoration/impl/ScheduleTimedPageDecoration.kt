package com.cyxbs.pages.course.view.decoration.impl

import com.cyxbs.components.config.time.MinuteTimeDate
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
 * 把有开始、结束时间的日程渲染为普通课表 Item。
 *
 * 该 Decoration 应紧邻并位于 AffairPageDecoration 之前，使时间段日程与事务采用同一套重叠算法，
 * 初始层级略高于事务；日程数据观察也封装在本 Decoration 内，Manager 只负责排列层级。
 */
class ScheduleTimedPageDecoration(
  courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  platformItemFactory: PlatformScheduleItemFactory,
) : SchedulePageDecoration<CourseScheduleItem>(courseFrame, coroutineScope) {

  init {
    scheduleRangeFlow.onEach { range ->
      val items = if (range == null) emptyList() else projectTimedRange(range)
      itemHierarchy.reset(
        items.map { item ->
          ScheduleItemWhatTime(item, isDeadline = false, platformItemFactory)
        },
      )
    }.launchIn(coroutineScope)
  }

  /** 只转换当前 Decoration 需要的时间段 occurrence，并把整个学期内的跨日区间切成逐日 Item。 */
  private fun projectTimedRange(
    range: ScheduleRange,
  ): List<ScheduleCourseDecorationItem> = buildList {
    val dayCount = range.startDate.daysUntil(range.endDateExclusive)
    range.occurrences.forEach { occurrence ->
      val timing = occurrence.timing as? ScheduleOccurrenceTiming.Timed ?: return@forEach
      val endExclusive = timing.start.plusMinutes(timing.durationMinutes)
      repeat(dayCount) { dayIndex ->
        val date = range.startDate.plusDays(dayIndex)
        val dayStart = MinuteTimeDate(date, 0, 0)
        val dayEnd = MinuteTimeDate(date.plusDays(1), 0, 0)
        if (timing.start >= dayEnd || endExclusive <= dayStart) return@repeat
        val startMinute = if (timing.start > dayStart) timing.start.minuteOfDay else 0
        val endMinute =
          if (endExclusive < dayEnd) endExclusive.minuteOfDay else MINUTES_PER_DAY
        if (endMinute <= startMinute) return@repeat
        val page = courseFrame.getPage(date) ?: return@repeat
        val beginTime = minuteTimeForCourse(startMinute, isEnd = false)
        val finalTime = minuteTimeForCourse(endMinute, isEnd = true)
        add(
          ScheduleCourseDecorationItem(
            stableId = occurrence.courseSegmentIdentity(
              date,
              beginTime,
              finalTime,
              page,
              "timed",
            ),
            occurrence = occurrence,
            page = page,
            dayOfWeek = date.dayOfWeek,
            beginTime = beginTime,
            finalTime = finalTime,
            title = occurrence.title,
            description = occurrence.description,
          ),
        )
      }
    }
  }
}
