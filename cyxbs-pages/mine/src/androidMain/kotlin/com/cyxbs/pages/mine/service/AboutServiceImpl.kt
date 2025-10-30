package com.cyxbs.pages.mine.service

import androidx.compose.runtime.MutableState
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.mine.about.service.IAboutService
import com.cyxbs.pages.mine.util.ui.DebugUpdateDialog
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * @Desc : 关于我们服务实现类
 * @Author : zzx
 * @Date : 2025/10/30 17:40
 */

@ImplProvider(clazz = IAboutService::class, name = "about")
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

    override fun bingUpdate(state: MutableState<String>) {
        selfUpdateCheck = false // 这里需要初始化一下，防止退出重进后会弹toast
        val activity = appTopActivity.get() ?: return
        IAppUpdateService::class.impl().apply {
            getUpdateStatus().observe(activity as LifecycleOwner) {
                when (it) {
                    AppUpdateStatus.UNCHECK -> checkUpdate()

                    AppUpdateStatus.DATED -> {
                        state.value = "发现新版本"
                        if (selfUpdateCheck) noticeUpdate(activity as FragmentActivity)
                    }

                    AppUpdateStatus.VALID -> {
                        state.value = "已是最新版本"
                        if (selfUpdateCheck) toast("已经是最新版了哦")
                    }

                    AppUpdateStatus.ERROR -> {
                        state.value = "建议再试试哟~"
                        if (selfUpdateCheck) toast("有一股神秘力量阻拦了更新，请稍候重试或尝试反馈")
                    }

                    else -> {}
                }
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