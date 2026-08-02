package com.cyxbs.functions.code.js.storage

import com.cyxbs.functions.code.js.program.JsProgramRef
import com.cyxbs.functions.code.js.program.JsSourcePackage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/**
 * 基于 Okio 的跨平台 JavaScript 源码持久化。
 *
 * Android、iOS 与 Desktop 调用方应传入各自 App 私有目录下的 [rootDirectory]。源码使用 JSON
 * 保存，并通过临时文件加原子移动避免半写入。具体引擎缓存由对应实现模块单独管理。
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
) : JsSourcePackageStore {
  private val mutex = Mutex()
  private val sourceDirectory = rootDirectory / "sources"

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
}
