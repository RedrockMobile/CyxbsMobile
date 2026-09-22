package com.cyxbs.pages.course.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
}
