package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.internal.DynamicLanguagePackageLoader
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class DynamicLanguageManagerTest {

  /** 构造默认 Manager 不应提前解析 QuickJS Provider 或创建 npm Service Loader。 */
  @Test
  fun defaultManagerConstructionDoesNotInitializeRuntime() {
    assertNotNull(DynamicLanguageManager())
  }

  /** 并发发现只读取一次 Catalog JSON，并复用同一份校验后快照。 */
  @Test
  fun supportedLanguagesCachesCatalogJson() = runTest {
    val loader = FakePackageLoader(catalogJson = Json.encodeToString(validCatalog()))
    val manager = DynamicLanguageManager(loader)

    val results = List(8) {
      async { manager.supportedLanguages() }
    }.awaitAll()

    assertEquals(1, loader.catalogLoadCount)
    results.forEach { assertSame(results.first(), it) }
  }

  /** 语言别名忽略大小写和首尾空白，并直接返回 npm Loader 创建的 Service。 */
  @Test
  fun loadByAliasReturnsService() = runTest {
    val languageService = FakeLanguageService(
      highlightResult = listOf(
        DynamicHighlightSpan(from = 0, to = 3, styleIds = listOf("keyword")),
      ),
      completionResult = DynamicCompletionResult(
        from = 0,
        to = 3,
        options = listOf(DynamicCompletionItem(label = "const")),
      ),
    )
    val loader = FakePackageLoader(
      catalogJson = Json.encodeToString(validCatalog()),
      languageService = languageService,
    )
    val service = DynamicLanguageManager(loader).load(" JS ")
    val workspace = DynamicLanguageWorkspace(
      listOf(DynamicSourceFile(path = "main.js", source = "let")),
    )

    assertSame(languageService, service)
    assertEquals(1, loader.languageLoadCount)
    assertEquals(
      listOf("keyword"),
      service.highlight(workspace, "main.js").spans.single().styleIds,
    )
    assertEquals(
      "const",
      service.complete(
        DynamicLanguageWorkspace(listOf(DynamicSourceFile("main.js", "con"))),
        "main.js",
        3,
        false,
      )?.options?.single()?.label,
    )

    service.close()
    assertEquals(1, languageService.closeCount)
  }

  /** Catalog 中 ID、别名之间发生碰撞时拒绝缓存。 */
  @Test
  fun duplicateCatalogIdentityIsRejected() = runTest {
    val catalogJson = Json.encodeToString(
      DynamicLanguageCatalog(
        languages = listOf(
          validLanguageInfo(),
          validLanguageInfo().copy(
            languageId = "typescript",
            npmPackageName = "@cyxbs-mobile/language-typescript",
            aliases = listOf("js"),
          ),
        ),
      ),
    )
    val manager = DynamicLanguageManager(FakePackageLoader(catalogJson))

    assertFailsWith<DynamicLanguageProtocolException> {
      manager.supportedLanguages()
    }
  }

  /** 无法按当前协议解析的 Catalog JSON 转换为稳定的领域协议异常。 */
  @Test
  fun malformedCatalogJsonIsRejected() = runTest {
    val manager = DynamicLanguageManager(FakePackageLoader("{}"))

    assertFailsWith<DynamicLanguageProtocolException> {
      manager.supportedLanguages()
    }
  }

  /** Catalog 新增根字段或语言字段时，旧客户端忽略未知内容并继续读取已有基础字段。 */
  @Test
  fun additiveUnknownCatalogFieldsAreIgnored() = runTest {
    val catalogJson = """
      {
        "generatedAt": "2026-08-11T12:00:00+08:00",
        "languages": [
          {
            "languageId": "javascript",
            "displayName": "JavaScript",
            "npmPackageName": "@cyxbs-mobile/language-javascript",
            "semanticFeatures": {
              "rename": true
            }
          }
        ]
      }
    """.trimIndent()

    val languages = DynamicLanguageManager(FakePackageLoader(catalogJson)).supportedLanguages()

    assertEquals(listOf("javascript"), languages.map(DynamicLanguageInfo::languageId))
    assertEquals("@cyxbs-mobile/language-javascript", languages.single().npmPackageName)
  }

  /** 为 Manager 测试提供可计数且不访问网络的包加载边界。 */
  private class FakePackageLoader(
    private val catalogJson: String,
    private val languageService: DynamicLanguageService = FakeLanguageService(),
  ) : DynamicLanguagePackageLoader {
    var catalogLoadCount = 0
    var languageLoadCount = 0

    override suspend fun loadCatalog(): String {
      catalogLoadCount += 1
      return catalogJson
    }

    override suspend fun loadLanguage(packageName: String): DynamicLanguageService {
      assertEquals("@cyxbs-mobile/language-javascript", packageName)
      languageLoadCount += 1
      return languageService
    }
  }

  /** 可注入分析返回值并观察关闭次数的语言 Service 测试实现。 */
  private class FakeLanguageService(
    private val highlightResult: List<DynamicHighlightSpan> = emptyList(),
    private val completionResult: DynamicCompletionResult? = null,
  ) : DynamicLanguageService {
    var closeCount = 0

    /** 测试替身不维护语法树，仅返回可预测的完整解析指标。 */
    override suspend fun highlight(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
    ): DynamicHighlightResult {
      val source = workspace.files.first { file -> file.path == filePath }.source
      return DynamicHighlightResult(
        spans = highlightResult,
        metrics = DynamicHighlightMetrics(
          cacheMode = DynamicHighlightCacheMode.FULL,
          sourceLength = source.length,
          parseMicroseconds = 0,
          collectMicroseconds = 0,
        ),
      )
    }

    override suspend fun complete(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
      explicit: Boolean,
    ): DynamicCompletionResult? = completionResult

    override suspend fun definition(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
    ): DynamicSymbolDefinition? = null

    override suspend fun references(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
    ): DynamicSymbolReferencesResult? = null

    override suspend fun rename(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
      newName: String,
    ): DynamicRenameResult? = null

    override suspend fun close() {
      closeCount += 1
    }
  }

  private companion object {
    /** 创建包含 JavaScript 的最小合法 Catalog。 */
    fun validCatalog(): DynamicLanguageCatalog {
      return DynamicLanguageCatalog(
        languages = listOf(validLanguageInfo()),
      )
    }

    /** 创建与正式 Catalog 约束一致的 JavaScript 定义。 */
    fun validLanguageInfo(): DynamicLanguageInfo {
      return DynamicLanguageInfo(
        languageId = "javascript",
        displayName = "JavaScript",
        npmPackageName = "@cyxbs-mobile/language-javascript",
        aliases = listOf("js"),
        fileExtensions = listOf("js", "mjs", "cjs"),
      )
    }

  }
}
