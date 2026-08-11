package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession
import com.cyxbs.generated.npmjs.__cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

    val spans = JavaScriptDynamicLanguageService.highlight(source).spans

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

    val spans = JavaScriptDynamicLanguageService.highlight(source).spans
    val answerFrom = source.indexOf("answer")
    val answerSpan = spans.firstOrNull {
      it.from == answerFrom && it.to == answerFrom + "answer".length
    }

    assertNotNull(answerSpan)
    assertTrue(answerSpan.styleIds.contains("tok-variableName"))
  }

  /** 相同源码应直接命中结果缓存，不再次执行 Lezer 解析与高亮遍历。 */
  @Test
  fun repeatedSourceReusesExactHighlightResult() = runTest {
    val source = "const cachedValue = 42"
    val session = LezerSyntaxHighlighterSession(parser)

    val first = session.highlight(source)
    val cached = session.highlight(source)

    assertEquals(DynamicHighlightCacheMode.FULL, first.metrics.cacheMode)
    assertEquals(DynamicHighlightCacheMode.EXACT, cached.metrics.cacheMode)
    assertEquals(0, cached.metrics.parseMicroseconds)
    assertEquals(0, cached.metrics.collectMicroseconds)
    assertTrue(cached.spans.stylesFor(source, "42").contains("tok-number"))
  }

  /** 小范围编辑应复用未受影响的语法树片段，并输出新源码对应的高亮区间。 */
  @Test
  fun smallEditUsesIncrementalSyntaxTreeFragments() = runTest {
    val session = LezerSyntaxHighlighterSession(parser)
    val original = buildString {
      repeat(20) { index -> appendLine("const value$index = $index;") }
    }
    val updated = original.replace("value10 = 10", "value10 = \"ten\"")

    session.highlight(original)
    val result = session.highlight(updated)

    assertEquals(DynamicHighlightCacheMode.INCREMENTAL, result.metrics.cacheMode)
    assertNotNull(result.metrics.changedRange)
    assertTrue(result.metrics.reusableFragmentCount > 0)
    assertTrue(result.spans.stylesFor(updated, "\"ten\"").contains("tok-string"))
  }

  /** 显式初始化应可重复调用，并按 commonMain 协议完成参数及返回值的 JSON 转换。 */
  @Test
  fun generatedDispatcherInvokesService() = runTest {
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript()
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript()
    val bridge: dynamic = js("globalThis.CyxbsNpmJsService")
    assertTrue(bridge != undefined)
    assertEquals(SERVICE_ID, _JavaScriptDynamicLanguageServiceNpmJsDispatcher.serviceId)
    val describedMethods = Json.decodeFromString<List<String>>(bridge.describe(SERVICE_ID) as String)
    assertEquals(
      setOf("complete", "highlight"),
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.methodNames,
    )
    assertEquals(setOf("complete", "highlight"), describedMethods.toSet())

    val highlightResult = Json.decodeFromString<DynamicHighlightResult>(
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.invoke(
        method = "highlight",
        argumentsJson = """["const answer = 42"]""",
      ),
    )
    assertTrue(highlightResult.spans.stylesFor("const answer = 42", "42").contains("tok-number"))

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
