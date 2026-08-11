package com.cyxbs.functions.code.npm.service.test.js.impl

import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证 KSP 生成的 JavaScript Dispatcher 使用稳定且可前向演进的 JSON 配置。 */
class NpmJsServiceJsonCompatibilityTest {

  /**
   * 新发送端增加未知字段时旧实现应忽略它，同时保持默认值省略和必填 nullable 字段显式编码。
   */
  @Test
  fun dispatcherUsesForwardCompatibleJsonConfiguration() = runTest {
    val resultJson = _NpmJsServiceLoaderTestServiceJsNpmJsDispatcher.invoke(
      method = "echoPayload",
      argumentsJson = """
        [{
          "value": "forward-compatible",
          "nullableValue": null,
          "futureField": {"enabled": true}
        }]
      """.trimIndent(),
    )

    val result = Json.decodeFromString<NpmJsServiceLoaderTestPayload>(resultJson)
    assertEquals("forward-compatible", result.value)
    assertEquals(null, result.nullableValue)
    assertEquals(null, result.optionalValue)
    assertTrue("\"nullableValue\":null" in resultJson)
    assertFalse("\"optionalValue\"" in resultJson)
    assertFalse("\"futureField\"" in resultJson)
  }
}
