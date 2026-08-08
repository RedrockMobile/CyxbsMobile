package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
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

  /** 默认测试集不访问公网；设置 `CYXBS_NPM_INTEGRATION_TEST=true` 后执行真实下载。 */
  @Test
  fun downloadPublicPackageAndExecuteWithQuickJs() = withQuickJsRuntime { runtimeFactory ->
    if (System.getenv(INTEGRATION_TEST_ENV) != "true") return@withQuickJsRuntime

    val result = downloadResolvedAndExecute(
      runtimeFactory = runtimeFactory,
      request = NpmEntryRequest(
        packageName = ESCAPE_PACKAGE,
        version = NpmEntryVersion.Exact(ESCAPE_VERSION),
      ),
      code = """
        import escapeStringRegexp from "$ESCAPE_PACKAGE";
        capture(escapeStringRegexp("How much $DOLLAR for a unicorn?"));
      """.trimIndent(),
    )

    assertEquals("How much \\$ for a unicorn\\?", result.captured)
  }

  /** 验证客户端递归解析真实 npm 依赖，并通过裸包名 import 共同参与 QuickJS 执行。 */
  @Test
  fun downloadPublicPackageWithDependencyAndExecuteWithQuickJs() = withQuickJsRuntime { runtimeFactory ->
    if (System.getenv(INTEGRATION_TEST_ENV) != "true") return@withQuickJsRuntime

    val result = downloadResolvedAndExecute(
      runtimeFactory = runtimeFactory,
      request = NpmEntryRequest(
        packageName = ONETIME_PACKAGE,
        version = NpmEntryVersion.Exact(ONETIME_VERSION),
      ),
      code = """
        import onetime from "$ONETIME_PACKAGE";
        let calls = 0;
        const once = onetime(() => ++calls);
        capture(once() + ":" + once() + ":" + calls);
      """.trimIndent(),
    )

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

  /** 使用客户端 registry 解析器递归准备真实依赖闭包，并通过宿主函数验证执行结果。 */
  private suspend fun downloadResolvedAndExecute(
    runtimeFactory: JsRuntimeFactory,
    request: NpmEntryRequest,
    code: String,
  ): PublicPackageExecution {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-resolver-integration-${Random.nextLong().toString(16)}"
    val httpClient = HttpClient(OkHttp)
    try {
      val pool = NpmPackagePool(
        transport = KtorNpmHttpTransport(httpClient),
        rootDirectory = root,
        fileSystem = fileSystem,
      )
      var captured: String? = null
      NpmJsExecutor(
        packagePool = pool,
        moduleGraphFactory = NpmModuleGraphFactory(fileSystem = fileSystem),
      ).executeValue(
        request = request,
        runtimeFactory = runtimeFactory,
        code = code,
        filename = "npm-integration-entry.js",
        runtimeOptions = JsRuntimeOptions(allowBytecodeCache = false),
        configureRuntime = { runtime ->
          runtime.bindFunction("capture") { args ->
            captured = args.single() as String
            null
          }
        },
      )
      return PublicPackageExecution(captured)
    } finally {
      httpClient.close()
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 真实执行仅返回跨引擎稳定的宿主桥结果，避免暴露具体 JavaScript 引擎对象。 */
  private data class PublicPackageExecution(val captured: String?)

  private companion object {
    const val INTEGRATION_TEST_ENV = "CYXBS_NPM_INTEGRATION_TEST"
    const val DOLLAR = '$'

    const val ESCAPE_PACKAGE = "escape-string-regexp"
    const val ESCAPE_VERSION = "5.0.0"
    const val ONETIME_PACKAGE = "onetime"
    const val ONETIME_VERSION = "6.0.0"
  }
}
