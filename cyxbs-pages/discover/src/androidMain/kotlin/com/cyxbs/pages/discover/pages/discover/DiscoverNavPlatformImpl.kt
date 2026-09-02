package com.cyxbs.pages.discover.pages.discover

import androidx.core.net.toUri
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.webview.WebViewNavArgument
import com.cyxbs.components.config.route.DISCOVER_NEWS
import com.cyxbs.components.config.route.DISCOVER_NEWS_ITEM
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.navigation.AppScheme
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

  override fun onBannerClick(pictureGotoUrl: String, keyword: String) {
    if (IAccountService::class.impl().isLogin()) {
      // banner 位的点击埋点
      TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_BANNER_ENTRY)
    }
    val finalUrl = if (pictureGotoUrl.startsWith("http")) {
      val uri = pictureGotoUrl.toUri()
      if (uri.getQueryParameter(WebViewNavArgument.DEFAULT_TITLE_QUERY_PARAMETER) == null) {
        // Banner 关键词作为 WebView 路由的兜底标题。
        uri.buildUpon().appendQueryParameter(WebViewNavArgument.DEFAULT_TITLE_QUERY_PARAMETER, keyword).build()
          .toString()
      } else pictureGotoUrl
    } else pictureGotoUrl
    AppScheme.jump(finalUrl)
  }
}
