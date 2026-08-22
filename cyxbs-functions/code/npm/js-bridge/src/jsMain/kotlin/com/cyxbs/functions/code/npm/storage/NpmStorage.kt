package com.cyxbs.functions.code.npm.storage

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.js.JSON

/**
 * 当前 npm 包可用的统一持久化入口。
 *
 * [packageScope] 使用宿主提供的稳定 npm 包名隔离数据，不包含包版本；[globalScope] 由调用方
 * 指定共享 namespace。每个作用域同时提供轻量 [NpmStorageScope.settings] 与持久文件
 * [NpmStorageScope.files]。
 */
object NpmStorage {
  /** 当前 npm 包的私有作用域。 */
  val packageScope: NpmStorageScope = NpmStorageScope(
    scopeKind = NpmStorageHostAbi.SCOPE_PACKAGE,
    namespace = null,
  )

  /**
   * 获取具名全局作用域。
   *
   * 相同 namespace 的 npm 包共享同一份 Settings 与文件目录。namespace 只承担逻辑分组，不作为
   * 权限边界。
   */
  fun globalScope(namespace: String): NpmStorageScope {
    require(namespace.isNotBlank()) { "Global npm storage namespace must not be blank." }
    return NpmStorageScope(
      scopeKind = NpmStorageHostAbi.SCOPE_GLOBAL,
      namespace = namespace,
    )
  }
}

/** 同一逻辑作用域下的 Settings 与文件存储。 */
class NpmStorageScope internal constructor(
  scopeKind: String,
  namespace: String?,
) {
  val settings: NpmSettingsStorage = NpmSettingsStorage(scopeKind, namespace)
  val files: NpmFileStorage = NpmFileStorage(scopeKind, namespace)
}

/**
 * 面向小型状态和配置的 Settings 存储。
 *
 * 所有方法使用 suspend，以便宿主统一串行跨 Runtime 的访问。带默认值的读取与项目现有
 * Settings 约定一致；需要区分“未保存”和类型默认值时应调用对应的 OrNull 方法。
 */
class NpmSettingsStorage internal constructor(
  private val scopeKind: String,
  private val namespace: String?,
) {
  suspend fun keys(): Set<String> =
    stringArray(invokeStorage(NpmStorageHostAbi.SETTINGS_KEYS), "values").toSet()

  suspend fun contains(key: String): Boolean =
    boolean(invokeStorage(NpmStorageHostAbi.SETTINGS_CONTAINS, key = key), "value")

  suspend fun getString(key: String, defaultValue: String = ""): String =
    getStringOrNull(key) ?: defaultValue

  suspend fun getStringOrNull(key: String): String? =
    nullableString(invokeStorage(NpmStorageHostAbi.SETTINGS_GET_STRING, key = key), "value")

  suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
    getBooleanOrNull(key) ?: defaultValue

  suspend fun getBooleanOrNull(key: String): Boolean? =
    nullableBoolean(invokeStorage(NpmStorageHostAbi.SETTINGS_GET_BOOLEAN, key = key), "value")

  suspend fun getInt(key: String, defaultValue: Int = 0): Int =
    getIntOrNull(key) ?: defaultValue

  suspend fun getIntOrNull(key: String): Int? =
    nullableString(invokeStorage(NpmStorageHostAbi.SETTINGS_GET_INT, key = key), "value")
      ?.toInt()

  suspend fun getLong(key: String, defaultValue: Long = 0L): Long =
    getLongOrNull(key) ?: defaultValue

  suspend fun getLongOrNull(key: String): Long? =
    nullableString(invokeStorage(NpmStorageHostAbi.SETTINGS_GET_LONG, key = key), "value")
      ?.toLong()

  suspend fun getFloat(key: String, defaultValue: Float = 0F): Float =
    getFloatOrNull(key) ?: defaultValue

  suspend fun getFloatOrNull(key: String): Float? =
    nullableString(invokeStorage(NpmStorageHostAbi.SETTINGS_GET_FLOAT, key = key), "value")
      ?.toFloat()

  suspend fun getDouble(key: String, defaultValue: Double = 0.0): Double =
    getDoubleOrNull(key) ?: defaultValue

  suspend fun getDoubleOrNull(key: String): Double? =
    nullableString(invokeStorage(NpmStorageHostAbi.SETTINGS_GET_DOUBLE, key = key), "value")
      ?.toDouble()

  suspend fun putString(key: String, value: String) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_PUT_STRING, key = key, stringValue = value)
  }

  suspend fun putBoolean(key: String, value: Boolean) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_PUT_BOOLEAN, key = key, booleanValue = value)
  }

  suspend fun putInt(key: String, value: Int) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_PUT_INT, key = key, stringValue = value.toString())
  }

  suspend fun putLong(key: String, value: Long) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_PUT_LONG, key = key, stringValue = value.toString())
  }

  suspend fun putFloat(key: String, value: Float) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_PUT_FLOAT, key = key, stringValue = value.toString())
  }

  suspend fun putDouble(key: String, value: Double) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_PUT_DOUBLE, key = key, stringValue = value.toString())
  }

  suspend fun remove(key: String) {
    invokeStorage(NpmStorageHostAbi.SETTINGS_REMOVE, key = key)
  }

  suspend fun clear() {
    invokeStorage(NpmStorageHostAbi.SETTINGS_CLEAR)
  }

  /** 组装当前作用域的宿主请求，避免每个公开方法重复协议字段。 */
  private suspend fun invokeStorage(
    operation: String,
    key: String? = null,
    stringValue: String? = null,
    booleanValue: Boolean? = null,
  ): dynamic = invokeNpmStorage(
    operation = operation,
    scopeKind = scopeKind,
    namespace = namespace,
    key = key,
    stringValue = stringValue,
    booleanValue = booleanValue,
  )
}

/**
 * 面向源码、较大文本和二进制内容的作用域文件存储。
 *
 * path 始终是使用 '/' 分隔的相对路径。宿主拒绝绝对路径、反斜杠、'.' 与 '..' 段；写入会自动
 * 创建父目录，并通过临时文件加原子移动替换旧内容。
 */
class NpmFileStorage internal constructor(
  private val scopeKind: String,
  private val namespace: String?,
) {
  suspend fun exists(path: String): Boolean =
    boolean(invokeStorage(NpmStorageHostAbi.FILES_EXISTS, path), "value")

  suspend fun metadata(path: String): NpmStorageFileMetadata? =
    nullableMetadata(invokeStorage(NpmStorageHostAbi.FILES_METADATA, path), "value")

  suspend fun list(directory: String = ""): List<NpmStorageFileMetadata> =
    metadataArray(invokeStorage(NpmStorageHostAbi.FILES_LIST, directory), "values")

  suspend fun readText(path: String): String? =
    nullableString(invokeStorage(NpmStorageHostAbi.FILES_READ_TEXT, path), "value")

  suspend fun writeText(path: String, value: String) {
    invokeStorage(NpmStorageHostAbi.FILES_WRITE_TEXT, path, stringValue = value)
  }

  suspend fun readBytes(path: String): ByteArray? =
    nullableString(invokeStorage(NpmStorageHostAbi.FILES_READ_BYTES, path), "value")
      ?.let { Base64.decode(it) }

  suspend fun writeBytes(path: String, value: ByteArray) {
    invokeStorage(
      NpmStorageHostAbi.FILES_WRITE_BYTES,
      path,
      stringValue = Base64.encode(value),
    )
  }

  suspend fun delete(path: String, recursive: Boolean = false) {
    invokeStorage(NpmStorageHostAbi.FILES_DELETE, path, recursive = recursive)
  }

  suspend fun clear() {
    invokeStorage(NpmStorageHostAbi.FILES_CLEAR, path = "")
  }

  /** 组装当前作用域的文件请求；Base64 仅是跨宿主边界的传输细节。 */
  private suspend fun invokeStorage(
    operation: String,
    path: String,
    stringValue: String? = null,
    recursive: Boolean = false,
  ): dynamic = invokeNpmStorage(
    operation = operation,
    scopeKind = scopeKind,
    namespace = namespace,
    path = path,
    stringValue = stringValue,
    recursive = recursive,
  )
}

/** 文件列表和 metadata 返回的稳定平台无关信息。 */
data class NpmStorageFileMetadata(
  val path: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val lastModifiedEpochMillis: Long?,
)

/** npm Storage 桥执行失败；底层平台路径与 Settings 实现不会暴露给业务包。 */
class NpmStorageException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

/** 默认 JSON 兼容新增字段，并避免无意义的默认值与显式 null 膨胀 Settings。 */
val NpmStorageJson: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
  explicitNulls = false
}

/** 读取并反序列化 JSON；键不存在时返回 null，格式错误仍向调用方报告。 */
suspend inline fun <reified T> NpmSettingsStorage.getJsonOrNull(
  key: String,
  json: Json = NpmStorageJson,
): T? = getStringOrNull(key)?.let { json.decodeFromString<T>(it) }

/** 读取并反序列化 JSON；仅在键不存在时返回 [defaultValue]。 */
suspend inline fun <reified T> NpmSettingsStorage.getJson(
  key: String,
  defaultValue: T,
  json: Json = NpmStorageJson,
): T = getJsonOrNull<T>(key, json) ?: defaultValue

/** 使用指定 [json] 将值序列化到当前 Settings 作用域。 */
suspend inline fun <reified T> NpmSettingsStorage.putJson(
  key: String,
  value: T,
  json: Json = NpmStorageJson,
) {
  putString(key, json.encodeToString<T>(value))
}

/** 调用 KSP 生成的强类型桥代理，并把底层桥异常收敛为 Storage 领域异常。 */
private suspend fun invokeNpmStorage(
  operation: String,
  scopeKind: String,
  namespace: String?,
  key: String? = null,
  path: String? = null,
  stringValue: String? = null,
  booleanValue: Boolean? = null,
  recursive: Boolean = false,
): dynamic {
  val request: dynamic = js("({})")
  request.operation = operation
  request.scope = scopeKind
  if (namespace != null) request.namespace = namespace
  if (key != null) request.key = key
  if (path != null) request.path = path
  if (stringValue != null) request.stringValue = stringValue
  if (booleanValue != null) request.booleanValue = booleanValue
  if (recursive) request.recursive = true

  val requestJson = JSON.stringify(request)
  val responseJson = try {
    npmStorageBridge.invoke(requestJson).getOrThrow()
  } catch (exception: CancellationException) {
    throw exception
  } catch (throwable: Throwable) {
    throw NpmStorageException(
      throwable.message ?: "The npm Storage host bridge invocation failed.",
      throwable,
    )
  }
  val response: dynamic = JSON.parse(responseJson)
  if (response.ok != true) {
    throw NpmStorageException(
      (response.error as? String) ?: "The npm Storage host rejected the request.",
    )
  }
  return response
}

private fun nullableString(target: dynamic, name: String): String? {
  val value = target[name]
  return if (value == null || value == undefined) null else value as String
}

private fun boolean(target: dynamic, name: String): Boolean = target[name] as Boolean

private fun nullableBoolean(target: dynamic, name: String): Boolean? {
  val value = target[name]
  return if (value == null || value == undefined) null else value as Boolean
}

private fun stringArray(target: dynamic, name: String): Array<String> =
  target[name] as Array<String>

private fun nullableMetadata(target: dynamic, name: String): NpmStorageFileMetadata? {
  val value = target[name]
  return if (value == null || value == undefined) null else toMetadata(value)
}

private fun metadataArray(target: dynamic, name: String): List<NpmStorageFileMetadata> =
  (target[name] as Array<dynamic>).map(::toMetadata)

private fun toMetadata(value: dynamic): NpmStorageFileMetadata = NpmStorageFileMetadata(
  path = value.path as String,
  isDirectory = value.isDirectory as Boolean,
  sizeBytes = (value.sizeBytes as String).toLong(),
  lastModifiedEpochMillis = (value.lastModifiedEpochMillis as? String)?.toLong(),
)
