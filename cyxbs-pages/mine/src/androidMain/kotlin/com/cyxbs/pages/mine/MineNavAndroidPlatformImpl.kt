package com.cyxbs.pages.mine

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.route.STORE_ENTRY
import com.cyxbs.components.config.route.UFIELD_CENTER_ENTRY
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.pages.mine.home.MineNavPlatform
import com.cyxbs.pages.mine.page.feedback.center.ui.FeedbackCenterActivity
import com.cyxbs.pages.mine.page.setting.SettingActivity
import com.cyxbs.pages.mine.page.sign.DailySignActivity
import com.cyxbs.pages.notification.api.ILaunchNotificationService
import com.cyxbs.pages.notification.api.INotificationService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * 「我的」主页平台能力的 Android 实现，供 commonMain 的 [MinePage] 通过
 * `MineNavPlatform::class.implOrNull()` 调用。
 */
@ImplProvider
object MineNavAndroidPlatformImpl : MineNavPlatform {

  override val unreadCount: StateFlow<Int>
    get() = INotificationService::class.impl().unreadCount

  override fun launchNotification() {
    if (IAccountService::class.impl().isLogin()) {
      // 消息中心入口点击埋点
      TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_XXZX_ENTRY)
    }
    ILaunchNotificationService::class.impl().start()
  }

  override fun jumpStore() {
    // “邮票中心”点击埋点
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_YPZX_ENTRY)
    startActivity(STORE_ENTRY)
  }

  override fun jumpFeedbackCenter() {
    // “反馈中心”点击埋点
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_FKZX_ENTRY)
    startActivity(FeedbackCenterActivity::class)
  }

  override fun jumpSign() {
    startActivity(DailySignActivity::class)
  }

  override fun jumpSetting() {
    startActivity(SettingActivity::class)
  }

  override fun jumpActivityCenter() {
    // “活动中心”点击埋点
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_HDZX_ENTRY)
    startActivity(UFIELD_CENTER_ENTRY)
  }
}