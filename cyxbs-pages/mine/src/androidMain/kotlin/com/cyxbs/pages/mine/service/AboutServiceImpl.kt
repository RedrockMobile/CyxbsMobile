package com.cyxbs.pages.mine.service

import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.mine.about.service.IAboutService
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * @Desc : 关于我们服务实现类
 * @Author : zzx
 * @Date : 2025/10/30 17:40
 */

@ImplProvider(clazz = IAboutService::class)
object AboutServiceImpl : IAboutService {

    override fun clickUserAgreement() {
        ILegalNoticeService::class.impl().startUserAgreementActivity()
    }

    override fun clickPrivacyPolicy() {
        ILegalNoticeService::class.impl().startPrivacyPolicyActivity()
    }
}