package com.cyxbs.pages.affair.repos

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.affair.bean.AffairEntity
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.uuid.ExperimentalUuidApi

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/20
 */
object LocalAddAffairRepository {

  private const val SETTING_KEY_AFFAIR_LOCAL_ADD = "setting_key_affair_local_add"

  private val addAffairMap = HashMap<String, MutableMap<String, AffairEntity>>()

  private val synchronizedObject = SynchronizedObject()

  fun get(stuNum: String): Map<String, AffairEntity> {
    return addAffairMap[stuNum] ?: synchronized(synchronizedObject) {
      addAffairMap.getOrPut(stuNum) {
        loadLocalAddAffair(stuNum)
      }
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  fun add(stuNum: String, affair: AffairEntity) {
    require(affair.localId.isNotEmpty()) { "localId 不能为空, $affair" }
    synchronized(synchronizedObject) {
      val map = addAffairMap.getOrPut(stuNum) {
        loadLocalAddAffair(stuNum)
      }
      map[affair.localId] = affair
      saveAffair(stuNum, map)
    }
  }

  fun update(stuNum: String, affair: AffairEntity) {
    require(affair.localId.isNotEmpty()) { "localId 不能为空, $affair" }
    synchronized(synchronizedObject) {
      val map = addAffairMap.getOrPut(stuNum) {
        loadLocalAddAffair(stuNum)
      }
      if (map.containsKey(affair.localId)) {
        map[affair.localId] = affair
        saveAffair(stuNum, map)
      }
    }
  }

  fun remove(stuNum: String, localId: String) {
    require(localId.isNotEmpty()) { "localId 不能为空" }
    synchronized(synchronizedObject) {
      val map = addAffairMap.getOrPut(stuNum) {
        loadLocalAddAffair(stuNum)
      }
      if (map.remove(localId) != null) {
        saveAffair(stuNum, map)
      }
    }
  }

  private fun loadLocalAddAffair(stuNum: String): MutableMap<String, AffairEntity> {
    // 读取磁盘
    val accountSettings = AccountSettings.get(stuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR_LOCAL_ADD)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<MutableMap<String, AffairEntity>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR_LOCAL_ADD)
      }.getOrNull()
    } ?: mutableMapOf()
  }

  private fun saveAffair(stuNum: String, map: Map<String, AffairEntity>) {
    // 保存进磁盘
    AccountSettings.get(stuNum).putString(
      SETTING_KEY_AFFAIR_LOCAL_ADD,
      defaultJson.encodeToString<Map<String, AffairEntity>>(map)
    )
  }
}