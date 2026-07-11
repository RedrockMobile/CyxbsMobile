package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.util.Log
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.DateSerializer
import com.cyxbs.components.init.appApplication
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.encodeToUrl
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import java.time.LocalDate
import java.time.ZoneId

/**
 * Schedule 专用的 Android [CalendarContract] 适配器。
 *
 * 本层只负责权限后的 Provider CRUD 和 Schedule 独立日历账户管理，不承载 RRULE/完成状态等业务决策；
 * 业务语义已经由 [ScheduleCalendarProjection] 归一化。所有 update/delete 在执行前都会校验 eventId
 * 是否仍属于当前账号的 Schedule 日历，防止账号切换或陈旧映射误操作其他日历事件。
 */
internal object AndroidScheduleCalendarProvider {

  // 未实现 SyncAdapter 的本地日历必须使用系统约定的 LOCAL 类型，部分厂商会静默拒绝自定义类型。
  private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
  private const val CALENDAR_NAME = "邮子清单"
  private val context: Context get() = appApplication

  /** 同时具备日历读写权限才允许执行查询与写入；权限申请由 Compose 设置入口负责。 */
  fun hasPermission(): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
      context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

  /**
   * 直接从系统日历恢复 `projectionKey → eventId`，无需把 eventId 映射单独持久化。
   *
   * 只扫描当前账号的 Schedule 专用 calendarId，并同时校验 package 与统一 `cyxbs://schedule` 参数类型；
   * 其他应用或无法解析的历史事件不会进入对账集合。
   */
  fun getManagedEvents(): Map<String, Long> {
    if (!hasPermission()) return emptyMap()
    val calendarId = findCalendar() ?: return emptyMap()
    return runCatching {
      buildMap {
        context.contentResolver.query(
          Events.CONTENT_URI,
          arrayOf(Events._ID, Events.CUSTOM_APP_URI),
          "${Events.CALENDAR_ID}=? AND ${Events.CUSTOM_APP_PACKAGE}=? AND ${Events.DELETED}=0",
          arrayOf(calendarId.toString(), context.packageName),
          null,
        )?.use { cursor ->
          val idIndex = cursor.getColumnIndexOrThrow(Events._ID)
          val uriIndex = cursor.getColumnIndexOrThrow(Events.CUSTOM_APP_URI)
          while (cursor.moveToNext()) {
            val argument = cursor.getString(uriIndex)?.let(AppNavArgument::decodeFromUrl)
              as? ScheduleMainNavArgument ?: continue
            val todoId = argument.todoId ?: continue
            val key = argument.recurrenceId?.let { "$todoId:${it}" } ?: run {
              // 无 recurrenceId 时通过 RRULE 区分系列与普通单次事件。
              if (eventHasRRule(cursor.getLong(idIndex))) "$todoId:series" else "$todoId:single"
            }
            put(key, cursor.getLong(idIndex))
          }
        }
      }
    }.getOrDefault(emptyMap())
  }

  /** 查询事件是否带 RRULE，仅用于恢复无 recurrenceId deep link 的 projectionKey 类型。 */
  private fun eventHasRRule(eventId: Long): Boolean = runCatching {
    context.contentResolver.query(
      ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
      arrayOf(Events.RRULE), null, null, null,
    )?.use { it.moveToFirst() && !it.getString(0).isNullOrBlank() } == true
  }.getOrDefault(false)

  /**
   * 新增事件并替换提醒。
   *
   * 事件创建成功但提醒写入失败时会回滚删除事件，避免调用方保存 eventId 后留下“有事件但不提醒”的
   * 部分成功状态。
   *
   * @return 新 eventId；权限、账户创建、事件或提醒任一步失败时返回 null。
   */
  fun add(projection: ScheduleCalendarProjection): Long? {
    if (!hasPermission()) return null
    val calendarId = findOrCreateCalendar() ?: run {
      Log.e("ScheduleCalendar", "Cannot add event: Schedule calendar is unavailable")
      return null
    }
    val uri = try {
      context.contentResolver.insert(Events.CONTENT_URI, projection.toContentValues(calendarId))
    } catch (throwable: Throwable) {
      Log.e("ScheduleCalendar", "Failed to insert Schedule event", throwable)
      null
    } ?: return null
    val eventId = uri.lastPathSegment?.toLongOrNull() ?: return null
    if (!replaceReminder(eventId, projection.remindMinutes)) {
      Log.e("ScheduleCalendar", "Failed to replace reminder for eventId=$eventId")
      delete(eventId)
      return null
    }
    return eventId
  }

  /**
   * 更新已映射事件及其提醒。陈旧 eventId、账号切换或用户手动删除事件均返回 false，由 reconciler
   * 决定是否重新创建。
   */
  fun update(eventId: Long, projection: ScheduleCalendarProjection): Boolean {
    if (!hasPermission()) return false
    val calendarId = findCalendar() ?: return false
    if (!belongsToCalendar(eventId, calendarId)) return false
    val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
    val updated = runCatching {
      context.contentResolver.update(uri, projection.toContentValues(calendarId), null, null) > 0
    }.getOrDefault(false)
    return updated && replaceReminder(eventId, projection.remindMinutes)
  }

  /** 仅删除当前账号 Schedule 日历中的目标事件；不接受任意 Provider eventId。 */
  fun delete(eventId: Long): Boolean {
    if (!hasPermission()) return false
    val calendarId = findCalendar() ?: return false
    if (!belongsToCalendar(eventId, calendarId)) return false
    val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
    return runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
  }

  /**
   * 将平台无关投影编码为 CalendarContract 字段。
   *
   * 本地日期/分钟通过系统 [ZoneId] 转成真实 instant，避免手工减 rawOffset 的 DST 问题。截止型在
   * 截止时刻写成 1 分钟事件；全天事件从本地 00:00 开始持续一天；纯 RRULE 原样交给 Provider。
   */
  private fun ScheduleCalendarProjection.toContentValues(calendarId: Long): ContentValues {
    val zone = ZoneId.systemDefault()
    val startMinute = start?.minuteOfDay ?: end?.minuteOfDay ?: 0
    val startMillis = LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
      .atStartOfDay(zone).plusMinutes(startMinute.toLong()).toInstant().toEpochMilli()
    val durationMinutes = when {
      allDay -> 24 * 60
      start != null && end != null -> start.minutesUntil(end).coerceAtLeast(1)
      else -> 1 // 截止型在截止时刻创建 1 分钟事件
    }
    return ContentValues().apply {
      put(Events.CALENDAR_ID, calendarId)
      put(Events.TITLE, title)
      put(Events.DESCRIPTION, description)
      put(Events.DTSTART, startMillis)
      put(Events.DURATION, "PT${durationMinutes}M")
      put(Events.EVENT_TIMEZONE, zone.id)
      put(Events.ALL_DAY, if (allDay) 1 else 0)
      put(Events.HAS_ALARM, if (remindMinutes >= 0) 1 else 0)
      put(Events.CUSTOM_APP_PACKAGE, context.packageName)
      put(Events.CUSTOM_APP_URI, deepLinkUrl())
      if (rrule.isNullOrBlank()) putNull(Events.RRULE) else put(Events.RRULE, rrule)
      putNull(Events.RDATE)
    }
  }

  /**
   * 使用统一导航参数生成 `cyxbs://schedule?...`，避免 Calendar 映射标识与应用 deep link 各自维护协议。
   * 展开的 occurrence key 末段是原始 recurrenceId；单次/纯系列事件不携带 recurrenceId。
   */
  private fun ScheduleCalendarProjection.deepLinkUrl(): String {
    val recurrenceId = key.substringAfter(':', "")
      .takeUnless { it == "single" || it == "series" }
      ?.let { runCatching { DateSerializer.deserialize(it) }.getOrNull() }
    return ScheduleMainNavArgument(todoId = todoId, recurrenceId = recurrenceId)
      .encodeToUrl().toString()
  }

  /**
   * 删除旧 reminder 后按当前偏移重建。`minutes == 0` 是合法的准时提醒，只有负数才表示不提醒。
   */
  private fun replaceReminder(eventId: Long, minutes: Int): Boolean = runCatching {
    context.contentResolver.delete(
      Reminders.CONTENT_URI,
      "${Reminders.EVENT_ID}=?",
      arrayOf(eventId.toString()),
    )
    if (minutes >= 0) {
      val values = ContentValues().apply {
        put(Reminders.EVENT_ID, eventId)
        put(Reminders.MINUTES, minutes)
        put(Reminders.METHOD, Reminders.METHOD_ALERT)
      }
      context.contentResolver.insert(Reminders.CONTENT_URI, values) != null
    } else true
  }.getOrDefault(false)

  /** 校验 eventId 的 CALENDAR_ID，作为所有破坏性操作前的账号隔离防线。 */
  private fun belongsToCalendar(eventId: Long, calendarId: Long): Boolean = runCatching {
    context.contentResolver.query(
      ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
      arrayOf(Events.CALENDAR_ID), null, null, null,
    )?.use { it.moveToFirst() && it.getLong(0) == calendarId } == true
  }.getOrDefault(false)

  /** 按当前学号与 Schedule 专用 ACCOUNT_TYPE 查询日历，实现账号级隔离。 */
  private fun findCalendar(): Long? {
    val account = IAccountService::class.impl().stuNum.orEmpty()
    return runCatching {
      context.contentResolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
        arrayOf(account, ACCOUNT_TYPE), null,
      )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()
  }

  /**
   * 懒创建 Schedule 独立日历账户。使用 sync-adapter URI 是 Calendar Provider 创建本地账户日历所需约定，
   * 并不代表 Schedule 自己实现了 Android SyncAdapter。
   */
  private fun findOrCreateCalendar(): Long? = findCalendar() ?: try {
    val account = IAccountService::class.impl().stuNum.orEmpty()
    val values = ContentValues().apply {
      put(Calendars.ACCOUNT_NAME, account)
      put(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
      put(Calendars.NAME, CALENDAR_NAME)
      put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
      put(Calendars.OWNER_ACCOUNT, account)
      put(Calendars.CALENDAR_COLOR, Color.BLUE)
      put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
      put(Calendars.VISIBLE, 1)
      put(Calendars.SYNC_EVENTS, 1)
      put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
    }
    val uri = Calendars.CONTENT_URI.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
      .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
      .build()
    context.contentResolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
  } catch (throwable: Throwable) {
    // Provider 厂商差异只在此边界降级；保留日志便于真机定位账户创建失败原因。
    Log.e("ScheduleCalendar", "Failed to create Schedule calendar", throwable)
    null
  }
}
