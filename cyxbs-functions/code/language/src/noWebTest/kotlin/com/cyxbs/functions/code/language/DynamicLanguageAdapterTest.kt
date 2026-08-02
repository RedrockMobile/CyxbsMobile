package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 动态语言稳定协议与 JavaScript Runtime 适配边界测试。 */
class DynamicLanguageAdapterTest {

  /** 验证适配器可以从内存 Module 图加载依赖并转换高亮结果。 */
  @Test
  fun highlightFromResolvedModuleGraph() = runTest {
    val adapter = createAdapter()
    try {
      val spans = adapter.highlight("const answer = 42;")

      assertEquals("javascript", adapter.metadata.languageId)
      assertEquals(DynamicLanguageAdapter.PROTOCOL_VERSION, adapter.metadata.protocolVersion)
      assertEquals(listOf("tok-keyword"), spans.single().styleIds)
    } finally {
      adapter.close()
    }
  }

  /** 验证补全结果会跨 JavaScript 边界转换为稳定 DTO。 */
  @Test
  fun completeFromResolvedModuleGraph() = runTest {
    val adapter = createAdapter()
    try {
      val source = "const content = 42;\ncon"

      val result = assertNotNull(adapter.complete(source, source.length, explicit = true))

      assertEquals(source.length - 3, result.from)
      assertEquals(source.length, result.to)
      assertEquals("content", result.options.single().label)
    } finally {
      adapter.close()
    }
  }

  /** 验证协议版本不匹配时初始化失败且不会返回半初始化实例。 */
  @Test
  fun rejectUnsupportedProtocolVersion() = runTest {
    val entrySource = bridgeSource(protocolVersion = 2)

    assertFailsWith<DynamicLanguageProtocolException> {
      createAdapterFromEntry(entrySource)
    }
  }

  /** 验证底层 JavaScript 引擎异常不会穿透公开 API。 */
  @Test
  fun wrapEngineFailureWithStableException() = runTest {
    val exception = assertFailsWith<DynamicLanguageExecutionException> {
      createAdapterFromEntry("throw new Error('module failed')")
    }

    assertTrue(exception.cause != null)
  }

  /** 验证关闭后不会继续访问已经释放的 JavaScript Runtime。 */
  @Test
  fun rejectAnalysisAfterClose() = runTest {
    val adapter = createAdapter()
    adapter.close()

    assertFailsWith<IllegalStateException> {
      adapter.highlight("const value = 1")
    }
  }

  /** 创建包含静态 import 的内存 Module 图，避免测试依赖 npm 或生成产物。 */
  private suspend fun createAdapter(): DynamicLanguageAdapter {
    val entryName = "@cyxbs/language-javascript/entry.mjs"
    val serviceName = "@cyxbs/language-javascript/service.mjs"
    return DynamicLanguageAdapter.create(
      runtimeFactory = QuickJsRuntimeFactory,
      moduleGraph = DynamicLanguageModuleGraph(
        entryModule = entryName,
        moduleSources = mapOf(
          serviceName to SERVICE_SOURCE,
          entryName to """
            import { highlight, complete } from '$serviceName';
            globalThis.CyxbsDynamicLanguage = {
              metadata: {
                languageId: "javascript",
                displayName: "JavaScript",
                protocolVersion: 1
              },
              highlight,
              complete
            };
          """.trimIndent(),
        ),
      ),
    )
  }

  /** 创建只有入口 Module 的测试图，用于验证初始化异常边界。 */
  private suspend fun createAdapterFromEntry(entrySource: String): DynamicLanguageAdapter {
    val entryName = "@cyxbs/test-language/entry.mjs"
    return DynamicLanguageAdapter.create(
      runtimeFactory = QuickJsRuntimeFactory,
      moduleGraph = DynamicLanguageModuleGraph(
        entryModule = entryName,
        moduleSources = mapOf(entryName to entrySource),
      ),
    )
  }

  /** 生成只改变协议版本的最小有效桥。 */
  private fun bridgeSource(protocolVersion: Int): String = """
    globalThis.CyxbsDynamicLanguage = {
      metadata: {
        languageId: "test",
        displayName: "Test",
        protocolVersion: $protocolVersion
      },
      highlight() { return "[]"; },
      complete() { return "null"; }
    };
  """.trimIndent()

  private companion object {
    val SERVICE_SOURCE = """
      export function highlight(source) {
        return JSON.stringify([{ from: 0, to: 5, classes: "tok-keyword" }]);
      }

      export function complete(source, position) {
        return JSON.stringify({
          from: position - 3,
          to: position,
          options: [{ label: "content", type: "variable" }]
        });
      }
    """.trimIndent()
  }
}
