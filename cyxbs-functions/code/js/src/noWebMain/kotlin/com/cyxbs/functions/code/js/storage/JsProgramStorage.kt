package com.cyxbs.functions.code.js.storage

import com.cyxbs.functions.code.js.program.JsProgramRef
import com.cyxbs.functions.code.js.program.JsSourcePackage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JavaScript 源码包的持久化接口。
 *
 * 源码是程序长期主数据，存储实现不能把它和具体引擎的可重建缓存混在一起清理。
 */
interface JsSourcePackageStore {

  /**
   * 保存或覆盖相同 [JsProgramRef] 的源码包。
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
 * 仅保存在内存中的源码存储。
 *
 * 适合测试和教学预览；生产动态下发应使用 [OkioJsProgramStorage]。
 */
class InMemoryJsProgramStorage : JsSourcePackageStore {
  private val mutex = Mutex()
  private val sources = mutableMapOf<JsProgramRef, JsSourcePackage>()

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

}
