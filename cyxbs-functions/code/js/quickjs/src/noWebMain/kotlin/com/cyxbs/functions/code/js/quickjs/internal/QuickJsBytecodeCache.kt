package com.cyxbs.functions.code.js.quickjs.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import kotlin.jvm.JvmInline
import kotlin.random.Random

/** QuickJS 内部使用的固定长度缓存键，不会暴露到公共 Program 或 Runtime API。 */
@JvmInline
internal value class QuickJsCacheKey(val value: String) {
  init {
    require(KEY_REGEX.matches(value)) { "QuickJS cache key must be a lowercase SHA-256." }
  }

  private companion object {
    val KEY_REGEX = Regex("[0-9a-f]{64}")
  }
}

/**
 * QuickJS 固定位置的跨平台字节码缓存。
 *
 * 缓存根目录位于平台临时缓存目录，文件布局和格式版本完全由 `quickjs` 模块管理。每份负载都带
 * SHA-256，损坏文件会被当作未命中并删除，绝不会直接交给 QuickJS 反序列化。
 */
internal object QuickJsBytecodeCache {
  private val fileSystem = FileSystem.SYSTEM
  private val mutex = Mutex()
  private val rootDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
    "cyxbs-code" / "quickjs" / "v$CACHE_LAYOUT_VERSION"
  private val entryDirectory = rootDirectory / "entries"
  private val moduleDirectory = rootDirectory / "modules"
  private val manifestDirectory = rootDirectory / "manifests"

  suspend fun readEntry(key: QuickJsCacheKey): ByteArray? {
    return readPayload(entryDirectory / "${key.value}.qbc", BYTECODE_MAGIC)
  }

  suspend fun writeEntry(key: QuickJsCacheKey, bytecode: ByteArray) {
    writePayload(entryDirectory / "${key.value}.qbc", BYTECODE_MAGIC, bytecode)
  }

  suspend fun removeEntry(key: QuickJsCacheKey) {
    delete(entryDirectory / "${key.value}.qbc")
  }

  suspend fun readModule(key: QuickJsCacheKey): ByteArray? {
    return readPayload(moduleDirectory / "${key.value}.qbc", BYTECODE_MAGIC)
  }

  suspend fun writeModule(key: QuickJsCacheKey, bytecode: ByteArray) {
    writePayload(moduleDirectory / "${key.value}.qbc", BYTECODE_MAGIC, bytecode)
  }

  suspend fun removeModule(key: QuickJsCacheKey) {
    delete(moduleDirectory / "${key.value}.qbc")
  }

  /** 读取入口上次观察到的静态和动态 Module 名称，用于在进入引擎锁前预读缓存。 */
  suspend fun readManifest(key: QuickJsCacheKey): Set<String> {
    val path = manifestDirectory / "${key.value}.graph"
    val payload = readPayload(path, MANIFEST_MAGIC) ?: return emptySet()
    return try {
      val buffer = Buffer().write(payload)
      val count = buffer.readInt()
      require(count in 0..MAX_MANIFEST_MODULES)
      buildSet(count) {
        repeat(count) {
          val size = buffer.readInt()
          require(size in 1..MAX_MODULE_NAME_BYTES && size.toLong() <= buffer.size)
          add(buffer.readUtf8(size.toLong()))
        }
      }.also { require(buffer.exhausted()) }
    } catch (_: Throwable) {
      delete(path)
      emptySet()
    }
  }

  /** 原子保存入口已观察到的 Module 名称，供下次 Runtime 预热依赖缓存。 */
  suspend fun writeManifest(key: QuickJsCacheKey, names: Set<String>) {
    val normalized = names.asSequence()
      .filter { it.isNotBlank() }
      .filter { it.encodeToByteArray().size <= MAX_MODULE_NAME_BYTES }
      .distinct()
      .sorted()
      .take(MAX_MANIFEST_MODULES)
      .toList()
    val payload = Buffer().apply {
      writeInt(normalized.size)
      normalized.forEach { name ->
        val bytes = name.encodeToByteArray()
        writeInt(bytes.size)
        write(bytes)
      }
    }.readByteArray()
    writePayload(
      path = manifestDirectory / "${key.value}.graph",
      magic = MANIFEST_MAGIC,
      payload = payload,
    )
  }

  private suspend fun readPayload(path: Path, magic: String): ByteArray? = withFileLock {
    if (!fileSystem.exists(path)) return@withFileLock null
    val encoded = try {
      fileSystem.read(path) { readByteArray() }
    } catch (_: IOException) {
      return@withFileLock null
    }
    val buffer = Buffer().write(encoded)
    val actualMagic = buffer.readUtf8Line()
    val expectedHash = buffer.readUtf8Line()
    val payload = buffer.readByteArray()
    val actualHash = payload.toByteString().sha256().hex()
    if (actualMagic != magic || expectedHash != actualHash) {
      fileSystem.delete(path, mustExist = false)
      return@withFileLock null
    }
    payload
  }

  private suspend fun writePayload(path: Path, magic: String, payload: ByteArray) {
    withFileLock {
      val encoded = Buffer()
        .writeUtf8(magic)
        .writeByte('\n'.code)
        .writeUtf8(payload.toByteString().sha256().hex())
        .writeByte('\n'.code)
        .write(payload)
        .readByteArray()
      writeAtomically(path, encoded)
    }
  }

  private suspend fun delete(path: Path) {
    withFileLock { fileSystem.delete(path, mustExist = false) }
  }

  /** 磁盘访问统一离开 QuickJS 锁并串行执行，避免并发写入同一缓存文件。 */
  private suspend fun <T> withFileLock(block: () -> T): T {
    return withContext(Dispatchers.Default) { mutex.withLock { block() } }
  }

  private fun writeAtomically(target: Path, bytes: ByteArray) {
    val parent = requireNotNull(target.parent)
    fileSystem.createDirectories(parent)
    val temporary = parent / ".${target.name}.${Random.nextLong().toString(16)}.tmp"
    try {
      fileSystem.write(temporary, mustCreate = true) { write(bytes) }
      fileSystem.atomicMove(temporary, target)
    } finally {
      fileSystem.delete(temporary, mustExist = false)
    }
  }

  private const val CACHE_LAYOUT_VERSION = 1
  private const val BYTECODE_MAGIC = "CYXBS_QJS_BYTECODE_2"
  private const val MANIFEST_MAGIC = "CYXBS_QJS_GRAPH_1"
  private const val MAX_MANIFEST_MODULES = 4_096
  private const val MAX_MODULE_NAME_BYTES = 64 * 1024
}
