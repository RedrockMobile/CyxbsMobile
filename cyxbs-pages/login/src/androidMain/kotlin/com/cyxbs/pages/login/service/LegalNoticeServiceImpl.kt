package com.cyxbs.pages.login.service

import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.login.ui.LegalNoticeActivity
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/23
 */
@ImplProvider
object LegalNoticeServiceImpl : ILegalNoticeService {
  override fun startUserAgreementActivity() {
    LegalNoticeActivity.start(
      url = "https://fe-prod.redrock.cqupt.edu.cn/redrock-cqapp-protocol/user-agreement/index.html"
    )
  }

  override fun startPrivacyPolicyActivity() {
    LegalNoticeActivity.start(
      url = "https://fe-prod.redrock.cqupt.edu.cn/redrock-cqapp-protocol/privacy-notice/index.html"
    )
  }
}