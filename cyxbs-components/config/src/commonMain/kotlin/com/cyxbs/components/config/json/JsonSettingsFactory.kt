package com.cyxbs.components.config.json

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**  
 * description: Json存储的工厂类
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/9/27 15:18
 */
object JsonSettingsFactory {

  private val instances = mutableMapOf<String, JsonSettings>()

  private val lock = SynchronizedObject()

  fun get(name: String): JsonSettings {
    require(name.isNotBlank()) {
      "JsonSettings name cannot be blank"
    }

    return synchronized(lock) {
      instances.getOrPut(name) {
        JsonSettings(name)
      }
    }
  }

}