package com.cyxbs.pages.notification.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.notification.api.IHomeNoticeDialogService
import com.cyxbs.pages.notification.bean.NoticeDialogBean
import com.cyxbs.pages.notification.api.NoticeNavArgument
import com.cyxbs.pages.notification.dialog.NoticeDialogContent
import com.cyxbs.pages.notification.network.INoticeDialogApiService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2025/11/3
 */
@ImplProvider
object HomeNoticeDialogServiceImpl : IHomeNoticeDialogService {

  val noticeStateFlow = MutableStateFlow<NoticeDialogBean?>(null)

  val KEY_NOTICE_HAS_SHOWN = "notice_dialog_has_shown_"

  init {
    // 触发请求
    appCoroutineScope.launch {
      runCatchingCoroutine {
        INoticeDialogApiService::class.impl().getNoticeDialog()
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data
      }.onSuccess { response ->
        response.notice.firstOrNull { notice ->
          !defaultSettings.getBoolean("${KEY_NOTICE_HAS_SHOWN}${notice.id}", false)
        }?.also {
          noticeStateFlow.value = it
        }
      }
    }
  }

  @Composable
  override fun HomeNoticeDialogContent() {
    HomeNoticeDialog()
  }
}

@Composable
private fun HomeNoticeDialog() {
  val noticeState = HomeNoticeDialogServiceImpl.noticeStateFlow.collectAsState()
  val notice = noticeState.value ?: return
  Dialog(
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = false,
    ),
    onDismissRequest = {
      HomeNoticeDialogServiceImpl.noticeStateFlow.value = null
      defaultSettings.putBoolean("${HomeNoticeDialogServiceImpl.KEY_NOTICE_HAS_SHOWN}${notice.id}", true)
    }
  ) {
    NoticeDialogContent(
      NoticeNavArgument(
        title = notice.title,
        content = notice.content,
        map = notice.map,
        button = notice.button,
      )
    )
  }
}