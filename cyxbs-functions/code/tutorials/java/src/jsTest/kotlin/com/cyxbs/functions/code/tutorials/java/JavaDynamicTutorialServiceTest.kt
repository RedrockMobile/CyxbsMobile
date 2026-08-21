package com.cyxbs.functions.code.tutorials.java

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证首个 Java 教程包的目录、正文和运行结果校验保持一致。 */
class JavaDynamicTutorialServiceTest {

  @Test
  fun exposesOrderedCoursePath() = runTest {
    val manifest = JavaDynamicTutorialService.manifest()

    assertEquals("java", manifest.languageId)
    assertEquals(
      listOf("java-getting-started", "java-control-flow", "java-generics-collections"),
      manifest.courses.map { it.courseId },
    )
    assertEquals(
      listOf("java-control-flow"),
      manifest.courses.last().prerequisiteCourseIds,
    )
  }

  @Test
  fun evaluatesHelloWorldOutput() = runTest {
    val course = assertNotNull(JavaDynamicTutorialService.course("java-getting-started"))
    val lesson = course.lessons.single()
    val request = DynamicTutorialEvaluationRequest(
      courseId = course.summary.courseId,
      lessonId = lesson.lessonId,
      stepId = "run-main",
      workspace = lesson.initialFiles,
      runExecuted = true,
      standardOutput = "Hello, Java!\n",
    )

    assertTrue(JavaDynamicTutorialService.evaluate(request).completed)
    assertFalse(
      JavaDynamicTutorialService.evaluate(request.copy(standardOutput = "")).completed,
    )
  }
}
