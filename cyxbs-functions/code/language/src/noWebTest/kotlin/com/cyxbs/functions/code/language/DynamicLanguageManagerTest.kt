package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.internal.DynamicLanguagePackageLoader
import com.cyxbs.functions.code.language.internal.LoadedDynamicLanguagePackage
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIconPath
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageProjectFile
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageProjectTemplate
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
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
import kotlin.test.assertTrue

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

  /** 语言别名忽略大小写和首尾空白，缓存代理应完整透传除图标外的 Service 能力。 */
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

    assertEquals(1, loader.languageLoadCount)
    assertEquals(0, languageService.fileIconCallCount)
    assertEquals(TEST_ICON, service.fileIcon().getOrThrow())
    assertEquals(TEST_ICON, service.fileIcon().getOrThrow())
    assertEquals(1, languageService.fileIconCallCount)
    assertEquals(
      listOf("keyword"),
      service.highlight(workspace, "main.js").getOrThrow().spans.single().styleIds,
    )
    assertEquals(
      "const",
      service.complete(
        DynamicLanguageWorkspace(listOf(DynamicSourceFile("main.js", "con"))),
        "main.js",
        3,
        false,
      ).getOrThrow()?.options?.single()?.label,
    )
    val runResult = service.run(
      DynamicProgramRunRequest(
        compilation = DynamicCompilationRequest(
          workspace = workspace,
          entry = DynamicProgramEntry("main.js"),
        ),
      ),
    )
    assertEquals(false, runResult.executed)
    assertEquals("TEST_COMPILER_UNAVAILABLE", runResult.diagnostics.single().code)

    service.close()
    assertEquals(1, languageService.closeCount)
  }

  /** Session 的显式 compile 与 run 内部 compile 都会进入同一匿名化能力统计链。 */
  @Test
  fun compilationRecordsUnsupportedCapabilityStatistics() = runTest {
    val statistics = DynamicLanguageUnsupportedCapabilityStatistics.inMemory()
    val unsupported = DynamicCompilationDiagnostic(
      code = "JAVASCRIPT_COMMONJS_UNSUPPORTED",
      message = "CommonJS is not supported.",
      severity = DynamicCompilationDiagnosticSeverity.ERROR,
    )
    val loader = FakePackageLoader(
      catalogJson = Json.encodeToString(validCatalog()),
      languageService = FakeLanguageService(
        compilationDiagnostics = listOf(unsupported, unsupported),
      ),
      npmPackageVersion = "2.0.0",
    )
    val manager = DynamicLanguageManager(
      packageLoader = loader,
      unsupportedCapabilityStatistics = statistics,
    )
    val session = manager.load("js")
    val request = DynamicCompilationRequest(
      workspace = DynamicLanguageWorkspace(listOf(DynamicSourceFile("main.js", "require('x')"))),
      entry = DynamicProgramEntry("main.js"),
    )

    session.compile(request)
    session.run(DynamicProgramRunRequest(request))

    val entry = manager.unsupportedCapabilityStatistics(" JavaScript ").single()
    assertEquals("2.0.0", entry.npmPackageVersion)
    assertEquals(2, entry.affectedCompilationCount)
    assertEquals(4, entry.diagnosticOccurrenceCount)
    assertTrue(entry.diagnosticCode.endsWith("UNSUPPORTED"))
    manager.clearUnsupportedCapabilityStatistics("js")
    assertTrue(manager.unsupportedCapabilityStatistics().isEmpty())
    session.close()
  }

  /** 项目文件列表恢复图标时不能创建 Runtime 或加载语言 npm 包。 */
  @Test
  fun cachedIconsRestoreWithoutLoadingLanguageService() = runTest {
    val iconCache = DynamicLanguageIconCache.inMemory()
    iconCache.update(validLanguageInfo(), "1.0.0", TEST_ICON)
    val loader = FakePackageLoader(catalogJson = Json.encodeToString(validCatalog()))
    val manager = DynamicLanguageManager(loader, iconCache = iconCache)

    val icons = manager.cachedIcons()

    assertEquals(TEST_ICON, icons[validLanguageInfo()])
    assertEquals(0, loader.languageLoadCount)
  }

  /** 相同 npm 版本应复用持久图标，版本变化才重新调用 JS 并覆盖缓存。 */
  @Test
  fun languagePackageVersionInvalidatesPersistentIcon() = runTest {
    val language = validLanguageInfo()
    val iconCache = DynamicLanguageIconCache.inMemory()
    iconCache.update(language, "1.0.0", TEST_ICON)
    val unchangedService = FakeLanguageService(icon = UPDATED_TEST_ICON)
    val unchangedManager = DynamicLanguageManager(
      packageLoader = FakePackageLoader(
        catalogJson = Json.encodeToString(validCatalog()),
        languageService = unchangedService,
        npmPackageVersion = "1.0.0",
      ),
      iconCache = iconCache,
    )

    val unchangedProxy = unchangedManager.load("javascript")

    assertEquals(0, unchangedService.fileIconCallCount)
    assertEquals(TEST_ICON, unchangedProxy.fileIcon().getOrThrow())
    assertEquals(TEST_ICON, unchangedProxy.fileIcon().getOrThrow())
    unchangedProxy.close()
    assertEquals(TEST_ICON, unchangedManager.cachedIcons()[language])

    val updatedService = FakeLanguageService(icon = UPDATED_TEST_ICON)
    val updatedManager = DynamicLanguageManager(
      packageLoader = FakePackageLoader(
        catalogJson = Json.encodeToString(validCatalog()),
        languageService = updatedService,
        npmPackageVersion = "2.0.0",
      ),
      iconCache = iconCache,
    )

    val updatedProxy = updatedManager.load("javascript")

    assertEquals(0, updatedService.fileIconCallCount)
    assertEquals(UPDATED_TEST_ICON, updatedProxy.fileIcon().getOrThrow())
    assertEquals(UPDATED_TEST_ICON, updatedProxy.fileIcon().getOrThrow())
    updatedProxy.close()
    assertEquals(1, updatedService.fileIconCallCount)
    assertEquals(UPDATED_TEST_ICON, updatedManager.cachedIcons()[language])
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
    private val npmPackageVersion: String = "1.0.0",
  ) : DynamicLanguagePackageLoader {
    var catalogLoadCount = 0
    var languageLoadCount = 0

    override suspend fun loadCatalog(): String {
      catalogLoadCount += 1
      return catalogJson
    }

    override suspend fun loadLanguage(packageName: String): LoadedDynamicLanguagePackage {
      assertEquals("@cyxbs-mobile/language-javascript", packageName)
      languageLoadCount += 1
      return LoadedDynamicLanguagePackage(
        service = languageService,
        npmPackageVersion = npmPackageVersion,
      )
    }
  }

  /** 可注入分析返回值并观察关闭次数的语言 Service 测试实现。 */
  private class FakeLanguageService(
    private val highlightResult: List<DynamicHighlightSpan> = emptyList(),
    private val completionResult: DynamicCompletionResult? = null,
    private val icon: DynamicLanguageIcon = TEST_ICON,
    private val compilationDiagnostics: List<DynamicCompilationDiagnostic>? = null,
  ) : DynamicLanguageService {
    var closeCount = 0
    var fileIconCallCount = 0

    override suspend fun fileIcon(): NpmJsResult<DynamicLanguageIcon> {
      fileIconCallCount += 1
      return NpmJsResult.success(icon)
    }

    /** 测试替身提供最小模板，使协议新增方法保持显式实现。 */
    override suspend fun projectTemplate(): NpmJsResult<DynamicLanguageProjectTemplate> =
      NpmJsResult.success(
        DynamicLanguageProjectTemplate(
          defaultProjectName = "TestProject",
          activeFilePath = "main.test",
          sourceFiles = listOf(DynamicLanguageProjectFile("main.test", "test")),
        ),
      )

    /** 测试替身不暴露运行入口。 */
    override suspend fun runTargets(
      workspace: DynamicLanguageWorkspace,
      activeFilePath: String,
    ) = NpmJsResult.success(emptyList<com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget>())

    /** 测试替身未提供可执行程序，使用结构化编译诊断验证协议透传。 */
    override suspend fun compile(request: DynamicCompilationRequest): NpmJsResult<DynamicCompilationResult> {
      return NpmJsResult.success(DynamicCompilationResult(
        diagnostics = compilationDiagnostics ?: listOf(
          DynamicCompilationDiagnostic(
            code = "TEST_COMPILER_UNAVAILABLE",
            message = "The test language does not provide a compiler.",
            severity = DynamicCompilationDiagnosticSeverity.ERROR,
          ),
        ),
      ))
    }

    /** 测试替身不维护语法树，仅返回可预测的完整解析指标。 */
    override suspend fun highlight(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
    ): NpmJsResult<DynamicHighlightResult> {
      val source = workspace.files.first { file -> file.path == filePath }.source
      return NpmJsResult.success(DynamicHighlightResult(
        spans = highlightResult,
        metrics = DynamicHighlightMetrics(
          cacheMode = DynamicHighlightCacheMode.FULL,
          sourceLength = source.length,
          parseMicroseconds = 0,
          collectMicroseconds = 0,
        ),
      ))
    }

    override suspend fun complete(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
      explicit: Boolean,
    ): NpmJsResult<DynamicCompletionResult?> = NpmJsResult.success(completionResult)

    override suspend fun definition(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
    ): NpmJsResult<DynamicSymbolDefinition?> = NpmJsResult.success(null)

    override suspend fun references(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
    ): NpmJsResult<DynamicSymbolReferencesResult?> = NpmJsResult.success(null)

    override suspend fun rename(
      workspace: DynamicLanguageWorkspace,
      filePath: String,
      position: Int,
      newName: String,
    ): NpmJsResult<DynamicRenameResult?> = NpmJsResult.success(null)

    override suspend fun close(): NpmJsResult<Unit> {
      closeCount += 1
      return NpmJsResult.success(Unit)
    }
  }

  private companion object {
    val TEST_ICON = DynamicLanguageIcon(
      viewportWidth = 24F,
      viewportHeight = 24F,
      paths = listOf(DynamicLanguageIconPath("M0 0H24V24H0Z", "#F7DF1E")),
    )

    val UPDATED_TEST_ICON = DynamicLanguageIcon(
      viewportWidth = 32F,
      viewportHeight = 32F,
      paths = listOf(DynamicLanguageIconPath("M0 0H32V32H0Z", "#3776AB")),
    )

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
