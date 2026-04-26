package com.cyxbs.applications.test

import com.cyxbs.components.base.BaseApp
import com.cyxbs.components.config.ConfigApplicationInfo
import com.g985892345.provider.api.annotation.ImplProvider
import com.g985892345.provider.cyxbsmobile.cyxbsapplications.test.TestKtProviderInitializer

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/15
 */
class TestApp : BaseApp() {
  override fun initProvider() {
    TestKtProviderInitializer.tryInitKtProvider()
  }
}

@ImplProvider
object AndroidConfigApplicationInfo : ConfigApplicationInfo {
  override fun isDebug(): Boolean {
    return true
  }
}