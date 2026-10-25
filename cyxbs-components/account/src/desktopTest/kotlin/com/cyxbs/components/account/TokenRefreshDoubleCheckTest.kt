package com.cyxbs.components.account

import kotlinx.coroutines.CancellationException
import com.cyxbs.components.account.provider.UserInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** 用锁排队确定性重现二次检查窗口，不依赖真实刷新网络及线程调度时间。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenRefreshDoubleCheckTest {
  private val dispatcher = StandardTestDispatcher()

  // 仅 JVM 测试通过反射控制私有锁，避免为测试向生产服务暴露同步入口。
  private val requestMutex: Mutex
    get() = TokenStateImpl::class.java.getDeclaredField("requestMutex").let {
      it.isAccessible = true
      it.get(AccountService.session.value.tokenState) as Mutex
    }

  /** 统一主线程调度并创建有效账户，取消与本用例无关的用户资料请求。 */
  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    AccountService.onLoginSuccess(
      "20260001",
      "eyJEYXRhIjp7ImdlbmRlciI6IueUtyIsInN0dV9udW0iOiIyMDI2MDAwMSJ9LCJleHAiOiI0MTAyNDQ0ODAwIn0=",
      "refresh",
    )
    UserInfoProvider.clear()
  }

  /** 取消账户任务，恢复其他测试使用的主线程调度器。 */
  @AfterTest
  fun tearDown() {
    AccountService.onLogout()
    Dispatchers.resetMain()
  }

  /** 多个调用已经判断旧 token 过期，取得锁后应直接返回新凭据，不重复刷新。 */
  @Test
  fun queuedReadersRecheckUpdatedTokenBeforeRefreshing() = runTest(dispatcher) {
    val session = AccountService.session.value
    val fresh = requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value).copy(token = "new-token", refreshToken = "new-refresh")
    (session.tokenState as TokenStateImpl).tokenFlow.value = fresh.copy(token = "old-token", tokenExpiredAtMillis = 0)
    val mutex = requestMutex
    mutex.lock()
    try {
      val readers = List(8) { async { session.tokenState?.getOrRefreshToken() } }
      runCurrent()
      readers.forEach { assertFalse(it.isCompleted) }
      (session.tokenState as TokenStateImpl).tokenFlow.value = fresh
      mutex.unlock()
      readers.forEach { assertSame(fresh, it.await()) }
      assertSame(fresh, (session.tokenState as TokenStateImpl).tokenFlow.value)
    } finally {
      if (mutex.isLocked) mutex.unlock()
    }
  }

  /** 等待锁期间账户作用域已取消，需要刷新的调用按协程机制正常取消。 */
  @Test
  fun queuedReaderRejectsEndedSession() = runTest(dispatcher) {
    val session = AccountService.session.value
    (session.tokenState as TokenStateImpl).tokenFlow.value = requireNotNull((session.tokenState as TokenStateImpl).tokenFlow.value).copy(tokenExpiredAtMillis = 0)
    val mutex = requestMutex
    mutex.lock()
    try {
      val reader = async { runCatching { session.tokenState?.getOrRefreshToken() } }
      runCurrent()
      assertFalse(reader.isCompleted)
      AccountService.onLogout()
      mutex.unlock()
      assertFailsWith<CancellationException> { reader.await().getOrThrow() }
    } finally {
      if (mutex.isLocked) mutex.unlock()
    }
  }
}
