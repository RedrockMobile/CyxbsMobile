package com.cyxbs.pages.schedule.support

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.g985892345.provider.api.init.IKtProviderDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 测试用账号：固定发布单次 Login 会话，账号协程作用域独立于生产服务。 */
class FakeAccountService(stuNum: String) : IAccountService {
  override val state: StateFlow<AccountState> = MutableStateFlow(AccountState.Login(stuNum))
  override val session: StateFlow<AccountSession> = MutableStateFlow(AccountSession(1, state.value))
  override val accountCoroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
  override fun accountCoroutineScopeFor(expectedSession: AccountSession): CoroutineScope? =
    accountCoroutineScope.takeIf { session.value === expectedSession }
}

/** 测试账号学号常量。 */
const val TEST_STU_NUM = "test_stu"

/**
 * 通过 KtProvider 的 [IKtProviderDelegate] companion hook 注册假 [IAccountService]，
 * 使被测代码里的 `IAccountService::class.impl()` 在单测环境返回测试替身（生产代码无需改动）。
 */
fun registerFakeAccount(stuNum: String = TEST_STU_NUM) {
  // addImplProvider 对重复 key 会抛异常，而 @BeforeTest 每个用例都会调用，故需幂等。
  if (IAccountService::class in IKtProviderDelegate.ImplProviderMap) return
  IKtProviderDelegate.addImplProvider(IAccountService::class, "") { FakeAccountService(stuNum) }
}
