package com.cyxbs.pages.notification.widget

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cyxbs.components.config.route.MINE_CHECK_IN
import com.cyxbs.components.config.service.implClass
import com.cyxbs.pages.notification.util.Constant.NOTIFY_TAG
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Author by OkAndGreat
 * Date on 2022/5/6 21:20.
 * 提醒打卡的worker
 */
class NotifySignWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {
    override fun doWork(): Result {
        WorkManager.getInstance(applicationContext).cancelAllWorkByTag(NOTIFY_TAG)

        val isNextDay = inputData.getBoolean("isNextDay", false)
        val shouldNotify = inputData.getBoolean("shouldNotify", true)

        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        // 设置在大约 18:00:00 AM 执行
        dueDate.set(Calendar.HOUR_OF_DAY, 18)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        //当前时间已经过了18点
        if (dueDate.before(currentDate) or isNextDay) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val dailyWorkRequest = OneTimeWorkRequestBuilder<NotifySignWorker>()
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .addTag(NOTIFY_TAG)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueue(dailyWorkRequest)

        if (Calendar.HOUR >= 18 && shouldNotify) {
            sendNotification(ctx)
        }

        return Result.success()
    }

    private fun sendNotification(ctx: Context) {
        val manager =
            ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager

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
        val builder = NotificationCompat.Builder(ctx, "CyxbsSignNotification")
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
        val intent = Intent(ctx, Activity::class.implClass(MINE_CHECK_IN).java)
        val pIntent = PendingIntent.getActivity(ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pIntent)
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        val notification = builder.build()
        manager.notify(1, notification)
    }
}