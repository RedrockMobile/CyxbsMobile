package com.cyxbs.pages.sport.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.sport.model.network.SportApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 体育打卡信息说明（feed 说明弹窗内容）共享源，后端下发，进程内拉取一次。
 */
object SportNoticeRepository {

  /**
   * 信息说明数据。
   * - null：尚未拉取到结果
   * - Result：一次请求的成功或失败结果
   */
  val noticeData: StateFlow<Result<List<NoticeItem>>?> get() = _noticeData
  private val _noticeData = MutableStateFlow<Result<List<NoticeItem>>?>(null)

  init {
    getNoticeInfo()
  }

  fun getNoticeInfo() {
    appCoroutineScope.launch {
      runCatchingCoroutine {
        SportApiService::class.impl().getSportNotice()
      }.mapCatching { it.data }
        .onSuccess { _noticeData.value = Result.success(it) }
        .onFailure { _noticeData.value = Result.failure(it) }
    }
  }
}
