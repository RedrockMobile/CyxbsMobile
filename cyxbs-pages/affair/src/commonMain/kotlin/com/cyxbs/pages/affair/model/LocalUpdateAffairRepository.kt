package com.cyxbs.pages.affair.model

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.api.IAffairService2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/21
 */
object LocalUpdateAffairRepository {

  private const val SETTING_KEY_AFFAIR_LOCAL_UPDATE = "setting_key_affair_local_update"

  private val updateAffairFlow = MutableStateFlow<Map<Int, IAffairService2.Affair>>(emptyMap())

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .onEach {
        updateAffairFlow.value = if (it != null) loadLocalUpdateAffair() else emptyMap()
      }.launchIn(appCoroutineScope)
  }

  fun update(affair: IAffairService2.Affair) {
    if (affair.id >= 0) {
      updateAffairFlow.update { map ->
        map.toMutableMap().apply { put(affair.id, affair) }
      }
      saveAffair()
    }
  }

  fun delete(id: Int) {
    if (id >= 0) {
      val old = updateAffairFlow.getAndUpdate { map ->
        if (map.containsKey(id)) {
          map.toMutableMap().apply { remove(id) }
        } else map
      }
      if (old.containsKey(id)) {
        saveAffair()
      }
    }
  }

  fun getFlow(): StateFlow<Map<Int, IAffairService2.Affair>> {
    return updateAffairFlow
  }

  private fun loadLocalUpdateAffair(): Map<Int, IAffairService2.Affair> {
    // 读取磁盘
    val accountSettings = AccountSettings.now
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR_LOCAL_UPDATE)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<Map<Int, IAffairService2.Affair>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR_LOCAL_UPDATE)
      }.getOrNull()
    } ?: emptyMap()
  }

  private fun saveAffair() {
    // 保存进磁盘
    AccountSettings.now.putString(
      SETTING_KEY_AFFAIR_LOCAL_UPDATE,
      defaultJson.encodeToString<Map<Int, IAffairService2.Affair>>(updateAffairFlow.value)
    )
  }
}