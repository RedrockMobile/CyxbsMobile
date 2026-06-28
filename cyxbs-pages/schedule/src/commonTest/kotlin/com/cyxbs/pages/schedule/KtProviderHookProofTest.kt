package com.cyxbs.pages.schedule

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.g985892345.provider.api.init.IKtProviderDelegate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证：单测可通过 [IKtProviderDelegate] 的 companion hook 注册假实现，
 * 使 `::class.impl()` 在测试环境返回测试替身（生产代码无需改动）。
 */
class KtProviderHookProofTest {

  private interface Greeter {
    fun hi(): String
  }

  @Test
  fun can_register_fake_impl_for_unit_test() {
    IKtProviderDelegate.addImplProvider(Greeter::class, "") { object : Greeter {
      override fun hi() = "hello-from-fake"
    } }
    assertEquals("hello-from-fake", Greeter::class.impl().hi())
    assertEquals("hello-from-fake", Greeter::class.implOrNull()?.hi())
  }
}
