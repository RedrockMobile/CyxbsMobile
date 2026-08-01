package com.cyxbs.functions.code.js.storage

import com.cyxbs.functions.code.js.JsProgramRef
import com.cyxbs.functions.code.js.JsSourcePackage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmInline

/**
 * JavaScript 源码包的持久化接口。
 *
 * 源码是可恢复字节码缓存的主数据，存储实现不能把它和普通缓存一起无条件清理。
 */
interface JsSourcePackageStore {

  /**
   * 保存或覆盖相同 [com.cyxbs.functions.code.js.JsProgramRef] 的源码包。
   */
  suspend fun writeSource(sourcePackage: JsSourcePackage)

  /**
   * 读取指定源码包；不存在时返回 null。
   */
  suspend fun readSource(reference: JsProgramRef): JsSourcePackage?

  /**
   * 删除指定源码包。
   */
  suspend fun removeSource(reference: JsProgramRef)
}

/**
 * QuickJS 字节码缓存键。
 *
 * [value] 是固定长度 SHA-256，可安全用于本地文件名。
 */
@JvmInline
value class JsBytecodeCacheKey(val value: String) {

  init {
    require(KEY_REGEX.matches(value)) { "Bytecode cache key must be a lowercase SHA-256 hex string." }
  }

  companion object {
    private val KEY_REGEX = Regex("[0-9a-f]{64}")
  }
}

/**
 * 可删除、可重建的 QuickJS 字节码缓存。
 *
 * 实现读取到损坏内容时应把它视为未命中，而不是把未知字节交给 QuickJS 执行。
 */
interface JsBytecodeCache {

  /**
   * 读取字节码；不存在或完整性校验失败时返回 null。
   */
  suspend fun readBytecode(key: JsBytecodeCacheKey): ByteArray?

  /**
   * 原子保存字节码。
   */
  suspend fun writeBytecode(key: JsBytecodeCacheKey, bytecode: ByteArray)

  /**
   * 删除指定缓存。
   */
  suspend fun removeBytecode(key: JsBytecodeCacheKey)

  /**
   * 清空全部可重建字节码，但不得删除源码主数据。
   */
  suspend fun clearBytecode()
}

/**
 * 仅保存在内存中的源码与字节码存储。
 *
 * 适合测试、预览或不需要跨进程缓存的场景；生产动态下发应使用 [OkioJsProgramStorage]。
 */
class InMemoryJsProgramStorage : JsSourcePackageStore, JsBytecodeCache {
  private val mutex = Mutex()
  private val sources = mutableMapOf<JsProgramRef, JsSourcePackage>()
  private val bytecodes = mutableMapOf<JsBytecodeCacheKey, ByteArray>()

  override suspend fun writeSource(sourcePackage: JsSourcePackage) {
    mutex.withLock {
      sources[sourcePackage.reference] = sourcePackage
    }
  }

  override suspend fun readSource(reference: JsProgramRef): JsSourcePackage? {
    return mutex.withLock {
      sources[reference]
    }
  }

  override suspend fun removeSource(reference: JsProgramRef) {
    mutex.withLock {
      sources.remove(reference)
    }
  }

  override suspend fun readBytecode(key: JsBytecodeCacheKey): ByteArray? {
    return mutex.withLock {
      bytecodes[key]?.copyOf()
    }
  }

  override suspend fun writeBytecode(key: JsBytecodeCacheKey, bytecode: ByteArray) {
    mutex.withLock {
      bytecodes[key] = bytecode.copyOf()
    }
  }

  override suspend fun removeBytecode(key: JsBytecodeCacheKey) {
    mutex.withLock {
      bytecodes.remove(key)
    }
  }

  override suspend fun clearBytecode() {
    mutex.withLock {
      bytecodes.clear()
    }
  }
}
