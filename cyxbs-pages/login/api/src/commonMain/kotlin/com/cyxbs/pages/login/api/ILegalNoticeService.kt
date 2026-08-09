package com.cyxbs.pages.login.api

/** 用户协议与隐私政策的统一跳转服务。 */
interface ILegalNoticeService {

  fun startUserAgreementActivity()

  fun startPrivacyPolicyActivity()
}
