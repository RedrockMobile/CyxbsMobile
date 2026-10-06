package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountState
import kotlinx.coroutines.CancellationException
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.login.api.ILoginService
import com.g985892345.provider.api.init.IKtProviderDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * 账户身份切换时协程作用域的生命周期契约测试。
 *
 * 测试直接覆盖登录、游客与再次登录的连续状态转换，防止旧账号后台任务跨身份存活。
 */
/** 记录条件登出后的导航次数，避免 lifecycle 单测依赖真实页面实现。 */
private object TestLoginService : ILoginService {
  var jumpCount = 0

  override fun jumpToLoginPage() {
    jumpCount += 1
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountServiceScopeTest {

  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    // Provider 注册表跨测试用例复用；重复注册同一接口会被框架主动拒绝。
    if (IAccountService::class !in IKtProviderDelegate.ImplProviderMap) {
      IKtProviderDelegate.addImplProvider(IAccountService::class, "") { AccountService }
    }
    if (ILoginService::class !in IKtProviderDelegate.ImplProviderMap) {
      IKtProviderDelegate.addImplProvider(ILoginService::class, "") { TestLoginService }
    }
    TestLoginService.jumpCount = 0
    AccountService.onLogout()
  }

  @AfterTest
  fun tearDown() {
    AccountService.onLogout()
    kotlinx.coroutines.Dispatchers.resetMain()
  }

  @Test
  fun loginToTouristCancelsOldScopeAndNextLoginGetsUsableNewScope() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val loginScope = AccountService.accountCoroutineScope
    val loginJob = loginScope.launch(dispatcher) { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()
    assertTrue(loginJob.isActive)

    AccountService.onTouristMode()

    val touristScope = AccountService.accountCoroutineScope
    assertIs<AccountState.Tourist>(AccountService.state.value)
    assertNotSame(loginScope, touristScope)
    assertTrue(loginJob.isCancelled)
    assertFalse(touristScope.coroutineContext[Job]!!.isCancelled)

    val touristJob = touristScope.launch(dispatcher) { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()
    assertTrue(touristJob.isActive)

    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-2",
    )

    val nextLoginScope = AccountService.accountCoroutineScope
    assertIs<AccountState.Login>(AccountService.state.value)
    assertNotSame(touristScope, nextLoginScope)
    assertTrue(touristJob.isCancelled)
    assertFalse(nextLoginScope.coroutineContext[Job]!!.isCancelled)
    val nextLoginJob = nextLoginScope.launch(dispatcher) { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()
    assertTrue(nextLoginJob.isActive)
    nextLoginJob.cancel()
  }

  /** 游客状态虽为同一对象，每次重新进入仍应结束旧任务并创建新的生命周期。 */
  @Test
  fun reenteringTouristModeCancelsOldTasksWithoutChangingBusinessState() = runTest(dispatcher) {
    AccountService.onTouristMode()
    val firstSession = AccountService.session.value
    val oldTask = firstSession.accountCoroutineScope.launch(dispatcher) {
      kotlinx.coroutines.awaitCancellation()
    }
    dispatcher.scheduler.runCurrent()

    AccountService.onTouristMode()
    val secondSession = AccountService.session.value

    assertSame(firstSession.state, secondSession.state)
    assertNotSame(firstSession, secondSession)
    assertTrue(oldTask.isCancelled)
    assertTrue(firstSession.accountCoroutineScope.coroutineContext[Job]!!.isCancelled)
    assertFalse(secondSession.accountCoroutineScope.coroutineContext[Job]!!.isCancelled)
  }

  /** 同学号重新登录仍创建新 session，新旧作用域分别属于各自的生命周期。 */
  @Test
  fun sameAccountReloginPublishesNewSessionGenerationAndStateIdentity() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val firstSession = AccountService.session.value
    val firstLogin = assertIs<AccountState.Login>(AccountService.state.value)
    firstLogin.userInfo.value = createUserInfo("20260001", "旧资料")
    val firstScope = AccountService.accountCoroutineScope
    assertSame(firstScope, firstSession.accountCoroutineScope)

    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-2",
    )

    val secondSession = AccountService.session.value
    val secondLogin = assertIs<AccountState.Login>(AccountService.state.value)
    assertEquals("refresh-token-1", (firstSession.tokenState as TokenStateImpl).tokenFlow.value?.refreshToken)
    assertEquals("refresh-token-2", (secondSession.tokenState as TokenStateImpl).tokenFlow.value?.refreshToken)
    assertEquals("20260001", firstSession.accountId)
    assertEquals("20260001", secondSession.accountId)
    assertTrue(secondSession.generation > firstSession.generation)
    assertNotSame(firstLogin, secondLogin)
    assertSame(secondLogin, secondSession.state)
    assertNull(secondLogin.userInfo.value)
    assertNotSame(firstScope, AccountService.accountCoroutineScope)
    assertSame(secondSession.accountCoroutineScope, AccountService.accountCoroutineScope)
    assertTrue(firstSession.accountCoroutineScope.coroutineContext[Job]!!.isCancelled)
    // 旧 session 即使仍被请求方持有，也只能创建立即取消的任务，不能借到新账户 scope。
    val staleTask = firstSession.accountCoroutineScope.launch(dispatcher) {
      error("旧账户任务不应执行")
    }
    dispatcher.scheduler.runCurrent()
    assertTrue(staleTask.isCancelled)
  }

  @Test
  fun authoritativeSessionCollectorSeesBoundScopeAfterOldScopeCancelled() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val oldSession = AccountService.session.value
    val oldScope = oldSession.accountCoroutineScope
    val oldJob = oldScope.launch(dispatcher) { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()

    // Unconfined collector 会在 session.value 发布现场同步恢复，精确核验 publication 顺序。
    val bindingAtPublication = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      val newSession = AccountService.session.drop(1).first()
      // 在 session 发布现场读取，派生 state 不应等待另一个协程更新缓存。
      assertSame(newSession.state, AccountService.state.value)
      assertSame(newSession.state, AccountService.state.replayCache.single())
      Triple(
        newSession,
        newSession.accountCoroutineScope,
        oldScope.coroutineContext[Job]!!.isCancelled,
      )
    }

    AccountService.onTouristMode()
    val (newSession, newScope, oldScopeCancelled) = bindingAtPublication.await()

    assertIs<AccountState.Tourist>(newSession.state)
    assertTrue(oldScopeCancelled)
    assertTrue(oldJob.isCancelled)
    assertSame(AccountService.accountCoroutineScope, newScope)
    assertSame(newSession.accountCoroutineScope, newScope)
    assertFalse(newScope.coroutineContext[Job]!!.isCancelled)
  }

  @Test
  fun legacyLogoutStateIsNeverVisibleBeforeAuthoritativeSession() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val loginGeneration = AccountService.session.value.generation
    // 派生 state 通知时读取同一份 session，不能出现状态已登出而 session 仍登录的情况。
    val sessionAtLegacyPublication =
      backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
        AccountService.state.drop(1).first { it is AccountState.Logout }
        AccountService.session.value
      }

    AccountService.onLogout()
    val observed = sessionAtLegacyPublication.await()

    assertIs<AccountState.Logout>(observed.state)
    assertTrue(observed.generation > loginGeneration)
  }

  /** 派生 state 保留相等值合并语义，重复游客不通知，同学号重新登录通知新的 Login。 */
  @Test
  fun derivedStateDeduplicatesTouristButEmitsSameAccountRelogin() = runTest(dispatcher) {
    val observed = mutableListOf<AccountState>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AccountService.state.collect { observed += it }
    }
    AccountService.onTouristMode()
    val firstTourist = AccountService.session.value
    AccountService.onTouristMode()
    assertNotSame(firstTourist, AccountService.session.value)
    assertEquals(2, observed.size)

    loginForTest("20260001", tokenFor("20260001"), "first-refresh")
    val firstLogin = AccountService.session.value.state
    loginForTest("20260001", tokenFor("20260001"), "second-refresh")
    assertEquals(4, observed.size)
    assertSame(firstLogin, observed[2])
    assertSame(AccountService.session.value.state, observed[3])
    assertSame(AccountService.session.value.state, AccountService.state.value)
    AccountService.onLogout()
    assertEquals(5, observed.size)
    assertSame(AccountService.session.value.state, observed.last())
  }

  /** 正常登录过期只跳转一次，随后重新登录立即恢复可用凭据。 */
  @Test
  fun currentExpiryNavigatesOnceAndReloginRestoresToken() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "old-refresh")
    val expired = AccountService.session.value
    expired.tokenState?.tryRefreshTokenExpired("登录过期", requireNotNull((expired.tokenState as TokenStateImpl).tokenFlow.value))
    expired.tokenState?.tryRefreshTokenExpired("重复过期响应", requireNotNull((expired.tokenState as TokenStateImpl).tokenFlow.value))
    dispatcher.scheduler.runCurrent()

    assertIs<AccountState.Logout>(AccountService.state.value)
    assertNull(AccountService.session.value.tokenState)
    assertNull(TokenStateImpl.load())
    assertEquals(1, TestLoginService.jumpCount)

    loginForTest("20260001", tokenFor("20260001"), "new-refresh")
    assertFalse(AccountService.session.value.tokenState?.isRefreshTokenExpired() != false)
    assertEquals(tokenFor("20260001"), AccountService.session.value.tokenState?.getOrRefreshToken()?.token)
  }

  /** 旧请求的过期响应不能清除重新登录后的 token，也不能跳回登录页。 */
  @Test
  fun expiredResponseFromPreviousLoginIsIgnored() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "old-refresh")
    val oldSession = AccountService.session.value
    loginForTest("20260001", tokenFor("20260001"), "new-refresh")
    val currentToken = (AccountService.session.value.tokenState as TokenStateImpl).tokenFlow.value

    oldSession.tokenState?.tryTokenExpired(requireNotNull((oldSession.tokenState as TokenStateImpl).tokenFlow.value))
    oldSession.tokenState?.tryRefreshTokenExpired("迟到的过期响应", requireNotNull((oldSession.tokenState as TokenStateImpl).tokenFlow.value))
    dispatcher.scheduler.runCurrent()

    assertSame(currentToken, AccountService.session.value.tokenState?.token)
    assertTrue(AccountService.isLogin())
    assertTrue(currentToken!!.tokenExpiredAtMillis > Clock.System.now().toEpochMilliseconds())
    assertEquals(0, TestLoginService.jumpCount)
  }

  /** 同一生命周期更换 access token 或 refresh token 后，旧凭据的过期响应均不得修改新凭据。 */
  @Test
  fun expiredResponseFromPreviousCredentialsIsIgnored() = runTest(dispatcher) {
    for (changeAccessToken in listOf(true, false)) {
      loginForTest("20260001", tokenFor("20260001"), "old-refresh")
      val session = AccountService.session.value
      val previous = requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value)
      val refreshed = TokenStateImpl.toAccountToken(
        bean = TokenBean(
          if (changeAccessToken) previous.token + ".refreshed" else previous.token,
          if (changeAccessToken) previous.refreshToken else "new-refresh",
        ),
      )
      TokenStateImpl.save(refreshed)
      (session.tokenState as TokenStateImpl).tokenFlow.value = refreshed

      session.tokenState?.tryTokenExpired(previous)
      session.tokenState?.tryRefreshTokenExpired("旧凭据过期", previous)
      dispatcher.scheduler.runCurrent()

      assertSame(session, AccountService.session.value)
      assertSame(refreshed, (session.tokenState as TokenStateImpl).tokenFlow.value)
      assertEquals(refreshed, TokenStateImpl.load())
      assertTrue(AccountService.isLogin())
      assertEquals(0, TestLoginService.jumpCount)
    }
  }

  /** 只修改本地有效期不代表签发新凭据，随后同一 token 的 refresh 失效仍需正常登出一次。 */
  @Test
  fun expiryTimeCopyDoesNotInvalidateRequestOwnership() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "refresh")
    val session = AccountService.session.value
    val sent = requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value)
    session.tokenState?.tryTokenExpired(sent)
    val expired = requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value)
    assertNotSame(sent, expired)
    assertEquals(0L, expired.tokenExpiredAtMillis)
    session.tokenState?.tryTokenExpired(sent)
    assertSame(expired, (session.tokenState as TokenStateImpl).tokenFlow.value)

    session.tokenState?.tryRefreshTokenExpired("当前凭据过期", sent)
    session.tokenState?.tryRefreshTokenExpired("重复响应", sent)
    dispatcher.scheduler.runCurrent()

    assertIs<AccountState.Logout>(AccountService.state.value)
    assertNull(TokenStateImpl.load())
    assertEquals(1, TestLoginService.jumpCount)
  }

  /** 登出在主线程排队时完成刷新，执行时必须再次校验，不能清除新凭据及其缓存。 */
  @Test
  fun queuedExpiryRechecksCredentialsBeforeLogout() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "old-refresh")
    val session = AccountService.session.value
    val sent = requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value)
    session.tokenState?.tryRefreshTokenExpired("旧凭据过期", sent)
    val refreshed = TokenStateImpl.toAccountToken(TokenBean(sent.token + ".refreshed", "new-refresh"))
    TokenStateImpl.save(refreshed)
    (session.tokenState as TokenStateImpl).tokenFlow.value = refreshed
    dispatcher.scheduler.runCurrent()

    assertSame(session, AccountService.session.value)
    assertSame(refreshed, (session.tokenState as TokenStateImpl).tokenFlow.value)
    assertEquals(refreshed, TokenStateImpl.load())
    assertTrue(AccountService.isLogin())
    assertEquals(0, TestLoginService.jumpCount)
  }

  /** 过期事件排队后完成新登录，主线程入口校验忽略旧 session 的登出、提示与导航。 */
  @Test
  fun reloginCancelsQueuedExpiredNavigation() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "old-refresh")
    val session = AccountService.session.value
    session.tokenState?.tryRefreshTokenExpired("登录过期", requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value))
    assertIs<AccountState.Login>(AccountService.state.value)
    val loginScope = AccountService.accountCoroutineScope

    loginForTest("20260001", tokenFor("20260001"), "new-refresh")
    dispatcher.scheduler.runCurrent()

    assertTrue(loginScope.coroutineContext[Job]!!.isCancelled)
    assertTrue(AccountService.isLogin())
    assertEquals(0, TestLoginService.jumpCount)
    assertFalse(AccountService.session.value.tokenState?.isRefreshTokenExpired() != false)
  }

  /** 延迟读取旧响应只抛业务异常，不会使新登录的凭据过期或跳回登录页。 */
  @Test
  fun delayedResponseReadDoesNotChangeCurrentLogin() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "old-refresh")
    val wrappers = listOf(20002, 20003, 20004).map { status ->
      defaultJson.decodeFromString<ApiWrapper<String>>(
        """{"status":$status,"info":"expired"}""",
      )
    }
    loginForTest("20260002", tokenFor("20260002"), "new-refresh")
    val currentSession = AccountService.session.value
    val currentToken = (currentSession.tokenState as TokenStateImpl).tokenFlow.value

    wrappers.forEach { assertFailsWith<ApiException> { it.data } }
    listOf(20002, 20003, 20004).forEach { status ->
      assertFailsWith<ApiException> { ApiStatus(status, "expired").throwApiExceptionIfFail() }
    }

    assertSame(currentSession, AccountService.session.value)
    assertSame(currentToken, (currentSession.tokenState as TokenStateImpl).tokenFlow.value)
    assertEquals(tokenFor("20260002"), AccountService.session.value.tokenState?.token?.token)
    assertEquals(0, TestLoginService.jumpCount)
  }

  /** 当前凭据可以直接取得字符串，登出和游客状态均不暴露 token。 */
  @Test
  fun tokenStringsFollowAccountLifecycle() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "refresh")
    assertEquals(tokenFor("20260001"), AccountService.session.value.tokenState?.getOrRefreshToken()?.token)
    AccountService.session.value.tokenState?.tryTokenExpired(requireNotNull(AccountService.session.value.tokenState?.token))
    assertNull(AccountService.session.value.tokenState?.token?.token)
    AccountService.onTouristMode()
    assertNull(AccountService.session.value.tokenState?.getOrRefreshToken()?.token)
    assertNull(AccountService.session.value.tokenState?.token?.token)
  }

  /** 发布生命周期之前必须同步切换凭据与配置分区，不能让观察者读到上一身份的存储。 */
  @Test
  fun accountChangesPublishCredentialsAndSettingsBeforeSession() = runTest(dispatcher) {
    val loginSettings = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      val session = AccountService.session.drop(1).first()
      assertEquals(session.accountId, (session.tokenState as TokenStateImpl).tokenFlow.value?.stuNum)
      assertEquals((session.tokenState as TokenStateImpl).tokenFlow.value, TokenStateImpl.load())
      AccountSettings.now.stuNum
    }
    loginForTest("20260001", tokenFor("20260001"), "refresh")
    assertEquals("20260001", loginSettings.await())

    val touristSettings = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      val session = AccountService.session.drop(1).first()
      assertNull(session.tokenState)
      assertNull(TokenStateImpl.load())
      AccountSettings.now.stuNum
    }
    AccountService.onTouristMode()
    assertNull(touristSettings.await())
  }

  /** 缓存写入本身不改变运行态；刷新凭据后，读 token 与观察者使用同一个 session。 */
  @Test
  fun refreshedCredentialsStayInSameSessionAndScope() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "old-refresh")
    val session = AccountService.session.value
    val scope = session.accountCoroutineScope
    val oldToken = (session.tokenState as TokenStateImpl).tokenFlow.value
    val refreshedToken = tokenFor("20260001") + ".refreshed"
    val refreshed = TokenStateImpl.toAccountToken(TokenBean(refreshedToken, "new-refresh"))
    TokenStateImpl.save(refreshed)

    assertSame(oldToken, (session.tokenState as TokenStateImpl).tokenFlow.value)
    assertEquals(tokenFor("20260001"), AccountService.session.value.tokenState?.token?.token)
    assertEquals(refreshed, TokenStateImpl.load())
    val observed = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      (session.tokenState as TokenStateImpl).tokenFlow.drop(1).first()
    }
    (session.tokenState as TokenStateImpl).tokenFlow.value = refreshed

    assertSame(refreshed, observed.await())
    assertSame(session, AccountService.session.value)
    assertSame(scope, AccountService.accountCoroutineScope)
    assertFalse(scope.coroutineContext[Job]!!.isCancelled)
    assertEquals(refreshedToken, AccountService.session.value.tokenState?.token?.token)
    assertEquals(refreshedToken, AccountService.session.value.tokenState?.getOrRefreshToken()?.token)

    // 有效期也以 session 为准；缓存里仍是有效凭据，不应覆盖运行态的过期判断。
    (session.tokenState as TokenStateImpl).tokenFlow.value = refreshed.copy(refreshTokenExpiredAtMillis = 0)
    assertTrue(AccountService.session.value.tokenState?.isRefreshTokenExpired() == true)
    assertEquals(refreshed, TokenStateImpl.load())
  }

  /** 独立凭据状态无需成为全局 session；一个实例过期或结束不影响另一个实例。 */
  @Test
  fun tokenStatesOwnCredentialsAndLifetimeWithoutReadingAccountSession() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "refresh")
    val published = AccountService.session.value
    val credentials = requireNotNull(published.tokenState?.tokenFlow?.value)
    val firstScope = CoroutineScope(SupervisorJob() + dispatcher)
    val secondScope = CoroutineScope(SupervisorJob() + dispatcher)
    try {
      val first = TokenStateImpl(firstScope, credentials, onExpired = { _, _ -> })
      val secondToken = credentials.copy(token = "second-token", refreshToken = "second-refresh")
      val second = TokenStateImpl(secondScope, secondToken, onExpired = { _, _ -> })
      assertSame(credentials, first.getOrRefreshToken())
      assertSame(secondToken, second.getOrRefreshToken())
      first.tryTokenExpired(credentials)
      assertEquals(0, first.tokenFlow.value?.tokenExpiredAtMillis)
      assertSame(secondToken, second.token)
      assertSame(credentials, published.tokenState?.tokenFlow?.value)
      firstScope.cancel()
      assertNull(first.token)
      assertFailsWith<CancellationException> { first.getOrRefreshToken() }
      assertSame(secondToken, second.getOrRefreshToken())
      // 取消只结束异步工作，仍有效的凭据可以正常读取。
      secondScope.cancel()
      assertSame(secondToken, second.token)
      assertSame(secondToken, second.getOrRefreshToken())
      assertSame(published, AccountService.session.value)
    } finally {
      firstScope.cancel()
      secondScope.cancel()
    }
  }

  /** refresh token 失效只通知回调，TokenState 自身不登出或导航，也不处理旧凭据的响应。 */
  @Test
  fun standaloneTokenStateReportsExpiryToOwner() = runTest(dispatcher) {
    loginForTest("20260001", tokenFor("20260001"), "refresh")
    val published = AccountService.session.value
    val credentials = requireNotNull(published.tokenState?.tokenFlow?.value)
    var messages = emptyList<String>()
    val tokenState = TokenStateImpl(backgroundScope, credentials) { source, msg ->
      assertSame(credentials, source.tokenFlow.value)
      messages = messages + msg
    }
    tokenState.tryRefreshTokenExpired("old", credentials.copy(refreshToken = "old-refresh"))
    tokenState.tryRefreshTokenExpired("current", credentials)
    dispatcher.scheduler.runCurrent()
    assertEquals(listOf("current"), messages)
    assertSame(published, AccountService.session.value)
    assertEquals(0, TestLoginService.jumpCount)
  }

  /** 登录后立即取消真实用户资料请求，保证生命周期单测不依赖外部网络时序。 */
  private fun loginForTest(stuNum: String, token: String, refreshToken: String) {
    AccountService.onLoginSuccess(stuNum, token, refreshToken)
    UserInfoProvider.clear()
  }

  /** 构造最小用户资料，验证同学号新生命周期不会继承旧 Login.userInfo。 */
  private fun createUserInfo(stuNum: String, nickname: String) = UserInfo(
    gender = "男",
    photoSrc = "",
    stuNum = stuNum,
    username = nickname,
    nickname = nickname,
    college = "测试学院",
  )

  /**
   * 构造 AccountService 可解析的确定性测试 token；exp 固定为未来时间，仅用于账户生命周期测试。
   */
  private fun tokenFor(stuNum: String): String {
    return when (stuNum) {
      "20260001" -> "eyJEYXRhIjp7ImdlbmRlciI6IueUtyIsInN0dV9udW0iOiIyMDI2MDAwMSJ9LCJleHAiOiI0MTAyNDQ0ODAwIn0="
      "20260002" -> "eyJEYXRhIjp7ImdlbmRlciI6IueUtyIsInN0dV9udW0iOiIyMDI2MDAwMiJ9LCJleHAiOiI0MTAyNDQ0ODAwIn0="
      else -> error("Unsupported test account: $stuNum")
    }
  }
}
