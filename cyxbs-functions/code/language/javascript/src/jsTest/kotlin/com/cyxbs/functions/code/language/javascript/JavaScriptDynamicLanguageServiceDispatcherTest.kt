package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.generated.npmjs.__cyxbsNpmJsServiceInitialize__cyxbs_mobile_cyxbs_functions_code_language_javascript
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageMetadata
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证 KSP 生成的下发端分发器能够注册并调用 Kotlin/JS 业务实现。 */
class JavaScriptDynamicLanguageServiceDispatcherTest {

  /** Lezer 应覆盖关键词之外的常见 JavaScript 语法，并保留组合样式。 */
  @Test
  fun lezerHighlightsJavaScriptSyntax() = runTest {
    val source = """
      const message = "hello"
      // comment
      function greet() {
        return 42
      }
    """.trimIndent()

    val spans = JavaScriptDynamicLanguageService.highlight(source)

    assertTrue(spans.stylesFor(source, "const").contains("tok-keyword"))
    assertTrue(spans.stylesFor(source, "\"hello\"").contains("tok-string"))
    assertTrue(spans.stylesFor(source, "// comment").contains("tok-comment"))
    assertTrue(spans.stylesFor(source, "42").contains("tok-number"))
    assertTrue(spans.any {
      "tok-variableName" in it.styleIds && "tok-definition" in it.styleIds
    })
  }

  /** JavaScript 与 Kotlin/JS 都按 UTF-16 计数，表情后的区间不能被错误换算成码点偏移。 */
  @Test
  fun highlightOffsetsUseUtf16() = runTest {
    val source = "const emoji = \"😀\"; const answer = 42"

    val spans = JavaScriptDynamicLanguageService.highlight(source)
    val answerFrom = source.indexOf("answer")
    val answerSpan = spans.firstOrNull {
      it.from == answerFrom && it.to == answerFrom + "answer".length
    }

    assertNotNull(answerSpan)
    assertTrue(answerSpan.styleIds.contains("tok-variableName"))
  }

  /** 显式初始化应可重复调用，并按 commonMain 协议完成参数及返回值的 JSON 转换。 */
  @Test
  fun generatedDispatcherInvokesService() = runTest {
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_cyxbs_functions_code_language_javascript()
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_cyxbs_functions_code_language_javascript()
    val bridge: dynamic = js("globalThis.CyxbsNpmJsService")
    assertTrue(bridge != undefined)
    assertEquals(_JavaScriptDynamicLanguageServiceNpmJsDispatcher.schemaHash, bridge.describe(SERVICE_ID))
    assertEquals(SERVICE_ID, _JavaScriptDynamicLanguageServiceNpmJsDispatcher.serviceId)
    assertTrue(_JavaScriptDynamicLanguageServiceNpmJsDispatcher.schemaHash.isNotBlank())

    val metadataJson = (bridge.invoke(SERVICE_ID, "metadata", "[]") as Promise<String>).await()
    val metadata = Json.decodeFromString<DynamicLanguageMetadata>(metadataJson)
    assertEquals("javascript", metadata.languageId)

    val completion = Json.decodeFromString<DynamicCompletionResult?>(
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.invoke(
        method = "complete",
        argumentsJson = """["co",2,false]""",
      ),
    )
    assertNotNull(completion)
    assertEquals(0, completion.from)
    assertTrue(completion.options.any { it.label == "const" })
  }

  private companion object {
    const val SERVICE_ID =
      "com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService"
  }

  /** 返回与指定源码片段完全重合的样式集合，避免测试依赖整棵语法树的节点数量。 */
  private fun List<DynamicHighlightSpan>.stylesFor(
    source: String,
    text: String,
  ): List<String> {
    val from = source.indexOf(text)
    require(from >= 0) { "Text not found in source: $text" }
    return firstOrNull { it.from == from && it.to == from + text.length }?.styleIds.orEmpty()
  }
}
