package com.cyxbs.functions.code.npm.service

import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.npm.model.NpmPackageId
import com.cyxbs.functions.code.npm.model.NpmPreparedEntry
import com.cyxbs.functions.code.npm.pool.NpmPreparedEntryLease
import com.cyxbs.functions.code.npm.api.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.api.bridge.NpmJsServiceProtocolException
import com.g985892345.provider.cyxbsmobile.cyxbsfunctions.code.js.quickjs.QuickjsKtProviderInitializer
import com.g985892345.provider.testing.withKtProviderTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 使用真实 QuickJS 验证 npm Service 会话的协议、调用和资源释放边界。 */
class NpmJsServiceSessionTest {

  /** 验证同步或异步 JavaScript 返回值都能经过顶层 await 和 JSON 协议返回。 */
  @Test
  fun initializeInvokeAndClose() = withQuickJsSession { session, isReleased ->
    session.initialize(SERVICE_SOURCE, ENTRY_MODULE)

    assertEquals("\"hello\"", session.invoke("echo", "[\"hello\"]"))
    assertEquals("5", session.invoke("addAsync", "[2,3]"))

    session.close()

    assertTrue(isReleased())
    assertFailsWith<NpmJsServiceProtocolException> {
      session.invoke("echo", "[\"closed\"]")
    }
  }

  /** JavaScript 执行异常必须转换为稳定异常，同时关闭仍应释放 npm 入口租约。 */
  @Test
  fun invocationFailureIsConvertedAndLeaseIsReleased() =
    withQuickJsSession { session, isReleased ->
      session.initialize(SERVICE_SOURCE, ENTRY_MODULE)

      val failure = assertFailsWith<NpmJsServiceInvocationException> {
        session.invoke("fail", "[]")
      }
      assertTrue(failure.cause != null)

      session.close()
      assertTrue(isReleased())
    }

  /** 协议摘要不一致时不允许创建代理，并且失败会话仍可完整关闭。 */
  @Test
  fun schemaMismatchStopsInitialization() = withQuickJsSession(
    schemaHash = "unexpected-schema",
  ) { session, isReleased ->
    assertFailsWith<NpmJsServiceProtocolException> {
      session.initialize(SERVICE_SOURCE, ENTRY_MODULE)
    }

    session.close()
    assertTrue(isReleased())
  }

  /**
   * 在隔离 KtProvider Registry 中创建真实 QuickJS Runtime 和最小入口租约。
   *
   * [block] 返回前应主动关闭会话；测试会检查 Service 自身的释放流程而非依赖兜底清理。
   */
  private fun withQuickJsSession(
    schemaHash: String = SCHEMA_HASH,
    block: suspend (NpmJsServiceSession, () -> Boolean) -> Unit,
  ) {
    withKtProviderTest(QuickjsKtProviderInitializer) {
      runTest {
        val runtimeFactory = checkNotNull(JsRuntimeFactory.implOrNull()) {
          "QuickJS runtime provider is not installed for the npm Service test."
        }
        var released = false
        val lease = NpmPreparedEntryLease(
          preparedEntry = NpmPreparedEntry(
            resolvedAtEpochMillis = 0L,
            entryPackage = NpmPackageId("@cyxbs-mobile/test-service", "1.0.0"),
            entryModule = ENTRY_MODULE,
            archives = emptyList(),
            resolvedPackages = emptyList(),
          ),
          releaseAction = { released = true },
        )
        val session = NpmJsServiceSession(
          runtime = runtimeFactory.create(
            JsRuntimeOptions(allowBytecodeCache = false),
          ),
          lease = lease,
          serviceId = SERVICE_ID,
          schemaHash = schemaHash,
        )
        block(session) { released }
      }
    }
  }

  private companion object {
    const val SERVICE_ID = "com.cyxbs.functions.code.npm.service.TestService"
    const val SCHEMA_HASH = "test-schema"
    const val ENTRY_MODULE = "test-service.mjs"

    val SERVICE_SOURCE = """
      globalThis.CyxbsNpmJsService = {
        describe(serviceId) {
          return serviceId === "$SERVICE_ID" ? "$SCHEMA_HASH" : null;
        },
        async invoke(serviceId, method, argumentsJson) {
          if (serviceId !== "$SERVICE_ID") {
            throw new Error("Unknown service: " + serviceId);
          }
          const args = JSON.parse(argumentsJson);
          switch (method) {
            case "echo":
              return JSON.stringify(args[0]);
            case "addAsync":
              return JSON.stringify(await Promise.resolve(args[0] + args[1]));
            case "fail":
              throw new Error("expected test failure");
            case "${'$'}close":
              return "null";
            default:
              throw new Error("Unknown method: " + method);
          }
        },
      };
    """.trimIndent()
  }
}
