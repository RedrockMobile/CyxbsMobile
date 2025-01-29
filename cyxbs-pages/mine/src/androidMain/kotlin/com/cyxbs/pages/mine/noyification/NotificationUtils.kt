package com.cyxbs.pages.mine.noyification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.mine.page.sign.DailySignActivity
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

/**
 * module_notification 模块专用逻辑
 *
 * 之前是中泰写在了 module_main 中，现我把它移到 module_mine 中
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/15 16:51
 */
object NotificationUtils {
  
  //消息模块的sp file name
  private const val NOTIFICATION_SP_FILE_NAME = "notification_module_sp_file"
  
  //消息设置界面switch2是否选中的key
  private const val IS_SWITCH2_SELECT = "setting_switch2_state"
  
  private const val NOTIFY_TAG = "notify_tag"
  
  /**
   * 大前提：用户允许签到提醒
   * 如果已经签到&&时间小于18 shouldNotify false next day true
   * 已经签到&&时间大于等于18  shouldNotify false next day true
   * 没有签到&&时间小于18  shouldNotify false next day false
   * 没有签到&&时间大于等于18 shouldNotify true next day true
   */
  fun tryNotificationSign(isSign: Boolean) {
    //用户不允许提醒 直接返回
    val notificationSp = com.cyxbs.components.init.appContext.getSharedPreferences(NOTIFICATION_SP_FILE_NAME, Context.MODE_PRIVATE)
    if (!notificationSp.getBoolean(IS_SWITCH2_SELECT, true)) return
    val workManager = WorkManager.getInstance(com.cyxbs.components.init.appContext)
    val hour = Calendar.HOUR_OF_DAY
    val data: Data
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
  }
  
  class NotifySignWorker(
    private val ctx: Context,
    params: WorkerParameters
  ) : Worker(ctx, params) {
    override fun doWork(): Result {
      WorkManager.getInstance(applicationContext).cancelAllWorkByTag(NOTIFY_TAG)
      
      val isNextDay = inputData.getBoolean("isNextDay",false)
      val shouldNotify = inputData.getBoolean("shouldNotify",true)
      
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
      
      if(Calendar.HOUR >= 18 && shouldNotify){
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
      val intent = Intent(ctx, DailySignActivity::class.java)
      val pIntent = PendingIntent.getActivity(ctx, 1, intent, 0)
      builder.setContentIntent(pIntent)
      builder.setDefaults(NotificationCompat.DEFAULT_ALL)
      val notification = builder.build()
      manager.notify(1, notification)
    }
  }
}