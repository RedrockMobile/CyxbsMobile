package com.cyxbs.functions.code.language.js

import com.cyxbs.functions.code.language.api.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.api.bridge.DynamicLanguageMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证 KSP 生成的下发端分发器能够注册并调用 Kotlin/JS 业务实现。 */
class JavaScriptDynamicLanguageServiceDispatcherTest {

  /** 生成类型以英文下划线开头，并按 commonMain 协议完成参数及返回值的 JSON 转换。 */
  @Test
  fun generatedDispatcherInvokesService() = runTest {
    assertTrue(_JavaScriptDynamicLanguageServiceNpmJsRegistration)
    assertEquals(SERVICE_ID, _JavaScriptDynamicLanguageServiceNpmJsDispatcher.serviceId)
    assertTrue(_JavaScriptDynamicLanguageServiceNpmJsDispatcher.schemaHash.isNotBlank())

    val metadata = Json.decodeFromString<DynamicLanguageMetadata>(
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.invoke("metadata", "[]"),
    )
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
