package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.login.api.ILoginService
import com.g985892345.provider.api.init.IKtProviderDelegate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val loginJob = loginScope.launch { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()
    assertTrue(loginJob.isActive)

    AccountService.onTouristMode()

    val touristScope = AccountService.accountCoroutineScope
    assertIs<AccountState.Tourist>(AccountService.state.value)
    assertNotSame(loginScope, touristScope)
    assertTrue(loginJob.isCancelled)
    assertFalse(touristScope.coroutineContext[Job]!!.isCancelled)

    val touristJob = touristScope.launch { kotlinx.coroutines.awaitCancellation() }
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
    val nextLoginJob = nextLoginScope.launch { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()
    assertTrue(nextLoginJob.isActive)
    nextLoginJob.cancel()
  }

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

    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-2",
    )

    val secondSession = AccountService.session.value
    val secondLogin = assertIs<AccountState.Login>(AccountService.state.value)
    assertEquals("20260001", firstSession.accountId)
    assertEquals("20260001", secondSession.accountId)
    assertTrue(secondSession.generation > firstSession.generation)
    assertNotSame(firstLogin, secondLogin)
    assertSame(secondLogin, secondSession.state)
    assertNull(secondLogin.userInfo.value)
    assertNotSame(firstScope, AccountService.accountCoroutineScope)
    assertNull(AccountService.accountCoroutineScopeFor(firstSession))
    assertSame(
      AccountService.accountCoroutineScope,
      AccountService.accountCoroutineScopeFor(secondSession)
    )
  }

  @Test
  fun strictLeaseRejectsStaleLoggedOutExpectedSession() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-old",
    )
    val staleSession = AccountService.session.value

    AccountService.onLogout()

    assertFailsWith<CancellationException> {
      TokenServiceImpl.getOrRequestTokenLease(staleSession)
    }
  }

  @Test
  fun strictLeaseRejectsStructurallyCopiedExpectedSession() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-current",
    )
    val currentSession = AccountService.session.value
    val copiedSession = currentSession.copy()
    assertEquals(currentSession, copiedSession)
    assertNotSame(currentSession, copiedSession)

    assertFailsWith<CancellationException> {
      TokenServiceImpl.getOrRequestTokenLease(copiedSession)
    }
  }

  @Test
  fun strictLeaseRejectsSameAccountNewGenerationAndUsesNewSession() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-old",
    )
    val oldSession = AccountService.session.value
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-new",
    )
    val currentSession = AccountService.session.value

    assertFailsWith<CancellationException> {
      TokenServiceImpl.getOrRequestTokenLease(oldSession)
    }
    assertEquals(tokenFor("20260001"), TokenServiceImpl.getOrRequestTokenLease(currentSession).token)
  }

  @Test
  fun strictLeaseReacquiresLeaseForRefreshedSourceToken() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-source",
    )
    val expectedSession = AccountService.session.value
    val initialLease = TokenServiceImpl.getOrRequestTokenLease(expectedSession)
    val snapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val refreshedTokenValue = tokenFor("20260001") + ".refreshed"
    assertTrue(
      AccountService.commitRefreshedToken(
        snapshot.session,
        snapshot.token,
        TokenBean(refreshedTokenValue, "refresh-token-refreshed"),
      )
    )

    val refreshedLease = TokenServiceImpl.getOrRequestTokenLease(expectedSession)
    assertEquals(tokenFor("20260001"), initialLease.token)
    assertEquals(refreshedTokenValue, refreshedLease.token)
    assertNotSame(initialLease, refreshedLease)
  }

  @Test
  fun strictLeaseRecoversWhenConcurrentRefreshAdvancesSameSessionToken() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-source",
    )
    val snapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val staleSourceRefresh = CompletableDeferred<String>()
    val leaseRequest = async {
      TokenServiceImpl.awaitRefreshLease(
        expectedSession = snapshot.session,
        sourceToken = snapshot.token,
        refreshDeferred = staleSourceRefresh,
      )
    }
    testScheduler.runCurrent()
    assertTrue(leaseRequest.isActive)

    val refreshedToken = TokenBean(
      token = tokenFor("20260001") + ".refreshed",
      refreshToken = "refresh-token-refreshed",
    )
    assertTrue(
      AccountService.commitRefreshedToken(snapshot.session, snapshot.token, refreshedToken)
    )
    // 精确模拟旧 source refresh 在新 TokenBean 已提交后命中 commit guard 的交错。
    staleSourceRefresh.completeExceptionally(TokenLifecycleChangedCancellationException())

    val lease = leaseRequest.await()
    assertEquals(refreshedToken.token, lease.token)
    assertSame(refreshedToken, TokenProvider.stateFlow.value)
  }

  @Test
  fun strictLeaseRejectsExpectedSessionWhenTokenIsMissing() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-current",
    )
    val expectedSession = AccountService.session.value
    TokenProvider.clear()

    assertFailsWith<CancellationException> {
      TokenServiceImpl.getOrRequestTokenLease(expectedSession)
    }
  }

  @Test
  fun logoutAndTouristClearCurrentLoginUserInfo() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val firstLogin = assertIs<AccountState.Login>(AccountService.state.value)
    firstLogin.userInfo.value = createUserInfo("20260001", "登出前资料")

    AccountService.onLogout()
    val logout = assertIs<AccountState.Logout>(AccountService.state.value)
    assertSame(logout, AccountService.session.value.state)
    assertSame(firstLogin, logout.login)
    assertNull(AccountService.userInfo)

    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-2",
    )
    assertIs<AccountState.Login>(AccountService.state.value).userInfo.value =
      createUserInfo("20260001", "游客前资料")
    AccountService.onTouristMode()

    assertIs<AccountState.Tourist>(AccountService.state.value)
    assertSame(AccountService.state.value, AccountService.session.value.state)
    assertNull(AccountService.userInfo)
  }

  @Test
  fun authoritativeSessionCollectorSeesBoundScopeAfterOldScopeCancelled() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val oldSession = AccountService.session.value
    val oldScope = requireNotNull(AccountService.accountCoroutineScopeFor(oldSession))
    val oldJob = oldScope.launch { kotlinx.coroutines.awaitCancellation() }
    dispatcher.scheduler.runCurrent()

    // Unconfined collector 会在 session.value 发布现场同步恢复，精确核验 publication 顺序。
    val bindingAtPublication = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      val newSession = AccountService.session.drop(1).first()
      Triple(
        newSession,
        AccountService.accountCoroutineScopeFor(newSession),
        oldScope.coroutineContext[Job]!!.isCancelled,
      )
    }

    AccountService.onTouristMode()
    val (newSession, newScope, oldScopeCancelled) = bindingAtPublication.await()

    assertIs<AccountState.Tourist>(newSession.state)
    assertTrue(oldScopeCancelled)
    assertTrue(oldJob.isCancelled)
    assertNull(AccountService.accountCoroutineScopeFor(oldSession))
    assertSame(AccountService.accountCoroutineScope, newScope)
    assertFalse(requireNotNull(newScope).coroutineContext[Job]!!.isCancelled)
  }

  @Test
  fun legacyLogoutStateIsNeverVisibleBeforeAuthoritativeSession() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-1",
    )
    val loginGeneration = AccountService.session.value.generation
    // Unconfined collector 会在 state.value 发布现场恢复，精确观察两条 StateFlow 之间的线性化顺序。
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

  @Test
  fun staleRefreshCannotCommitAfterLogoutOrAccountSwitch() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a1",
    )
    val logoutSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    AccountService.onLogout()

    val lateAfterLogout = TokenBean(
      token = tokenFor("20260001"),
      refreshToken = "late-refresh-after-logout",
    )
    assertFalse(
      AccountService.commitRefreshedToken(
        logoutSnapshot.session,
        logoutSnapshot.token,
        lateAfterLogout,
      )
    )
    assertNull(TokenProvider.stateFlow.value)

    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a2",
    )
    val switchSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b1",
    )
    val currentToken = requireNotNull(TokenProvider.stateFlow.value)

    assertFalse(
      AccountService.commitRefreshedToken(
        switchSnapshot.session,
        switchSnapshot.token,
        TokenBean(tokenFor("20260001"), "late-refresh-after-switch"),
      )
    )
    assertSame(currentToken, TokenProvider.stateFlow.value)
    assertEquals("20260002", AccountService.session.value.accountId)
  }

  @Test
  fun sameAccountReloginRejectsOldRefreshAndUserInfo() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-old",
    )
    val oldSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-new",
    )
    val currentSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())

    assertFalse(
      AccountService.commitRefreshedToken(
        oldSnapshot.session,
        oldSnapshot.token,
        TokenBean(tokenFor("20260001"), "late-refresh-old-generation"),
      )
    )
    assertFalse(
      AccountService.commitUserInfo(
        oldSnapshot.session,
        createUserInfo("20260001", "旧 generation 资料"),
      )
    )
    assertTrue(
      AccountService.commitUserInfo(
        currentSnapshot.session,
        createUserInfo("20260001", "当前 generation 资料"),
      )
    )
    assertEquals("当前 generation 资料", AccountService.userInfo?.nickname)
    assertSame(currentSnapshot.token, TokenProvider.stateFlow.value)
  }

  @Test
  fun staleUserInfoCannotCommitAfterLogoutOrAccountSwitch() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a1",
    )
    val logoutSession = AccountService.session.value
    AccountService.onLogout()
    assertFalse(
      AccountService.commitUserInfo(
        logoutSession,
        createUserInfo("20260001", "登出后迟到资料"),
      )
    )
    assertNull(AccountService.userInfo)

    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a2",
    )
    val switchSession = AccountService.session.value
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b1",
    )
    assertFalse(
      AccountService.commitUserInfo(
        switchSession,
        createUserInfo("20260001", "切号后迟到资料"),
      )
    )
    assertNull(AccountService.userInfo)
  }

  @Test
  fun currentRefreshCommitRequiresMatchingAccount() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-source",
    )
    val snapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val wrongAccountToken = TokenBean(tokenFor("20260002"), "refresh-token-wrong-account")

    assertFalse(
      AccountService.commitRefreshedToken(snapshot.session, snapshot.token, wrongAccountToken)
    )
    assertSame(snapshot.token, TokenProvider.stateFlow.value)

    val refreshedToken = TokenBean(tokenFor("20260001"), "refresh-token-committed")
    assertTrue(
      AccountService.commitRefreshedToken(snapshot.session, snapshot.token, refreshedToken)
    )
    assertSame(refreshedToken, TokenProvider.stateFlow.value)
  }

  @Test
  fun refreshTokenFailureLogoutIsConditional() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-old",
    )
    val oldSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-current",
    )

    assertNull(
      AccountService.logoutIfCurrentTokenLifecycle(oldSnapshot.session, oldSnapshot.token)
    )
    assertEquals("20260002", AccountService.session.value.accountId)

    val currentSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val logoutSession = requireNotNull(
      AccountService.logoutIfCurrentTokenLifecycle(
        currentSnapshot.session,
        currentSnapshot.token,
      )
    )
    assertIs<AccountState.Logout>(logoutSession.state)
    assertSame(logoutSession, AccountService.session.value)
    assertIs<AccountState.Logout>(AccountService.state.value)
    assertNull(TokenProvider.stateFlow.value)

    val logoutScope = requireNotNull(AccountService.accountCoroutineScopeFor(logoutSession))
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-after-logout",
    )
    assertTrue(requireNotNull(logoutScope.coroutineContext[Job]).isCancelled)
    assertNull(AccountService.accountCoroutineScopeFor(logoutSession))
  }

  @Test
  fun staleSessionCannotRegisterUserInfoRefreshAfterAccountSwitch() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-old",
    )
    val oldSession = AccountService.session.value
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-current",
    )
    var staleBeginCalled = false

    assertNull(
      AccountService.beginUserInfoRefresh(oldSession) {
        staleBeginCalled = true
        1L
      }
    )
    assertFalse(staleBeginCalled)

    val currentSession = AccountService.session.value
    assertEquals(
      2L,
      AccountService.beginUserInfoRefresh(currentSession) { 2L },
    )
  }

  @Test
  fun staleAuthenticatedStatusCannotExpireNewLifecycleToken() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a",
    )
    val switchedLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b",
    )

    TokenServiceImpl.handleAuthenticatedApiStatus(switchedLease, 20002, "late switch")
    assertEquals(tokenFor("20260002"), TokenServiceImpl.getToken())

    val sameAccountLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b-relogin",
    )
    TokenServiceImpl.handleAuthenticatedApiStatus(sameAccountLease, 20003, "late relogin")
    assertEquals(tokenFor("20260002"), TokenServiceImpl.getToken())

    val oldTokenLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    val snapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val refreshedTokenValue = tokenFor("20260002") + ".refreshed"
    assertTrue(
      AccountService.commitRefreshedToken(
        snapshot.session,
        snapshot.token,
        TokenBean(refreshedTokenValue, "refresh-token-b-new"),
      )
    )
    TokenServiceImpl.handleAuthenticatedApiStatus(oldTokenLease, 20002, "late old token")
    assertEquals(refreshedTokenValue, TokenServiceImpl.getToken())
  }

  @Test
  fun currentAuthenticatedStatusExpiresExactSourceTokenWithoutCrossLifecycleThrottle() =
    runTest(dispatcher) {
      loginForTest(
        stuNum = "20260001",
        token = tokenFor("20260001"),
        refreshToken = "refresh-token-a",
      )
      val firstLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
      TokenServiceImpl.handleAuthenticatedApiStatus(firstLease, 20002, "current token")
      assertNull(TokenServiceImpl.getToken())

      // 新 lifecycle 不得被上一账号的 30 分钟窗口抑制。
      loginForTest(
        stuNum = "20260002",
        token = tokenFor("20260002"),
        refreshToken = "refresh-token-b",
      )
      val secondLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
      TokenServiceImpl.handleAuthenticatedApiStatus(secondLease, 20003, "current verify")
      assertNull(TokenServiceImpl.getToken())
    }

  @Test
  fun authenticatedStatus20004LogsOutOnlyExactLifecycle() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a",
    )
    val switchedLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b",
    )
    TokenServiceImpl.handleAuthenticatedApiStatus(switchedLease, 20004, "late switch")
    assertEquals("20260002", AccountService.session.value.accountId)

    val sameAccountLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b-relogin",
    )
    TokenServiceImpl.handleAuthenticatedApiStatus(sameAccountLease, 20004, "late relogin")
    assertEquals("20260002", AccountService.session.value.accountId)

    val oldTokenLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    val snapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val refreshedToken = TokenBean(
      tokenFor("20260002") + ".refreshed",
      "refresh-token-b-new",
    )
    assertTrue(
      AccountService.commitRefreshedToken(snapshot.session, snapshot.token, refreshedToken)
    )
    TokenServiceImpl.handleAuthenticatedApiStatus(oldTokenLease, 20004, "late old token")
    assertEquals("20260002", AccountService.session.value.accountId)
    assertSame(refreshedToken, TokenProvider.stateFlow.value)

    val currentLease = requireNotNull(TokenServiceImpl.getOrRequestTokenLease())
    TokenServiceImpl.handleAuthenticatedApiStatus(currentLease, 20004, "current lifecycle")
    assertIs<AccountState.Logout>(AccountService.session.value.state)
    dispatcher.scheduler.runCurrent()
    assertEquals(1, TestLoginService.jumpCount)
  }

  @Test
  fun delayedWrapperAndStatusAccessOnlyThrowsWithoutAccountSideEffects() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-a",
    )
    val wrappers = listOf(20002, 20003, 20004).map { status ->
      defaultJson.decodeFromString<ApiWrapper<String>>(
        """{"status":$status,"info":"expired"}""",
      )
    }
    loginForTest(
      stuNum = "20260002",
      token = tokenFor("20260002"),
      refreshToken = "refresh-token-b",
    )
    val currentSession = AccountService.session.value
    val currentToken = requireNotNull(TokenProvider.stateFlow.value)

    wrappers.forEach { wrapper ->
      assertFailsWith<ApiException> { wrapper.data }
    }
    listOf(20002, 20003, 20004).forEach { status ->
      assertFailsWith<ApiException> {
        ApiStatus(status = status, info = "expired").throwApiExceptionIfFail()
      }
    }

    assertSame(currentSession, AccountService.session.value)
    assertSame(currentToken, TokenProvider.stateFlow.value)
    assertEquals(tokenFor("20260002"), TokenServiceImpl.getToken())
  }

  @Test
  fun sameSessionRefreshReturnsLatestCommittedTokenInsteadOfCancelling() = runTest(dispatcher) {
    loginForTest(
      stuNum = "20260001",
      token = tokenFor("20260001"),
      refreshToken = "refresh-token-source",
    )
    val snapshot = requireNotNull(AccountService.freezeTokenLifecycle())
    val refreshedTokenValue = tokenFor("20260001") + ".refreshed"
    val refreshedToken = TokenBean(refreshedTokenValue, "refresh-token-new")
    assertTrue(
      AccountService.commitRefreshedToken(snapshot.session, snapshot.token, refreshedToken)
    )

    assertEquals(
      refreshedTokenValue,
      TokenServiceImpl.currentTokenForSession(snapshot.session),
    )
  }

  @Test
  fun refreshDeferredRegistrySeparatesGenerationAndOldCompletionCannotClearNewSlot() =
    runTest(dispatcher) {
      loginForTest(
        stuNum = "20260001",
        token = tokenFor("20260001"),
        refreshToken = "refresh-token-old",
      )
      val oldSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
      val registry = TokenRefreshDeferredRegistry()
      val oldDeferred = CompletableDeferred<String>()
      assertSame(
        oldDeferred,
        registry.getOrCreate(oldSnapshot.session, oldSnapshot.token) { oldDeferred },
      )
      assertSame(
        oldDeferred,
        registry.getOrCreate(oldSnapshot.session, oldSnapshot.token) {
          error("同一生命周期不应重复创建 refresh Deferred")
        },
      )

      loginForTest(
        stuNum = "20260001",
        token = tokenFor("20260001"),
        refreshToken = "refresh-token-new",
      )
      val newSnapshot = requireNotNull(AccountService.freezeTokenLifecycle())
      val newDeferred = CompletableDeferred<String>()
      assertSame(
        newDeferred,
        registry.getOrCreate(newSnapshot.session, newSnapshot.token) { newDeferred },
      )
      assertNotSame(oldDeferred, newDeferred)

      oldDeferred.complete("old-token")
      assertSame(
        newDeferred,
        registry.getOrCreate(newSnapshot.session, newSnapshot.token) {
          error("旧 Deferred 完成不能清除新 generation 的共享槽")
        },
      )

      newDeferred.complete("new-token")
      val alreadyCompleted = CompletableDeferred<String>().apply { complete("completed") }
      assertSame(
        alreadyCompleted,
        registry.getOrCreate(newSnapshot.session, newSnapshot.token) { alreadyCompleted },
      )
      val replacement = CompletableDeferred<String>()
      assertSame(
        replacement,
        registry.getOrCreate(newSnapshot.session, newSnapshot.token) { replacement },
      )
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
