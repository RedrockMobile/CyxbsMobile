package com.cyxbs.pages.login.api

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/23
 */
interface ILegalNoticeService {

  /**
   * 跳转到用户协议页面
   */
  fun startUserAgreementActivity()

  /**
   * 跳转到隐私政策页面
   */
  fun startPrivacyPolicyActivity()
}