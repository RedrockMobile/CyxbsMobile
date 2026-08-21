package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.internal.DynamicTutorialPackageLoader
import com.cyxbs.functions.code.tutorials.internal.LoadedDynamicTutorialPackage
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
  }
}
