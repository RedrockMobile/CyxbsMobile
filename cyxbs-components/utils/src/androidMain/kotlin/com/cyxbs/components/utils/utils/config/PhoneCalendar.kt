package com.cyxbs.components.utils.utils.config

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.icu.util.TimeZone
import android.provider.CalendarContract.CALLER_IS_SYNCADAPTER
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.annotation.IntRange
import androidx.fragment.app.FragmentActivity
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appApplication
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.utils.config.PhoneCalendar.ACCOUNT_TYPE
import com.cyxbs.components.utils.utils.config.PhoneCalendar.add
import com.cyxbs.components.utils.utils.config.PhoneCalendar.getCalendarAccount
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar
import kotlin.coroutines.resume

/**
 * 一个给手机日历添加事件的工具类
 *
 * 能实现的需求：
 * - 定时提醒 (允许使用闹钟的提醒)
 * - 塞一些“永久”信息 (即使软件被卸载后也能保留在手机上 :)
 *
 *
 * https://developer.android.google.cn/guide/topics/providers/calendar-provider?hl=zh-cn
 *
 * 除了官方文档外，你可能还需要阅读以下文档，因为日历信息的添加是有一套国际标准的
 * RFC5545 规范中英对照文档：http://rfc2cn.com/rfc5545.html（如果你想详细研究一下怎么写规则，强烈推荐看下这篇文档）
 *               对应章节
 * RERIOD    规则: 3.3.9
 * RDATE     规则：3.8.5.2
 * RRULE     规则：3.8.5.3、3.3.10
 * DURATION  规则：3.3.6、3.8.2.5
 * DATE      规则：3.3.4
 * DATE-TIME 规则：3.3.5
 * DTSTART   规则：3.8.2.4
 *
 * 当然，你觉得太麻烦了也不可以不用阅读，我对添加事件进行了封装，你只需要传入 [Event] 即可
 *
 * @author 985892345
 * @date 2022/9/24 20:01
 */
object PhoneCalendar {
  
  private val context: Context
    get() = appApplication
  
  /**
   * 账户名称。这个在手机日历账号管理中可以看到。比如：123456789
   *
   * 注意：ACCOUNT_NAME 和 ACCOUNT_TYPE 是一个人日历账户的唯一标识
   */
  fun getAccountName(): String {
    return IAccountService::class.impl().stuNum.orEmpty()
  }
  
  // 账户类型。这个不会显示给用户
  const val ACCOUNT_TYPE = "掌上重邮"
  // 日历路径来源。这个在手机日历账号管理中可以看到。比如：Xiaomi Calendar
  const val NAME = "红岩网校工作站"
  
  
  
  ///////////////////////////////
  //
  //          权限相关
  //
  ///////////////////////////////
  
  /**
   * 检查权限，如果未申请则返回 false
   */
  fun checkPermission(): Boolean {
    return context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
      && context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
  }
  
  /**
   * 申请日历读写权限
   */
  fun applyForPermission(activity: FragmentActivity): Completable {
    return if (checkPermission()) {
      Completable.complete()
    } else {
      Completable.create {
        val dialog = activity.doPermissionAction(
          Manifest.permission.READ_CALENDAR,
          Manifest.permission.WRITE_CALENDAR
        ) {
          doAfterGranted {
            if (!it.isDisposed) {
              it.onComplete()
            }
          }
          doAfterRefused {
            it.tryOnError(RuntimeException("申请权限被拒绝"))
          }
          doOnCancel {
            it.tryOnError(RuntimeException("申请权限被取消"))
          }
        }
        it.setCancellable {
          dialog?.cancel()
        }
      }
    }
  }
  
  ///////////////////////////////
  //
  //        Event 增删改查
  //
  ///////////////////////////////
  
  /**
   * 添加事件，成功就返回事件 Id，失败返回 null
   *
   * 该方法自带了权限申请，所以使用协程
   *
   * @param accountType 账户类型。这个不同可以生成不同的日历账号
   */
  suspend fun add(
    activity: FragmentActivity,
    event: Event,
    accountType: String = ACCOUNT_TYPE
  ): Long? = suspendCancellableCoroutine { continuation ->
    val dispose = applyForPermission(activity)
      .subscribe(
        {
          if (continuation.isActive) {
            continuation.resume(add(event, accountType))
          }
        },
        { continuation.resume(null) }
      )
    continuation.invokeOnCancellation {
      dispose.dispose()
    }
  }
  
  /**
   * 添加事件，成功就返回事件 Id，失败返回 null
   *
   * 注意：如果没有申请读写日历权限，那么将添加失败。要么你自己申请权限，要么使用另一个 [add] 方法
   *
   * @param accountType 账户类型。这个不同可以生成不同的日历账号
   */
  fun add(event: Event, accountType: String = ACCOUNT_TYPE): Long? {
    if (!checkPermission()) return null
    val calendarId = checkOrAddCalendarAccounts(accountType) ?: return null
    if (calendarId < 0) return null // 获取日历失败直接返回
    val value = when (event) {
      is CommonEvent -> getCommonEventContent(event, calendarId)
      is FrequencyEvent -> getFrequencyEventContent(event, calendarId)
    } ?: return null
    try {
      val eventId = context.contentResolver.insert(Events.CONTENT_URI, value)
        ?.lastPathSegment
        ?.toLong() ?: return null
      if (event.remind > 0) {
        addRemind(eventId, event.remind)
      }
      return eventId
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
  }
  
  /**
   * 删除事件,成功返回 true，失败返回 false
   */
  fun delete(eventId: Long): Boolean {
    if (!checkPermission()) return false
    val deleteUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
    return try {
      deleteRemind(eventId) // 经检查，删除 Event 时会自动同步删除
      context.contentResolver.delete(deleteUri, null, null) > 0
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
  
  /**
   * 删除当前账号下所有的事件
   */
  fun deleteAll(accountType: String = ACCOUNT_TYPE): Boolean {
    if (!checkPermission()) return false
    val calendarId = getCalendarAccount(accountType = accountType) ?: return false
    return try {
      context.contentResolver.delete(
        Events.CONTENT_URI,
        "${Events.CALENDAR_ID}=?",
        arrayOf(calendarId.toString())
      ) > 0
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
  
  /**
   * 更新事件,成功返回 true,失败返回 false
   */
  fun update(eventId: Long, event: Event, accountType: String = ACCOUNT_TYPE): Boolean {
    if (!checkPermission()) return false
    val calendarId = getCalendarAccount(accountType = accountType) ?: return false
    if (calendarId < 1) return false
    val value = when (event) {
      is CommonEvent -> {
        getCommonEventContent(event, calendarId) {
          putNull(Events.RRULE) // 删除 RRULE，防止之前是 FrequencyEvent
        }
      }
      is FrequencyEvent -> getFrequencyEventContent(event, calendarId) {
        putNull(Events.RDATE) // 删除 RDATE，防止之前是 CommonEvent
      }
    } ?: return false
    val updateUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
    try {
      if (context.contentResolver.update(updateUri, value, null, null) > 0) {
        // 因为不知道之前有没有设置过提醒时间，所以只能先删除再添加
        deleteRemind(eventId)
        if (event.remind > 0) {
          addRemind(eventId, event.remind)
        }
        return true
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return false
  }
  
  /**
   * 得到当前账户下所有的 Event
   */
  fun getAllEventId(accountType: String = ACCOUNT_TYPE): List<Long>? {
    if (!checkPermission()) return null
    val calendarId = getCalendarAccount(accountType = accountType) ?: return null
    val result = mutableListOf<Long>()
    context.contentResolver.query(
      Events.CONTENT_URI,
      arrayOf(Events._ID),
      "${Events.CALENDAR_ID}=?",
      arrayOf(calendarId.toString()),
      null
    )?.use {
      while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndexOrThrow(Events._ID))
        result.add(id)
      }
    }
    return result
  }
  
  
  
  
  ///////////////////////////////
  //
  //        Remind 增删改查
  //
  ///////////////////////////////
  
  /**
   * 为事件设定提醒
   * @param eventId 事件 id
   * @param minute 分钟数
   */
  private fun addRemind(eventId: Long, minute: Int): Boolean {
    val values = ContentValues().apply {
      put(Reminders.EVENT_ID, eventId)
      put(Reminders.MINUTES, minute)
      put(Reminders.METHOD, Reminders.METHOD_ALERT)
    }
    return try {
      context.contentResolver.insert(Reminders.CONTENT_URI, values) ?: return false
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
  
  private fun deleteRemind(eventId: Long): Boolean {
    return try {
      context.contentResolver.delete(
        Reminders.CONTENT_URI,
        "${Reminders.EVENT_ID}=?",
        arrayOf(eventId.toString())
      ) > 0
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
  
  
  
  ///////////////////////////////
  //
  //       日历账户 增删改查
  //
  ///////////////////////////////
  
  /**
   * 检查是否存在日历账户，不存在时返回 null
   *
   * @param accountType 账户类型。默认是 [ACCOUNT_TYPE]，但你可以在 [add] 时传入其他的
   *
   * @return 返回 CALENDAR_ID，不存在时返回 null
   */
  fun getCalendarAccount(
    accountType: String = ACCOUNT_TYPE,
  ): Long? {
    if (!checkPermission()) return null
    return try {
      context.contentResolver.query(
        Calendars.CONTENT_URI,
        arrayOf(
          // 这个是查询结果只展示这几列
          Calendars._ID,
          Calendars.ACCOUNT_NAME,
          Calendars.ACCOUNT_TYPE,
        ),
        // 下面这个是 WHERE 语句
        "(${Calendars.ACCOUNT_NAME}=?) AND " +
          "(${Calendars.ACCOUNT_TYPE}=?)",
        arrayOf(
          // 这个是用于填充上面写的 ? 值
          getAccountName(),
          accountType,
        ),
        null
      ).use { cursor ->
        if (cursor == null || cursor.count == 0) return null
        cursor.moveToNext() // 必须调用，不然查询就报错
        cursor.getColumnIndex(Calendars._ID).let {
          if (it >= 0) cursor.getLong(it) else null
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }
  
  /**
   * 删除创建的日历账户
   * @param CALENDAR_ID 可以使用 [getCalendarAccount] 获得
   */
  fun deleteCalendarAccount(CALENDAR_ID: Long): Boolean {
    if (!checkPermission()) return false
    return try {
      context.contentResolver.delete(
        Calendars.CONTENT_URI,
        // 下面这个是 WHERE 语句
        "${Calendars._ID}=?",
        arrayOf( // 这个是用于填充上面写的 ? 值
          CALENDAR_ID.toString()
        )
      ) > 0
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
  
  /**
   * 获取日历 ID，如果不存在，则添加一个日历账号
   * @return 日历 ID
   */
  private fun checkOrAddCalendarAccounts(accountType: String): Long? {
    val oldId = getCalendarAccount(accountType = accountType)
    return oldId ?: addCalendarAccount(accountType = accountType)
  }
  
  /**
   * 添加一个日历账户
   */
  private fun addCalendarAccount(accountType: String): Long? {
    if (!checkPermission()) return null
    val value = ContentValues().apply {
      // 日历路径来源。这个在手机日历账号管理中可以看到。比如：Xiaomi Calendar
      put(Calendars.NAME, NAME)
      put(Calendars.ACCOUNT_NAME, getAccountName())
      // 账户类型。这个不会显示给用户
      put(Calendars.ACCOUNT_TYPE, accountType)
      // 给用户显示的该日历名称，一般与 accountType 保持一致。比如：小米日历
      put(Calendars.CALENDAR_DISPLAY_NAME, accountType)
      // 账户账号。日历中添加事件选择日历账户时可以看到，一般与 ACCOUNT_NAME 保持一致
      put(Calendars.OWNER_ACCOUNT, getAccountName())
      put(Calendars.VISIBLE, 1)
      put(Calendars.CALENDAR_COLOR, Color.BLUE)
      put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
      put(Calendars.SYNC_EVENTS, 1)
      put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
      put(Calendars.CAN_ORGANIZER_RESPOND, 0)
    }
    
    return context.contentResolver.insert(
      Calendars.CONTENT_URI.buildUpon()
        .appendQueryParameter(CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, getAccountName())
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
        .build(),
      value
    )?.lastPathSegment?.toLong()
  }
  
  
  ///////////////////////////////
  //
  //        其他工具类函数
  //
  ///////////////////////////////
  
  /**
   * 将时间戳根据 国际 RFC5545 规则装换为 DATE-TIME 类型
   * RFC5545 规范中英对照文档：http://rfc2cn.com/rfc5545.html
   * DATE-TIME 规则：3.3.5
   */
  private fun Calendar.toDateTime(): String {
    val year = get(Calendar.YEAR)
    val month = get(Calendar.MONTH) + 1
    val date = get(Calendar.DATE)
    val hour = get(Calendar.HOUR_OF_DAY)
    val minute = get(Calendar.MINUTE)
    val second = get(Calendar.SECOND)
    return "$year" +
      "${if (month < 10) "0$month" else month}" +
      "${if (date < 10) "0$date" else date}" +
      "T" +
      "${if (hour < 10) "0$hour" else hour}" +
      "${if (minute < 10) "0$minute" else minute}" +
      "${if (second < 10) "0$second" else second}"
  }
  
  private fun getCommonEventContent(
    event: CommonEvent,
    calendarId: Long,
    block: (ContentValues.() -> Unit)? = null
  ): ContentValues? {
    if (event.startTime.isEmpty()) return null
    val zone = TimeZone.getDefault()
    return ContentValues().apply {
      put(Events.TITLE, event.title)
      put(Events.DESCRIPTION, event.description)
      put(Events.CALENDAR_ID, calendarId)
      // 在使用了 RDATE 后，DTSTART 会失效，但又必须填上，所以写个 0 用于区分 CommonEvent 和 FrequencyEvent
      put(Events.DTSTART, 0)
      put(Events.DURATION, event.duration.toDuration())
      put(Events.RDATE, event.startTime.joinToString(separator = ",") {
        (it.clone() as Calendar).run {
          // 不知道什么原因，这个日历写进去的时间有一个时区的时间偏移量，所以这里需要单独减掉
          // 即使 Date-Time 后面添加了 Z 也是一样
          timeInMillis -= zone.rawOffset
          toDateTime()
        }
      })
      put(Events.HAS_ALARM, if (event.remind >= 0) 1 else 0) //设置有提醒
      put(Events.EVENT_TIMEZONE, zone.id) //设置时区
      block?.invoke(this)
    }
  }
  
  private fun getFrequencyEventContent(
    event: FrequencyEvent,
    calendarId: Long,
    block: (ContentValues.() -> Unit)? = null
  ): ContentValues {
    return ContentValues().apply {
      put(Events.TITLE, event.title)
      put(Events.DESCRIPTION, event.description)
      put(Events.CALENDAR_ID, calendarId)
      put(Events.DTSTART, event.startTime.timeInMillis)
      put(Events.DURATION, event.duration.toDuration())
      put(Events.RRULE, event.toRRule())
      put(Events.HAS_ALARM, if (event.remind >= 0) 1 else 0) //设置有提醒
      put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id) //设置时区
      block?.invoke(this)
    }
  }
  
  
  
  
  ///////////////////////////////
  //
  //            Event
  //
  ///////////////////////////////
  
  sealed interface Event {
    val title: String
    val description: String
    
    /**
     * 提前几分钟提醒(单位：分钟)。值为 负数 时表示不提醒；值为 0 时表示立即提醒
     */
    val remind: Int
    
    /**
     * 持续时间
     */
    val duration: Duration
    
    class Duration(
      @param:IntRange(from = 0) val day: Int = 0,
      @param:IntRange(from = 0) val hour: Int = 0,
      @param:IntRange(from = 0) val minute: Int = 0,
      @param:IntRange(from = 0) val second: Int = 0,
    ) {
      // 转变成日历需要的字符串，具体请看：3.3.6
      internal fun toDuration(): String {
        return "P${day}DT${hour}H${minute}M${second}S"
      }
    }
  }
  
  /**
   * 在某几天内重复的事件
   * @param startTime 开始的时间
   */
  data class CommonEvent(
    override val title: String,
    override val description: String,
    override val remind: Int,
    override val duration: Event.Duration,
    val startTime: List<Calendar>
  ) : Event
  
  /**
   * 带有一定频率的事件，更多参考的例子请查看：http://rfc2cn.com/rfc5545.html 中的 3.8.5.3
   * 每个参数的详细解释请查看 3.3.10
   *
   * - 每隔一天，总发生 10 次：FREQ=DAILY;INTERVAL=2;count=10
   * - 每月的第一个星期日和最后一个星期日：FREQ=MONTHLY;BYDAY=1SU,-1SU
   * - 3月的每个星期四：FREQ=YEARLY;BYMONTH=3;BYDAY=TH
   * - 当月最后一个工作日：FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1
   *
   * @param freq 频率单位
   * @param interval 重复事件之间的间隔差，单位由 [freq] 决定。比如每隔一天应该：FREQ=DAILY;INTERVAL=2
   * @param until 直到什么时候结束
   * @param count 总次数
   * @param byDay 星期数
   * @param byMonthDay 一月中的天数。例如，-10 表示一个月的倒数第 10 天
   * @param byYearDay 一年中的日期。例如，-1 表示一年中的最后一天（12月31日）
   * @param byWeekNo 一年中的周数。例如，3 代表一年中的第三周
   * @param byMonth 一年的月份。例如，2 代表一年中的第二月
   * @param bySetPos 与 ByXXX 结合使用，用于指定哪一次
   */
  data class FrequencyEvent(
    override val title: String,
    override val description: String,
    override val remind: Int,
    override val duration: Event.Duration,
    val startTime: Calendar,
    val freq: Freq,
    val interval: Int = 1,
    val until: Calendar? = null,
    @param:IntRange(from = 1)
    val count: Int? = null,
    val byDay: List<ByDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byYearDay: List<Int> = emptyList(),
    val byWeekNo: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
  ) : Event {
    enum class Freq { SECONDLY, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }
    sealed class ByDay(val name: String) {
      /**
       * 用于表示在每月或每年的 “RRULE” 中特定日期的第 n 次出现。支持负数
       *
       * 比如：
       * +1MO（或简单地说是 1MO）表示该月（或该年）的第一个星期一，而 -1MO 表示该月的最后一个星期一
       */
      abstract val num: Int
      
      class MO(override val num: Int = 0) : ByDay("MO")
      class TU(override val num: Int = 0) : ByDay("TU")
      class WE(override val num: Int = 0) : ByDay("WE")
      class TH(override val num: Int = 0) : ByDay("TH")
      class FR(override val num: Int = 0) : ByDay("FR")
      class SA(override val num: Int = 0) : ByDay("SA")
      class SU(override val num: Int = 0) : ByDay("SU")
      
      override fun toString(): String {
        return if (num == 0) name else "$num" + name
      }
    }
    
    // 转变成 RRULE，请看：3.3.10 的限制
    internal fun toRRule(): String {
      return "FREQ=${freq.name};INTERVAL=$interval;" +
        (until?.toDateTime() ?: "") +
        if (count != null) "COUNT=$count;" else {
          ""
        } +
        byDay.joinToString(",").let {
          "BYDAY=$it"
        } +
        if (freq != Freq.WEEKLY) {
          byMonthDay.filter { it in -31..31 && it != 0 }.joinToString(",").let {
            "BYMONTHDAY=$it"
          }
        } else {
          ""
        } +
        if (freq < Freq.DAILY || freq == Freq.YEARLY) {
          byYearDay.filter { it in -366..366 && it != 0 }.joinToString(",").let {
            "BYYEARDAY=$it"
          }
        } else {
          ""
        } +
        if (freq == Freq.YEARLY) {
          byWeekNo.filter { it in -53..53 && it != 0 }.joinToString(",").let {
            "BYWEEKNO=$it"
          }
        } else {
          ""
        } +
        byMonth.filter { it in 1..12 }.joinToString(",").let {
          "BYMONTH=$it"
        } +
        bySetPos.filter { it in -366..366 && it != 0 }.joinToString(",").let {
          "BYSETPOS=$it"
        }
    }
  }
}