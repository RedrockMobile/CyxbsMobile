package com.cyxbs.pages.notification.viewmodel

import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.notification.model.ActivityMessageRepository
import com.cyxbs.pages.notification.model.ItineraryRepository
import com.cyxbs.pages.notification.model.SystemMessageRepository
import kotlinx.coroutines.supervisorScope

/**
 * Author by OkAndGreat
 * Date on 2022/4/27 17:08.
 *
 */
class NotificationViewModel : BaseViewModel() {

    val refreshState = MutableLiveData<Boolean>()

    init {
      refreshAllNotification()
    }

    /**
     * 刷新所有消息
     */
    fun refreshAllNotification() {
        launch {
            refreshState.value = true
            try {
                supervisorScope {
                    launch { ActivityMessageRepository.requestActivityMessage() }
                    launch { SystemMessageRepository.requestSystemMessage() }
                    launch { ItineraryRepository.requestReceivedItinerary() }
                    launch { ItineraryRepository.requestSentItinerary() }
                }
            } finally {
                refreshState.value = false
            }
        }
    }
}