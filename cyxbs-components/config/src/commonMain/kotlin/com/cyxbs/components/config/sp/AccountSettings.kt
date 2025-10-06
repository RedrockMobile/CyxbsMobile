package com.cyxbs.components.config.sp

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * 获取指定学号的 Key-Value 对象本地存储
 *
 * @date 2025/9/28
 */
class AccountSettings(val stuNum: String?) : PreferencesSettings("AccountSettings-${stuNum}") {
  companion object {

    private val map = mutableMapOf<String?, AccountSettings>()

    // 获取当前登录人的 AccountSettings
    var now: AccountSettings = get(IAccountService::class.impl().stuNum)

    private val mapSynchronized = SynchronizedObject()

    // 获取指定学号的 AccountSettings
    // stuNum 为 null 表示未登录
    fun get(stuNum: String?): AccountSettings {
      val value = map[stuNum]
      if (value != null) return value
      return synchronized(mapSynchronized)  {
        map.getOrPut(stuNum) { AccountSettings(stuNum) }
      }
    }
  }
}