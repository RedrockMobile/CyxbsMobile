package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsModuleNormalizer
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.js.runtime.evaluate
import com.cyxbs.functions.code.npm.model.NpmLockedPackage
import com.cyxbs.functions.code.npm.model.NpmReleaseSnapshot
import com.cyxbs.functions.code.npm.storage.OkioNpmPackageArchiveStore
import com.cyxbs.functions.code.npm.transport.KtorNpmHttpTransport
import com.g985892345.provider.cyxbsmobile.cyxbsfunctions.code.js.quickjs.QuickjsKtProviderInitializer
import com.g985892345.provider.testing.withKtProviderTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** 通过固定公开 npm 坐标验证 registry 下载、tgz 解包、Module 解析和 QuickJS 执行。 */
class NpmQuickJsIntegrationTest {

  /**
   * 默认测试集不访问公网；设置 `CYXBS_NPM_INTEGRATION_TEST=true` 后执行真实下载。
  */
  @Test
  fun downloadPublicPackageAndExecuteWithQuickJs() = withQuickJsRuntime { runtimeFactory ->
    if (System.getenv(INTEGRATION_TEST_ENV) != "true") return@withQuickJsRuntime

    val result = downloadAndExecute(
      runtimeFactory = runtimeFactory,
      snapshot = NpmReleaseSnapshot(
        releaseTime = RELEASE_TIME,
        entries = mapOf(ESCAPE_PACKAGE to "index.js"),
        urls = listOf(NPM_REGISTRY),
        packages = mapOf(
          ESCAPE_PACKAGE to NpmLockedPackage(
            version = ESCAPE_VERSION,
            integrity = ESCAPE_INTEGRITY,
          ),
        ),
      ),
      entryPackage = ESCAPE_PACKAGE,
      code = """
        import escapeStringRegexp from "$ESCAPE_PACKAGE";
        capture(escapeStringRegexp("How much $DOLLAR for a unicorn?"));
      """.trimIndent(),
    )

    assertEquals(listOf(ESCAPE_PACKAGE), result.packageNames)
    assertEquals("How much \\$DOLLAR for a unicorn\\?", result.captured)
  }

  /**
   * 验证入口包和其真实 npm 依赖均按快照下载，并通过裸包名 import 共同参与 QuickJS 执行。
  */
  @Test
  fun downloadPublicPackageWithDependencyAndExecuteWithQuickJs() = withQuickJsRuntime { runtimeFactory ->
    if (System.getenv(INTEGRATION_TEST_ENV) != "true") return@withQuickJsRuntime

    val result = downloadAndExecute(
      runtimeFactory = runtimeFactory,
      snapshot = NpmReleaseSnapshot(
        releaseTime = RELEASE_TIME,
        entries = mapOf(ONETIME_PACKAGE to "index.js"),
        urls = listOf(NPM_REGISTRY),
        packages = mapOf(
          ONETIME_PACKAGE to NpmLockedPackage(
            version = ONETIME_VERSION,
            dependencies = listOf(MIMIC_FN_PACKAGE),
            integrity = ONETIME_INTEGRITY,
          ),
          MIMIC_FN_PACKAGE to NpmLockedPackage(
            version = MIMIC_FN_VERSION,
            integrity = MIMIC_FN_INTEGRITY,
          ),
        ),
      ),
      entryPackage = ONETIME_PACKAGE,
      code = """
        import onetime from "$ONETIME_PACKAGE";
        let calls = 0;
        const callOnce = onetime(() => ++calls);
        capture(String(callOnce()) + ":" + String(callOnce()) + ":" + String(calls));
      """.trimIndent(),
    )

    assertEquals(listOf(MIMIC_FN_PACKAGE, ONETIME_PACKAGE), result.packageNames)
    assertEquals("1:1:1", result.captured)
  }

  /**
   * 在隔离的 KtProvider Registry 中加载 QuickJS 模块，再执行一条完整的 Runtime 集成测试。
   *
   * 测试结束后 `withKtProviderTest` 会恢复生产 Resolver，避免 Provider 注册泄漏到其他测试。
   */
  private fun withQuickJsRuntime(block: suspend (JsRuntimeFactory) -> Unit) {
    withKtProviderTest(QuickjsKtProviderInitializer) {
      runTest {
        val runtimeFactory = checkNotNull(JsRuntimeFactory.implOrNull()) {
          "QuickJS runtime provider is not installed for the integration test."
        }
        block(runtimeFactory)
      }
    }
  }

  /**
   * 使用全新缓存目录完成真实下载、解包、Module 加载和执行，并在返回前释放全部资源。
   */
  private suspend fun downloadAndExecute(
    runtimeFactory: JsRuntimeFactory,
    snapshot: NpmReleaseSnapshot,
    entryPackage: String,
    code: String,
  ): PublicPackageExecution {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-integration-${Random.nextLong().toString(16)}"
    val httpClient = HttpClient(OkHttp)
    try {
      val downloader = NpmPackageDownloader(
        transport = KtorNpmHttpTransport(httpClient),
        archiveStore = OkioNpmPackageArchiveStore(
          rootDirectory = root,
          fileSystem = fileSystem,
        ),
      )
      val prepared = downloader.prepareEntry(
        snapshot = snapshot,
        entryPackage = entryPackage,
      )
      val graph = NpmModuleGraphFactory(fileSystem = fileSystem).create(prepared)
      val loader = object : JsModuleLoader {
        override val normalizer = JsModuleNormalizer { baseName, requestedName ->
          graph.normalize(baseName = baseName, requestedName = requestedName)
        }

        override fun load(name: String): String? = graph.load(name)
      }
      val runtime = runtimeFactory.create(
        JsRuntimeOptions(
          moduleLoader = loader,
          allowBytecodeCache = false,
        ),
      )
      var captured: String? = null
      try {
        runtime.bindFunction("capture") { args ->
          captured = args.single() as String
          null
        }
        runtime.evaluate<Unit>(
          code = code,
          filename = "npm-integration-entry.js",
          asModule = true,
        )
      } finally {
        runtime.close()
      }
      return PublicPackageExecution(
        packageNames = prepared.archives.map { it.packageName },
        captured = captured,
      )
    } finally {
      httpClient.close()
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 真实 npm 闭包执行结果。 */
  private data class PublicPackageExecution(
    val packageNames: List<String>,
    val captured: String?,
  )

  private companion object {
    const val INTEGRATION_TEST_ENV = "CYXBS_NPM_INTEGRATION_TEST"
    const val RELEASE_TIME = "2026.08.03 12:01:10"
    const val NPM_REGISTRY = "https://registry.npmjs.org"
    const val DOLLAR = '$'

    const val ESCAPE_PACKAGE = "escape-string-regexp"
    const val ESCAPE_VERSION = "5.0.0"
    const val ESCAPE_INTEGRITY =
      "sha512-/veY75JbMK4j1yjvuUxuVsiS/hr/4iHs9FTT6cgTexxdE0Ly/glccBAkloH/DofkjRbZU3bnoj38mOmhkZ0lHw=="

    const val ONETIME_PACKAGE = "onetime"
    const val ONETIME_VERSION = "6.0.0"
    const val ONETIME_INTEGRITY =
      "sha512-1FlR+gjXK7X+AsAHso35MnyN5KqGwJRi/31ft6x0M194ht7S+rWAvd7PHss9xSKMzE0asv1pyIHaJYq+BbacAQ=="

    const val MIMIC_FN_PACKAGE = "mimic-fn"
    const val MIMIC_FN_VERSION = "4.0.0"
    const val MIMIC_FN_INTEGRITY =
      "sha512-vqiC06CuhBTUdZH+RYl8sFrL096vA45Ok5ISO6sE/Mr1jRbGH4Csnhi8f3wKVl7x8mO4Au7Ir9D3Oyv1VYMFJw=="
  }
}
