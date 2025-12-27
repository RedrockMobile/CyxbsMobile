package com.cyxbs.pages.affair.repos

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/21
 */
object LocalDeleteAffairRepository {

  private const val SETTING_KEY_AFFAIR_LOCAL_DELETE = "setting_key_affair_local_delete"

  private val deleteAffairMap = mutableMapOf<String, MutableSet<String>>()

  private val synchronizedObject = SynchronizedObject()

  fun get(stuNum: String): Set<String> {
    return deleteAffairMap[stuNum] ?: synchronized(synchronizedObject) {
      deleteAffairMap.getOrPut(stuNum) {
        loadLocalDeleteAffair(stuNum)
      }
    }
  }

  fun add(stuNum: String, localId: String) {
    require(localId.isNotEmpty()) { "localId 不能为空" }
    synchronized(synchronizedObject) {
      val set = deleteAffairMap.getOrPut(stuNum) {
        loadLocalDeleteAffair(stuNum)
      }
      set.add(localId)
      saveAffair(stuNum, set)
    }
  }

  fun remove(stuNum: String, localId: String) {
    require(localId.isNotEmpty()) { "localId 不能为空" }
    synchronized(synchronizedObject) {
      val set = deleteAffairMap.getOrPut(stuNum) {
        loadLocalDeleteAffair(stuNum)
      }
      set.remove(localId)
      saveAffair(stuNum, set)
    }
  }

  private fun loadLocalDeleteAffair(stuNum: String): MutableSet<String> {
    // 读取磁盘
    val accountSettings = AccountSettings.get(stuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR_LOCAL_DELETE)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<MutableSet<String>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR_LOCAL_DELETE)
      }.getOrNull()
    } ?: mutableSetOf()
  }

  private fun saveAffair(stuNum: String, set: Set<String>) {
    // 保存进磁盘
    AccountSettings.get(stuNum).putString(
      SETTING_KEY_AFFAIR_LOCAL_DELETE,
      defaultJson.encodeToString<Set<String>>(set)
    )
  }
}