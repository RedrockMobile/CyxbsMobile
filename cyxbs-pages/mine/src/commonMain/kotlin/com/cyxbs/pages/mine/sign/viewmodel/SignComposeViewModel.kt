package com.cyxbs.pages.mine.sign.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.mine.sign.model.bean.SignStatus
import com.cyxbs.pages.mine.sign.model.service.SignService
import com.cyxbs.pages.mine.sign.util.SignUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**  
 * description: Sign的Viewmodel
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 13:45
 */
class SignComposeViewModel : BaseViewModel() {

  private val _signStatus: MutableStateFlow<SignStatus?> = MutableStateFlow(null)
  val signStatus: StateFlow<SignStatus?> get() = _signStatus.asStateFlow()

  // 签到的状态
  private val _signState: MutableStateFlow<SignState> = MutableStateFlow(SignState.UNSIGNED)
  val signState: StateFlow<SignState> get() = _signState.asStateFlow()

  // 发送动画的事件
  private val _updateProgressEvent: MutableSharedFlow<SignEvent> = MutableSharedFlow(
    extraBufferCapacity = 1
  )
  val updateProgressEvent: SharedFlow<SignEvent> get() = _updateProgressEvent.asSharedFlow()

  init {
    refreshScoreStatus()
  }

  /**
   * 刷新状态
   * @param animateLine 是否发送启动动画事件
   */
  fun refreshScoreStatus(animateLine: Boolean = false) {
    launchByViewModelScope {
      runCatchingCoroutine {
        SignService::class.impl().getSignStatus()
      }.mapCatching {
        it.data
      }.getOrElse {
        toast("获取积分失败，请稍后再试")
        null
      }?.let { signStatus ->
        // 数据显示的是周日->周一，需要反转成周一->周日
        _signStatus.value = signStatus.copy(
          weekInfo = signStatus.weekInfo.reversed()
        )
        if (signStatus.canCheckIn && signStatus.isChecked) {
          _signState.value = SignState.SIGNED
        } else if (signStatus.canCheckIn && !signStatus.isChecked) {
          _signState.value = SignState.UNSIGNED
        } else {
          _signState.value = SignState.INVOCATION
        }

        val todayIndex = SignUtil.getTodayOfWeek()
        if (animateLine && todayIndex in 0..5) {
          _updateProgressEvent.emit(
            SignEvent.AnimateWeekLine(
              index = todayIndex,
              weekInfo = signStatus.weekInfo
            )
          )
        }
      }
    }
  }

  /**
   * 执行签到
   */
  fun checkIn() {
    launchByViewModelScope {
      runCatchingCoroutine {
        SignService::class.impl().checkIn()
      }.onSuccess { response ->
        when {
          response.status == 405 -> {
            toast("寒暑假不可签到")
            refreshScoreStatus() // 保留旧版的重新请求，但不播动画
          }

          response.isSuccess() -> {
            refreshScoreStatus(animateLine = true)
          }

          else -> {
            toast("签到失败，请稍后再试")
          }
        }
      }.onFailure {
        toast("签到失败，请稍后再试")
      }
    }
  }

}

enum class SignState {
  SIGNED, // 签到
  UNSIGNED, // 没签到
  INVOCATION // 在假期
}

sealed interface SignEvent {
  data class AnimateWeekLine(
    val index: Int,
    val weekInfo: String
  ) : SignEvent
}