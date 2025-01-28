package com.cyxbs.pages.mine.page.setting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.base.dailog.ChooseDialog
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.extensions.launch
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.utils.config.PhoneCalendar
import com.cyxbs.components.utils.utils.judge.RedrockNetwork
import com.cyxbs.pages.login.api.ILoginService
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.security.activity.SecurityActivity
import com.cyxbs.pages.mine.util.ui.CourseMaxWeekDialog
import com.cyxbs.pages.mine.util.ui.WarningDialog
import com.cyxbs.pages.mine.util.widget.SwitchPlus
import com.mredrock.cyxbs.common.config.COURSE_SHOW_STATE
import com.mredrock.cyxbs.common.config.SP_WIDGET_NEED_FRESH
import com.mredrock.cyxbs.common.config.WIDGET_COURSE

class SettingActivity : BaseActivity() {
    private val mSwitch by R.id.mine_setting_switch.view<SwitchPlus>()
    private val mFmSecurity by R.id.mine_setting_fm_security.view<FrameLayout>()
    private val mFmClear by R.id.mine_setting_fm_clear.view<FrameLayout>()
    private val mFmCourseMaxWeek by R.id.mine_setting_fm_course_max_week.view<FrameLayout>()
    private val mBtnExit by R.id.mine_setting_btn_exit.view<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_setting)
        val toolbar = findViewById<JToolbar>(R.id.toolbar)
        //初始化toolbar

        toolbar.apply {
            setBackgroundColor(
                ContextCompat.getColor(
                    this@SettingActivity,
                    com.mredrock.cyxbs.common.R.color.common_mine_setting_common_back_color
                )
            )
            init(
                this@SettingActivity,
                "设置",
                withSplitLine = true,
                titleOnLeft = false
            )
        }
        //启动App优先显示课表
        mSwitch.setOnCheckedChangeListener { _, isChecked ->
            defaultSp.edit {
                if (isChecked) {
                    putBoolean(COURSE_SHOW_STATE, true)
                } else {
                    putBoolean(COURSE_SHOW_STATE, false)
                }
            }
        }
        mSwitch.isChecked = defaultSp.getBoolean(COURSE_SHOW_STATE, false)

        //账号安全
        mFmSecurity.setOnSingleClickListener {
            doIfLogin {
                startActivity(Intent(this, SecurityActivity::class.java))
            }
        }
        
        // 清理软件数据
        mFmClear.setOnSingleClickListener {
            var boolean = false
            ChooseDialog.Builder(
                this,
                ChooseDialog.DataImpl(
                    content = "清理软件数据将重新登录并还原所有本地设置，请慎重选择！",
                    positiveButtonText = "确定",
                    negativeButtonText = "取消",
                    height = 160,
                )
            ).setPositiveClick {
                if (!boolean) {
                    toast("请再次点击进行确定")
                    boolean = true
                } else {
                    try {
                        // 用命令清理软件数据
                        Runtime.getRuntime().exec("pm clear $packageName")
                    } catch (e: Exception) {
                        toastLong("清理失败，请进入设置页面手动点击”清理数据“")
                        // 会打开手机应用设置中的掌邮页面，让他自动点击清理软件数据
                        val uri = Uri.parse("package:$packageName")
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                        startActivity(intent)
                    }
                }
            }.setNegativeClick {
                dismiss()
            }.show()
        }

        // 课表最大周数设置
        mFmCourseMaxWeek.setOnSingleClickListener {
            CourseMaxWeekDialog.Builder(this)
                .show()
        }
        
        //退出登录
        mBtnExit.setOnSingleClickListener {
            doIfLogin {
                toast("退出登录会先检查请求是否正常，请稍后~")
                onExitClick()
            }
        }
    }

    /**
     * 当前是否正在 ping 后端网络，防止重复点击退出
     */
    private var mIsInPingNetWork: Boolean = false

    private fun onExitClick() {
        if (mIsInPingNetWork) {
            // 防止重复点击
            return
        }
        mIsInPingNetWork = true
        launch {
            val result = RedrockNetwork.tryPingNetWork()
            if (result != null && result.isSuccess) {
                //判定magipoke系列接口正常，允许正常退出登陆
                doExit()
            } else {
                //判定magipoke系列接口异常，极有可能会导致退出之后无法重新登陆，弹一个dialog提示一下
                WarningDialog.showDialog(
                    this@SettingActivity,
                    "温馨提示",
                    "因服务器或当前手机网络原因，检测到掌邮核心服务暂不可用，退出登录之后有可能会导致无法正常登录，是否确认退出登录？",
                    onNegativeClick = {
                        //内部已经将dialog消除，这里啥都不用处理
                    },
                    onPositiveClick = {
                        jumpToLoginActivity()
                    }
                )
            }
            mIsInPingNetWork = false
        }
    }

    private fun doExit() {
        ChooseDialog.Builder(
            this,
            ChooseDialog.DataImpl(
                content = "是否退出登录？",
                positiveButtonText = "确定",
                negativeButtonText = "取消",
                height = 160
            )
        ).setPositiveClick {
            jumpToLoginActivity()
        }.setNegativeClick {
            dismiss()
        }.show()
    }
    
    private fun jumpToLoginActivity() {
        cleanData()
        //清除user信息，必须要在LoginStateChangeEvent之前
        IAccountEditService::class.impl().onLogout()
        ILoginService::class.impl().jumpToLoginPage()
        finishAndRemoveTask()
    }

    private fun cleanData() {
        // 清理小组件缓存
        defaultSp.edit {
            putString(WIDGET_COURSE, "")
            putBoolean(SP_WIDGET_NEED_FRESH, true)
        }
        // 清除手机日历事件
        val calendarId = PhoneCalendar.getCalendarAccount()
        if (calendarId != null) {
            PhoneCalendar.deleteCalendarAccount(calendarId)
        }
    }
}