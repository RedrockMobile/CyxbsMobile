package com.cyxbs.pages.notification.service

import com.cyxbs.pages.notification.api.ILaunchNotificationService
import com.cyxbs.pages.notification.ui.activity.NotificationActivity
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/6
 */
@ImplProvider
object LaunchNotificationServiceImpl : ILaunchNotificationService {
  override fun start(page: ILaunchNotificationService.NotificationPage) {
    NotificationActivity.start(page)
  }
}