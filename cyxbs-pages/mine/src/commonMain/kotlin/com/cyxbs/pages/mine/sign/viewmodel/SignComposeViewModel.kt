package com.cyxbs.pages.mine.sign.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.mine.sign.model.bean.SignStatus
import com.cyxbs.pages.mine.sign.model.service.SignService
import com.cyxbs.pages.mine.sign.util.SignUtil
import com.cyxbs.pages.mine.sign.util.postDailySignTask
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

  private val _isChecking: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isChecking: StateFlow<Boolean> get() = _isChecking.asStateFlow()

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
  fun refreshScoreStatus(
    animateLine: Boolean = false,
    onRefreshSuccess: (() -> Unit)? = null,
  ) {
    launchByViewModelScope {
      refreshScoreStatusInternal(animateLine, onRefreshSuccess)
    }
  }

  private suspend fun refreshScoreStatusInternal(
    animateLine: Boolean = false,
    onRefreshSuccess: (() -> Unit)? = null,
  ): Boolean {
    val result = runCatchingCoroutine {
      SignService::class.impl().getSignStatus()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data
    }
    if (result.isFailure) {
      toast("获取积分失败，请稍后再试")
      return false
    }

    val signStatus = result.getOrThrow()
    // 数据显示的是周日->周一，需要反转成周一->周日
    val uiSignStatus = signStatus.copy(
      weekInfo = signStatus.weekInfo.reversed()
    )
    _signStatus.value = uiSignStatus
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
          weekInfo = uiSignStatus.weekInfo
        )
      )
    }
    onRefreshSuccess?.invoke()
    return true
  }

  /**
  * 执行签到
   */
  fun checkIn() {
    if (_isChecking.value) return
    _isChecking.value = true
    launchByViewModelScope {
      try {
        val result = runCatchingCoroutine {
          SignService::class.impl().checkIn().also { response ->
            if (response.status != 405) {
              response.throwApiExceptionIfFail()
            }
          }
        }
        if (result.isFailure) {
          toast("签到失败，请稍后再试")
          return@launchByViewModelScope
        }

        val response = result.getOrThrow()
        if (response.status == 405) {
          toast("寒暑假不可签到")
          refreshScoreStatusInternal()
        } else if (refreshScoreStatusInternal(animateLine = true)) {
          postDailySignTask()
        }
      } finally {
        _isChecking.value = false
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
