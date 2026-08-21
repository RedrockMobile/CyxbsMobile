package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证动态课程路径不会绕过前置课程约束。 */
class DynamicTutorialCourseAvailabilityTest {

  @Test
  fun resolvesAvailableInProgressCompletedAndLockedCourses() {
    val manifest = manifest()

    val availability = manifest.resolveCourseAvailability(
      completedCourseIds = setOf("basics"),
      startedCourseIds = setOf("basics", "control"),
    )

    assertEquals(
      listOf(
        DynamicTutorialCourseState.COMPLETED,
        DynamicTutorialCourseState.IN_PROGRESS,
        DynamicTutorialCourseState.LOCKED,
      ),
      availability.map(DynamicTutorialCourseAvailability::state),
    )
    assertEquals(listOf("control"), availability.last().missingPrerequisiteCourseIds)
  }

  @Test
  fun keepsUnknownAndCircularPrerequisitesLocked() {
    val manifest = DynamicTutorialManifest(
      languageId = "java",
      courses = listOf(
        summary("first", prerequisites = listOf("second")),
        summary("second", prerequisites = listOf("first")),
        summary("broken", prerequisites = listOf("removed-course")),
      ),
    )

    val availability = manifest.resolveCourseAvailability(emptySet())

    assertEquals(
      listOf(
        DynamicTutorialCourseState.LOCKED,
        DynamicTutorialCourseState.LOCKED,
        DynamicTutorialCourseState.LOCKED,
      ),
      availability.map(DynamicTutorialCourseAvailability::state),
    )
    assertEquals(listOf("removed-course"), availability.last().missingPrerequisiteCourseIds)
  }

  @Test
  fun keepsCompletedCourseAvailableAfterManifestAddsPrerequisite() {
    val manifest = DynamicTutorialManifest(
      languageId = "java",
      courses = listOf(summary("completed", prerequisites = listOf("new-prerequisite"))),
    )

    val availability = manifest.resolveCourseAvailability(setOf("completed"))

    assertEquals(DynamicTutorialCourseState.COMPLETED, availability.single().state)
    assertEquals(listOf("new-prerequisite"), availability.single().missingPrerequisiteCourseIds)
  }

  private companion object {
    /** 构造三段式 Java 学习路径。 */
    fun manifest() = DynamicTutorialManifest(
      languageId = "java",
      courses = listOf(
        summary("basics"),
        summary("control", prerequisites = listOf("basics")),
        summary("collections", prerequisites = listOf("control")),
      ),
    )

    /** 构造只保留稳定身份和前置关系的课程摘要。 */
    fun summary(
      courseId: String,
      prerequisites: List<String> = emptyList(),
    ) = DynamicTutorialCourseSummary(
      courseId = courseId,
      title = courseId,
      description = "",
      order = 0,
      estimatedMinutes = 1,
      prerequisiteCourseIds = prerequisites,
    )
  }
}
