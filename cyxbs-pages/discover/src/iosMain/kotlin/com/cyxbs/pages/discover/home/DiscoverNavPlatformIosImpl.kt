package com.cyxbs.pages.discover.home

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.toast
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 发现首页平台能力的 iOS 实现，供 commonMain 的 [DiscoverPage] 通过
 * `DiscoverNavPlatform::class.implOrNull()` 调用。
 *
 * 与 Android 端 DiscoverNavPlatformImpl 对齐：实际跳转动作下放到 [DiscoverIosPlatform]，
 * 由 cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 注入。
 * 实现缺失时降级为 toast，避免崩溃。
 */
@ImplProvider
object DiscoverNavPlatformIosImpl : DiscoverNavPlatform {

  override fun launchNotification() {
    DiscoverIosPlatform::class.implOrNull()?.launchNotification() ?: toast("暂不支持跳转")
  }

  override fun jumpCheckIn() {
    DiscoverIosPlatform::class.implOrNull()?.jumpCheckIn() ?: toast("暂不支持跳转")
  }

  override fun jumpJwNewsList() {
    DiscoverIosPlatform::class.implOrNull()?.jumpJwNewsList() ?: toast("暂不支持跳转")
  }

  override fun jumpJwNewsItem(newId: String) {
    DiscoverIosPlatform::class.implOrNull()?.jumpJwNewsItem(newId) ?: toast("暂不支持跳转")
  }

  override fun onBannerClick(pictureGotoUrl: String, keyword: String) {
    DiscoverIosPlatform::class.implOrNull()?.onBannerClick(pictureGotoUrl, keyword) ?: toast("暂不支持跳转")
  }
}
