package com.cyxbs.pages.notification.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.notification.model.ActivityMessageRepository

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/6
 */
class UFieldMsgViewModel : BaseViewModel() {

  val ufieldMsgFlow = ActivityMessageRepository.activityMessageFlow

  /*
    * 改变活动消息的读取状态
    * */
  fun changeUfieldMsgStatus(messageId: Int) {
    launch {
      ActivityMessageRepository.changeActivityMsgStatus(messageId)
    }
  }
}