package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLessonSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 验证课程路径只依赖 Manifest 与持久化步骤即可稳定汇总课时进度。 */
class DynamicTutorialCourseProgressTest {
  @Test
  fun resolvesCompletedLessonsAndNextLesson() {
    val summary = summary()
    val progress = progress(
      completedSteps = listOf(
        DynamicTutorialCompletedStep("lesson-1", "read"),
        DynamicTutorialCompletedStep("lesson-1", "run"),
        DynamicTutorialCompletedStep("lesson-2", "edit"),
      ),
    )

    val result = summary.resolveProgress(progress)

    assertEquals(1, result.completedLessonCount)
    assertEquals(2, result.totalLessonCount)
    assertEquals("lesson-2", result.nextLesson?.lessonId)
  }

  @Test
  fun keepsCompletedCourseCompletedAfterPackageAddsSteps() {
    val result = summary().resolveProgress(progress(isCourseCompleted = true))

    assertEquals(2, result.completedLessonCount)
    assertEquals(2, result.totalLessonCount)
    assertNull(result.nextLesson)
  }

  @Test
  fun fallsBackForLegacyManifestWithoutLessonSummaries() {
    val result = summary().copy(lessons = emptyList()).resolveProgress(progress())

    assertFalse(result.isKnown)
    assertEquals(0, result.totalLessonCount)
  }

  @Test
  fun prefersMostRecentIncompleteCourseForResume() {
    val records = listOf(
      progress().copy(courseId = "old"),
      progress(isCourseCompleted = true).copy(courseId = "completed"),
      progress().copy(courseId = "recent"),
    )

    assertEquals("recent", records.preferredResumeCourseId())
    assertEquals(
      "completed",
      records.filter(DynamicTutorialProgress::isCourseCompleted).preferredResumeCourseId(),
    )
  }

  private fun summary() = DynamicTutorialCourseSummary(
    courseId = "course",
    title = "课程",
    description = "",
    order = 1,
    estimatedMinutes = 10,
    lessons = listOf(
      DynamicTutorialLessonSummary("lesson-1", "第一课", listOf("read", "run")),
      DynamicTutorialLessonSummary("lesson-2", "第二课", listOf("edit", "run")),
    ),
  )

  private fun progress(
    completedSteps: List<DynamicTutorialCompletedStep> = emptyList(),
    isCourseCompleted: Boolean = false,
  ) = DynamicTutorialProgress(
    languageId = "java",
    npmPackageName = "tutorial-java",
    npmPackageVersion = "1.0.0",
    courseId = "course",
    lessonId = "lesson-1",
    stepId = "read",
    completedSteps = completedSteps,
    isCourseCompleted = isCourseCompleted,
  )
}
