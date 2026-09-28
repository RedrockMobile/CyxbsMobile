package com.cyxbs.components.config.json

import com.cyxbs.components.config.serializable.defaultJson
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * json形式存储键值对，解决sp大数据存储会超出上限的问题
 * 路径格式：
 * 应用文件目录/json_settings/{name}/{key}.json
 * 比如map下的map_info存储信息：
 * FileKit.filesDir/
 * └── json_settings/
 *     └── map/
 *         └── map_info.json
 */
actual class JsonSettings internal actual constructor(name: String) {

  private val storageName = validateSegment(name)
  // 协程里的互斥锁，同一时刻，只允许一个协程执行被它保护的代码
  private val mutex = Mutex()
  // 文件系统
  private val fileSystem = FileSystem.SYSTEM

  // 地址信息
  private val directory: Path by lazy {
    FileKit.filesDir.path.toPath() / "json_settings" / storageName
  }

  actual suspend fun <T> put(
    key: String,
    value: T,
    serializer: KSerializer<T>
  ): Boolean {
    validateSegment(key)
    return access(false) {
      val json = defaultJson.encodeToString(serializer, value)
      val file = fileOf(key)
      val tmpFile = directory / "${file.name}.tmp"

      fileSystem.createDirectories(directory)

      try {
        // 先写临时文件，再替换正式文件，原子替换
        fileSystem.write(tmpFile) { writeUtf8(json) }
        fileSystem.atomicMove(tmpFile, file)
        true
      } finally {
        try {
          fileSystem.delete(tmpFile, false)
        } catch (_: IOException) {
          // 下次写入会覆盖
        }
      }
    }
  }

  actual suspend fun <T> getOrNull(
    key: String,
    serializer: KSerializer<T>
  ): T? {
    validateSegment(key)

    return access(null){
      val file = fileOf(key)
      if (!fileSystem.exists(file)) {
        null
      } else {
        val json = fileSystem.read(file) { readUtf8() }
        defaultJson.decodeFromString(serializer, json)
      }
    }
  }

  actual suspend fun remove(key: String): Boolean {
    validateSegment(key)
    return access(false) {
      fileSystem.delete(fileOf(key), false)
      true
    }
  }

  actual suspend fun clear(): Boolean {
    return access(false) {
      fileSystem.deleteRecursively(directory, false)
      true
    }
  }

  private fun fileOf(key: String) = directory / "$key.json"

  private suspend fun <T> access(
    fallback: T,
    block: () -> T
  ): T = withContext(Dispatchers.IO) {
    mutex.withLock {
      try {
        block.invoke()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        fallback
      }
    }
  }
}

/**
 * 判断是否符合规则
 */
private fun validateSegment(value: String): String {
  require(value.matches(Regex("[A-Za-z0-9_-]+"))) {
    "JsonSettings name/key must contain only letters, digits, '_' or '-'"
  }
  return value
}