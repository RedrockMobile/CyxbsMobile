package com.cyxbs.pages.course.reminder

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import kotlinx.datetime.TimeZone

/**
 * Android 课表提醒的受管日历存储。
 *
 * 课表使用 ACCOUNT_NAME=`cyxbs-course:<学号>`、LOCAL 类型和固定名称“掌邮课表”，与 Schedule
 * 的 ACCOUNT_NAME=学号、名称“掌邮日程”完全分离；用户可见的 OWNER_ACCOUNT 只显示学号。
 * 该日历只保存由课表派生的事件，因此课程快照变化时可安全全量替换。
 */
internal class AndroidCourseCalendarStore(
  private val context: Context,
) {

  /**
   * 全量替换当前账号的课程事件，并为每个事件写入提前 20 分钟的系统提醒。
   *
   * [ensureAuthorized] 会在每次 CalendarProvider 写入前复核账号、开关和权限；已经发出的 Provider 调用不可取消。
   */
  fun replaceEvents(
    accountId: String,
    events: List<CourseReminderEvent>,
    ensureAuthorized: () -> Unit,
  ) {
    require(accountId.isNotBlank()) { "accountId must not be blank" }
    ensureAuthorized()
    requireCalendarPermissions()
    val calendarId = getOrCreateCalendar(accountId, ensureAuthorized)

    ensureAuthorized()
    context.contentResolver.delete(
      CalendarContract.Events.CONTENT_URI,
      "${CalendarContract.Events.CALENDAR_ID} = ?",
      arrayOf(calendarId.toString()),
    )

    events.chunked(EVENTS_PER_BATCH).forEach { chunk ->
      val operations = ArrayList<ContentProviderOperation>(chunk.size * 2)
      chunk.forEach { event ->
        val eventOperationIndex = operations.size
        operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
          .withValues(eventValues(calendarId, accountId, event))
          .build()
        operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
          .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOperationIndex)
          .withValue(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES)
          .withValue(
            CalendarContract.Reminders.METHOD,
            CalendarContract.Reminders.METHOD_ALERT,
          )
          .build()
      }
      ensureAuthorized()
      context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
    }
  }

  /**
   * 删除当前账号独立的“掌邮课表” Calendar row 及其事件。
   *
   * 完整身份和 ownership marker 不匹配时拒绝删除，避免误伤用户或其他模块创建的日历。
   */
  fun deleteManagedCalendar(accountId: String) {
    if (!hasCalendarPermissions(context) || accountId.isBlank()) return
    val calendar = findCalendar(accountId) ?: return
    check(calendar.ownershipMarker == OWNERSHIP_MARKER) {
      "Course calendar ownership marker does not match"
    }
    val accountName = calendarAccountName(accountId)
    val operations = arrayListOf(
      ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
        .withSelection(
          "${CalendarContract.Events.CALENDAR_ID} = ?",
          arrayOf(calendar.id.toString()),
        )
        .build(),
      ContentProviderOperation.newDelete(calendarSyncAdapterUri(accountName))
        .withSelection(
          "${CalendarContract.Calendars._ID} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.NAME} = ? AND " +
            "${CalendarContract.Calendars.CAL_SYNC1} = ?",
          arrayOf(
            calendar.id.toString(),
            accountName,
            CalendarContract.ACCOUNT_TYPE_LOCAL,
            CALENDAR_NAME,
            OWNERSHIP_MARKER,
          ),
        )
        .withExpectedCount(1)
        .build(),
    )
    context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
  }

  /** 查找或创建当前账号唯一的课表日历。 */
  private fun getOrCreateCalendar(
    accountId: String,
    ensureAuthorized: () -> Unit,
  ): Long {
    val existing = findCalendar(accountId)
    ensureAuthorized()
    if (existing != null) {
      check(existing.ownershipMarker == OWNERSHIP_MARKER) {
        "Existing course calendar is not owned by this projection"
      }
      return existing.id
    }

    val accountName = calendarAccountName(accountId)
    val values = ContentValues().apply {
      put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
      put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
      put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
      put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
      put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
      put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
      put(CalendarContract.Calendars.OWNER_ACCOUNT, accountId)
      put(CalendarContract.Calendars.VISIBLE, 1)
      put(CalendarContract.Calendars.SYNC_EVENTS, 1)
      put(CalendarContract.Calendars.CAL_SYNC1, OWNERSHIP_MARKER)
    }
    ensureAuthorized()
    val uri = checkNotNull(
      context.contentResolver.insert(calendarSyncAdapterUri(accountName), values),
    ) { "CalendarProvider did not create the course calendar" }
    return ContentUris.parseId(uri).also { check(it > 0) }
  }

  /** 按完整账号身份读取日历；同一身份出现多行时终止写入，避免覆盖不确定目标。 */
  private fun findCalendar(accountId: String): ManagedCalendar? {
    val accountName = calendarAccountName(accountId)
    val cursor = context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CAL_SYNC1),
      "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
        "${CalendarContract.Calendars.NAME} = ?",
      arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, CALENDAR_NAME),
      null,
    ) ?: error("CalendarProvider returned a null cursor")
    return cursor.use {
      if (!it.moveToFirst()) return@use null
      val calendar = ManagedCalendar(it.getLong(0), it.getString(1))
      check(!it.moveToNext()) { "Multiple course calendars share the same identity" }
      calendar
    }
  }

  /** 构造一次课程事件；课程按周展开，不使用 RRULE，避免单双周和不连续周的厂商兼容差异。 */
  private fun eventValues(
    calendarId: Long,
    accountId: String,
    event: CourseReminderEvent,
  ): ContentValues = ContentValues().apply {
    put(CalendarContract.Events.CALENDAR_ID, calendarId)
    put(CalendarContract.Events.TITLE, event.title)
    put(CalendarContract.Events.DESCRIPTION, event.description)
    put(CalendarContract.Events.EVENT_LOCATION, event.location)
    put(CalendarContract.Events.DTSTART, event.start.toEpochMilliseconds())
    put(CalendarContract.Events.DTEND, event.end.toEpochMilliseconds())
    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
    put(CalendarContract.Events.ALL_DAY, 0)
    put(CalendarContract.Events.HAS_ALARM, 1)
    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
    put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
    put(
      CalendarContract.Events.CUSTOM_APP_URI,
      Uri.Builder()
        .scheme("cyxbs")
        .authority("course-calendar")
        .appendPath(accountId)
        .appendPath(event.stableId)
        .build()
        .toString(),
    )
  }

  /** sync-adapter URI 绑定课表独立账号，CalendarProvider 不会把写入归入 Schedule 日历。 */
  private fun calendarSyncAdapterUri(accountName: String): Uri =
    CalendarContract.Calendars.CONTENT_URI.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
      .appendQueryParameter(
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.ACCOUNT_TYPE_LOCAL,
      )
      .build()

  /** 每次写入前同步验证权限，避免仅依赖设置页授权时刻的旧状态。 */
  private fun requireCalendarPermissions() {
    check(hasCalendarPermissions(context)) {
      "Calendar read/write permissions are required"
    }
  }

  private data class ManagedCalendar(
    val id: Long,
    val ownershipMarker: String?,
  )

  companion object {
    private const val CALENDAR_NAME = "掌邮课表"
    private const val CALENDAR_DISPLAY_NAME = "掌邮课表"
    private const val COURSE_ACCOUNT_PREFIX = "cyxbs-course:"
    private const val OWNERSHIP_MARKER = "cyxbs-course-calendar-v1"
    private const val REMINDER_MINUTES = 20
    private const val EVENTS_PER_BATCH = 100
    private val CALENDAR_COLOR = 0xFF5A7FFF.toInt()

    /** 课表账号名刻意不同于 Schedule 使用的裸学号，确保二者不是同一个系统日历账号。 */
    private fun calendarAccountName(accountId: String): String =
      COURSE_ACCOUNT_PREFIX + accountId.lowercase()

    /** 系统日历提醒同时依赖读取与写入权限。 */
    fun hasCalendarPermissions(context: Context): Boolean =
      context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

  }
}
