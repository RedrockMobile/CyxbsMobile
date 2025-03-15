package com.cyxbs.pages.course.api

import com.cyxbs.components.config.time.MinuteTime
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/14
 */
data class LessonByWeeks(
  val course: String, // 课程名
  val courseNum: String, // 课程号
  val type: String, // 选修 or 必修
  val teacher: String, // 老师
  val classroom: String, // 教室
  val week: List<Int>, // 哪几周
  val dayOfWeek: DayOfWeek, // 星期几
  val beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始 (只有事务才存在)
  val period: Int, // 课的长度
  val rawWeek: String, // 周期，比如：1周,3-15周单周
) {

  val beginTime: MinuteTime = CourseUtils.getStartMinuteTime(beginLesson)
  val finalTime: MinuteTime = CourseUtils.getEndMinuteTime(beginLesson + period - 1)

  // 简化课表名称，比如：B411/B412
  val classroomSimplify: String by lazy {
    if (classroom.length <= 7) classroom else {
      Regex("[^[a-zA-Z0-9]]*([a-zA-Z0-9|/]{3,})").findAll(classroom)
        .mapNotNull {
          it.groups[1]?.value
        }.joinToString("/")
    }
  }
}
