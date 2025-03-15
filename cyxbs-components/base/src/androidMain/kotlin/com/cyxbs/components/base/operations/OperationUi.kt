package com.cyxbs.components.base.operations

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.dailog.BaseChooseDialog
import com.cyxbs.components.base.dailog.ChooseDialog
import com.cyxbs.components.base.ui.BaseUi
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.login.api.ILoginService

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/16 17:47
 */

/**
 * 如果没有登录则会引导去登录界面
 */
fun BaseUi.doIfLogin(msg: String? = "此功能", next: (() -> Unit)? = null): Boolean {
  val accountService = IAccountService::class.impl()
  if (accountService.isLogin()) {
    next?.invoke()
    return true
  } else {
    ChooseDialog.Builder(
      rootView.context,
      ChooseDialog.DataImpl(
        type = BaseChooseDialog.DialogType.ONE_BUT,
        height = 150,
        content = "请先登录才能使用${msg}哦~",
        contentSize = 14F,
        positiveButtonText = "去登录"
      )
    ).setPositiveClick {
      ILoginService::class.impl().jumpToLoginPage()
    }.show()
    return false
  }
}