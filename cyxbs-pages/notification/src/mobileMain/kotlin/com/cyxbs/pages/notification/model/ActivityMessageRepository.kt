package com.cyxbs.pages.notification.model

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.notification.bean.UfieldMsgBean
import com.cyxbs.pages.notification.network.ActivityMessageApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/6
 */
object ActivityMessageRepository {

  val activityMessageFlow: StateFlow<List<UfieldMsgBean>> get() = _activityMessageFlow
  private val _activityMessageFlow = MutableStateFlow(emptyList<UfieldMsgBean>())

  init {
    IAccountService::class.impl().state.mapLatest {
      if (it == AccountState.Login) {
        supervisorScope {
          launch { requestActivityMessage() }
        }
      } else if (it == AccountState.Logout) {
        _activityMessageFlow.emit(emptyList())
      } else Unit
    }.launchIn(appCoroutineScope)
  }

  suspend fun requestActivityMessage(): Result<List<UfieldMsgBean>> {
    return runCatchingCoroutine {
      ActivityMessageApiService::class.impl().getActivityMessage()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data
    }.onSuccess {
      _activityMessageFlow.emit(it)
    }
  }

  suspend fun changeActivityMsgStatus(messageId: Int): Result<Unit> {
    return runCatchingCoroutine {
      ActivityMessageApiService::class.impl().changeActivityMsgStatus(messageId)
    }.mapCatching {
      it.throwApiExceptionIfFail()
    }.onSuccess {
      _activityMessageFlow.update { list ->
        list.map {
          if (it.messageId == messageId) it.copy(clicked = true) else it
        }
      }
    }
  }
}