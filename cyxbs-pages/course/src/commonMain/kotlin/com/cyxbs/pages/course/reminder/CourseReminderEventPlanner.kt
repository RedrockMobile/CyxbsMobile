package com.cyxbs.pages.course.reminder

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.api.LessonByWeeks

/**
 * 一次可用于平台日历或本地通知的课程实例。
 *
 * 一个 [LessonByWeeks] 可能覆盖多周，这里按“周”展开，确保单双周与不连续周不依赖平台重复规则。
 */
internal data class CourseReminderEvent(
  val stableId: String,
  val title: String,
  val description: String,
  val location: String,
  val start: MinuteTimeDate,
  val end: MinuteTimeDate,
)

/**
 * 把课表缓存转换为无平台依赖的课程事件。
 *
 * 规划器只负责确定日期、时间与稳定身份；权限、账号以及日历写入或通知投递由平台实现处理。
 */
internal object CourseReminderEventPlanner {

  /**
   * 展开本学期的全部课程实例。
   *
   * [firstMonday] 是第一周周一；非法周数会被忽略，相同身份的重复课程只保留一份。
   */
  fun plan(
    firstMonday: Date,
    lessons: List<LessonByWeeks>,
  ): List<CourseReminderEvent> = lessons
    .flatMap { lesson ->
      lesson.week
        .filter { it > 0 }
        .distinct()
        .map { week ->
          val date = firstMonday
            .plusWeeks(week - 1)
            .plusDays(lesson.dayOfWeek.ordinal)
          CourseReminderEvent(
            stableId = buildStableId(lesson, week),
            title = lesson.course,
            description = buildDescription(lesson),
            location = lesson.classroom,
            start = MinuteTimeDate(date, lesson.beginTime),
            end = MinuteTimeDate(date, lesson.finalTime),
          )
        }
    }
    .distinctBy(CourseReminderEvent::stableId)
    .sortedWith(compareBy(CourseReminderEvent::start, CourseReminderEvent::stableId))

  /** 稳定身份包含课程、周次与时间槽，避免同一天不同教学班相互覆盖。 */
  private fun buildStableId(lesson: LessonByWeeks, week: Int): String = listOf(
    lesson.courseNum,
    lesson.course,
    lesson.teacher,
    lesson.classroom,
    week.toString(),
    lesson.dayOfWeek.ordinal.toString(),
    lesson.beginLesson.toString(),
    lesson.period.toString(),
  ).joinToString("|")

  /** 地点作为独立字段交给平台，说明区只保留课堂教师与原始周次。 */
  private fun buildDescription(lesson: LessonByWeeks): String = buildList {
    lesson.teacher.takeIf(String::isNotBlank)?.let { add("教师：$it") }
    lesson.rawWeek.takeIf(String::isNotBlank)?.let { add("周次：$it") }
  }.joinToString("\n")
}
