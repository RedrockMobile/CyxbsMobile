package com.cyxbs.pages.affair.repos

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.affair.bean.AffairEntity
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/21
 */
object LocalUpdateAffairRepository {

  private const val SETTING_KEY_AFFAIR_LOCAL_UPDATE = "setting_key_affair_local_update"

  private val updateAffairMap = mutableMapOf<String, MutableMap<Int, AffairEntity>>()

  private val synchronizedObject = SynchronizedObject()

  fun get(stuNum: String): Map<Int, AffairEntity> {
    return updateAffairMap[stuNum] ?: synchronized(synchronizedObject) {
      updateAffairMap.getOrPut(stuNum) {
        loadLocalUpdateAffair(stuNum)
      }
    }
  }

  fun add(stuNum: String, affair: AffairEntity) {
    require(affair.remoteId > 0) { "remoteId 需要大于 0, $affair" }
    synchronized(synchronizedObject) {
      val map = updateAffairMap.getOrPut(stuNum) {
        loadLocalUpdateAffair(stuNum)
      }
      map[affair.remoteId] = affair
      saveAffair(stuNum, map)
    }
  }

  fun remove(stuNum: String, remoteId: Int) {
    require(remoteId > 0) { "remoteId 需要大于 0" }
    synchronized(synchronizedObject) {
      val map = updateAffairMap.getOrPut(stuNum) {
        loadLocalUpdateAffair(stuNum)
      }
      map.remove(remoteId)
      saveAffair(stuNum, map)
    }
  }

  private fun loadLocalUpdateAffair(stuNum: String): MutableMap<Int, AffairEntity> {
    // 读取磁盘
    val accountSettings = AccountSettings.get(stuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR_LOCAL_UPDATE)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<MutableMap<Int, AffairEntity>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR_LOCAL_UPDATE)
      }.getOrNull()
    } ?: mutableMapOf()
  }

  private fun saveAffair(stuNum: String, map: Map<Int, AffairEntity>) {
    // 保存进磁盘
    AccountSettings.get(stuNum).putString(
      SETTING_KEY_AFFAIR_LOCAL_UPDATE,
      defaultJson.encodeToString<Map<Int, AffairEntity>>(map)
    )
  }
}