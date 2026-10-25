package com.cyxbs.pages.login.viewmodel

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.pages.login.api.LoginNavArgument
import com.cyxbs.pages.login.service.LoginIosPlatform

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/31
 */
actual class LoginViewModel actual constructor(argument: LoginNavArgument) :
  CommonLoginViewModel(argument) {

  override fun clickForgetPassword() {
    LoginIosPlatform::class.impl().jumpForgotPassword(stuNum.value)
  }

  override fun clickUserAgreement() {
    LoginIosPlatform::class.impl().jumpUserAgreement()
  }

  override fun clickPrivacyPolicy() {
    LoginIosPlatform::class.impl().jumpPrivacyPolicy()
  }

  override fun clickDisagreeUserAgreement() {
    if (appNavBackStack.size == 1) {
      // 没有上一级时就退出应用
      LoginIosPlatform::class.impl().exitApp()
    } else {
      argument.popBackStack()
    }
  }
}