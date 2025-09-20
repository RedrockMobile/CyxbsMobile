package com.cyxbs.pages.course.bean

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.IApiWrapper
import com.cyxbs.pages.course.api.LessonByWeeks
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 学生课程
 */
@Serializable
data class StuLessonBean(
  @SerialName("data")
  override val `data`: List<StuLesson>,
  @SerialName("info")
  override val info: String,
  @SerialName("nowWeek")
  val nowWeek: Int,
  @SerialName("status")
  override val status: Int,
  @SerialName("stuNum")
  val stuNum: String,
  @SerialName("version")
  val version: String
) : IApiWrapper<List<StuLessonBean.StuLesson>> {
  @Serializable
  data class StuLesson(
    @SerialName("course")
    val course: String, // 课程名
    @SerialName("course_num")
    val courseNum: String, // 课程号
    @SerialName("type")
    val type: String, // 选修 or 必修
    @SerialName("teacher")
    val teacher: String, // 老师
    @SerialName("classroom")
    val classroom: String, // 教室
    @SerialName("week")
    val week: List<Int>, // 哪几周
    @SerialName("hash_day")
    val hashDay: Int, // 星期数，星期一为 0
    @SerialName("begin_lesson")
    val beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始 (只有事务才存在)
    @SerialName("period")
    val period: Int, // 课的长度
    @SerialName("rawWeek")
    val rawWeek: String, // 周期，比如：1周,3-15周单周
  ) {
    fun toLessonByWeeks(): LessonByWeeks? {
      if (week.isEmpty()) {
        if (isDebug()) toast("课表数据错误(week.isEmpty())，$this")
        return null
      }
      if (week.any { it <= 0 }) {
        if (isDebug()) toast("课表数据错误(week.any { it <= 0 })，$this")
        return null
      }
      if (hashDay !in 0..6) {
        if (isDebug()) toast("课表数据错误(hashDay !in 0..6)，$this")
        return null
      }
      if (beginLesson !in 1..12) {
        if (isDebug()) toast("课表数据错误(beginLesson !in 1..12)，$this")
        return null
      }
      if (period <= 0) {
        if (isDebug()) toast("课表数据错误(period <= 0)，$this")
        return null
      }
      if (beginLesson + period - 1 > 12) {
        if (isDebug()) toast("课表数据错误(beginLesson + period - 1 > 12)，$this")
        return null
      }
      return LessonByWeeks(
        course = course,
        courseNum = courseNum,
        type = type,
        teacher = teacher,
        classroom = classroom,
        week = week,
        dayOfWeek = DayOfWeek(hashDay + 1),
        beginLesson = beginLesson,
        period = period,
        rawWeek = rawWeek
      )
    }
  }
}