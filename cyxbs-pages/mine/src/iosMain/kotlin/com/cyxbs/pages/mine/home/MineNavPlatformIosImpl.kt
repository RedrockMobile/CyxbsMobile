package com.cyxbs.pages.mine.home

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.notification.api.INotificationService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * 「我的」主页平台能力的 iOS 实现，供 commonMain 的 [MinePage] 通过
 * `MineNavPlatform::class.implOrNull()` 调用。
 *
 * 实际跳转动作下放到 [MineIosPlatform]，由 cyxbs-applications/multiplatform 的
 * IOSKmpInterfaceLink 注入。实现缺失时降级为 toast，避免崩溃。
 */
@ImplProvider
object MineNavPlatformIosImpl : MineNavPlatform {

  override val unreadCount: StateFlow<Int>
    get() = INotificationService::class.impl().unreadCount

  override fun launchNotification() {
    MineIosPlatform::class.implOrNull()?.launchNotification() ?: toast("暂不支持跳转")
  }

  override fun jumpStore() {
    MineIosPlatform::class.implOrNull()?.jumpStore() ?: toast("暂不支持跳转")
  }

  override fun jumpFeedbackCenter() {
    MineIosPlatform::class.implOrNull()?.jumpFeedbackCenter() ?: toast("暂不支持跳转")
  }

  override fun jumpSign() {
    MineIosPlatform::class.implOrNull()?.jumpSign() ?: toast("暂不支持跳转")
  }

  override fun jumpSetting() {
    MineIosPlatform::class.implOrNull()?.jumpSetting() ?: toast("暂不支持跳转")
  }

  override fun jumpActivityCenter() {
    MineIosPlatform::class.implOrNull()?.jumpActivityCenter() ?: toast("暂不支持跳转")
  }
}
