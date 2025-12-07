package com.cyxbs.pages.notification.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.notification.bean.ChangeReadStatusToBean
import com.cyxbs.pages.notification.model.SystemMessageRepository

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/6
 */
class SystemMsgViewModel : BaseViewModel() {

  val systemMsgFlow = SystemMessageRepository.systemMessageFlow

  /**
   * 改变系统消息已读状态
   * 如果是null则是改变所有消息的可读状态
   */
  fun changeMsgStatus(bean: ChangeReadStatusToBean) {
    launchByViewModelScope {
      SystemMessageRepository.changeMsgStatus(bean)
    }
  }
}