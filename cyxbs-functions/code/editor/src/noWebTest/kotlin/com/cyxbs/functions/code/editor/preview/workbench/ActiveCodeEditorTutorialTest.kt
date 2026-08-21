package com.cyxbs.functions.code.editor.preview.workbench

import com.cyxbs.functions.code.tutorials.DynamicTutorialCompletedStep
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentBlock
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLesson
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 教程 UI 状态测试只验证端上步骤导航，不复制 npm 包内的语言校验规则。 */
class ActiveCodeEditorTutorialTest {

  @Test
  fun advancesAndCompletesLastStep() {
    val initial = tutorialState()

    val second = initial.advance()
    val completed = second.advance()

    assertEquals(1, second.stepIndex)
    assertFalse(second.isCompleted)
    assertEquals("run", second.step.stepId)
    assertEquals(1, completed.stepIndex)
    assertTrue(completed.isCompleted)
    assertEquals(
      setOf(
        DynamicTutorialCompletedStep("lesson", "read"),
        DynamicTutorialCompletedStep("lesson", "run"),
      ),
      completed.completedSteps,
    )
    assertEquals("已完成 Hello。", completed.feedback)
  }

  @Test
  fun previousClearsCompletionAndFeedback() {
    val completed = tutorialState().advance().advance()

    val previous = completed.previous()

    assertEquals(0, previous.stepIndex)
    assertFalse(previous.isCompleted)
    assertEquals(null, previous.feedback)
  }

  @Test
  fun completingOneLessonDoesNotCompleteAMultiLessonCourse() {
    val firstLessonState = tutorialState(includeSecondLesson = true)

    val completedLesson = firstLessonState.advance().advance()

    assertTrue(completedLesson.isCurrentLessonCompleted)
    assertFalse(completedLesson.isCompleted)
  }

  /** 构造最小两步课时，避免测试依赖 Java 教程包的具体正文。 */
  private fun tutorialState(includeSecondLesson: Boolean = false): ActiveCodeEditorTutorial {
    val summary = DynamicTutorialCourseSummary(
      courseId = "course",
      title = "Course",
      description = "Test",
      order = 1,
      estimatedMinutes = 1,
    )
    val lesson = DynamicTutorialLesson(
      lessonId = "lesson",
      title = "Hello",
      description = "Test",
      initialFiles = emptyList(),
      activeFilePath = "Main.java",
      steps = listOf(step("read"), step("run")),
    )
    val lessons = if (includeSecondLesson) {
      listOf(
        lesson,
        lesson.copy(
          lessonId = "lesson-2",
          title = "Second",
          steps = listOf(step("second")),
        ),
      )
    } else {
      listOf(lesson)
    }
    return ActiveCodeEditorTutorial(
      course = DynamicTutorialCourse(summary = summary, lessons = lessons),
      lesson = lesson,
    )
  }

  /** 创建只用于导航测试的手动步骤。 */
  private fun step(id: String): DynamicTutorialStep {
    return DynamicTutorialStep(
      stepId = id,
      title = id,
      content = listOf(
        DynamicTutorialContentBlock(
          kind = DynamicTutorialContentKind.PARAGRAPH,
          text = id,
        ),
      ),
    )
  }
}
