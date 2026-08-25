package com.cyxbs.pages.course.view.decoration.impl

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.impl.CourseScheduleItem
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.course.view.item.impl.ScheduleCourseDecorationItem
import com.cyxbs.pages.course.view.item.impl.ScheduleItemWhatTime
import com.cyxbs.pages.schedule.api.IScheduleService2
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Schedule 课表 Decoration 的公共基类。
 *
 * 基类统一观察整个课表学期并直接从 Schedule API 获取全部 occurrence。ItemHierarchy 会按 page
 * 自行分桶，因此切换当前页面不重建数据；子类只负责筛选自己的排期类型并生成对应 Item。
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SchedulePageDecoration<Item : CourseItem>(
  protected val courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  private val scheduleService: IScheduleService2 = IScheduleService2::class.impl(),
) : CoursePageDecoration<Item>() {

  /** 当前课表学期的半开日期范围及允许投射到课表的 occurrence。 */
  protected data class ScheduleRange(
    val startDate: Date,
    val endDateExclusive: Date,
    val occurrences: List<ScheduleOccurrenceView>,
  )

  /** 整个课表学期的数据；学期起始日期尚未初始化时为 null。 */
  protected val scheduleRangeFlow: StateFlow<ScheduleRange?> = courseFrame.beginDate
    .flatMapLatest { beginDate ->
      val startDate = semesterStart(beginDate, courseFrame.timeline.beginDayOfWeek.ordinal)
      if (startDate == null) {
        flowOf<ScheduleRange?>(null)
      } else {
        val endDateExclusive = startDate.plusWeeks(courseFrame.maxWeek)
        scheduleService.observeLinkedOccurrencesInRange(
          startInclusive = MinuteTimeDate(startDate, 0, 0),
          endExclusive = MinuteTimeDate(endDateExclusive, 0, 0),
        ).map { occurrences ->
          ScheduleRange(startDate, endDateExclusive, occurrences)
        }
      }
    }.stateIn(
    scope = coroutineScope,
    started = SharingStarted.Eagerly,
    initialValue = null,
  )

  /** 同一 occurrence 的跨日切片使用独立身份，防止 ItemHierarchy 合并不同日期片段。 */
  protected fun ScheduleOccurrenceView.courseSegmentIdentity(
    date: Date,
    beginTime: MinuteTime,
    finalTime: MinuteTime,
    page: Int,
    type: String,
  ): String = "$identity|$date|$beginTime|$finalTime|$page|$type"

  /** 仅在 Course Item 适配点把 24:00 映射为组件当前可表示的 23:59。 */
  protected fun minuteTimeForCourse(minute: Int, isEnd: Boolean): MinuteTime {
    val value =
      if (isEnd && minute >= MINUTES_PER_DAY) MINUTES_PER_DAY - 1
      else minute.coerceIn(0, MINUTES_PER_DAY - 1)
    return MinuteTime(value / 60, value % 60)
  }

  /** 按课表配置的一周首日对齐学期起始日期。 */
  private fun semesterStart(
    beginDate: Date?,
    beginDayOffset: Int,
  ): Date? {
    if (beginDate == null) return null
    return beginDate.weekBeginDate.plusDays(beginDayOffset)
  }

  protected companion object {
    const val DAYS_PER_WEEK = 7
    const val MINUTES_PER_DAY = 24 * 60
  }
}

/**
 * TODO 时间段与事务时间段共用的切片基类。
 *
 * 具体子类在构造时固定 [kind]，因此每个 Decoration 只拥有一种来源的 ItemHierarchy；切片结果保持
 * module-internal，不会通过公共或 protected API 暴露给其他模块。
 */
abstract class ScheduleTimedKindPageDecoration protected constructor(
  courseFrame: AbstractCourseFrame,
  coroutineScope: CoroutineScope,
  platformItemFactory: PlatformScheduleItemFactory,
  private val kind: ScheduleOccurrenceKind,
  private val segmentType: String,
) : SchedulePageDecoration<CourseScheduleItem>(courseFrame, coroutineScope) {

  init {
    scheduleRangeFlow.onEach { range ->
      itemHierarchy.reset(
        range?.projectTimedOccurrences().orEmpty().map { item ->
          ScheduleItemWhatTime(item, isDeadline = false, platformItemFactory)
        },
      )
    }.launchIn(coroutineScope)
  }

  /** 过滤固定来源并把整个学期内的跨日时间段切成逐日课表 Item。 */
  private fun ScheduleRange.projectTimedOccurrences(): List<ScheduleCourseDecorationItem> = buildList {
    val dayCount = startDate.daysUntil(endDateExclusive)
    occurrences.forEach { occurrence ->
      if (occurrence.kind != kind) return@forEach
      val timing = occurrence.timing as? ScheduleOccurrenceTiming.Timed ?: return@forEach
      val endExclusive = timing.start.plusMinutes(timing.durationMinutes)
      repeat(dayCount) { dayIndex ->
        val date = startDate.plusDays(dayIndex)
        val dayStart = MinuteTimeDate(date, 0, 0)
        val dayEnd = MinuteTimeDate(date.plusDays(1), 0, 0)
        if (timing.start >= dayEnd || endExclusive <= dayStart) return@repeat
        val startMinute = if (timing.start > dayStart) timing.start.minuteOfDay else 0
        val endMinute = if (endExclusive < dayEnd) endExclusive.minuteOfDay else MINUTES_PER_DAY
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
              segmentType,
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
