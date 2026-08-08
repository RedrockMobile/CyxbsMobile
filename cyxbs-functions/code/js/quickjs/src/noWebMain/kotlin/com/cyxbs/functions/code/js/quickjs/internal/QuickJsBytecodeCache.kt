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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

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
 *
 * ```
 * read/write ──> 按天更新 access.index 的 lastUsedAt
 *                    └──最多每天触发一次 GC
 *                         ├──14 天内使用：保留
 *                         └──超过 14 天：删除 entry/module/manifest
 * ```
 *
 * QuickJS 在 evaluate 前已经把磁盘字节码复制到内存，因此删除过期磁盘文件不会影响正在运行的
 * Runtime；缓存文件访问和 GC 仍由同一个 [Mutex] 串行，避免删除正在读取的文件。
 */
internal object QuickJsBytecodeCache {
  private val fileSystem = FileSystem.SYSTEM
  private val mutex = Mutex()
  private val rootDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
    "cyxbs-code" / "quickjs" / "v$CACHE_LAYOUT_VERSION"
  private val entryDirectory = rootDirectory / "entries"
  private val moduleDirectory = rootDirectory / "modules"
  private val manifestDirectory = rootDirectory / "manifests"
  private val accessIndexPath = rootDirectory / "access.index"
  private var accessState: AccessState? = null

  suspend fun readEntry(key: QuickJsCacheKey): ByteArray? {
    return readPayload(CacheArtifact.entry(key), BYTECODE_MAGIC)
  }

  suspend fun writeEntry(key: QuickJsCacheKey, bytecode: ByteArray) {
    writePayload(CacheArtifact.entry(key), BYTECODE_MAGIC, bytecode)
  }

  suspend fun removeEntry(key: QuickJsCacheKey) {
    delete(CacheArtifact.entry(key))
  }

  suspend fun readModule(key: QuickJsCacheKey): ByteArray? {
    return readPayload(CacheArtifact.module(key), BYTECODE_MAGIC)
  }

  suspend fun writeModule(key: QuickJsCacheKey, bytecode: ByteArray) {
    writePayload(CacheArtifact.module(key), BYTECODE_MAGIC, bytecode)
  }

  suspend fun removeModule(key: QuickJsCacheKey) {
    delete(CacheArtifact.module(key))
  }

  /** 读取入口上次观察到的静态和动态 Module 名称，用于在进入引擎锁前预读缓存。 */
  suspend fun readManifest(key: QuickJsCacheKey): Set<String> {
    val artifact = CacheArtifact.manifest(key)
    val payload = readPayload(artifact, MANIFEST_MAGIC) ?: return emptySet()
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
      delete(artifact)
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
      artifact = CacheArtifact.manifest(key),
      magic = MANIFEST_MAGIC,
      payload = payload,
    )
  }

  private suspend fun readPayload(artifact: CacheArtifact, magic: String): ByteArray? = withFileLock {
    val now = Clock.System.now().toEpochMilliseconds()
    prepareMaintenanceLocked(now)
    val path = artifact.path()
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
      removeAccessLocked(artifact.id)
      return@withFileLock null
    }
    touchLocked(artifact.id, now)
    payload
  }

  private suspend fun writePayload(
    artifact: CacheArtifact,
    magic: String,
    payload: ByteArray,
  ) {
    withFileLock {
      val now = Clock.System.now().toEpochMilliseconds()
      prepareMaintenanceLocked(now)
      val encoded = Buffer()
        .writeUtf8(magic)
        .writeByte('\n'.code)
        .writeUtf8(payload.toByteString().sha256().hex())
        .writeByte('\n'.code)
        .write(payload)
        .readByteArray()
      writeAtomically(artifact.path(), encoded)
      touchLocked(artifact.id, now, force = true)
    }
  }

  private suspend fun delete(artifact: CacheArtifact) {
    withFileLock {
      loadAccessStateLocked(Clock.System.now().toEpochMilliseconds())
      fileSystem.delete(artifact.path(), mustExist = false)
      removeAccessLocked(artifact.id)
    }
  }

  /** 测试和显式维护使用；正常读写会按 [GC_INTERVAL_MILLIS] 自动触发。 */
  internal suspend fun collectGarbage(nowEpochMillis: Long) {
    withFileLock {
      loadAccessStateLocked(nowEpochMillis)
      collectGarbageLocked(nowEpochMillis)
    }
  }

  /** 加载索引并执行限频 GC；所有调用均发生在文件锁内。 */
  private fun prepareMaintenanceLocked(now: Long) {
    val state = loadAccessStateLocked(now)
    if (now - state.lastGcAtEpochMillis >= GC_INTERVAL_MILLIS) {
      collectGarbageLocked(now)
    }
  }

  /**
   * 读取访问索引。
   *
   * 索引缺失或损坏时无法证明已有字节码的新鲜度，因此清空本版本缓存；字节码本来就是可重建数据。
   */
  private fun loadAccessStateLocked(now: Long): AccessState {
    accessState?.let { return it }
    // 布局升级后旧字节码无法保证兼容，首次访问时直接回收上一版缓存。
    fileSystem.deleteRecursively(rootDirectory.parent!! / "v1", mustExist = false)
    if (!fileSystem.exists(accessIndexPath)) {
      if (fileSystem.exists(entryDirectory) || fileSystem.exists(moduleDirectory) ||
        fileSystem.exists(manifestDirectory)
      ) {
        fileSystem.deleteRecursively(rootDirectory, mustExist = false)
      }
      return AccessState(lastGcAtEpochMillis = now).also {
        accessState = it
        persistAccessStateLocked(it)
      }
    }
    val decoded = runCatching {
      val bytes = fileSystem.read(accessIndexPath) { readByteArray() }
      decodeAccessState(bytes)
    }.getOrNull()
    if (decoded != null) {
      accessState = decoded
      return decoded
    }
    fileSystem.deleteRecursively(rootDirectory, mustExist = false)
    return AccessState(lastGcAtEpochMillis = now).also {
      accessState = it
      persistAccessStateLocked(it)
    }
  }

  /** 删除超过 14 天未使用的独立字节码/图索引，并原子提交新的访问索引。 */
  private fun collectGarbageLocked(now: Long) {
    val state = requireNotNull(accessState)
    val retained = state.lastUsedAtEpochMillis.filterValues { lastUsedAt ->
      now - lastUsedAt < CACHE_TTL_MILLIS
    }
    (state.lastUsedAtEpochMillis.keys - retained.keys).forEach { id ->
      CacheArtifact.fromId(id)?.let { artifact ->
        fileSystem.delete(artifact.path(), mustExist = false)
      }
    }
    val updated = AccessState(
      lastGcAtEpochMillis = now,
      lastUsedAtEpochMillis = retained,
    )
    accessState = updated
    persistAccessStateLocked(updated)
  }

  /** 最多每天持久化一次命中时间；新文件和写入始终立即登记。 */
  private fun touchLocked(id: String, now: Long, force: Boolean = false) {
    val state = requireNotNull(accessState)
    val previous = state.lastUsedAtEpochMillis[id]
    if (!force && previous != null && now - previous < TOUCH_INTERVAL_MILLIS) return
    val updated = state.copy(
      lastUsedAtEpochMillis = state.lastUsedAtEpochMillis + (id to now),
    )
    accessState = updated
    persistAccessStateLocked(updated)
  }

  /** 删除文件时同步移除索引，避免后续 GC 反复处理不存在的路径。 */
  private fun removeAccessLocked(id: String) {
    val state = accessState ?: return
    if (id !in state.lastUsedAtEpochMillis) return
    val updated = state.copy(lastUsedAtEpochMillis = state.lastUsedAtEpochMillis - id)
    accessState = updated
    persistAccessStateLocked(updated)
  }

  /** 使用带 magic 和 SHA-256 的小型二进制格式保存访问时间。 */
  private fun persistAccessStateLocked(state: AccessState) {
    val payload = Buffer().apply {
      writeLong(state.lastGcAtEpochMillis)
      writeInt(state.lastUsedAtEpochMillis.size)
      state.lastUsedAtEpochMillis.entries.sortedBy { it.key }.forEach { (id, timestamp) ->
        val bytes = id.encodeToByteArray()
        writeInt(bytes.size)
        write(bytes)
        writeLong(timestamp)
      }
    }.readByteArray()
    val encoded = Buffer()
      .writeUtf8(ACCESS_INDEX_MAGIC)
      .writeByte('\n'.code)
      .writeUtf8(payload.toByteString().sha256().hex())
      .writeByte('\n'.code)
      .write(payload)
      .readByteArray()
    writeAtomically(accessIndexPath, encoded)
  }

  /** 校验并解码访问索引；任何结构异常均由调用方按缓存损坏处理。 */
  private fun decodeAccessState(encoded: ByteArray): AccessState {
    val envelope = Buffer().write(encoded)
    require(envelope.readUtf8Line() == ACCESS_INDEX_MAGIC)
    val expectedHash = envelope.readUtf8Line()
    val payload = envelope.readByteArray()
    require(payload.toByteString().sha256().hex() == expectedHash)
    val buffer = Buffer().write(payload)
    val lastGcAt = buffer.readLong()
    val count = buffer.readInt()
    require(count in 0..MAX_ACCESS_RECORDS)
    val records = buildMap(count) {
      repeat(count) {
        val size = buffer.readInt()
        require(size in 1..MAX_ACCESS_ID_BYTES && size.toLong() <= buffer.size)
        val id = buffer.readUtf8(size.toLong())
        require(CacheArtifact.fromId(id) != null)
        put(id, buffer.readLong())
      }
    }
    require(buffer.exhausted())
    return AccessState(lastGcAt, records)
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

  /** 将逻辑身份转换为当前布局中的真实路径，不接受远端或业务字符串。 */
  private fun CacheArtifact.path(): Path = when (kind) {
    ENTRY_KIND -> entryDirectory / "$key.qbc"
    MODULE_KIND -> moduleDirectory / "$key.qbc"
    MANIFEST_KIND -> manifestDirectory / "$key.graph"
    else -> error("Unknown QuickJS cache artifact kind.")
  }

  private data class AccessState(
    val lastGcAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Map<String, Long> = emptyMap(),
  )

  private data class CacheArtifact(val kind: String, val key: String) {
    val id: String get() = "$kind:$key"

    companion object {
      fun entry(key: QuickJsCacheKey) = CacheArtifact(ENTRY_KIND, key.value)
      fun module(key: QuickJsCacheKey) = CacheArtifact(MODULE_KIND, key.value)
      fun manifest(key: QuickJsCacheKey) = CacheArtifact(MANIFEST_KIND, key.value)

      fun fromId(id: String): CacheArtifact? {
        val kind = id.substringBefore(':', missingDelimiterValue = "")
        val key = id.substringAfter(':', missingDelimiterValue = "")
        if (kind !in setOf(ENTRY_KIND, MODULE_KIND, MANIFEST_KIND)) return null
        if (!Regex("[0-9a-f]{64}").matches(key)) return null
        return CacheArtifact(kind, key)
      }
    }
  }

  private const val CACHE_LAYOUT_VERSION = 2
  private const val BYTECODE_MAGIC = "CYXBS_QJS_BYTECODE_2"
  private const val MANIFEST_MAGIC = "CYXBS_QJS_GRAPH_1"
  private const val ACCESS_INDEX_MAGIC = "CYXBS_QJS_ACCESS_1"
  private const val ENTRY_KIND = "entry"
  private const val MODULE_KIND = "module"
  private const val MANIFEST_KIND = "manifest"
  private const val MAX_MANIFEST_MODULES = 4_096
  private const val MAX_MODULE_NAME_BYTES = 64 * 1024
  private const val MAX_ACCESS_RECORDS = 100_000
  private const val MAX_ACCESS_ID_BYTES = 80
  private val CACHE_TTL_MILLIS = 14.days.inWholeMilliseconds
  private val GC_INTERVAL_MILLIS = 1.days.inWholeMilliseconds
  private val TOUCH_INTERVAL_MILLIS = 1.days.inWholeMilliseconds
}
