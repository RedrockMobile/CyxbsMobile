package com.cyxbs.pages.notification.model

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import com.cyxbs.pages.notification.bean.toAffairDateBean
import com.cyxbs.pages.notification.network.ItineraryApiService
import com.cyxbs.pages.notification.network.NotificationApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/20
 * @Description:
 *
 */
object ItineraryRepository {

  val sentItineraryFlow: StateFlow<List<SentItineraryMsgBean>> get() = _sentItineraryFlow
  private val _sentItineraryFlow = MutableStateFlow(emptyList<SentItineraryMsgBean>())

  val receivedItineraryFlow: StateFlow<List<ReceivedItineraryMsgBean>> get() = _receivedItineraryFlow
  private val _receivedItineraryFlow = MutableStateFlow(emptyList<ReceivedItineraryMsgBean>())

  init {
    IAccountService::class.impl().state.mapLatest {
      if (it is AccountState.Login) {
        supervisorScope {
          launch { requestReceivedItinerary() }
          launch { requestSentItinerary() }
        }
      } else if (it is AccountState.Logout) {
        _sentItineraryFlow.emit(emptyList())
        _receivedItineraryFlow.emit(emptyList())
      } else Unit
    }.launchIn(appCoroutineScope)
  }

  /**
   * 请求发送的行程
   */
  suspend fun requestSentItinerary(): Result<List<SentItineraryMsgBean>> {
    return runCatchingCoroutine {
      NotificationApiService::class.impl().getSentItinerary()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data
    }.onSuccess {
      _sentItineraryFlow.emit(it)
    }
  }

  /**
   * 请求收到的行程
   */
  suspend fun requestReceivedItinerary(): Result<List<ReceivedItineraryMsgBean>> {
    return runCatchingCoroutine {
      NotificationApiService::class.impl().getReceivedItinerary()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data
    }.onSuccess {
      _receivedItineraryFlow.emit(it)
    }
  }

  /**
   * 取消行程的提醒
   * @param itineraryId 行程id
   */
  suspend fun cancelItineraryReminder(itineraryId: Int): Result<Unit> {
    return runCatchingCoroutine {
      ItineraryApiService::class.impl().cancelItineraryReminder(itineraryId.toString())
    }.mapCatching {
      it.throwApiExceptionIfFail()
    }
  }

  /**
   * 改变行程的已读状态（默认为将行程变为已读，即status为true时）
   * @param idList 行程id
   */
  suspend fun changeItineraryReadStatus(idList: List<Int>, receivedOrSend: Boolean): Result<Unit> {
    return runCatchingCoroutine {
      ItineraryApiService::class.impl().changeItineraryReadStatus(idList)
    }.mapCatching {
      it.throwApiExceptionIfFail()
    }.onSuccess {
      if (receivedOrSend) {
        _receivedItineraryFlow.update { list ->
          list.map {
            if (idList.contains(it.id)) it.copy(hasRead = true) else it
          }
        }
      } else {
        _sentItineraryFlow.update { list ->
          list.map {
            if (idList.contains(it.id)) it.copy(hasRead = true) else it
          }
        }
      }
    }
  }

  /**
   * 改变行程的是否被添加到日程状态（默认为将行程变为已添加，即status为true时）
   * @param itineraryId 行程id
   */
  suspend fun changeItineraryAddStatus(itineraryId: Int): Result<Unit> {
    return runCatchingCoroutine {
      ItineraryApiService::class.impl().changeItineraryAddStatus(itineraryId)
    }.mapCatching {
      it.throwApiExceptionIfFail()
    }
  }

  /**
   * 添加行程到事务中
   * todo 这里后续改成直接调用 affair 模块暴露的方法，否则需要下次打开课表才会生效
   */
  suspend fun addAffair(remindTime: Int, info: ReceivedItineraryMsgBean): Result<Unit> {
    return runCatchingCoroutine {
      ItineraryApiService::class.impl().addAffair(
        remindTime,
        info.title,
        info.content,
        defaultJson.encodeToString(listOf(info.dateJson).toAffairDateBean())
      )
    }.mapCatching {
      it.throwApiExceptionIfFail()
    }
  }
}