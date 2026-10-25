package com.cyxbs.pages.login.service

/**
 * 登录模块在 iOS 端的平台能力契约
 *
 * cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 通过 KtProvider 实现本接口，
 * 最终落到 iosApp 的 KmpInterfaceImpl 调用原生能力。
 */
interface LoginIosPlatform {

  /**
   * 跳转找回密码页面
   * @param stuNum 当前输入的学号（可能为空）
   */
  fun jumpForgotPassword(stuNum: String)

  /**
   * 打开用户协议页面
   */
  fun jumpUserAgreement()

  /**
   * 打开隐私政策页面
   */
  fun jumpPrivacyPolicy()

  /**
   * 退出应用（用户不同意用户协议时调用）
   */
  fun exitApp()
}
