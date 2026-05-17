package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.account.provider.TouristProvider
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.EmptyCoroutineExceptionHandler
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
@ImplProvider(clazz = IAccountService::class)
@ImplProvider(clazz = IAccountEditService::class)
object AccountService : IAccountService, IAccountEditService {

  override val state = MutableStateFlow<AccountState>(AccountState.Logout(null))

  override var accountCoroutineScope: CoroutineScope =
    CoroutineScope(SupervisorJob(appCoroutineScope.coroutineContext[Job]) + EmptyCoroutineExceptionHandler)

  init {
    val tokenBean = TokenProvider.stateFlow.value
    if (tokenBean != null) {
      // 初始状态就是登陆状态
      val login = AccountState.Login(tokenBean.info.data.stuNum)
      login.userInfo.value = UserInfoProvider.value
      state.value = login
    }
  }

  override fun onLoginSuccess(stuNum: String, token: String, refreshToken: String) {
    AccountSettings.now = AccountSettings.get(stuNum)
    UserInfoProvider.clear()
    TouristProvider.set(false)
    TokenProvider.set(TokenBean(token = token, refreshToken = refreshToken))
    refreshInfo()
    resetAccountCoroutineScope()
    state.value = AccountState.Login(stuNum)
  }

  override fun onLogout() {
    TouristProvider.set(false)
    TokenProvider.clear()
    UserInfoProvider.clear()
    AccountSettings.now = AccountSettings.get(null)
    resetAccountCoroutineScope()
    val login = state.value as? AccountState.Login
    state.value = AccountState.Logout(login)
  }

  override fun onTouristMode() {
    state.value = AccountState.Tourist
    UserInfoProvider.clear()
    TokenProvider.clear()
    TouristProvider.set(true)
  }

  override fun refreshInfo() {
    UserInfoProvider.refresh()
  }

  private fun resetAccountCoroutineScope() {
    val oldScope = accountCoroutineScope
    val supervisor = SupervisorJob(appCoroutineScope.coroutineContext[Job])
    accountCoroutineScope = CoroutineScope(supervisor + EmptyCoroutineExceptionHandler)
    oldScope.cancel()
  }
}



