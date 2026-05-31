package com.cyxbs.pages.ufield.fairground.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.ufield.fairground.model.FairgroundDaysCache
import com.cyxbs.pages.ufield.fairground.network.FairgroundApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 邮乐园入口页 ViewModel（commonMain）
 *
 * - 个人信息（昵称、头像）来自账户中心 [IAccountService]，进入页面时先调用
 *   [IAccountEditService.refreshInfo] 触发一次刷新（刷新后会同步到 [IAccountService.userInfo]）。
 * - 来到天数：优先用本地缓存的首日反推（离线可用），再请求网络刷新并回写缓存。
 */
class FairgroundComposeViewModel : BaseViewModel() {

  private val accountService = IAccountService::class.impl()

  /** 用户信息（昵称、头像），未登录时为 null，随账户状态变化而更新 */
  @OptIn(ExperimentalCoroutinesApi::class)
  val userInfo: StateFlow<UserInfo?> = accountService.state
    .flatMapLatest { state ->
      (state as? AccountState.Login)?.userInfo ?: flowOf(null)
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, accountService.userInfo)

  /** 来到邮乐园的天数，默认 "1" 与原页面初始文案一致 */
  val days = mutableStateOf(FairgroundDaysCache.getCachedDays()?.toString() ?: "1")

  init {
    // 个人信息从账户中心获取，获取前先触发一次刷新
    IAccountEditService::class.impl().refreshInfo()
    getDays()
  }

  private fun getDays() {
    viewModelScope.launch {
      runCatchingCoroutine {
        FairgroundApiService::class.impl().getDays()
      }.mapCatching {
        it.data
      }.onSuccess {
        days.value = it.days
        // 以服务端为准回写本地缓存，下次进入可离线推算
        it.days.toIntOrNull()?.let { d -> FairgroundDaysCache.saveDaysFromServer(d) }
      }
    }
  }
}
