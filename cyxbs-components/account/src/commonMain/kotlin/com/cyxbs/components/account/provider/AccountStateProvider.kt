package com.cyxbs.components.account.provider

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.init.appCoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 用户登录状态提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object AccountStateProvider {
  @OptIn(ExperimentalCoroutinesApi::class)
  val stateFlow: StateFlow<AccountState> = TouristProvider.stateFlow.flatMapLatest {
    if (it) flowOf(AccountState.Tourist) else {
      TokenProvider.stateFlow.map { token ->
        if (token == null) AccountState.Logout else AccountState.Login
      }
    }
  }.stateIn(
    scope = appCoroutineScope,
    started = SharingStarted.Eagerly,
    initialValue = if (TouristProvider.stateFlow.value) AccountState.Tourist
    else if (TokenProvider.stateFlow.value != null) AccountState.Login
    else AccountState.Logout
  )
}