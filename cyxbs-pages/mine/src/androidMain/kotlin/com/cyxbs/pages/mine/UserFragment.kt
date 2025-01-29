package com.cyxbs.pages.mine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.Pair
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.config.route.MINE_ENTRY
import com.cyxbs.components.config.route.NOTIFICATION_HOME
import com.cyxbs.components.config.route.STORE_ENTRY
import com.cyxbs.components.config.route.UFIELD_CENTER_ENTRY
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.setAvatarImageFromUrl
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.service.startActivity
import com.cyxbs.pages.mine.noyification.NotificationUtils
import com.cyxbs.pages.mine.page.about.AboutActivity
import com.cyxbs.pages.mine.page.edit.EditInfoActivity
import com.cyxbs.pages.mine.page.feedback.center.ui.FeedbackCenterActivity
import com.cyxbs.pages.mine.page.setting.SettingActivity
import com.cyxbs.pages.mine.page.sign.DailySignActivity
import com.g985892345.provider.api.annotation.ImplProvider
import com.mredrock.cyxbs.common.utils.extensions.loadAvatar
import kotlinx.coroutines.launch

/**
 * Created by zzzia on 2018/8/14.
 * 我的 主界面Fragment
 * 这个类的代码不要格式化了吧 否则initView里面的代码会很凌乱
 */
@SuppressLint("SetTextI18n")
@ImplProvider(clazz = Fragment::class, name = MINE_ENTRY)
class UserFragment : BaseFragment() {

    private val viewModel by viewModels<UserViewModel>()

    private val mine_user_iv_center_stamp by R.id.mine_user_iv_center_stamp.view<ImageView>()
    private val mine_user_iv_center_feedback by R.id.mine_user_iv_center_feedback.view<ImageView>()
    private val mine_user_tv_sign by R.id.mine_user_tv_sign.view<TextView>()
    private val mine_user_btn_sign by R.id.mine_user_btn_sign.view<TextView>()
    private val mine_user_fm_about_us by R.id.mine_user_fm_about_us.view<FrameLayout>()
    private val mine_user_fm_setting by R.id.mine_user_fm_setting.view<FrameLayout>()
    private val mine_user_cl_info by R.id.mine_user_cl_info.view<ConstraintLayout>()
    private val mine_user_iv_center_notification by R.id.mine_user_iv_center_notification.view<ImageView>()
    private val mine_user_avatar by R.id.mine_user_avatar.view<ImageView>()
    private val mine_user_username by R.id.mine_user_username.view<TextView>()
    private val mine_user_iv_center_activity by R.id.mine_user_iv_center_activity.view<ImageView>()
    private val mine_user_tv_center_notification_count by R.id.mine_user_tv_center_notification_count.view<TextView>()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 偏移状态栏
        // 因为外层是 Compose 会默认消耗 OnApplyWindowInsets，所以这里只能单独获取状态栏高度
        requireActivity().window.decorView.doOnAttach {
            val statusBarsInsets = WindowInsetsCompat.toWindowInsetsCompat(it.rootWindowInsets)
                .getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(statusBarsInsets.left, statusBarsInsets.top, statusBarsInsets.right, statusBarsInsets.bottom)
        }
        addObserver()
        initView()
    }

    private fun initView() {
        //功能按钮
        context?.apply {
            mine_user_iv_center_stamp.setOnSingleClickListener {
                doIfLogin {
                    // “邮票中心”点击埋点
                    appCoroutineScope.launch {
                        TrackingUtils.trackClickEvent(ClickEvent.CLICK_YLC_YPZX_ENTRY)
                    }

                    startActivity(STORE_ENTRY)
                }
            }
            mine_user_iv_center_feedback.setOnSingleClickListener {
                doIfLogin {
                    // “反馈中心”点击埋点
                    appCoroutineScope.launch {
                        TrackingUtils.trackClickEvent(ClickEvent.CLICK_YLC_FKZX_ENTRY)
                    }

                    startActivity(
                        Intent(
                            this,
                            FeedbackCenterActivity::class.java
                        )
                    )
                }
            }

            mine_user_tv_sign.setOnSingleClickListener {
                doIfLogin {
                    startActivity(
                        Intent(
                            this,
                            DailySignActivity::class.java
                        )
                    )
                }
            }
            mine_user_btn_sign.setOnSingleClickListener {
                doIfLogin {
                    startActivity(
                        Intent(
                            this,
                            DailySignActivity::class.java
                        )
                    )
                }
            }

            mine_user_fm_about_us.setOnSingleClickListener {
                doIfLogin {
                    startActivity(
                        Intent(
                            this,
                            AboutActivity::class.java
                        )
                    )
                }
            }
            mine_user_fm_setting.setOnSingleClickListener {
                doIfLogin {
                    startActivity(
                        Intent(
                            this,
                            SettingActivity::class.java
                        )
                    )
                }
            }
            mine_user_cl_info.setOnSingleClickListener {
                doIfLogin {
                    startActivity(
                        Intent(
                            context,
                            EditInfoActivity::class.java
                        ),
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                            context as Activity,
                            Pair(mine_user_avatar, "avatar")
                        ).toBundle()
                    )
                }
            }

            mine_user_iv_center_notification.setOnSingleClickListener {
                if (IAccountService::class.impl().isLogin()) {
                    // 消息中心入口点击埋点
                    appCoroutineScope.launch {
                        TrackingUtils.trackClickEvent(ClickEvent.CLICK_YLC_XXZX_ENTRY)
                    }
                }

                startActivity(NOTIFICATION_HOME)
                // 进入消息中心，移除红点
                mine_user_tv_center_notification_count.gone()
            }
            mine_user_iv_center_activity.setOnSingleClickListener {
                doIfLogin {
                    // “活动中心”点击埋点
                    appCoroutineScope.launch {
                        TrackingUtils.trackClickEvent(ClickEvent.CLICK_YLC_HDZX_ENTRY)
                    }
                    startActivity(UFIELD_CENTER_ENTRY)
                }
            }
            mine_user_avatar.setOnSingleClickListener {
                doIfLogin {
                    startActivity(
                        Intent(
                            context,
                            EditInfoActivity::class.java
                        ),
                        (context as? Activity)?.let { it1 ->
                            ActivityOptionsCompat.makeSceneTransitionAnimation(
                                it1,
                                Pair(mine_user_avatar, "avatar")
                            ).toBundle()
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addObserver() {
        viewModel.status.observe(viewLifecycleOwner) {
            mine_user_tv_sign.text = "已连续签到 ${it.serialDays} 天 "
            if (it.isChecked) {
                mine_user_btn_sign.apply {
                    background = ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.mine_bg_round_corner_grey,
                        null
                    )
                    text = "已签到"
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            com.mredrock.cyxbs.common.R.color.common_grey_button_text
                        )
                    )
                }
            } else {
                mine_user_btn_sign.apply {
                    text = "签到"
                    background = ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.mine_shape_bg_user_btn_sign,
                        null
                    )
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            com.mredrock.cyxbs.common.R.color.common_white_font_color
                        )
                    )
                }
            }
        }

        // 消息中心的红点显示逻辑
        viewModel.newNotificationCount.observe(viewLifecycleOwner) { value ->
            if (value == 0) {
                mine_user_tv_center_notification_count.gone()
            } else {
                mine_user_tv_center_notification_count.visible()
                if (value > 99) {
                    mine_user_tv_center_notification_count.text = "99+"
                } else {
                    mine_user_tv_center_notification_count.text = value.toString()
                }
            }
        }

    }

    override fun onStart() {
        super.onStart()
        // 发送签到的通知
        NotificationUtils.tryNotificationSign(viewModel.status.value?.isChecked ?: false)
        // 更新最新未读消息数量
//        viewModel.getNewNotificationCount()
    }

    override fun onResume() {
        super.onResume()
        if (IAccountService::class.impl().isLogin()) {
            fetchInfo()
        }
    }

    private fun fetchInfo() {
        viewModel.getScoreStatus()
        refreshUserLayout()
    }

    //刷新和User信息有关的界面
    private fun refreshUserLayout() {
        val userInfo = IAccountService::class.impl().userInfo.value ?: return
        mine_user_avatar.setAvatarImageFromUrl(userInfo.photoSrc)
        mine_user_username.text = userInfo.username
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.mine_fragment_main_new, container, false)
}