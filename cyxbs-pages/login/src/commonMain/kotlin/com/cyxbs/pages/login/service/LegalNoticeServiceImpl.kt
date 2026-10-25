package com.cyxbs.pages.login.service

import com.cyxbs.components.config.PRIVACY_POLICY_URL
import com.cyxbs.components.config.USER_AGREEMENT_URL
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.g985892345.provider.api.annotation.ImplProvider

@ImplProvider
object LegalNoticeServiceImpl : ILegalNoticeService {
  override fun openUserAgreementScreen() {
    AppScheme.jump(USER_AGREEMENT_URL)
  }

  override fun openPrivacyPolicyScreen() {
    AppScheme.jump(PRIVACY_POLICY_URL)
  }
}
