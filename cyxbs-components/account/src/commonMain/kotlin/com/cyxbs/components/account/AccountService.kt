package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.TokenState
import com.cyxbs.components.account.api.AccountToken
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TouristProvider
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.pages.login.api.ILoginService
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.EmptyCoroutineExceptionHandler
import com.cyxbs.components.utils.extensions.mapState
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 保存登录信息，并为每次登录、登出或游客切换创建独立的账户生命周期。 */
@ImplProvider(clazz = IAccountService::class)
@ImplProvider(clazz = IAccountEditService::class)
object AccountService : IAccountService, IAccountEditService {

  override val session: StateFlow<AccountSession>
    field = TokenStateImpl.load().let { token ->
      val accountState = if (token != null) {
        AccountState.Login(token.stuNum).also {
          it.userInfo.value = UserInfoProvider.loadForAccount(token.stuNum)
        }
      } else {
        AccountState.Logout(null)
      }
      MutableStateFlow(
        createSession(0, accountState, token)
      )
    }

  override val state: StateFlow<AccountState> = session.mapState { it.state }

  init {
    // session 与派生 state 就绪后再请求资料，避免初始化中读取尚未发布的账户状态。
    val login = state.value as? AccountState.Login
    if (login != null && login.userInfo.value == null) refreshInfo()
  }

  /** 先校验登录凭据，再保存并重置 session；无效凭据不会修改原账户，资料刷新使用新作用域。 */
  override fun onLoginSuccess(stuNum: String, token: String, refreshToken: String) {
    val accountToken = TokenStateImpl.toAccountToken(bean = TokenBean(token = token, refreshToken = refreshToken))
    AccountSettings.now = AccountSettings.get(accountToken.stuNum)
    UserInfoProvider.clear()
    TouristProvider.set(false)
    TokenStateImpl.save(accountToken)
    resetAccountSession(AccountState.Login(accountToken.stuNum), accountToken)
    refreshInfo()
  }

  /** 清除登录信息并取消旧账户任务；保留上一 Login 供旧调用方处理退出。 */
  override fun onLogout() {
    TouristProvider.set(false)
    TokenStateImpl.clear()
    UserInfoProvider.clear()
    AccountSettings.now = AccountSettings.get(null)
    val login = state.value as? AccountState.Login
    resetAccountSession(AccountState.Logout(login), token = null)
  }

  /** 游客模式也结束旧登录生命周期，使用独立的游客配置。 */
  override fun onTouristMode() {
    UserInfoProvider.clear()
    TokenStateImpl.clear()
    TouristProvider.set(true)
    AccountSettings.now = AccountSettings.get(null)
    resetAccountSession(AccountState.Tourist, token = null)
  }

  /** 刷新当前登录资料；未登录时不发起请求。 */
  override fun refreshInfo() {
    UserInfoProvider.refresh()
  }

  /**
   * 重置账户生命周期，替代旧实现单独重置 accountCoroutineScope 的方法。
   *
   * 身份切换由调用方串行执行；先取消旧任务，再发布新 session，state 自动投影其业务状态。
   * 即使学号或游客状态相同，也会创建独立的新作用域。
   * @param next 新生命周期的身份状态。
   * @param token 已持久化的登录凭据；调用方须明确为登出和游客传入 null。
   */
  private fun resetAccountSession(next: AccountState, token: AccountToken?) {
    val previous = session.value
    val current = createSession(generation = previous.generation + 1, state = next, token = token)
    previous.accountCoroutineScope.cancel()
    session.value = current
  }

  /** 创建身份与凭据状态；TokenState 只持有作用域及失效回调，不反向持有 session。 */
  private fun createSession(
    generation: Long,
    state: AccountState,
    token: AccountToken?
  ): AccountSession {
    val scope = createAccountScope()
    return AccountSession(
      generation = generation,
      state = state,
      accountCoroutineScope = scope,
      tokenState = token?.let {
        TokenStateImpl(
          accountScope = scope,
          initialToken = it,
          onExpired = ::onTokenExpired
        )
      }
    )
  }

  /** TokenState 已在主线程确认凭据失效；账户层负责结束身份、提示并进入登录页。 */
  private fun onTokenExpired(source: TokenState, msg: String) {
    if (session.value.tokenState !== source) return
    onLogout()
    toastLong("登录已过期，请重新登录\n原因：$msg")
    ILoginService::class.impl().jumpToLoginPage()
  }

  /** 沿用旧实现的监督作用域，改由对应的 AccountSession 持有。 */
  private fun createAccountScope(): CoroutineScope = CoroutineScope(
    SupervisorJob(appCoroutineScope.coroutineContext[Job]) + Dispatchers.Main.immediate + EmptyCoroutineExceptionHandler,
  )
}
