package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentBlock
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLesson
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLessonSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialStep
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证教程会话只向编辑器暴露经过边界校验的 npm 包数据。 */
class DynamicTutorialSessionTest {

  @Test
  fun validatesAndCachesManifestAndCourse() = runTest {
    val service = FakeService()
    val session = session(service)

    repeat(2) { session.manifest().getOrThrow() }
    repeat(2) { session.course("basics").getOrThrow() }
    assertNull(session.course("unknown").getOrThrow())

    assertEquals(1, service.manifestCalls)
    assertEquals(1, service.courseCalls)
    session.close()
    session.close()
    assertEquals(1, service.closeCalls)
  }

  @Test
  fun rejectsManifestWithMismatchedLanguageOrDuplicateCourse() = runTest {
    val mismatched = FakeService(manifest = validManifest().copy(languageId = "kotlin"))
    assertFailsWith<DynamicTutorialProtocolException> { session(mismatched).manifest().getOrThrow() }

    val summary = validSummary()
    val duplicate = FakeService(
      manifest = DynamicTutorialManifest("java", listOf(summary, summary)),
    )
    assertFailsWith<DynamicTutorialProtocolException> { session(duplicate).manifest().getOrThrow() }
  }

  @Test
  fun rejectsMissingActiveFileAndMismatchedCourseSummary() = runTest {
    val missingActiveFile = validCourse().copy(
      lessons = listOf(validLesson().copy(activeFilePath = "Missing.java")),
    )
    assertFailsWith<DynamicTutorialProtocolException> {
      session(FakeService(course = missingActiveFile)).course("basics").getOrThrow()
    }

    val mismatchedSummary = validCourse().copy(
      summary = validSummary().copy(title = "不同标题"),
    )
    assertFailsWith<DynamicTutorialProtocolException> {
      session(FakeService(course = mismatchedSummary)).course("basics").getOrThrow()
    }
  }

  @Test
  fun rejectsLessonSummaryThatDoesNotMatchCourseContent() = runTest {
    val summary = validSummary().copy(
      lessons = listOf(DynamicTutorialLessonSummary("hello", "Hello Java", listOf("other"))),
    )
    val service = FakeService(
      manifest = DynamicTutorialManifest("java", listOf(summary)),
      course = DynamicTutorialCourse(summary, listOf(validLesson())),
    )

    assertFailsWith<DynamicTutorialProtocolException> { session(service).course("basics").getOrThrow() }
  }

  @Test
  fun rejectsUnsafeSourcePathAndOversizedEvaluationFeedback() = runTest {
    val unsafeCourse = validCourse().copy(
      lessons = listOf(
        validLesson().copy(
          initialFiles = listOf(DynamicTutorialSourceFile("../Main.java", "class Main {}")),
          activeFilePath = "../Main.java",
        ),
      ),
    )
    assertFailsWith<DynamicTutorialProtocolException> {
      session(FakeService(course = unsafeCourse)).course("basics").getOrThrow()
    }

    val oversizedFeedback = FakeService(
      evaluation = DynamicTutorialEvaluationResult(false, "x".repeat(64 * 1024 + 1)),
    )
    assertFailsWith<DynamicTutorialProtocolException> {
      session(oversizedFeedback).evaluate(evaluationRequest()).getOrThrow()
    }
  }

  @Test
  fun rejectsUseAfterClose() = runTest {
    val session = session(FakeService())
    session.close()

    assertFailsWith<IllegalStateException> { session.manifest().getOrThrow() }
    assertFailsWith<IllegalStateException> { session.evaluate(evaluationRequest()).getOrThrow() }
  }

  /** 进度读写和单课程清理必须委托给 npm Service，Session 只负责协议边界。 */
  @Test
  fun delegatesProgressPersistenceToTutorialService() = runTest {
    val service = FakeService()
    val session = session(service)
    val intro = progress(courseId = "basics", stepId = "read")
    val collections = progress(courseId = "collections", stepId = "list")

    session.saveProgress(intro).getOrThrow()
    session.saveProgress(collections).getOrThrow()
    session.saveProgress(intro.copy(stepId = "run")).getOrThrow()
    assertEquals(
      listOf("collections", "basics"),
      session.savedProgress().getOrThrow().map { it.courseId },
    )
    assertEquals("run", session.savedProgress().getOrThrow().last().stepId)

    session.clearCourseProgress("basics").getOrThrow()
    assertEquals(listOf("collections"), session.savedProgress().getOrThrow().map { it.courseId })
    session.clearProgress().getOrThrow()
    assertTrue(session.savedProgress().getOrThrow().isEmpty())

    val migratedSession = session(
      FakeService(initialProgress = listOf(intro.copy(npmPackageVersion = "legacy-version"))),
    )
    assertEquals(
      "0.1.0",
      migratedSession.savedProgress().getOrThrow().single().npmPackageVersion,
    )
  }

  /** npm 包返回其他包身份或重复课程时必须在进入编辑器前失败。 */
  @Test
  fun rejectsInvalidProgressReturnedByPackage() = runTest {
    val mismatched = progress(courseId = "basics", stepId = "read")
      .copy(npmPackageName = "@cyxbs-mobile/tutorial-other")
    assertFailsWith<DynamicTutorialProtocolException> {
      session(FakeService(initialProgress = listOf(mismatched))).savedProgress().getOrThrow()
    }

    val duplicate = progress(courseId = "basics", stepId = "read")
    assertFailsWith<DynamicTutorialProtocolException> {
      session(FakeService(initialProgress = listOf(duplicate, duplicate.copy(stepId = "run"))))
        .savedProgress().getOrThrow()
    }

    val unsafeWorkspace = duplicate.copy(
      workspace = listOf(DynamicTutorialSourceFile("../Main.java", "class Main {}")),
      activeFilePath = "../Main.java",
    )
    assertFailsWith<DynamicTutorialProtocolException> {
      session(FakeService(initialProgress = listOf(unsafeWorkspace))).savedProgress().getOrThrow()
    }
  }

  /** 可计数的最小动态教程服务。 */
  private class FakeService(
    private val manifest: DynamicTutorialManifest = validManifest(),
    private val course: DynamicTutorialCourse? = validCourse(),
    private val evaluation: DynamicTutorialEvaluationResult = DynamicTutorialEvaluationResult(true),
    initialProgress: List<DynamicTutorialProgress> = emptyList(),
  ) : DynamicTutorialService {
    var manifestCalls = 0
    var courseCalls = 0
    var closeCalls = 0
    private val progress = initialProgress.toMutableList()

    override suspend fun manifest(): NpmJsResult<DynamicTutorialManifest> {
      manifestCalls++
      return NpmJsResult.success(manifest)
    }

    override suspend fun course(courseId: String): NpmJsResult<DynamicTutorialCourse?> {
      courseCalls++
      return NpmJsResult.success(course)
    }

    override suspend fun evaluate(
      request: DynamicTutorialEvaluationRequest,
    ): NpmJsResult<DynamicTutorialEvaluationResult> = NpmJsResult.success(evaluation)

    override suspend fun savedProgress(): NpmJsResult<List<DynamicTutorialProgress>> =
      NpmJsResult.success(progress.toList())

    override suspend fun saveProgress(progress: DynamicTutorialProgress): NpmJsResult<Unit> {
      this.progress.removeAll { it.courseId == progress.courseId }
      this.progress += progress
      return NpmJsResult.success(Unit)
    }

    override suspend fun clearProgress(): NpmJsResult<Unit> {
      progress.clear()
      return NpmJsResult.success(Unit)
    }

    override suspend fun clearCourseProgress(courseId: String): NpmJsResult<Unit> {
      progress.removeAll { it.courseId == courseId }
      return NpmJsResult.success(Unit)
    }

    override suspend fun close(): NpmJsResult<Unit> {
      closeCalls++
      return NpmJsResult.success(Unit)
    }
  }

  private companion object {
    /** 创建与 Java Catalog 身份一致的会话。 */
    fun session(service: DynamicTutorialService) = DynamicTutorialSession(
      tutorial = DynamicTutorialInfo(
        languageId = "java",
        displayName = "Java",
        npmPackageName = "@cyxbs-mobile/tutorial-java",
      ),
      npmPackageVersion = "0.1.0",
      service = service,
    )

    /** 返回只有一门课程的合法 Manifest。 */
    fun validManifest() = DynamicTutorialManifest("java", listOf(validSummary()))

    /** 返回最小合法课程摘要。 */
    fun validSummary() = DynamicTutorialCourseSummary(
      courseId = "basics",
      title = "Java 起步",
      description = "运行第一段程序",
      order = 0,
      estimatedMinutes = 5,
    )

    /** 返回带单文件和单步骤的合法课时。 */
    fun validLesson() = DynamicTutorialLesson(
      lessonId = "hello",
      title = "Hello Java",
      description = "",
      initialFiles = listOf(DynamicTutorialSourceFile("src/Main.java", "class Main {}")),
      activeFilePath = "src/Main.java",
      steps = listOf(
        DynamicTutorialStep(
          stepId = "read",
          title = "阅读代码",
          content = listOf(
            DynamicTutorialContentBlock(DynamicTutorialContentKind.PARAGRAPH, "阅读 Main.java"),
          ),
        ),
      ),
    )

    /** 返回与 Manifest 完全一致的课程正文。 */
    fun validCourse() = DynamicTutorialCourse(validSummary(), listOf(validLesson()))

    /** 返回校验反馈所需的最小请求。 */
    fun evaluationRequest() = DynamicTutorialEvaluationRequest(
      courseId = "basics",
      lessonId = "hello",
      stepId = "read",
      workspace = listOf(DynamicTutorialSourceFile("src/Main.java", "class Main {}")),
    )

    /** 返回与测试会话身份匹配的最小进度。 */
    fun progress(courseId: String, stepId: String) = DynamicTutorialProgress(
      languageId = "java",
      npmPackageName = "@cyxbs-mobile/tutorial-java",
      npmPackageVersion = "0.1.0",
      courseId = courseId,
      lessonId = "hello",
      stepId = stepId,
    )
  }
}
