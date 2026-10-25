package com.cyxbs.pages.mine.sign.model.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**  
 * description: 发送事件的仓库层
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/25 14:43
 */
object SignRepository {
  private val _statusChangedEvent = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1
  )
  val statusChangedEvent get() = _statusChangedEvent.asSharedFlow()

  suspend fun notifyStatusChanged() {
    _statusChangedEvent.emit(Unit)
  }
}