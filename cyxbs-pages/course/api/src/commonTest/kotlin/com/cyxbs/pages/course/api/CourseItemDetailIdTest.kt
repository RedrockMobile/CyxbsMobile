package com.cyxbs.pages.course.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CourseItemDetailIdTest {

  private fun lessonId(isLinked: Boolean) = courseLessonDetailId(
    isLinked = isLinked,
    courseNum = "A001",
    dayOfWeek = 1,
    beginLesson = 1,
    period = 2,
    rawWeek = "1-3周",
    teacher = "张老师",
    classroom = "二教205",
  )

  @Test
  fun lessonDetailId_keepsExistingSnapshotFormat() {
    assertEquals(
      "lesson:A001:1:1:2:1-3周:张老师:二教205",
      lessonId(isLinked = false),
    )
  }

  @Test
  fun linkedLessonDetailId_isIsolatedFromSelfLesson() {
    assertEquals(
      "link-lesson:A001:1:1:2:1-3周:张老师:二教205",
      lessonId(isLinked = true),
    )
    assertNotEquals(
      lessonId(isLinked = false),
      lessonId(isLinked = true),
    )
  }

  @Test
  fun scheduleDetailId_onlyWrapsStableOccurrenceIdentity() {
    assertEquals("schedule:occurrence-42", scheduleCourseItemDetailId("occurrence-42"))
  }

  /** Item ID 同时保存周次、实际 Item 键和详情键，但外部只需原样传递一个字符串。 */
  @Test
  fun courseItemId_containsWeekAndKeepsItemSegmentsDistinct() {
    val first = courseItemId(
      week = 5,
      itemKey = "occurrence-42|2026-09-28|08:00|10:00",
      detailId = "schedule:occurrence-42",
    )
    val second = courseItemId(
      week = 5,
      itemKey = "occurrence-42|2026-09-28|14:00|16:00",
      detailId = "schedule:occurrence-42",
    )

    assertEquals(5, courseItemWeekOrNull(first))
    assertEquals("schedule:occurrence-42", courseItemDetailIdOrNull(first))
    assertNotEquals(first, second)
  }

  /** 旧详情 ID 不是 Item 定位 ID，不能在没有独立 week 参数时被误解析。 */
  @Test
  fun invalidCourseItemId_doesNotExposePartialLocation() {
    assertNull(courseItemWeekOrNull("schedule:occurrence-42"))
    assertNull(courseItemDetailIdOrNull("schedule:occurrence-42"))
  }
}
