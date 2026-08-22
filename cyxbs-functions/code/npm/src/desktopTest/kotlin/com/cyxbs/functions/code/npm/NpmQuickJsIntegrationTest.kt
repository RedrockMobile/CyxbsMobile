package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.js.runtime.JsSyncFunctionBridge
import com.cyxbs.functions.code.npm.bridge.NpmJsBridgeContext
import com.cyxbs.functions.code.npm.bridge.NpmJsBridgeGateway
import com.cyxbs.functions.code.npm.bridge.NpmJsBridgeHostDispatcher
import com.cyxbs.functions.code.npm.bridge.NpmJsBridgeHostFactory
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeHostAbi
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgePackageScope
import com.cyxbs.functions.code.npm.model.NpmEntryRequest
import com.cyxbs.functions.code.npm.model.NpmEntryVersion
import com.cyxbs.functions.code.npm.module.NpmModuleGraphFactory
import com.cyxbs.functions.code.npm.pool.NpmPackagePool
import com.cyxbs.functions.code.npm.storage.NpmStorageBridge
import com.cyxbs.functions.code.npm.transport.KtorNpmHttpTransport
import com.g985892345.provider.cyxbsmobile.cyxbsfunctions.code.npm.NpmKtProviderInitializer
import com.g985892345.provider.testing.KtProviderTestScope
import com.g985892345.provider.testing.withKtProviderTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** 通过固定公开 npm 坐标验证 registry 下载、tgz 解包、Module 解析和 QuickJS 执行。 */
class NpmQuickJsIntegrationTest {

  /** 验证指定包范围、能力列表和缺失方法错误均由统一网关执行。 */
  @Test
  fun restrictBridgeVisibilityToConfiguredEntryPackages() = withQuickJsRuntime(
    configureProviders = {
      overrideImpl<NpmJsBridgeHostFactory>("npm-js-bridge:test-scope") {
        TestBridgeFactory()
      }
    },
  ) { runtimeFactory ->
    val allowed = requestBridgeGateway(
      runtimeFactory = runtimeFactory,
      entryPackageName = BRIDGE_ALLOWED_PACKAGE,
      arguments = listOf(NpmJsBridgeHostAbi.DESCRIBE, TEST_BRIDGE_ID),
    )
    assertEquals(true, allowed.getValue("ok").jsonPrimitive.content.toBoolean())
    assertEquals(
      listOf(TEST_BRIDGE_METHOD),
      allowed.getValue("methods").jsonArray.map { it.jsonPrimitive.content },
    )

    val missingMethod = requestBridgeGateway(
      runtimeFactory = runtimeFactory,
      entryPackageName = BRIDGE_ALLOWED_PACKAGE,
      arguments = listOf(
        NpmJsBridgeHostAbi.INVOKE,
        TEST_BRIDGE_ID,
        "newerMethod",
        "[]",
      ),
    )
    assertEquals(
      NpmJsBridgeHostAbi.ERROR_METHOD_NOT_IMPLEMENTED,
      missingMethod.getValue("code").jsonPrimitive.content,
    )

    val denied = requestBridgeGateway(
      runtimeFactory = runtimeFactory,
      entryPackageName = BRIDGE_DENIED_PACKAGE,
      arguments = listOf(NpmJsBridgeHostAbi.DESCRIBE, TEST_BRIDGE_ID),
    )
    val absent = requestBridgeGateway(
      runtimeFactory = runtimeFactory,
      entryPackageName = BRIDGE_DENIED_PACKAGE,
      arguments = listOf(NpmJsBridgeHostAbi.DESCRIBE, "test.bridge.Absent"),
    )
    assertEquals(NpmJsBridgeHostAbi.ERROR_BRIDGE_NOT_INSTALLED, denied["code"]?.jsonPrimitive?.content)
    assertEquals(absent, denied, "未授权桥不能泄露出与未安装桥不同的信息")
  }

  /** 验证 npm 网关通过 KtProvider 发现并调用 KSP 生成的 Storage dispatcher。 */
  @Test
  fun installNpmRuntimeBridgesFromSingleEntry() = withQuickJsRuntime { runtimeFactory ->
    val runtime = runtimeFactory.create(
      JsRuntimeOptions(
        allowBytecodeCache = false,
        bridges = listOf(NpmJsBridgeGateway(BRIDGE_TEST_PACKAGE)),
      ),
    )
    try {
      val request = JsonPrimitive(
        """{"operation":"settings.getString","scope":"package","key":"missing"}""",
      ).toString()
      val response = runtime.evaluateValue(
        code = """
          const envelope = JSON.parse(await globalThis["${NpmJsBridgeHostAbi.GATEWAY}"](
            "${NpmJsBridgeHostAbi.INVOKE}",
            "${NpmStorageBridge::class.qualifiedName}",
            "invoke",
            JSON.stringify([$request])
          ));
          JSON.parse(envelope.result)
        """.trimIndent(),
        filename = "npm-bridge-test.js",
        asModule = false,
      ) as String
      val result = Json.parseToJsonElement(response).jsonObject

      assertEquals("true", result.getValue("ok").jsonPrimitive.content, response)
      assertEquals(null, result.getValue("value").jsonPrimitive.contentOrNull)
    } finally {
      runtime.close()
    }
  }

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
  private fun withQuickJsRuntime(
    configureProviders: KtProviderTestScope.() -> Unit = {},
    block: suspend (JsRuntimeFactory) -> Unit,
  ) {
    withKtProviderTest(NpmKtProviderInitializer) {
      overrideImpl<JsRuntimeFactory>(JsRuntimeFactory.DEFAULT_PROVIDER_NAME) { QuickJsRuntimeFactory }
      configureProviders()
      runTest {
        val runtimeFactory = checkNotNull(JsRuntimeFactory.implOrNull()) {
          "QuickJS runtime provider is not installed for the integration test."
        }
        block(runtimeFactory)
      }
    }
  }

  /**
   * 为指定真实入口包创建隔离 Runtime，并直接读取统一网关返回的 JSON envelope。
   *
   * 每次请求都创建新 Runtime，避免 capabilities 缓存让权限测试互相污染。
   */
  private suspend fun requestBridgeGateway(
    runtimeFactory: JsRuntimeFactory,
    entryPackageName: String,
    arguments: List<String>,
  ) = runtimeFactory.create(
    JsRuntimeOptions(
      allowBytecodeCache = false,
      bridges = listOf(NpmJsBridgeGateway(entryPackageName)),
    ),
  ).let { runtime ->
    try {
      val encodedArguments = arguments.joinToString(", ") { JsonPrimitive(it).toString() }
      val response = runtime.evaluateValue(
        code = "await globalThis[${JsonPrimitive(NpmJsBridgeHostAbi.GATEWAY)}]($encodedArguments)",
        filename = "npm-bridge-scope-test.js",
        asModule = false,
      ) as String
      Json.parseToJsonElement(response).jsonObject
    } finally {
      runtime.close()
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
        runtimeOptions = JsRuntimeOptions(
          allowBytecodeCache = false,
          bridges = listOf(
            JsSyncFunctionBridge("capture") { args ->
              captured = args.single() as String
              null
            },
          ),
        ),
      )
      return PublicPackageExecution(captured)
    } finally {
      httpClient.close()
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 真实执行仅返回跨引擎稳定的宿主桥结果，避免暴露具体 JavaScript 引擎对象。 */
  private data class PublicPackageExecution(val captured: String?)

  /** 测试专用桥工厂，仅向一个入口包暴露一个 echo 能力。 */
  private class TestBridgeFactory : NpmJsBridgeHostFactory {
    override val bridgeId: String = TEST_BRIDGE_ID
    override val methodNames: Set<String> = setOf(TEST_BRIDGE_METHOD)
    override val packageScope: NpmJsBridgePackageScope =
      NpmJsBridgePackageScope.SPECIFIED_PACKAGES
    override val packageNames: Set<String> = setOf(BRIDGE_ALLOWED_PACKAGE)

    override fun create(context: NpmJsBridgeContext): NpmJsBridgeHostDispatcher =
      object : NpmJsBridgeHostDispatcher {
        override val bridgeId: String = this@TestBridgeFactory.bridgeId
        override val methodNames: Set<String> = this@TestBridgeFactory.methodNames

        override suspend fun invoke(methodName: String, argumentsJson: String): String =
          JsonPrimitive(argumentsJson).toString()
      }
  }

  private companion object {
    const val INTEGRATION_TEST_ENV = "CYXBS_NPM_INTEGRATION_TEST"
    const val BRIDGE_TEST_PACKAGE = "@cyxbs-mobile/npm-runtime-bridge-test"
    const val BRIDGE_ALLOWED_PACKAGE = "@cyxbs-mobile/allowed-bridge-package"
    const val BRIDGE_DENIED_PACKAGE = "@cyxbs-mobile/denied-bridge-package"
    const val TEST_BRIDGE_ID = "test.bridge.ScopedBridge"
    const val TEST_BRIDGE_METHOD = "echo"
    const val DOLLAR = '$'

    const val ESCAPE_PACKAGE = "escape-string-regexp"
    const val ESCAPE_VERSION = "5.0.0"
    const val ONETIME_PACKAGE = "onetime"
    const val ONETIME_VERSION = "6.0.0"
  }
}
