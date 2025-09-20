package com.cyxbs.pages.notification.service

import androidx.compose.ui.util.fastSumBy
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.notification.api.INotificationService
import com.cyxbs.pages.notification.model.ActivityMessageRepository
import com.cyxbs.pages.notification.model.ItineraryRepository
import com.cyxbs.pages.notification.model.SystemMessageRepository
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/30
 */
@ImplProvider
object NotificationServiceImpl : INotificationService {

  override val unreadCount = MutableStateFlow(0)

  init {
    combine(
      ActivityMessageRepository.activityMessageFlow,
      SystemMessageRepository.systemMessageFlow,
      ItineraryRepository.receivedItineraryFlow,
      ItineraryRepository.sentItineraryFlow,
    ) { a, b, c, d ->
      a.fastSumBy { if (it.clicked) 0 else 1 } +
          b.fastSumBy { if (it.has_read) 0 else 1 } +
          c.fastSumBy { if (it.hasRead) 0 else 1 } +
          d.fastSumBy { if (it.hasRead) 0 else 1 }
    }.onEach {
      unreadCount.emit(it)
    }.launchIn(appCoroutineScope)
  }
}