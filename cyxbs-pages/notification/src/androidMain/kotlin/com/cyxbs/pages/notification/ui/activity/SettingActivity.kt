package com.cyxbs.pages.notification.ui.activity

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.Switch
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.MINE_CHECK_IN
import com.cyxbs.components.config.route.NOTIFICATION_SETTING
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.implClass
import com.cyxbs.pages.notification.BuildConfig
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.util.Constant.IS_SWITCH1_SELECT
import com.cyxbs.pages.notification.util.Constant.IS_SWITCH2_SELECT
import com.cyxbs.pages.notification.util.Constant.NOTIFY_TAG
import com.cyxbs.pages.notification.util.NotificationSp
import com.cyxbs.pages.notification.viewmodel.NotificationViewModel
import com.cyxbs.pages.notification.widget.NotifySignWorker
import com.g985892345.provider.api.annotation.KClassProvider
import java.util.Calendar
import kotlin.properties.Delegates

/**
 * Author by OkAndGreat
 * Date on 2022/4/25 22:46.
 *
 */
//考虑到通知设置页以后可能会有其它渠道进入，故配置一个路由
@KClassProvider(clazz = Activity::class, name = NOTIFICATION_SETTING)
class SettingActivity : BaseActivity() {

    private val viewModel by viewModels<NotificationViewModel>()

    private var switch1Checked by Delegates.notNull<Boolean>()
    private var switch2Checked by Delegates.notNull<Boolean>()
    private var isSign = false

    private val notification_setting_switch_1 by R.id.notification_setting_switch_1.view<Switch>()
    private val notification_setting_switch_2 by R.id.notification_setting_switch_2.view<Switch>()
    private val notification_setting_test by R.id.notification_setting_test.view<Button>()
    private val notification_rl_setting_back by R.id.notification_rl_setting_back.view<RelativeLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notification_activity_setting)
        initSwitch()
        initViewClickListener()
        initSignObserver()
        viewModel.getCheckInStatus()
    }

    private fun initSignObserver() {
        viewModel.checkInStatus.observe {
            it?.let {
                isSign = it
            }
        }
    }

    private fun initSwitch() {
        switch1Checked = NotificationSp.getBoolean(IS_SWITCH1_SELECT, true)
        switch2Checked = NotificationSp.getBoolean(IS_SWITCH2_SELECT, true)
        notification_setting_switch_1.isChecked = switch1Checked
        notification_setting_switch_2.isChecked = switch2Checked
    }

    private fun initViewClickListener() {
        if (BuildConfig.DEBUG) {
            notification_setting_test.visible()
        }
        notification_setting_test.setOnClickListener {
            val manager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        "CyxbsSignNotification",
                        "签到提醒",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                manager.createNotificationChannel(channel)
            }


            //为了版本兼容  选择V7包下的NotificationCompat进行构造
            val builder = NotificationCompat.Builder(this, "CyxbsSignNotification")
            //Ticker是状态栏显示的提示
            builder.setTicker("打卡咯")
            //第一行内容  通常作为通知栏标题
            builder.setContentTitle("打卡提醒-已经过18点了你还没有打卡噢~~")
            //第二行内容 通常是通知正文
            builder.setContentText("可以在消息设置中关闭打卡呢~")
            //可以点击通知栏的删除按钮删除
            builder.setAutoCancel(true)
            //系统状态栏显示的小图标
            builder.setSmallIcon(com.cyxbs.components.config.R.drawable.config_ic_app_notifacation)
            //下拉显示的大图标
            val intent = Intent(
                this,
                Activity::class.implClass(MINE_CHECK_IN).java
            )
            val pIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pIntent)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            val notification = builder.build()
            manager.notify(1, notification)
        }
        notification_setting_switch_1.setOnCheckedChangeListener { _, isChecked ->
            NotificationSp.edit {
                if (isChecked) {
                    putBoolean(IS_SWITCH1_SELECT, true)
                } else {
                    putBoolean(IS_SWITCH1_SELECT, false)
                }
            }
        }

        notification_setting_switch_2.setOnCheckedChangeListener { _, isChecked ->
            NotificationSp.edit {
                if (isChecked) {
                    /**
                     * 用户选择了签到不提醒到签到提醒的逻辑处理：
                     * 如果已经签到&&时间小于18 shouldNotify false next day true
                     * 已经签到&&时间大于等于18  shouldNotify false next day true
                     * 没有签到&&时间小于18  shouldNotify false next day false
                     * 没有签到&&时间大于等于18 shouldNotify true next day true
                     */
                    val workManager = WorkManager.getInstance(applicationContext)
                    val hour = Calendar.HOUR_OF_DAY
                    var data: Data by Delegates.notNull()
                    var dailySignWorkRequest: OneTimeWorkRequest by Delegates.notNull()
                    val dailySignWorkRequestBuilder =
                        OneTimeWorkRequestBuilder<NotifySignWorker>().addTag(NOTIFY_TAG)

                    if (isSign) {
                        data = Data.Builder()
                            .putBoolean("isNextDay", true)
                            .putBoolean("shouldNotify", false)
                            .build()
                    } else {
                        data = if (hour < 18) {
                            Data.Builder()
                                .putBoolean("isNextDay", false)
                                .putBoolean("shouldNotify", false)
                                .build()
                        } else {
                            Data.Builder()
                                .putBoolean("isNextDay", true)
                                .putBoolean("shouldNotify", true)
                                .build()
                        }
                    }

                    dailySignWorkRequest = dailySignWorkRequestBuilder
                        .setInputData(data)
                        .build()
                    workManager.enqueue(dailySignWorkRequest)
                    putBoolean(IS_SWITCH2_SELECT, true)
                } else {
                    WorkManager.getInstance(applicationContext).cancelAllWorkByTag(NOTIFY_TAG)
                    putBoolean(IS_SWITCH2_SELECT, false)
                }
            }
        }

        notification_rl_setting_back.setOnClickListener {
            finish()
        }
    }
}