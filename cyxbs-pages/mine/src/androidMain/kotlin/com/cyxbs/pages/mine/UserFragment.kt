package com.cyxbs.pages.mine

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.route.MINE_ENTRY
import com.cyxbs.components.config.route.STORE_ENTRY
import com.cyxbs.components.config.route.UFIELD_CENTER_ENTRY
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.pages.mine.page.edit.EditInfoActivity
import com.cyxbs.pages.mine.page.feedback.center.ui.FeedbackCenterActivity
import com.cyxbs.pages.mine.page.setting.SettingActivity
import com.cyxbs.pages.mine.page.sign.DailySignActivity
import com.cyxbs.pages.mine.user.MineNavPlatform
import com.cyxbs.pages.mine.user.MinePage
import com.cyxbs.pages.notification.api.ILaunchNotificationService
import com.cyxbs.pages.notification.api.INotificationService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * Created by zzzia on 2018/8/14.
 * 我的 主界面Fragment
 * 这个类的代码不要格式化了吧 否则initView里面的代码会很凌乱
 */
@SuppressLint("SetTextI18n")
@ImplProvider(clazz = Fragment::class, name = MINE_ENTRY)
class UserFragment : BaseFragment() {

    // 页面已迁移为 commonMain 的 [com.cyxbs.pages.mine.user.MineNavEntry]，
    // 这里仅作为 ComposeView 宿主复用同一份 Compose 内容 [MinePage]，内容完全还原。
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    MinePage()
                }
            }
        }
    }
}

/**
 * 「我的」主页平台能力的 Android 实现，供 commonMain 的 [MinePage] 通过
 * `MineNavPlatform::class.implOrNull()` 调用。
 */
@ImplProvider
object MineNavPlatformImpl : MineNavPlatform {

    override val unreadCount: StateFlow<Int>
        get() = INotificationService::class.impl().unreadCount

    override fun launchNotification() {
        if (IAccountService::class.impl().isLogin()) {
            // 消息中心入口点击埋点
            TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_XXZX_ENTRY)
        }
        ILaunchNotificationService::class.impl().start()
    }

    override fun jumpStore() {
        // “邮票中心”点击埋点
        TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_YPZX_ENTRY)
        startActivity(STORE_ENTRY)
    }

    override fun jumpFeedbackCenter() {
        // “反馈中心”点击埋点
        TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_FKZX_ENTRY)
        startActivity(FeedbackCenterActivity::class)
    }

    override fun jumpSign() {
        startActivity(DailySignActivity::class)
    }

    override fun jumpSetting() {
        startActivity(SettingActivity::class)
    }

    override fun jumpEditInfo() {
        // 原有头像共享元素转场迁移为 Compose 后丢失，仅普通跳转
        startActivity(EditInfoActivity::class)
    }

    override fun jumpActivityCenter() {
        // “活动中心”点击埋点
        TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_HDZX_ENTRY)
        startActivity(UFIELD_CENTER_ENTRY)
    }
}