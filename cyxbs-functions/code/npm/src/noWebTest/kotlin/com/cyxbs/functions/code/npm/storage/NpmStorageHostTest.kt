package com.cyxbs.functions.code.npm.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证 npm Storage 宿主的作用域、数据类型与文件安全边界。 */
class NpmStorageHostTest {

  /**
   * 包作用域必须按 npm 包名隔离；具名全局作用域则应被不同包共享。
   */
  @Test
  fun packageAndGlobalSettingsUseIndependentScopes() = withHost { host, _, _ ->
    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.SETTINGS_PUT_STRING,
      key = "progress",
      stringValue = "package-a",
    ).assertSucceeded()

    assertEquals(
      "package-a",
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.SETTINGS_GET_STRING,
        key = "progress",
      ).valueOrNull(),
    )
    assertNull(
      host.request(
        packageName = PACKAGE_B,
        operation = NpmStorageHostAbi.SETTINGS_GET_STRING,
        key = "progress",
      ).valueOrNull(),
    )

    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.SETTINGS_PUT_LONG,
      scope = NpmStorageHostAbi.SCOPE_GLOBAL,
      namespace = GLOBAL_NAMESPACE,
      key = "completed",
      stringValue = Long.MAX_VALUE.toString(),
    ).assertSucceeded()

    assertEquals(
      Long.MAX_VALUE.toString(),
      host.request(
        packageName = PACKAGE_B,
        operation = NpmStorageHostAbi.SETTINGS_GET_LONG,
        scope = NpmStorageHostAbi.SCOPE_GLOBAL,
        namespace = GLOBAL_NAMESPACE,
        key = "completed",
      ).valueOrNull(),
    )
  }

  /** Settings 的全部基础类型、键枚举、删除和清空都应保持类型与范围。 */
  @Test
  fun settingsSupportAllApprovedPrimitiveTypes() = withHost { host, _, _ ->
    val values = listOf(
      NpmStorageHostAbi.SETTINGS_PUT_BOOLEAN to (null to true),
      NpmStorageHostAbi.SETTINGS_PUT_INT to ("-12" to null),
      NpmStorageHostAbi.SETTINGS_PUT_LONG to (Long.MAX_VALUE.toString() to null),
      NpmStorageHostAbi.SETTINGS_PUT_FLOAT to ("1.25" to null),
      NpmStorageHostAbi.SETTINGS_PUT_DOUBLE to ("-4.5" to null),
    )
    values.forEachIndexed { index, (operation, value) ->
      host.request(
        packageName = PACKAGE_A,
        operation = operation,
        key = "value-$index",
        stringValue = value.first,
        booleanValue = value.second,
      ).assertSucceeded()
    }

    val keys = host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.SETTINGS_KEYS,
    ).values()
    assertEquals((values.indices).map { "value-$it" }, keys)

    assertEquals(
      true,
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.SETTINGS_GET_BOOLEAN,
        key = "value-0",
      ).booleanValue(),
    )
    assertEquals(
      "-12",
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.SETTINGS_GET_INT,
        key = "value-1",
      ).valueOrNull(),
    )

    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.SETTINGS_REMOVE,
      key = "value-0",
    ).assertSucceeded()
    assertFalse(
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.SETTINGS_CONTAINS,
        key = "value-0",
      ).booleanValue(),
    )

    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.SETTINGS_CLEAR,
    ).assertSucceeded()
    assertTrue(
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.SETTINGS_KEYS,
      ).values().isEmpty(),
    )
  }

  /** 文本和二进制文件应原子写入作用域目录，并可列出稳定 metadata。 */
  @Test
  fun filesSupportTextBytesMetadataAndGlobalSharing() = withHost { host, _, _ ->
    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.FILES_WRITE_TEXT,
      path = "workspace/Main.java",
      stringValue = "class Main {}",
    ).assertSucceeded()

    assertEquals(
      "class Main {}",
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.FILES_READ_TEXT,
        path = "workspace/Main.java",
      ).valueOrNull(),
    )
    assertNull(
      host.request(
        packageName = PACKAGE_B,
        operation = NpmStorageHostAbi.FILES_READ_TEXT,
        path = "workspace/Main.java",
      ).valueOrNull(),
    )

    val bytes = byteArrayOf(0, 1, 2, -1)
    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.FILES_WRITE_BYTES,
      scope = NpmStorageHostAbi.SCOPE_GLOBAL,
      namespace = GLOBAL_NAMESPACE,
      path = "shared/data.bin",
      stringValue = Base64.encode(bytes),
    ).assertSucceeded()
    assertEquals(
      bytes.toList(),
      Base64.decode(
        checkNotNull(
          host.request(
            packageName = PACKAGE_B,
            operation = NpmStorageHostAbi.FILES_READ_BYTES,
            scope = NpmStorageHostAbi.SCOPE_GLOBAL,
            namespace = GLOBAL_NAMESPACE,
            path = "shared/data.bin",
          ).valueOrNull(),
        ),
      ).toList(),
    )

    val entries = host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.FILES_LIST,
      path = "workspace",
    ).jsonObject.getValue("values").toString()
    assertTrue("Main.java" in entries)
    assertTrue("sizeBytes" in entries)
  }

  /** 文件 API 必须拒绝绝对路径与父目录跳转，且 clear 只能清理当前作用域。 */
  @Test
  fun filesRejectEscapingPathsAndClearOnlyCurrentScope() = withHost { host, root, fileSystem ->
    val traversal = host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.FILES_WRITE_TEXT,
      path = "../outside.txt",
      stringValue = "unsafe",
    )
    assertFalse(traversal.success())
    assertFalse(fileSystem.exists(root / "outside.txt"))

    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.FILES_WRITE_TEXT,
      path = "state/a.txt",
      stringValue = "a",
    ).assertSucceeded()
    host.request(
      packageName = PACKAGE_B,
      operation = NpmStorageHostAbi.FILES_WRITE_TEXT,
      path = "state/b.txt",
      stringValue = "b",
    ).assertSucceeded()
    host.request(
      packageName = PACKAGE_A,
      operation = NpmStorageHostAbi.FILES_CLEAR,
      path = "",
    ).assertSucceeded()

    assertFalse(
      host.request(
        packageName = PACKAGE_A,
        operation = NpmStorageHostAbi.FILES_EXISTS,
        path = "state/a.txt",
      ).booleanValue(),
    )
    assertTrue(
      host.request(
        packageName = PACKAGE_B,
        operation = NpmStorageHostAbi.FILES_EXISTS,
        path = "state/b.txt",
      ).booleanValue(),
    )
  }

  /** 为每个测试创建独立 Settings Map 与临时目录，并在结束后可靠清理。 */
  private fun withHost(
    block: suspend (NpmStorageHost, Path, FileSystem) -> Unit,
  ) = runTest {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "npm-storage-host-test-${Random.nextLong().toString(16)}"
    val settings = mutableMapOf<String, MemorySettings>()
    val host = NpmStorageHost(
      rootDirectoryProvider = { root },
      settingsFactory = { name -> settings.getOrPut(name, ::MemorySettings) },
      fileSystem = fileSystem,
    )
    try {
      block(host, root, fileSystem)
    } finally {
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 使用与 JavaScript API 相同的 JSON 字段调用宿主，防止测试绕过协议层。 */
  private suspend fun NpmStorageHost.request(
    packageName: String,
    operation: String,
    scope: String = NpmStorageHostAbi.SCOPE_PACKAGE,
    namespace: String? = null,
    key: String? = null,
    path: String? = null,
    stringValue: String? = null,
    booleanValue: Boolean? = null,
  ): JsonElement {
    val request = buildJsonObject {
      put("operation", operation)
      put("scope", scope)
      namespace?.let { put("namespace", it) }
      key?.let { put("key", it) }
      path?.let { put("path", it) }
      stringValue?.let { put("stringValue", it) }
      booleanValue?.let { put("booleanValue", it) }
    }
    return Json.parseToJsonElement(handle(packageName, request.toString()))
  }

  private fun JsonElement.success(): Boolean =
    jsonObject.getValue("ok").jsonPrimitive.content.toBoolean()

  private fun JsonElement.assertSucceeded() {
    assertTrue(success(), jsonObject["error"]?.jsonPrimitive?.contentOrNull)
  }

  private fun JsonElement.valueOrNull(): String? =
    jsonObject["value"]?.jsonPrimitive?.contentOrNull

  private fun JsonElement.booleanValue(): Boolean =
    jsonObject.getValue("value").jsonPrimitive.content.toBoolean()

  private fun JsonElement.values(): List<String> =
    jsonObject.getValue("values").jsonArray.map { it.jsonPrimitive.content }

  private companion object {
    const val PACKAGE_A = "@cyxbs-mobile/tutorial-java"
    const val PACKAGE_B = "@cyxbs-mobile/tutorial-javascript"
    const val GLOBAL_NAMESPACE = "tutorials.progress"
  }
}

/** 仅供测试的完整 Settings Map 实现，避免触碰平台真实偏好文件。 */
private class MemorySettings : Settings {
  private val values = mutableMapOf<String, Any>()

  override val keys: Set<String>
    get() = values.keys

  override val size: Int
    get() = values.size

  override fun clear() = values.clear()
  override fun remove(key: String) {
    values.remove(key)
  }

  override fun hasKey(key: String): Boolean = key in values
  override fun putInt(key: String, value: Int) = values.set(key, value)
  override fun getInt(key: String, defaultValue: Int): Int =
    values[key] as? Int ?: defaultValue
  override fun getIntOrNull(key: String): Int? = values[key] as? Int
  override fun putLong(key: String, value: Long) = values.set(key, value)
  override fun getLong(key: String, defaultValue: Long): Long =
    values[key] as? Long ?: defaultValue
  override fun getLongOrNull(key: String): Long? = values[key] as? Long
  override fun putString(key: String, value: String) = values.set(key, value)
  override fun getString(key: String, defaultValue: String): String =
    values[key] as? String ?: defaultValue
  override fun getStringOrNull(key: String): String? = values[key] as? String
  override fun putFloat(key: String, value: Float) = values.set(key, value)
  override fun getFloat(key: String, defaultValue: Float): Float =
    values[key] as? Float ?: defaultValue
  override fun getFloatOrNull(key: String): Float? = values[key] as? Float
  override fun putDouble(key: String, value: Double) = values.set(key, value)
  override fun getDouble(key: String, defaultValue: Double): Double =
    values[key] as? Double ?: defaultValue
  override fun getDoubleOrNull(key: String): Double? = values[key] as? Double
  override fun putBoolean(key: String, value: Boolean) = values.set(key, value)
  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
    values[key] as? Boolean ?: defaultValue
  override fun getBooleanOrNull(key: String): Boolean? = values[key] as? Boolean
}
