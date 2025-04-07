package com.cyxbs.pages.notification.api

import kotlinx.coroutines.flow.StateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/30
 */
interface INotificationService {

  val unreadCount: StateFlow<Int>
}