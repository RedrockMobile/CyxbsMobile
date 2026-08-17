package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.account.provider.TouristProvider
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.EmptyCoroutineExceptionHandler
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
@ImplProvider(clazz = IAccountService::class)
@ImplProvider(clazz = IAccountEditService::class)
object AccountService : IAccountService, IAccountEditService {

  private val initialState: AccountState = AccountState.Logout(null)
  private val initialSession = AccountSession(generation = 0, state = initialState)
  private val initialAccountScope = createAccountCoroutineScope()
  override val state = MutableStateFlow(initialState)
  override val session = MutableStateFlow(initialSession)
  private val sessionPublicationGuard = SynchronizedObject()
  private var sessionGeneration = 0L

  /** 单一生命周期绑定，保证 session identity 与账号 scope 永远成对替换。 */
  private data class LifecycleBinding(
    val session: AccountSession,
    val scope: CoroutineScope,
  )

  private var lifecycleBinding = LifecycleBinding(initialSession, initialAccountScope)

  override val accountCoroutineScope: CoroutineScope
    get() = synchronized(sessionPublicationGuard) { lifecycleBinding.scope }

  /** 在同一账户 publication 临界区内核验绑定，防止旧 session 取得新 scope。 */
  override fun accountCoroutineScopeFor(expectedSession: AccountSession): CoroutineScope? =
    synchronized(sessionPublicationGuard) {
      lifecycleBinding.takeIf { it.session === expectedSession }?.scope
    }

  init {
    val tokenBean = TokenProvider.stateFlow.value
    if (tokenBean != null) {
      // 启动恢复时也要校验资料归属，避免历史版本遗留的跨账号缓存直接进入当前 Login。
      val stuNum = tokenBean.info.data.stuNum
      val cachedUserInfo = UserInfoProvider.value?.takeIf { it.stuNum == stuNum }
      if (cachedUserInfo == null && UserInfoProvider.value != null) UserInfoProvider.clear()
      val login = AccountState.Login(stuNum)
      login.userInfo.value = cachedUserInfo
      val publishedSession = publishAccountSession(resetAccountScope = false) { login }
      if (cachedUserInfo == null) UserInfoProvider.refresh(publishedSession)
    }
  }

  /**
   * 发布一次登录生命周期，并在同一线性化边界内替换身份 provider、scope 与公开状态。
   *
   * token 已由调用方在锁外完成解析；临界区内只执行同步持久化和内存 publication，不执行网络 I/O。用户资料刷新
   * 携带本次发布的 session，即使同学号重新登录，旧请求也不能写入新 generation。
   */
  override fun onLoginSuccess(stuNum: String, token: String, refreshToken: String) {
    val tokenBean = TokenBean(token = token, refreshToken = refreshToken)
    require(tokenBean.info.data.stuNum == stuNum) { "token account does not match login account" }
    val publishedSession = publishAccountSession(
      updateIdentityProviders = {
        AccountSettings.now = AccountSettings.get(stuNum)
        UserInfoProvider.clear()
        TouristProvider.set(false)
        TokenProvider.set(tokenBean)
      },
    ) { AccountState.Login(stuNum) }
    UserInfoProvider.refresh(publishedSession)
  }

  /** 清空当前身份 provider，并与 scope、session/state 一起线性化发布登出生命周期。 */
  override fun onLogout() {
    publishAccountSession(
      updateIdentityProviders = {
        TouristProvider.set(false)
        TokenProvider.clear()
        UserInfoProvider.clear()
        AccountSettings.now = AccountSettings.get(null)
      },
    ) { previous -> AccountState.Logout(previous as? AccountState.Login) }
  }

  /**
   * 进入游客模式并切断上一账户身份的全部协程任务。
   *
   * 身份 provider、账号 scope、权威 session 与兼容 state 在同一写方临界区内完成换代；调用方观察到游客状态时，
   * 已不存在上一账号 token、资料或可挂载新任务的旧 scope。
   */
  override fun onTouristMode() {
    publishAccountSession(
      updateIdentityProviders = {
        UserInfoProvider.clear()
        TokenProvider.clear()
        TouristProvider.set(true)
        AccountSettings.now = AccountSettings.get(null)
      },
    ) { AccountState.Tourist }
  }

  /** 刷新当前登录账号的用户资料；非登录状态不发起请求。 */
  override fun refreshInfo() {
    val currentSession = synchronized(sessionPublicationGuard) {
      lifecycleBinding.session.takeIf { it.state is AccountState.Login }
    } ?: return
    UserInfoProvider.refresh(currentSession)
  }

  /** Token 刷新请求在一次调用内冻结的 session、源 TokenBean 与剩余有效期。 */
  internal data class TokenLifecycleSnapshot(
    val session: AccountSession,
    val token: TokenBean,
    val tokenRemainTime: Duration,
  )

  /**
   * 在 publication guard 内冻结登录 session 与 token，防止调用方分别读取后拼出混合生命周期。
   *
   * 返回 `null` 表示当前未登录、token 缺失或 token 学号与登录态不一致，调用方必须 fail-closed。
   */
  internal fun freezeTokenLifecycle(): TokenLifecycleSnapshot? =
    synchronized(sessionPublicationGuard) {
      val currentSession = lifecycleBinding.session
      val login = currentSession.state as? AccountState.Login ?: return@synchronized null
      val token = TokenProvider.stateFlow.value ?: return@synchronized null
      if (token.info.data.stuNum != login.stuNum) return@synchronized null
      TokenLifecycleSnapshot(currentSession, token, TokenProvider.getTokenRemainTime())
    }

  /** 仅当冻结 session 仍是当前登录 generation 时返回其当前 token。 */
  internal fun currentTokenFor(expectedSession: AccountSession): TokenBean? =
    synchronized(sessionPublicationGuard) {
      val login = expectedSession.state as? AccountState.Login ?: return@synchronized null
      if (lifecycleBinding.session !== expectedSession) return@synchronized null
      TokenProvider.stateFlow.value?.takeIf { it.info.data.stuNum == login.stuNum }
    }

  /** 判断 session 与源 TokenBean identity 是否仍同时属于当前生命周期。 */
  internal fun isCurrentTokenLifecycle(
    expectedSession: AccountSession,
    expectedToken: TokenBean,
  ): Boolean = synchronized(sessionPublicationGuard) {
    lifecycleBinding.session === expectedSession && TokenProvider.stateFlow.value === expectedToken
  }

  /**
   * 仅当请求冻结的 session 与源 TokenBean 仍同时权威时，强制该 token 过期。
   *
   * 校验与 [TokenProvider.forceTokenExpired] 共用 publication guard，避免“校验通过后 refresh 或切号，旧响应再
   * 使新 token 过期”的 check-then-act 窗口。返回 `false` 表示请求已陈旧，调用方不得更新节流状态。
   */
  internal fun expireTokenIfCurrentTokenLifecycle(
    expectedSession: AccountSession,
    expectedToken: TokenBean,
  ): Boolean = synchronized(sessionPublicationGuard) {
    if (
      lifecycleBinding.session !== expectedSession ||
      TokenProvider.stateFlow.value !== expectedToken
    ) return@synchronized false
    TokenProvider.forceTokenExpired()
    true
  }

  /**
   * 条件提交 refresh 结果。
   *
   * 网络请求在锁外执行；只有 session identity、源 TokenBean identity 与返回 token 学号都匹配时才允许持久化。
   * 返回 `false` 表示结果已陈旧，调用方只能丢弃。
   */
  internal fun commitRefreshedToken(
    expectedSession: AccountSession,
    expectedToken: TokenBean,
    refreshedToken: TokenBean,
  ): Boolean = synchronized(sessionPublicationGuard) {
    val login = expectedSession.state as? AccountState.Login ?: return@synchronized false
    if (
      lifecycleBinding.session !== expectedSession ||
      TokenProvider.stateFlow.value !== expectedToken ||
      refreshedToken.info.data.stuNum != login.stuNum
    ) return@synchronized false
    TokenProvider.set(refreshedToken)
    true
  }

  /**
   * refreshToken 失效时条件登出。
   *
   * 校验和生命周期 publication 共用同一把锁，消除“校验通过后切号，旧响应再清除新账号”的窗口。成功时返回
   * 新发布的 Logout session，供后续提示或导航绑定其账号 scope；结果已陈旧时返回 `null`。
   */
  internal fun logoutIfCurrentTokenLifecycle(
    expectedSession: AccountSession,
    expectedToken: TokenBean,
  ): AccountSession? = synchronized(sessionPublicationGuard) {
    if (
      lifecycleBinding.session !== expectedSession ||
      TokenProvider.stateFlow.value !== expectedToken
    ) return@synchronized null
    publishAccountSessionLocked(
      updateIdentityProviders = {
        TouristProvider.set(false)
        TokenProvider.clear()
        UserInfoProvider.clear()
        AccountSettings.now = AccountSettings.get(null)
      },
    ) { previous -> AccountState.Logout(previous as? AccountState.Login) }
  }

  /**
   * 仅当 [expectedSession] 仍权威时，在 publication guard 内登记一次用户资料刷新。
   *
   * [begin] 只能更新 UserInfoProvider 自身的同步请求序号，不得挂起或反向调用 AccountService。把登记放在账号锁内，
   * 可防止迟到的旧 generation 调用在切号后再递增全局序号、使新账号请求失效。
   */
  internal fun beginUserInfoRefresh(
    expectedSession: AccountSession,
    begin: () -> Long,
  ): Long? = synchronized(sessionPublicationGuard) {
    if (lifecycleBinding.session !== expectedSession) return@synchronized null
    begin()
  }

  /**
   * 条件提交用户资料。
   *
   * 学号只能用于附加一致性校验；真正区分同学号重新登录的是 [expectedSession] identity。持久化与公开状态更新
   * 在同一 publication guard 内完成，避免校验后切号。[isRequestCurrent] 用于 provider 复核同 session 下最新请求，回调
   * 必须是无挂起的纯状态检查，且不得反向调用 AccountService。
   */
  internal fun commitUserInfo(
    expectedSession: AccountSession,
    userInfo: UserInfo,
    isRequestCurrent: () -> Boolean = { true },
  ): Boolean = synchronized(sessionPublicationGuard) {
    val login = expectedSession.state as? AccountState.Login ?: return@synchronized false
    if (
      lifecycleBinding.session !== expectedSession ||
      login.stuNum != userInfo.stuNum ||
      !isRequestCurrent()
    ) return@synchronized false
    UserInfoProvider.set(userInfo)
    login.userInfo.value = userInfo
    true
  }

  /**
   * 原子发布一次新的账户生命周期。
   *
   * [updateIdentityProviders] 与 lifecycle binding 在同一写方临界区内换代；旧 scope 会先取消，再通知权威 [session]
   * 及兼容 [state]。因此 collector 在发布现场同步读取 [accountCoroutineScopeFor] 时，只会得到对应新 scope，旧 session
   * 则 fail-closed。锁内不得执行网络 I/O 或挂起操作；回调仅允许同步更新 provider。
   *
   * 两条 StateFlow 是兼容层的顺序发布，不承诺跨 Flow 的双向原子观察；需要生命周期门禁的调用方必须以 [session]
   * identity 和 [accountCoroutineScopeFor] 为准。
   */
  private fun publishAccountSession(
    resetAccountScope: Boolean = true,
    updateIdentityProviders: () -> Unit = {},
    nextState: (AccountState) -> AccountState,
  ): AccountSession = synchronized(sessionPublicationGuard) {
    publishAccountSessionLocked(resetAccountScope, updateIdentityProviders, nextState)
  }

  /** publication guard 已持有时执行同步换代；禁止从锁内调用任何挂起或网络操作。 */
  private fun publishAccountSessionLocked(
    resetAccountScope: Boolean = true,
    updateIdentityProviders: () -> Unit = {},
    nextState: (AccountState) -> AccountState,
  ): AccountSession {
    val previousBinding = lifecycleBinding
    val publishedState = nextState(previousBinding.session.state)
    val nextScope = if (resetAccountScope) createAccountCoroutineScope() else previousBinding.scope
    updateIdentityProviders()
    sessionGeneration += 1
    val publishedSession = AccountSession(sessionGeneration, publishedState)
    lifecycleBinding = LifecycleBinding(publishedSession, nextScope)
    if (resetAccountScope) previousBinding.scope.cancel()
    // 先完成 scope 换代和旧 scope 取消，再通知 collector；session 是权威入口，state 仅作兼容投影。
    session.value = publishedSession
    state.value = publishedState
    return publishedSession
  }

  /** 创建尚未公开的账号 scope；只有与对应 session 组成 binding 后才会对外可见。 */
  private fun createAccountCoroutineScope(): CoroutineScope {
    val supervisor = SupervisorJob(appCoroutineScope.coroutineContext[Job])
    return CoroutineScope(supervisor + EmptyCoroutineExceptionHandler)
  }
}



