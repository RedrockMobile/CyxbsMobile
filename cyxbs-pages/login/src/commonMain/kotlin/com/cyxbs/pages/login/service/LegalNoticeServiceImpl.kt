package com.cyxbs.pages.login.service

import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.g985892345.provider.api.annotation.ImplProvider

@ImplProvider
object LegalNoticeServiceImpl : ILegalNoticeService {
  override fun startUserAgreementActivity() {
    AppScheme.jump(USER_AGREEMENT_URL)
  }

  override fun startPrivacyPolicyActivity() {
    AppScheme.jump(PRIVACY_POLICY_URL)
  }

  private const val USER_AGREEMENT_URL =
    "https://fe-prod.redrock.cqupt.edu.cn/redrock-cqapp-protocol/user-agreement/index.html?hideTitle=true"
  private const val PRIVACY_POLICY_URL =
    "https://fe-prod.redrock.cqupt.edu.cn/redrock-cqapp-protocol/privacy-notice/index.html?hideTitle=true"
}
