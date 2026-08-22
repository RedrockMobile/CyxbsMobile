package com.cyxbs.functions.code.npm.storage

import com.cyxbs.components.config.sp.PreferencesSettings
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.filesDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.encodeUtf8
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * 把包级和具名全局 Storage 安装到 npm Service Runtime。
 *
 * Settings 与文件共用作用域解析规则：包级作用域由加载器传入的 npm 包名决定；全局作用域由
 * JavaScript 请求携带 namespace。单个宿主实例串行处理全部 Runtime 请求，避免平台 Settings
 * 实现和同一全局文件目录被并发修改。
 */
internal class NpmStorageHost(
  private val rootDirectoryProvider: () -> Path = ::defaultNpmStorageRootDirectory,
  private val settingsFactory: (String) -> Settings = PreferencesSettings::get,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
  private val mutex = Mutex()

  /**
   * 处理一项 Storage 请求。
   *
   * 所有可预期的协议、Settings 与文件错误都会转换成 JSON 错误响应，避免向 npm 包暴露平台路径
   * 或具体 Settings 实现；协程取消仍由 Runtime 桥正常传播。
   */
  internal suspend fun handle(packageName: String, requestJson: String): String =
    mutex.withLock {
      try {
        val request = JSON.parseToJsonElement(requestJson).jsonObject
        execute(packageName, request)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: NpmStorageProtocolException) {
        errorResponse(exception.message ?: "npm Storage request is invalid.")
      } catch (exception: IllegalArgumentException) {
        errorResponse(exception.message ?: "npm Storage request is invalid.")
      } catch (_: Throwable) {
        // Okio 与平台 Settings 的异常消息可能包含绝对路径或具体实现类，不能跨桥返回。
        errorResponse("npm Storage request failed.")
      }
    }

  /** 按稳定 operation 分派 Settings 或文件操作。 */
  private fun execute(packageName: String, request: JsonObject): String {
    val operation = request.requiredString("operation")
    val scope = resolveScope(packageName, request)
    return when (operation) {
      NpmStorageHostAbi.SETTINGS_KEYS -> successValues(
        settings(scope).keys.sorted().map(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_CONTAINS -> successValue(
        JsonPrimitive(settings(scope).hasKey(request.requiredKey())),
      )
      NpmStorageHostAbi.SETTINGS_GET_STRING -> successNullable(
        settings(scope).getStringOrNull(request.requiredKey())?.let(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_GET_BOOLEAN -> successNullable(
        settings(scope).getBooleanOrNull(request.requiredKey())?.let(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_GET_INT -> successNullable(
        settings(scope).getIntOrNull(request.requiredKey())?.toString()?.let(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_GET_LONG -> successNullable(
        settings(scope).getLongOrNull(request.requiredKey())?.toString()?.let(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_GET_FLOAT -> successNullable(
        settings(scope).getFloatOrNull(request.requiredKey())?.toString()?.let(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_GET_DOUBLE -> successNullable(
        settings(scope).getDoubleOrNull(request.requiredKey())?.toString()?.let(::JsonPrimitive),
      )
      NpmStorageHostAbi.SETTINGS_PUT_STRING -> {
        settings(scope).putString(request.requiredKey(), request.requiredString("stringValue"))
        success()
      }
      NpmStorageHostAbi.SETTINGS_PUT_BOOLEAN -> {
        settings(scope).putBoolean(request.requiredKey(), request.requiredBoolean("booleanValue"))
        success()
      }
      NpmStorageHostAbi.SETTINGS_PUT_INT -> {
        settings(scope).putInt(
          request.requiredKey(),
          request.requiredString("stringValue").toInt(),
        )
        success()
      }
      NpmStorageHostAbi.SETTINGS_PUT_LONG -> {
        settings(scope).putLong(
          request.requiredKey(),
          request.requiredString("stringValue").toLong(),
        )
        success()
      }
      NpmStorageHostAbi.SETTINGS_PUT_FLOAT -> {
        settings(scope).putFloat(
          request.requiredKey(),
          request.requiredString("stringValue").toFloat(),
        )
        success()
      }
      NpmStorageHostAbi.SETTINGS_PUT_DOUBLE -> {
        settings(scope).putDouble(
          request.requiredKey(),
          request.requiredString("stringValue").toDouble(),
        )
        success()
      }
      NpmStorageHostAbi.SETTINGS_REMOVE -> {
        settings(scope).remove(request.requiredKey())
        success()
      }
      NpmStorageHostAbi.SETTINGS_CLEAR -> {
        settings(scope).clear()
        success()
      }
      NpmStorageHostAbi.FILES_EXISTS -> successValue(
        JsonPrimitive(fileSystem.exists(resolveFile(scope, request.requiredPath(), allowEmpty = true))),
      )
      NpmStorageHostAbi.FILES_METADATA -> {
        val root = fileRoot(scope)
        val target = resolveFile(scope, request.requiredPath(), allowEmpty = true)
        successNullable(
          fileSystem.metadataOrNull(target)?.toJsonMetadata(target.relativePathFrom(root)),
        )
      }
      NpmStorageHostAbi.FILES_LIST -> {
        val root = fileRoot(scope)
        val directory = resolveFile(scope, request.requiredPath(), allowEmpty = true)
        val entries = if (!fileSystem.exists(directory)) {
          emptyList()
        } else {
          fileSystem.list(directory).sorted().map { child ->
            fileSystem.metadata(child).toJsonMetadata(child.relativePathFrom(root))
          }
        }
        successValues(entries)
      }
      NpmStorageHostAbi.FILES_READ_TEXT -> {
        val target = resolveFile(scope, request.requiredPath(), allowEmpty = false)
        successNullable(
          target.takeIf(fileSystem::exists)?.let { fileSystem.read(it) { readUtf8() } }
            ?.let(::JsonPrimitive),
        )
      }
      NpmStorageHostAbi.FILES_WRITE_TEXT -> {
        atomicWrite(
          target = resolveFile(scope, request.requiredPath(), allowEmpty = false),
          bytes = request.requiredString("stringValue").encodeToByteArray(),
        )
        success()
      }
      NpmStorageHostAbi.FILES_READ_BYTES -> {
        val target = resolveFile(scope, request.requiredPath(), allowEmpty = false)
        successNullable(
          target.takeIf(fileSystem::exists)
            ?.let { fileSystem.read(it) { readByteArray() } }
            ?.let(Base64::encode)
            ?.let(::JsonPrimitive),
        )
      }
      NpmStorageHostAbi.FILES_WRITE_BYTES -> {
        atomicWrite(
          target = resolveFile(scope, request.requiredPath(), allowEmpty = false),
          bytes = Base64.decode(request.requiredString("stringValue")),
        )
        success()
      }
      NpmStorageHostAbi.FILES_DELETE -> {
        val target = resolveFile(scope, request.requiredPath(), allowEmpty = false)
        if (request.optionalBoolean("recursive")) {
          fileSystem.deleteRecursively(target, mustExist = false)
        } else {
          fileSystem.delete(target, mustExist = false)
        }
        success()
      }
      NpmStorageHostAbi.FILES_CLEAR -> {
        fileSystem.deleteRecursively(fileRoot(scope), mustExist = false)
        success()
      }
      else -> invalidRequest("Unknown npm Storage operation '$operation'.")
    }
  }

  /** 根据包名或显式 namespace 生成不会随 npm 版本变化的作用域身份。 */
  private fun resolveScope(packageName: String, request: JsonObject): StorageScope {
    return when (val kind = request.requiredString("scope")) {
      NpmStorageHostAbi.SCOPE_PACKAGE -> {
        require(request["namespace"] == null) {
          "Package npm Storage scope must not declare a namespace."
        }
        StorageScope(kind, packageName)
      }
      NpmStorageHostAbi.SCOPE_GLOBAL -> {
        val namespace = request.requiredString("namespace")
        require(GLOBAL_NAMESPACE.matches(namespace)) {
          "Global npm Storage namespace must match ${GLOBAL_NAMESPACE.pattern}."
        }
        StorageScope(kind, namespace)
      }
      else -> invalidRequest("Unknown npm Storage scope '$kind'.")
    }
  }

  /** 每个作用域使用独立 Settings 实例，原始包名和 namespace 不直接成为平台存储名称。 */
  private fun settings(scope: StorageScope): Settings =
    // PreferencesSettings 在部分平台限制节点名长度；保留 128-bit 摘要已足够隔离作用域。
    settingsFactory("NpmJsStorage-${scope.kind}-${scope.digest.take(SETTINGS_DIGEST_LENGTH)}")

  /** 获取当前作用域的持久文件根目录。 */
  private fun fileRoot(scope: StorageScope): Path =
    rootDirectoryProvider() / "v1" / scope.kind / scope.digest

  /**
   * 解析并验证作用域内路径。
   *
   * 禁止反斜杠、绝对路径、空段、'.' 与 '..'，使所有平台使用一致的 '/' 语义且无法逃逸根目录。
   */
  private fun resolveFile(scope: StorageScope, value: String, allowEmpty: Boolean): Path {
    if (value.isEmpty()) {
      require(allowEmpty) { "npm Storage file path must not be empty." }
      return fileRoot(scope)
    }
    require(value.length <= MAX_PATH_LENGTH) { "npm Storage file path is too long." }
    require(!value.startsWith('/') && '\\' !in value) {
      "npm Storage file path must be relative and use '/'."
    }
    val segments = value.split('/')
    require(segments.all { segment ->
      segment.isNotEmpty() && segment != "." && segment != ".." && '\u0000' !in segment
    }) {
      "npm Storage file path contains an invalid segment."
    }
    return segments.fold(fileRoot(scope)) { path, segment -> path / segment }
  }

  /** 使用同目录临时文件和原子移动，避免进程中断留下半份内容。 */
  private fun atomicWrite(target: Path, bytes: ByteArray) {
    val parent = requireNotNull(target.parent)
    fileSystem.createDirectories(parent)
    val temporary = parent / ".${target.name}.${Random.nextLong().toString(16)}.tmp"
    try {
      fileSystem.write(temporary, mustCreate = true) {
        write(bytes)
      }
      fileSystem.atomicMove(temporary, target)
    } finally {
      fileSystem.delete(temporary, mustExist = false)
    }
  }

  private data class StorageScope(
    val kind: String,
    val identity: String,
  ) {
    val digest: String = identity.encodeUtf8().sha256().hex()
  }

  /** 仅表示调用方协议错误；平台 I/O 异常不得使用该类型，以免跨桥泄露实现信息。 */
  private class NpmStorageProtocolException(message: String) : RuntimeException(message)

  internal companion object {
    val Default: NpmStorageHost by lazy(::NpmStorageHost)

    private const val MAX_KEY_LENGTH = 256
    private const val MAX_PATH_LENGTH = 1_024
    private const val SETTINGS_DIGEST_LENGTH = 32
    private val GLOBAL_NAMESPACE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    private val JSON = Json {
      ignoreUnknownKeys = false
      explicitNulls = false
    }

    /** 默认文件根位于 FileKit 的持久 filesDir，而不是 cache 或 temporary 目录。 */
    private fun defaultNpmStorageRootDirectory(): Path =
      FileKit.filesDir.absolutePath().toPath() / "cyxbs-code" / "npm-storage"

    private fun JsonObject.requiredString(name: String): String =
      this[name]?.jsonPrimitive?.contentOrNull
        ?: invalidRequest("npm Storage request is missing string '$name'.")

    private fun JsonObject.requiredBoolean(name: String): Boolean =
      this[name]?.jsonPrimitive?.booleanOrNull
        ?: invalidRequest("npm Storage request is missing boolean '$name'.")

    private fun JsonObject.optionalBoolean(name: String): Boolean =
      this[name]?.jsonPrimitive?.booleanOrNull ?: false

    private fun JsonObject.requiredKey(): String {
      val key = requiredString("key")
      require(key.isNotBlank()) { "npm Storage Settings key must not be blank." }
      require(key.length <= MAX_KEY_LENGTH && '\u0000' !in key) {
        "npm Storage Settings key is invalid."
      }
      return key
    }

    private fun JsonObject.requiredPath(): String =
      this["path"]?.jsonPrimitive?.contentOrNull
        ?: invalidRequest("npm Storage request is missing string 'path'.")

    private fun invalidRequest(message: String): Nothing =
      throw NpmStorageProtocolException(message)

    private fun success(): String = buildJsonObject {
      put("ok", true)
    }.toString()

    private fun successValue(value: JsonElement): String = buildJsonObject {
      put("ok", true)
      put("value", value)
    }.toString()

    private fun successNullable(value: JsonElement?): String = buildJsonObject {
      put("ok", true)
      put("value", value ?: JsonNull)
    }.toString()

    private fun successValues(values: List<JsonElement>): String = buildJsonObject {
      put("ok", true)
      put("values", JsonArray(values))
    }.toString()

    private fun errorResponse(message: String): String = buildJsonObject {
      put("ok", false)
      put("error", message)
    }.toString()

    private fun FileMetadata.toJsonMetadata(relativePath: String): JsonObject = buildJsonObject {
      put("path", relativePath)
      put("isDirectory", isDirectory)
      put("sizeBytes", (size ?: 0L).toString())
      lastModifiedAtMillis?.let { put("lastModifiedEpochMillis", it.toString()) }
    }

    private fun Path.relativePathFrom(root: Path): String =
      relativeTo(root).toString().replace('\\', '/')
  }
}
