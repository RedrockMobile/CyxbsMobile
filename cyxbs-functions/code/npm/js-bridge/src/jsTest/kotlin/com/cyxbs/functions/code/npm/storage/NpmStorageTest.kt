package com.cyxbs.functions.code.npm.storage

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.js.undefined
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 验证 npm 包实际调用的 Kotlin/JS Storage API 与稳定宿主 JSON 协议。 */
class NpmStorageTest {

  /** Settings 缺少键时应应用审批后的类型默认值，同时保留 OrNull 区分能力。 */
  @Test
  fun settingsApplyDefaultsAndKeepNullableReads() = runTest {
    withBridge(handler = { request ->
      when (request.operation()) {
        NpmStorageHostAbi.SETTINGS_GET_STRING,
        NpmStorageHostAbi.SETTINGS_GET_BOOLEAN,
        NpmStorageHostAbi.SETTINGS_GET_INT,
        NpmStorageHostAbi.SETTINGS_GET_LONG,
        NpmStorageHostAbi.SETTINGS_GET_FLOAT,
        NpmStorageHostAbi.SETTINGS_GET_DOUBLE,
        -> response(value = JsonNull)
        else -> response()
      }
    }) {
      val settings = NpmStorage.packageScope.settings
      assertEquals("", settings.getString("missing"))
      assertEquals("fallback", settings.getString("missing", "fallback"))
      assertEquals(false, settings.getBoolean("missing"))
      assertEquals(7, settings.getInt("missing", 7))
      assertEquals(8L, settings.getLong("missing", 8L))
      assertEquals(1.5F, settings.getFloat("missing", 1.5F))
      assertEquals(2.5, settings.getDouble("missing", 2.5))
      assertNull(settings.getStringOrNull("missing"))
    }
  }

  /** 包级请求不得携带 namespace；全局 Settings 与文件请求必须保留调用方 namespace。 */
  @Test
  fun packageAndGlobalScopesShareTheApprovedEntryPoints() = runTest {
    val requests = mutableListOf<JsonObject>()
    withBridge(handler = { request ->
      requests += request
      response()
    }) {
      NpmStorage.packageScope.settings.putString("state", "ready")
      NpmStorage.globalScope("tutorials.progress").files.writeBytes(
        "java/state.bin",
        byteArrayOf(1, 2, 3),
      )
    }

    val packageRequest = requests[0]
    assertEquals(NpmStorageHostAbi.SCOPE_PACKAGE, packageRequest.string("scope"))
    assertNull(packageRequest["namespace"])
    assertEquals("state", packageRequest.string("key"))

    val globalRequest = requests[1]
    assertEquals(NpmStorageHostAbi.SCOPE_GLOBAL, globalRequest.string("scope"))
    assertEquals("tutorials.progress", globalRequest.string("namespace"))
    assertEquals("java/state.bin", globalRequest.string("path"))
    assertEquals("AQID", globalRequest.string("stringValue"))
  }

  /** 文本、二进制、metadata 与 JSON 辅助方法应隐藏宿主传输细节。 */
  @Test
  fun filesAndJsonHelpersDecodeHostResponses() = runTest {
    val storedSettings = mutableMapOf<String, String>()
    withBridge(handler = { request ->
      when (request.operation()) {
        NpmStorageHostAbi.SETTINGS_PUT_STRING -> {
          storedSettings[request.string("key")] = request.string("stringValue")
          response()
        }
        NpmStorageHostAbi.SETTINGS_GET_STRING -> response(
          value = storedSettings[request.string("key")]?.let(::JsonPrimitive)
            ?: JsonNull,
        )
        NpmStorageHostAbi.FILES_READ_BYTES -> response(
          value = JsonPrimitive("AAEC/w=="),
        )
        NpmStorageHostAbi.FILES_METADATA -> """
          {"ok":true,"value":{"path":"workspace/Main.java","isDirectory":false,
          "sizeBytes":"42","lastModifiedEpochMillis":"1234"}}
        """.trimIndent()
        else -> response()
      }
    }) {
      val settings = NpmStorage.packageScope.settings
      settings.putJson("sample", Sample("Java", 8))
      assertEquals(Sample("Java", 8), settings.getJson("sample", Sample("", 0)))

      assertContentEquals(
        byteArrayOf(0, 1, 2, -1),
        NpmStorage.packageScope.files.readBytes("bytes.bin"),
      )
      assertEquals(
        NpmStorageFileMetadata(
          path = "workspace/Main.java",
          isDirectory = false,
          sizeBytes = 42,
          lastModifiedEpochMillis = 1_234,
        ),
        NpmStorage.packageScope.files.metadata("workspace/Main.java"),
      )
    }
  }

  /** 宿主拒绝响应必须统一转换为公开 NpmStorageException。 */
  @Test
  fun hostErrorsUseStableException() = runTest {
    withBridge(handler = {
      """{"ok":false,"error":"invalid namespace"}"""
    }) {
      val failure = assertFailsWith<NpmStorageException> {
        NpmStorage.globalScope("bad namespace").settings.keys()
      }
      assertEquals("invalid namespace", failure.message)
    }
  }

  /** 安装临时 Promise 宿主函数，并在测试结束后移除，避免污染其他 jsTest。 */
  private suspend fun withBridge(
    handler: (JsonObject) -> String,
    block: suspend () -> Unit,
  ) {
    val global: dynamic = js("globalThis")
    val scope = MainScope()
    global[NpmStorageHostAbi.INVOKE] = { requestJson: String ->
      scope.promise {
        handler(Json.parseToJsonElement(requestJson).jsonObject)
      }
    }
    try {
      block()
    } finally {
      global[NpmStorageHostAbi.INVOKE] = undefined
      scope.cancel()
    }
  }

  private fun JsonObject.operation(): String = string("operation")

  private fun JsonObject.string(name: String): String =
    getValue(name).jsonPrimitive.content

  private fun response(
    value: JsonElement? = null,
  ): String = buildJsonObject {
    put("ok", true)
    value?.let { put("value", it) }
  }.toString()

  @Serializable
  private data class Sample(
    val language: String,
    val version: Int,
  )
}
