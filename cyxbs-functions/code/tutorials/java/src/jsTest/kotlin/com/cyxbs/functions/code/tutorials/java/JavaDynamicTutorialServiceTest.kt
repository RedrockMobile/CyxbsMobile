package com.cyxbs.functions.code.tutorials.java

import com.cyxbs.functions.code.language.java.JavaDynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
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
      listOf(
        "java-getting-started",
        "java-control-flow",
        "java-object-oriented",
        "java-generics-collections",
      ),
      manifest.courses.map { it.courseId },
    )
    assertEquals(
      listOf("java-object-oriented"),
      manifest.courses.last().prerequisiteCourseIds,
    )
    manifest.courses.forEach { summary ->
      val course = assertNotNull(JavaDynamicTutorialService.course(summary.courseId))
      assertEquals(course.lessons.map { it.lessonId }, summary.lessons.map { it.lessonId })
      assertEquals(
        course.lessons.map { lesson -> lesson.steps.map { it.stepId } },
        summary.lessons.map { it.stepIds },
      )
    }
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

  @Test
  fun exposesMultipleIndependentCollectionLessons() = runTest {
    val course = assertNotNull(JavaDynamicTutorialService.course("java-generics-collections"))

    assertEquals(listOf("typed-list", "score-map"), course.lessons.map { it.lessonId })
    assertEquals("src/CourseScores.java", course.lessons.last().activeFilePath)
  }

  @Test
  fun acceptsAnyAdditionalCourseName() = runTest {
    val course = assertNotNull(JavaDynamicTutorialService.course("java-generics-collections"))
    val lesson = course.lessons.first { it.lessonId == "typed-list" }
    val request = DynamicTutorialEvaluationRequest(
      courseId = course.summary.courseId,
      lessonId = lesson.lessonId,
      stepId = "add-course",
      workspace = lesson.initialFiles,
    )

    assertFalse(JavaDynamicTutorialService.evaluate(request).completed)
    val customizedWorkspace = lesson.initialFiles.map { file ->
      if (file.path != lesson.activeFilePath) {
        file
      } else {
        file.copy(
          source = file.source.replace(
            "courses.add(\"集合\");",
            "courses.add(\"集合\");\n        courses.add(\"数据结构\");",
          ),
        )
      }
    }
    assertTrue(
      JavaDynamicTutorialService.evaluate(request.copy(workspace = customizedWorkspace)).completed,
    )
  }

  @Test
  fun exposesMultiFileObjectAndInheritanceLessons() = runTest {
    val course = assertNotNull(JavaDynamicTutorialService.course("java-object-oriented"))

    assertEquals(
      listOf("student-object", "inheritance-override"),
      course.lessons.map { it.lessonId },
    )
    assertEquals(
      listOf("src/Animal.java", "src/Dog.java", "src/AnimalMain.java"),
      course.lessons.last().initialFiles.map { it.path },
    )
    assertTrue(
      JavaDynamicTutorialService.evaluate(
        DynamicTutorialEvaluationRequest(
          courseId = course.summary.courseId,
          lessonId = "inheritance-override",
          stepId = "run-override",
          workspace = course.lessons.last().initialFiles,
          runExecuted = true,
          standardOutput = "旺财：汪\n",
        ),
      ).completed,
    )
  }

  @Test
  fun allTutorialExamplesCompileWithThePublishedJavaLanguageService() = runTest {
    JavaDynamicTutorialService.manifest().courses.forEach { summary ->
      val course = assertNotNull(JavaDynamicTutorialService.course(summary.courseId))
      course.lessons.forEach { lesson ->
        val activeSource = lesson.initialFiles.single { it.path == lesson.activeFilePath }.source
        val entryPosition = activeSource.indexOf("public static void main")
        assertTrue(entryPosition >= 0, "${lesson.lessonId} 缺少 public static void main 入口。")
        val result = JavaDynamicLanguageService.compile(
          DynamicCompilationRequest(
            workspace = DynamicLanguageWorkspace(
              lesson.initialFiles.map { sourceFile ->
                DynamicSourceFile(sourceFile.path, sourceFile.source)
              },
            ),
            entry = DynamicProgramEntry(
              filePath = lesson.activeFilePath,
              position = entryPosition,
            ),
          ),
        )

        assertNotNull(
          result.program,
          "${summary.courseId}/${lesson.lessonId} 编译失败：" +
            result.diagnostics.joinToString { it.message },
        )
      }
    }
  }
}
