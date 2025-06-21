package com.cyxbs.pages.notification.model

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.notification.bean.ChangeReadStatusToBean
import com.cyxbs.pages.notification.bean.SystemMsgBean
import com.cyxbs.pages.notification.network.SystemMessageApiService
import kotlinx.coroutines.flow.MutableStateFlow
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
object SystemMessageRepository {

  val systemMessageFlow = MutableStateFlow(emptyList<SystemMsgBean>())

  init {
    IAccountService::class.impl().state.mapLatest {
      if (it is AccountState.Login) {
        supervisorScope {
          launch { requestSystemMessage() }
        }
      } else if (it is AccountState.Logout) {
        systemMessageFlow.emit(emptyList())
      } else Unit
    }.launchIn(appCoroutineScope)
  }

  suspend fun requestSystemMessage(): Result<List<SystemMsgBean>> {
    return runCatchingCoroutine {
      SystemMessageApiService::class.impl().getSystemMessage()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data.system_msg
    }.onSuccess {
      systemMessageFlow.emit(it)
    }
  }

  suspend fun changeMsgStatus(bean: ChangeReadStatusToBean): Result<Unit> {
    return runCatchingCoroutine {
      SystemMessageApiService::class.impl().changeMsgStatus(bean)
    }.mapCatching {
      it.throwApiExceptionIfFail()
    }.onSuccess {
      systemMessageFlow.update { list ->
        list.map {
          if (bean.ids.contains(it.id.toString())) it.copy(has_read = true) else it
        }
      }
    }
  }
}