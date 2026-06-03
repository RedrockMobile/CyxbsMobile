package com.cyxbs.pages.mine.user.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.mine.user.MineNavPlatform
import com.cyxbs.pages.mine.user.network.MineApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「我的」主页 ViewModel（commonMain）
 *
 * - 个人信息（昵称、头像）来自账户中心 [IAccountService]，进入页面时先调用
 *   [IAccountEditService.refreshInfo] 触发一次刷新。
 * - 签到状态（连续签到天数、今日是否已签到）走 commonMain ktorfit [MineApiService]。
 * - 未读消息数来自平台层 [MineNavPlatform]（底层为 mobileMain 的 INotificationService）。
 */
class MineComposeViewModel : BaseViewModel() {

  private val accountService = IAccountService::class.impl()

  /** 用户信息（用户名、头像），未登录时为 null，随账户状态变化而更新 */
  @OptIn(ExperimentalCoroutinesApi::class)
  val userInfo: StateFlow<UserInfo?> = accountService.state
    .flatMapLatest { state ->
      (state as? AccountState.Login)?.userInfo ?: flowOf(null)
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, accountService.userInfo)

  /** 未读消息数，平台无实现时恒为 0（红点不显示） */
  val unreadCount: StateFlow<Int> =
    MineNavPlatform::class.implOrNull()?.unreadCount ?: MutableStateFlow(0)

  /** 连续签到天数 */
  val serialDays = mutableIntStateOf(0)

  /** 今日是否已签到 */
  val isChecked = mutableStateOf(false)

  init {
    // 个人信息从账户中心获取，获取前先触发一次刷新
    IAccountEditService::class.impl().refreshInfo()
    refreshSignStatus()
  }

  private fun refreshSignStatus() {
    if (!accountService.isLogin()) return
    viewModelScope.launch {
      runCatchingCoroutine {
        MineApiService::class.impl().getScoreStatus()
      }.mapCatching {
        it.data
      }.onSuccess {
        serialDays.intValue = it.serialDays
        isChecked.value = it.isChecked
      }
    }
  }
}
