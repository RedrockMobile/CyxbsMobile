package com.cyxbs.functions.code.js

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.random.Random

/**
 * 基于 Okio 的跨平台源码与字节码持久化。
 *
 * Android、iOS 与 Desktop 调用方应传入各自 App 私有目录下的 [rootDirectory]。源码使用 JSON
 * 保存；字节码使用带魔数和 SHA-256 的二进制格式，并通过临时文件加原子移动避免半写入。
 *
 * @param rootDirectory JS 数据根目录。
 * @param fileSystem Okio 文件系统，生产环境通常使用 [FileSystem.SYSTEM]。
 * @param ioDispatcher 文件操作调度器；公共代码没有通用 IO Dispatcher，默认使用 Default。
 * @param json 源码包序列化配置。
 */
class OkioJsProgramStorage(
  private val rootDirectory: Path,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  },
) : JsSourcePackageStore, JsBytecodeCache {
  private val mutex = Mutex()
  private val sourceDirectory = rootDirectory / "sources"
  private val bytecodeDirectory = rootDirectory / "bytecode"

  override suspend fun writeSource(sourcePackage: JsSourcePackage) {
    withFileLock {
      val target = sourcePath(sourcePackage.reference)
      writeAtomically(
        target = target,
        bytes = json.encodeToString(JsSourcePackage.serializer(), sourcePackage).encodeToByteArray(),
      )
    }
  }

  override suspend fun readSource(reference: JsProgramRef): JsSourcePackage? {
    return withFileLock {
      val path = sourcePath(reference)
      if (!fileSystem.exists(path)) {
        return@withFileLock null
      }
      val encoded = fileSystem.read(path) { readUtf8() }
      json.decodeFromString(JsSourcePackage.serializer(), encoded)
    }
  }

  override suspend fun removeSource(reference: JsProgramRef) {
    withFileLock {
      fileSystem.delete(sourcePath(reference), mustExist = false)
    }
  }

  override suspend fun readBytecode(key: JsBytecodeCacheKey): ByteArray? {
    return withFileLock {
      val path = bytecodePath(key)
      if (!fileSystem.exists(path)) {
        return@withFileLock null
      }

      // 字节码没有安全反序列化保证，因此必须在交给 QuickJS 前验证本地缓存完整性。
      val encoded = try {
        fileSystem.read(path) { readByteArray() }
      } catch (_: IOException) {
        return@withFileLock null
      }
      val buffer = Buffer().write(encoded)
      val magic = buffer.readUtf8Line()
      val expectedHash = buffer.readUtf8Line()
      val bytecode = buffer.readByteArray()
      val actualHash = bytecode.toByteString().sha256().hex()
      if (magic != BYTECODE_MAGIC || expectedHash != actualHash) {
        fileSystem.delete(path, mustExist = false)
        return@withFileLock null
      }
      bytecode
    }
  }

  override suspend fun writeBytecode(key: JsBytecodeCacheKey, bytecode: ByteArray) {
    withFileLock {
      val encoded = Buffer()
        .writeUtf8(BYTECODE_MAGIC)
        .writeByte('\n'.code)
        .writeUtf8(bytecode.toByteString().sha256().hex())
        .writeByte('\n'.code)
        .write(bytecode)
        .readByteArray()
      writeAtomically(target = bytecodePath(key), bytes = encoded)
    }
  }

  override suspend fun removeBytecode(key: JsBytecodeCacheKey) {
    withFileLock {
      fileSystem.delete(bytecodePath(key), mustExist = false)
    }
  }

  override suspend fun clearBytecode() {
    withFileLock {
      fileSystem.deleteRecursively(bytecodeDirectory, mustExist = false)
    }
  }

  /**
   * 在指定调度器和实例级互斥锁中执行文件操作。
   *
   * 同一根目录不应同时创建多个存储实例；如业务需要多进程访问，应在上层再增加进程锁。
   */
  private suspend fun <T> withFileLock(block: () -> T): T {
    return withContext(ioDispatcher) {
      mutex.withLock {
        block()
      }
    }
  }

  /**
   * 使用引用哈希生成源码文件路径，避免业务 packageId 参与路径解析。
   */
  private fun sourcePath(reference: JsProgramRef): Path {
    val digest = "${reference.packageId}\u0000${reference.version}"
      .encodeToByteArray()
      .toByteString()
      .sha256()
      .hex()
    return sourceDirectory / "$digest.json"
  }

  /**
   * 字节码缓存键已经是受约束的十六进制字符串，可以直接作为文件名。
   */
  private fun bytecodePath(key: JsBytecodeCacheKey): Path {
    return bytecodeDirectory / "${key.value}.qbc"
  }

  /**
   * 先完整写入同目录临时文件，再原子替换目标文件。
   */
  private fun writeAtomically(target: Path, bytes: ByteArray) {
    val parent = requireNotNull(target.parent) { "Storage target must have a parent directory." }
    fileSystem.createDirectories(parent)
    val temporary = parent / ".${target.name}.${Random.nextLong().toString(16)}.tmp"
    try {
      fileSystem.write(temporary, mustCreate = true) {
        write(bytes)
      }
      fileSystem.atomicMove(source = temporary, target = target)
    } finally {
      fileSystem.delete(temporary, mustExist = false)
    }
  }

  companion object {
    private const val BYTECODE_MAGIC = "CYXBS_QJS_BYTECODE_1"
  }
}
