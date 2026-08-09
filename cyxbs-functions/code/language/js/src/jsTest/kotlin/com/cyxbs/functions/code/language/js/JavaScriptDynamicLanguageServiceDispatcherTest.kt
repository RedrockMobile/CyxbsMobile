package com.cyxbs.functions.code.language.js

import com.cyxbs.generated.npmjs.__cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript
import com.cyxbs.functions.code.language.api.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.api.bridge.DynamicLanguageMetadata
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

  /** 显式初始化应可重复调用，并按 commonMain 协议完成参数及返回值的 JSON 转换。 */
  @Test
  fun generatedDispatcherInvokesService() = runTest {
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript()
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript()
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
      "com.cyxbs.functions.code.language.api.bridge.DynamicLanguageService"
  }
}
