package com.cyxbs.pages.course.api

import kotlinx.datetime.isoDayNumber

/** 自己课程的详情标识前缀；标识内容由课程模块解释，外部入口不得自行解析。 */
const val CourseLessonDetailIdPrefix = "lesson:"

/** 关联课程的详情标识前缀；标识内容由课程模块解释，外部入口不得自行解析。 */
const val CourseLinkedLessonDetailIdPrefix = "link-lesson:"

/** 日程详情标识前缀；标识内容由课程模块解释，外部入口不得自行解析。 */
const val CourseScheduleDetailIdPrefix = "schedule:"

/**
 * 生成课程或关联课程的稳定详情标识。
 *
 * 标识不包含教学周，因此同一课程跨周共用详情身份；[isLinked] 用于隔离本人和关联人的数据源。
 */
fun LessonByWeeks.courseItemDetailId(isLinked: Boolean): String {
  return courseLessonDetailId(
    isLinked = isLinked,
    courseNum = courseNum,
    dayOfWeek = dayOfWeek.isoDayNumber,
    beginLesson = beginLesson,
    period = period,
    rawWeek = rawWeek,
    teacher = teacher,
    classroom = classroom,
  )
}

/** ID 纯拼接实现，供协议测试避开 [LessonByWeeks] 的课节时间配置初始化。 */
internal fun courseLessonDetailId(
  isLinked: Boolean,
  courseNum: String,
  dayOfWeek: Int,
  beginLesson: Int,
  period: Int,
  rawWeek: String,
  teacher: String,
  classroom: String,
): String {
  val prefix = if (isLinked) CourseLinkedLessonDetailIdPrefix else CourseLessonDetailIdPrefix
  return "$prefix$courseNum:$dayOfWeek:$beginLesson:$period:$rawWeek:$teacher:$classroom"
}

/** 生成日程详情身份；移动或跨日后仍由 occurrence identity 定位同一业务对象。 */
fun scheduleCourseItemDetailId(identity: String): String = "$CourseScheduleDetailIdPrefix$identity"
