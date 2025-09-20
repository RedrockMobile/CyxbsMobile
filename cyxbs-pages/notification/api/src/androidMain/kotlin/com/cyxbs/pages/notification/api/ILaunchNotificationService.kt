package com.cyxbs.pages.notification.api

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/6
 */
interface ILaunchNotificationService {

  fun start(page: NotificationPage = NotificationPage.ACTIVITY)

  enum class NotificationPage {
    ACTIVITY,  // 活动通知
    SYSTEM,    // 系统通知
    ITINERARY, // 行程通知
  }
}