package com.cyxbs.applications.pro

import com.cyxbs.components.base.BaseApp
import com.cyxbs.components.config.ConfigApplicationInfo
import com.g985892345.provider.api.annotation.ImplProvider
import com.g985892345.provider.cyxbsmobile.cyxbsapplications.pro.ProKtProviderInitializer

/**
 * Created By jay68 on 2018/8/8.
 */
class ProApp : BaseApp() {
  override fun initProvider() {
    ProKtProviderInitializer.tryInitKtProvider()
  }
}

@ImplProvider
object AndroidConfigApplicationInfo : ConfigApplicationInfo {
  override fun isDebug(): Boolean {
    return BuildConfig.DEBUG
  }
}