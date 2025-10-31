package com.cyxbs.pages.mine.service

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.asFlow
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.mine.about.service.IAboutService
import com.cyxbs.pages.mine.util.ui.DebugUpdateDialog
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * @Desc : 关于我们服务实现类
 * @Author : zzx
 * @Date : 2025/10/30 17:40
 */

@ImplProvider(clazz = IAboutService::class)
object AboutServiceImpl : IAboutService {

    var selfUpdateCheck = false

    override fun clickUserAgreement() {
        ILegalNoticeService::class.impl().startUserAgreementActivity()
    }

    override fun clickPrivacyPolicy() {
        ILegalNoticeService::class.impl().startPrivacyPolicyActivity()
    }

    override fun debugUpdateInfo() {
        val activity = appTopActivity.get() ?: return
        DebugUpdateDialog.Builder(activity).setPositiveClick {
            IAppUpdateService::class.impl().debug(
                activity as FragmentActivity,
                getContent()
            )
        }.show()
    }

    override fun bindUpdate(): Flow<String> {
        selfUpdateCheck = false // 这里需要初始化一下，防止退出重进后会弹toast
        val activity = appTopActivity.get() ?: return flowOf("已是最新版本")
        val service = IAppUpdateService::class.impl()
        return service.getUpdateStatus()
            .asFlow()
            .onEach { status ->
                when (status) {
                    AppUpdateStatus.UNCHECK -> service.checkUpdate()

                    AppUpdateStatus.DATED -> {
                        if (selfUpdateCheck) service.noticeUpdate(activity as FragmentActivity)
                    }

                    AppUpdateStatus.VALID -> {
                        if (selfUpdateCheck) toast("已经是最新版了哦")
                    }

                    AppUpdateStatus.ERROR -> {
                        if (selfUpdateCheck) toast("有一股神秘力量阻拦了更新，请稍候重试或尝试反馈")
                    }

                    else -> {}
                }
            }.map { status ->
                when (status) {
                    AppUpdateStatus.DATED -> "发现新版本"
                    AppUpdateStatus.VALID -> "已是最新版本"
                    AppUpdateStatus.ERROR -> "建议再试试哟~"
                    else -> "已是最新版本"
                }
            }
    }

    override fun clickUpdate() {
        val activity = appTopActivity.get() ?: return
        selfUpdateCheck = true
        IAppUpdateService::class.impl().apply {
            when (getUpdateStatus().value) {
                AppUpdateStatus.DATED -> {
                    noticeUpdate(activity as FragmentActivity)
                }

                else -> checkUpdate()
            }
        }
    }
}