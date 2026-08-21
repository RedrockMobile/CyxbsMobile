package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.internal.DynamicTutorialPackageLoader
import com.cyxbs.functions.code.tutorials.internal.LoadedDynamicTutorialPackage
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentBlock
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLesson
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialStep
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证教程 Catalog 的缓存、别名解析和独立 Service 生命周期。 */
class DynamicTutorialManagerTest {

  @Test
  fun cachesCatalogForConcurrentCallers() = runTest {
    val loader = FakeTutorialPackageLoader()
    val manager = DynamicTutorialManager(loader)

    val results = List(6) { async { manager.supportedTutorials() } }.awaitAll()

    assertEquals(1, loader.catalogLoadCount)
    results.forEach { assertSame(results.first(), it) }
  }

  @Test
  fun loadsTutorialByAliasAndClosesRuntime() = runTest {
    val loader = FakeTutorialPackageLoader()
    val session = DynamicTutorialManager(loader).load(" JAVA ")

    assertEquals("java", session.tutorial.languageId)
    assertEquals("0.1.0", session.npmPackageVersion)
    assertEquals("java", session.manifest().languageId)
    session.close()
    assertEquals(1, loader.service.closeCount)
  }

  @Test
  fun rejectsDuplicateCatalogIdentity() = runTest {
    val duplicate = validCatalog().copy(
      tutorials = validCatalog().tutorials + validInfo().copy(
        languageId = "kotlin",
        npmPackageName = "@cyxbs-mobile/tutorial-kotlin",
        aliases = listOf("java"),
      ),
    )
    val manager = DynamicTutorialManager(
      FakeTutorialPackageLoader(Json.encodeToString(duplicate)),
    )

    assertFailsWith<DynamicTutorialProtocolException> { manager.supportedTutorials() }
  }

  @Test
  fun replacesProgressForTheSameCourseAndKeepsOtherCourses() = runTest {
    val store = InMemoryDynamicTutorialProgressStore()
    val manager = DynamicTutorialManager(
      packageLoader = FakeTutorialPackageLoader(),
      progressStore = store,
    )
    manager.saveProgress(progress(courseId = "intro", stepId = "edit"))
    manager.saveProgress(progress(courseId = "collections", stepId = "list"))
    manager.saveProgress(progress(courseId = "intro", stepId = "run"))

    val saved = manager.savedProgress(" JAVA ")

    assertEquals(2, saved.size)
    assertEquals("run", saved.single { it.courseId == "intro" }.stepId)
    assertEquals(2, store.state.entries.size)
  }

  @Test
  fun restoresWorkspaceOnlyForTheSamePackageVersion() {
    val course = resumableCourse()
    val progress = progress(
      stepId = "run",
      workspace = listOf(DynamicTutorialSourceFile("Main.java", "edited")),
      activeFilePath = "Main.java",
    )

    val sameVersion = course.resolveResumeState(progress, npmPackageVersion = "0.1.0")
    val newerVersion = course.resolveResumeState(progress, npmPackageVersion = "0.2.0")

    assertTrue(sameVersion.restoredWorkspace)
    assertEquals("edited", sameVersion.workspace.single().source)
    assertFalse(newerVersion.restoredWorkspace)
    assertEquals("initial", newerVersion.workspace.single().source)
    assertEquals(1, newerVersion.stepIndex)
  }

  @Test
  fun ignoresRemovedStepsAndMovesToTheFirstIncompleteLesson() {
    val course = resumableCourse()
    val progress = progress(
      lessonId = "removed-lesson",
      stepId = "removed-step",
      completedSteps = listOf(
        DynamicTutorialCompletedStep("basics", "edit"),
        DynamicTutorialCompletedStep("basics", "removed-step"),
      ),
    )

    val resumed = course.resolveResumeState(progress, npmPackageVersion = "0.2.0")

    assertEquals("basics", resumed.lesson.lessonId)
    assertEquals(1, resumed.stepIndex)
    assertEquals(setOf(DynamicTutorialCompletedStep("basics", "edit")), resumed.completedSteps)
  }

  /** 为 Manager 测试提供可计数的内存加载器。 */
  private class FakeTutorialPackageLoader(
    private val catalogJson: String = Json.encodeToString(validCatalog()),
    val service: FakeTutorialService = FakeTutorialService(),
  ) : DynamicTutorialPackageLoader {
    var catalogLoadCount = 0

    override suspend fun loadCatalog(): String {
      catalogLoadCount++
      return catalogJson
    }

    override suspend fun loadTutorial(packageName: String): LoadedDynamicTutorialPackage {
      assertEquals("@cyxbs-mobile/tutorial-java", packageName)
      return LoadedDynamicTutorialPackage(service, "0.1.0")
    }
  }

  /** 最小教程 Service，用于验证 Manager 不改写业务调用。 */
  private class FakeTutorialService : DynamicTutorialService {
    var closeCount = 0

    override suspend fun manifest(): DynamicTutorialManifest {
      return DynamicTutorialManifest(
        languageId = "java",
        courses = listOf(DynamicTutorialCourseSummary("intro", "入门", "", 0, 1)),
      )
    }

    override suspend fun course(courseId: String): DynamicTutorialCourse? = null

    override suspend fun evaluate(
      request: DynamicTutorialEvaluationRequest,
    ): DynamicTutorialEvaluationResult = DynamicTutorialEvaluationResult(false)

    override suspend fun close() {
      closeCount++
    }
  }

  private companion object {
    /** 返回字段合法且带 `java` 别名的教程定义。 */
    fun validInfo() = DynamicTutorialInfo(
      languageId = "java",
      displayName = "Java",
      npmPackageName = "@cyxbs-mobile/tutorial-java",
      aliases = listOf("jdk"),
    )

    /** 返回只有一个语言教程的合法 Catalog。 */
    fun validCatalog() = DynamicTutorialCatalog(listOf(validInfo()))

    /** 构造一条可由 Manager 校验并持久化的最小课程进度。 */
    fun progress(
      courseId: String = "intro",
      lessonId: String = "basics",
      stepId: String,
      completedSteps: List<DynamicTutorialCompletedStep> = emptyList(),
      workspace: List<DynamicTutorialSourceFile> = emptyList(),
      activeFilePath: String? = null,
    ) = DynamicTutorialProgress(
      languageId = "java",
      npmPackageName = "@cyxbs-mobile/tutorial-java",
      npmPackageVersion = "0.1.0",
      courseId = courseId,
      lessonId = lessonId,
      stepId = stepId,
      completedSteps = completedSteps,
      workspace = workspace,
      activeFilePath = activeFilePath,
    )

    /** 提供两个稳定步骤的课程，用于验证版本升级时的恢复策略。 */
    fun resumableCourse(): DynamicTutorialCourse {
      val content = listOf(DynamicTutorialContentBlock(DynamicTutorialContentKind.PARAGRAPH, "text"))
      return DynamicTutorialCourse(
        summary = DynamicTutorialCourseSummary("intro", "入门", "", 0, 1),
        lessons = listOf(
          DynamicTutorialLesson(
            lessonId = "basics",
            title = "基础",
            description = "",
            initialFiles = listOf(DynamicTutorialSourceFile("Main.java", "initial")),
            activeFilePath = "Main.java",
            steps = listOf(
              DynamicTutorialStep("edit", "编辑", content),
              DynamicTutorialStep("run", "运行", content),
            ),
          ),
        ),
      )
    }
  }
}
