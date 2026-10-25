package com.cyxbs.pages.discover.pages.discover

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.route.DISCOVER_NEWS
import com.cyxbs.components.config.route.DISCOVER_NEWS_ITEM
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.pages.discover.home.DiscoverNavPlatform
import com.cyxbs.pages.notification.api.ILaunchNotificationService
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 发现入口页平台能力的 Android 实现，供 commonMain 的 [com.cyxbs.pages.discover.home.DiscoverPage]
 * 通过 `DiscoverNavPlatform::class.implOrNull()` 调用。
 */
@ImplProvider
object DiscoverNavPlatformImpl : DiscoverNavPlatform {

  override fun launchNotification() {
    ILaunchNotificationService::class.impl().start()
  }

  override fun jumpJwNewsList() {
    startActivity(DISCOVER_NEWS)
  }

  override fun jumpJwNewsItem(newId: String) {
    startActivity(DISCOVER_NEWS_ITEM) {
      putExtra("newId", newId)
    }
  }

  override fun trackBannerClick() {
    if (IAccountService::class.impl().isLogin()) {
      // banner 位的点击埋点
      TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_BANNER_ENTRY)
    }
  }
}
