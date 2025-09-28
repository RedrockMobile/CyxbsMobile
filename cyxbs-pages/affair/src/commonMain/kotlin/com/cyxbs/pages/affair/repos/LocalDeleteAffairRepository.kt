package com.cyxbs.pages.affair.repos

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/21
 */
object LocalDeleteAffairRepository {

  private const val SETTING_KEY_AFFAIR_LOCAL_DELETE = "setting_key_affair_local_delete"

  private val deleteAffairFlow = MutableStateFlow<Set<Int>>(emptySet())

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .onEach {
        deleteAffairFlow.value = if (it != null) loadLocalDeleteAffair() else emptySet()
      }.launchIn(appCoroutineScope)
  }

  fun delete(id: Int) {
    if (id >= 0) {
      deleteAffairFlow.update { map ->
        map.toMutableSet().apply { add(id) }
      }
      saveAffair()
    }
  }

  fun getFlow(): StateFlow<Set<Int>> {
    return deleteAffairFlow
  }

  private fun loadLocalDeleteAffair(): Set<Int> {
    // 读取磁盘
    val accountSettings = AccountSettings.now
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR_LOCAL_DELETE)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<Set<Int>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR_LOCAL_DELETE)
      }.getOrNull()
    } ?: emptySet()
  }

  private fun saveAffair() {
    // 保存进磁盘
    AccountSettings.now.putString(
      SETTING_KEY_AFFAIR_LOCAL_DELETE,
      defaultJson.encodeToString<Set<Int>>(deleteAffairFlow.value)
    )
  }
}