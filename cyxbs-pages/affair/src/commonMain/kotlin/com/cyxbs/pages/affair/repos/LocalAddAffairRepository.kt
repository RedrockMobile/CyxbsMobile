package com.cyxbs.pages.affair.repos

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.bean.AffairEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/20
 */
object LocalAddAffairRepository {

  private const val SETTING_KEY_AFFAIR_LOCAL_ADD = "setting_key_affair_local_add"

  private val addAffairFlow = MutableStateFlow<Map<Int, AffairEntity>>(emptyMap())

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .onEach {
        addAffairFlow.value = if (it != null) loadLocalAddAffair() else emptyMap()
      }.launchIn(appCoroutineScope)
  }

  fun add(affair: AffairEntity): AffairEntity {
    while (true) {
      val prevValue = addAffairFlow.value
      val id = (prevValue.minOfOrNull { it.value.id } ?: 0) - 1
      val newAffair = affair.copy(id = id)
      val nextValue = prevValue.toMutableMap().apply { put(id, newAffair) }
      if (addAffairFlow.compareAndSet(prevValue, nextValue)) {
        saveAffair()
        return newAffair
      }
    }
  }

  fun update(affair: AffairEntity) {
    if (affair.id < 0) {
      val old = addAffairFlow.getAndUpdate { map ->
        if (map.containsKey(affair.id)) {
          map.toMutableMap().apply { put(affair.id, affair) }
        } else map
      }
      if (old.containsKey(affair.id)) {
        saveAffair()
      }
    }
  }

  fun delete(id: Int) {
    if (id < 0) {
      val old = addAffairFlow.getAndUpdate { map ->
        if (map.containsKey(id)) {
          map.toMutableMap().apply { remove(id) }
        } else map
      }
      if (old.containsKey(id)) {
        saveAffair()
      }
    }
  }

  fun getFlow(): StateFlow<Map<Int, AffairEntity>> {
    return addAffairFlow
  }

  private fun loadLocalAddAffair(): Map<Int, AffairEntity> {
    // 读取磁盘
    val accountSettings = AccountSettings.now
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR_LOCAL_ADD)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<Map<Int, AffairEntity>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR_LOCAL_ADD)
      }.getOrNull()
    } ?: emptyMap()
  }

  private fun saveAffair() {
    // 保存进磁盘
    AccountSettings.now.putString(
      SETTING_KEY_AFFAIR_LOCAL_ADD,
      defaultJson.encodeToString<Map<Int, AffairEntity>>(addAffairFlow.value)
    )
  }
}