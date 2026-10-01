package com.cyxbs.pages.course.reminder

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.api.LessonByWeeks
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证课表课程按真实周次展开为平台日历事件。
 *
 * LessonByWeeks 会初始化 CourseUtils 中的持久化设置，因此在具有 Settings 实现的桌面 JVM 上执行，
 * 避免为日期规划测试额外引入 Android Application 模拟环境。
 */
class CourseReminderEventPlannerTest {

  /** 不连续周次应分别落到正确日期，重复和非法周数不得产生重复事件。 */
  @Test
  fun planExpandsWeeksAndKeepsCourseTimes() {
    val lesson = LessonByWeeks(
      course = "移动应用开发",
      courseNum = "CST-101",
      type = "必修",
      teacher = "张老师",
      classroom = "二教A101",
      week = listOf(1, 3, 3, 0),
      dayOfWeek = DayOfWeek.TUESDAY,
      beginLesson = 3,
      period = 2,
      rawWeek = "1,3周",
    )

    val events = CourseReminderEventPlanner.plan(
      firstMonday = Date(2026, 2, 23),
      lessons = listOf(lesson),
    )

    assertEquals(2, events.size)
    assertEquals(Date(2026, 2, 24), events[0].start.date)
    assertEquals(Date(2026, 3, 10), events[1].start.date)
    assertEquals(MinuteTime(10, 15), events[0].start.time)
    assertEquals(MinuteTime(11, 55), events[0].end.time)
    assertEquals("教师：张老师\n周次：1,3周", events[0].description)
  }
}
