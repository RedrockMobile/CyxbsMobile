package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifier
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import java.io.IOException
import java.util.UUID

/**
 * Android 受管日历注册器。
 *
 * 日历身份边界固定为 ACCOUNT_NAME=学号、ACCOUNT_TYPE=CalendarContract.ACCOUNT_TYPE_LOCAL、
 * NAME=CURRENT_CALENDAR_NAME（"邮子清单"）。这三项只定位候选 row；真正写资格还必须匹配正数 `_ID` 与创建时
 * 持久化在 `CAL_SYNC1` 的 UUID incarnation，避免 Provider 复用数字 id 后把 replacement 当成旧日历。
 */
class AndroidManagedCalendarRegistry(private val context: Context) {
  /**
   * 获取或创建当前学号的受管日历。
   *
   * [ensureAuthorized] 必须是调用方的实时生命周期门禁：每次可能阻塞的 Calendar 查询返回后与每个独立写入前
   * 都会复核。Provider 调用本身不可取消，故单次已发出的调用仍是 best-effort 边界；但撤销返回后绝不允许由
   * 查询恢复继续创建 Calendar row。
   */
  fun getOrCreateManagedCalendar(
    accountId: String,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit = {},
  ): Long? {
    ensureAuthorized()
    if (!hasCalendarPermissions()) return null
    require(accountId.isNotBlank()) { "accountId must not be blank" }
    // scope 仅校验为非空白，不参与日历身份；查找和创建均使用 ACCOUNT_NAME=账号、ACCOUNT_TYPE=CalendarContract.ACCOUNT_TYPE_LOCAL、NAME=CURRENT_CALENDAR_NAME（"邮子清单"）。
    require(scope.value.isNotBlank()) { "scope must not be blank" }
    return findCurrentManagedCalendar(accountId, ensureAuthorized)
      ?: createManagedCalendar(accountId, ensureAuthorized)
  }

  /**
   * 只查找当前格式日历，不存在时绝不创建；供 legacy 写入口和清理前查询使用。
   *
   * 已存在的同名 row 必须同时携带 canonical `CAL_SYNC1` incarnation。缺失或异常 token 是不可静默跨越的迁移
   * 边界：本方法按 Provider 读取失败终止，既不认领旧 row，也不原地 backfill。查询完成后仍调用
   * [ensureAuthorized]，避免调用方在被阻塞的 Provider 读取期间撤销后继续后续写入。
   */
  fun findCurrentManagedCalendar(
    accountId: String,
    ensureAuthorized: () -> Unit = {},
  ): Long? {
    val calendar = findManagedCalendar(currentIdentity(accountId), ensureAuthorized) ?: return null
    val incarnation = calendar.incarnation ?: throw IOException(
      "Managed calendar is missing its CAL_SYNC1 incarnation",
    )
    val decoded = runCatching {
      AndroidManagedCalendarIdentifier(calendar.id, incarnation)
    }.getOrElse { cause ->
      throw IOException("Managed calendar has an invalid CAL_SYNC1 incarnation", cause)
    }
    ensureAuthorized()
    return decoded.calendarRowId
  }

  /**
   * 非创建地确认当前受管 Calendar row 仍是预期完整身份。
   *
   * finalized Create/Update 会在各自 Provider 预写窗口反复调用此入口；Create 还会在 retry callback 返回后再查一次。
   * 只有账号、LOCAL 类型、固定名称、row id 与 `CAL_SYNC1` incarnation 全部相等才返回 row id；同一数字 id 上被
   * 删除重建的新 row 也会因 token 不同返回 `null`。本查询无法让后续 Provider 写与 Calendar row 原子化，因此
   * 调用方仍须在最靠近写入的位置再次读取；这里不增加厂商分支、assert query 或静默修复。
   */
  fun findCurrentManagedCalendarMatching(
    accountId: String,
    expectedCalendarIdentity: AndroidManagedCalendarIdentifier,
    ensureAuthorized: () -> Unit = {},
  ): Long? {
    val current = findManagedCalendar(currentIdentity(accountId), ensureAuthorized)
    ensureAuthorized()
    return current?.id?.takeIf { rowId ->
      rowId == expectedCalendarIdentity.calendarRowId &&
          current.incarnation == expectedCalendarIdentity.incarnation
    }
  }

  /**
   * 删除当前学号完整身份下的受管 Calendar rows 与其事件。
   *
   * 每个目标在破坏性操作前先复核 row + nullable `CAL_SYNC1`，随后把 Events 删除与带 incarnation 条件的
   * Calendars 删除放入同一 Provider batch；Calendar 条件未命中时由 expectedCount 触发整批回滚。tokenless row
   * 仅在用户确认的显式清理中通过 `CAL_SYNC1 IS NULL` 分支处理，绝不取得普通事件写资格。本边界不宣称跨查询
   * 窗口 CAS，Provider 调用发出后仍是不可取消的 best-effort 操作。
   */
  fun clearAndDeleteManagedCalendars(
    accountId: String,
    ensureAuthorized: () -> Unit = {},
  ): DeleteResult {
    ensureAuthorized()
    requireCalendarPermissions()
    val targets = findManagedCalendarsForAccount(accountId, ensureAuthorized)
    if (targets.isEmpty()) return DeleteResult.AlreadyAbsent

    var deletedEvents = 0
    val deletedCalendarIds = mutableListOf<Long>()
    targets.forEach { target ->
      // 进入破坏性操作前重新核验完整身份，避免把初始枚举后已经替换的 row 直接带入删除 batch。
      check(isManagedCalendarTargetCurrent(target, ensureAuthorized)) {
        "Managed calendar identity changed before deletion"
      }
      val calendarSelection = buildString {
        append("${CalendarContract.Calendars._ID} = ? AND ")
        append("${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ")
        append("${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ")
        append("${CalendarContract.Calendars.NAME} = ? AND ")
        if (target.incarnation == null) {
          // 旧 tokenless row 只允许在用户确认的显式清理中按 SQL NULL 精确删除，绝不因此取得事件写资格。
          append("${CalendarContract.Calendars.CAL_SYNC1} IS NULL")
        } else {
          append("${CalendarContract.Calendars.CAL_SYNC1} = ?")
        }
      }
      val calendarSelectionArgs = buildList {
        add(target.id.toString())
        add(target.identity.accountName)
        add(CalendarContract.ACCOUNT_TYPE_LOCAL)
        add(target.identity.calendarName)
        target.incarnation?.let { add(it) }
      }.toTypedArray()
      val operations = arrayListOf(
        ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
          .withSelection(
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(target.id.toString()),
          )
          .build(),
        ContentProviderOperation.newDelete(calendarSyncAdapterUri(target.identity.accountName))
          .withSelection(calendarSelection, calendarSelectionArgs)
          .withExpectedCount(1)
          .build(),
      )

      // 同一 authority 的 batch 让 token 条件失败通过 expectedCount 回滚前序 Event 删除；这只是 best-effort
      // 破坏性边界，不把 Provider 实现宣称为跨查询窗口 CAS。
      ensureAuthorized()
      val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
      ensureAuthorized()
      val eventCount = checkNotNull(results.getOrNull(0)?.count) {
        "Calendar Provider did not return the deleted event count for calendar ${target.id}"
      }
      check(results.getOrNull(1)?.count == 1) {
        "Calendar Provider did not delete managed calendar ${target.id}"
      }
      deletedEvents += eventCount
      deletedCalendarIds += target.id
    }
    return DeleteResult.Deleted(deletedCalendarIds, deletedEvents)
  }

  /**
   * 查找完整账号、LOCAL 类型与固定名称下的全部受管日历；仅供用户确认后的显式清理。
   *
   * `CAL_SYNC1` 原样保留到删除前复核，使同一 row id 在查询窗口内被替换时不会被旧目标误删；旧 tokenless row
   * 仍可作为清理目标，但绝不会因此取得事件写资格。
   */
  private fun findManagedCalendarsForAccount(
    accountId: String,
    ensureAuthorized: () -> Unit,
  ): List<ManagedCalendar> {
    require(accountId.isNotBlank()) { "accountId must not be blank" }
    val projection = arrayOf(
      CalendarContract.Calendars._ID,
      CalendarContract.Calendars.NAME,
      CalendarContract.Calendars.CAL_SYNC1,
    )
    val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
    // 每次 Provider 读取前后均同步复核，撤销发生在 query 阻塞期间时不可继续枚举或进入删除。
    ensureAuthorized()
    val cursor = context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      projection,
      selection,
      arrayOf(accountId, CalendarContract.ACCOUNT_TYPE_LOCAL, CURRENT_CALENDAR_NAME),
      null,
    )
    // null 也是 query 返回值的一部分，必须先复核撤销再解释；非空 cursor 则把复核放进 use，
    // 从而即使 gate 抛异常也确定关闭 Provider 资源。
    return if (cursor == null) {
      ensureAuthorized()
      throw IOException("Calendar provider returned a null cursor while reading account calendars")
    } else {
      cursor.use {
        ensureAuthorized()
        buildList {
          while (it.moveToNext()) {
            val name = it.getString(1) ?: continue
            if (name == CURRENT_CALENDAR_NAME) {
              add(
                ManagedCalendar(
                  id = it.getLong(0),
                  identity = CalendarIdentity(accountId, name),
                  incarnation = it.getString(2),
                ),
              )
            }
          }
        }
      }
    }
  }

  /**
   * 按初始枚举得到的完整 row 重新确认单个清理目标，避免同名重复 row 让 unique-by-name 查询阻断逐行清理。
   *
   * selection 同时锁定 `_ID`、账号、LOCAL 类型、受管名称与 nullable `CAL_SYNC1`；tokenless 目标必须走
   * `IS NULL`，不能把任意新 incarnation 视为同一目标。该读取只缩小 query→batch 漂移窗口，不构成 CAS。
   */
  private fun isManagedCalendarTargetCurrent(
    target: ManagedCalendar,
    ensureAuthorized: () -> Unit,
  ): Boolean {
    val selection = buildString {
      append("${CalendarContract.Calendars._ID} = ? AND ")
      append("${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ")
      append("${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ")
      append("${CalendarContract.Calendars.NAME} = ? AND ")
      if (target.incarnation == null) {
        append("${CalendarContract.Calendars.CAL_SYNC1} IS NULL")
      } else {
        append("${CalendarContract.Calendars.CAL_SYNC1} = ?")
      }
    }
    val selectionArgs = buildList {
      add(target.id.toString())
      add(target.identity.accountName)
      add(CalendarContract.ACCOUNT_TYPE_LOCAL)
      add(target.identity.calendarName)
      target.incarnation?.let(::add)
    }.toTypedArray()

    ensureAuthorized()
    val cursor = context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      arrayOf(CalendarContract.Calendars._ID),
      selection,
      selectionArgs,
      null,
    )
    return if (cursor == null) {
      ensureAuthorized()
      throw IOException("Calendar provider returned a null cursor while revalidating managed calendar")
    } else {
      cursor.use {
        ensureAuthorized()
        if (!it.moveToFirst()) {
          false
        } else {
          check(it.getLong(0) == target.id) { "Managed calendar exact-ID query returned another row" }
          check(!it.moveToNext()) { "Managed calendar exact identity returned multiple rows" }
          true
        }
      }
    }
  }


  /**
   * 按账号、LOCAL 类型与固定名称读取唯一受管 Calendar row，并保留原始 incarnation marker。
   *
   * 这里不修复或补写 `CAL_SYNC1`：调用方可以把缺失/异常 token 作为显式清理目标，但任何写资格都必须由
   * [findCurrentManagedCalendar] 或 [findCurrentManagedCalendarMatching] 在读取后再次执行完整身份校验。
   */
  private fun findManagedCalendar(
    identity: CalendarIdentity,
    ensureAuthorized: () -> Unit,
  ): ManagedCalendar? {
    val projection = arrayOf(
      CalendarContract.Calendars._ID,
      CalendarContract.Calendars.CAL_SYNC1,
    )
    val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
    // 公共只读入口也可能直接调用到这里，不能假定外层已经做过 query 前授权。
    ensureAuthorized()
    val cursor = context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      projection,
      selection,
      arrayOf(identity.accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, identity.calendarName),
      null,
    )
    // 不能把 null cursor 当成无需授权的提前返回；有 cursor 时在 use 内复核，保证撤销异常也会关闭资源。
    return if (cursor == null) {
      ensureAuthorized()
      throw IOException("Calendar provider returned a null cursor while reading managed calendar")
    } else {
      cursor.use {
        ensureAuthorized()
        if (!it.moveToFirst()) null else {
          val calendar = ManagedCalendar(
            id = it.getLong(0),
            identity = identity,
            incarnation = it.getString(1),
          )
          check(!it.moveToNext()) { "Multiple managed calendars share the same identity" }
          calendar
        }
      }
    }
  }

  /**
   * 按固定账号、LOCAL 类型与名称创建新的本地日历；scope 不参与日历身份。
   *
   * 每次调用都生成全新的 lowercase UUID 并通过既有 sync-adapter URI 持久化到 `CAL_SYNC1`。该 token 区分数字
   * row id 被 Provider 复用前后的两次创建，不能从旧 row 推导、复用或事后 backfill。创建前最后一次
   * [ensureAuthorized] 专门覆盖 find→create 的不可取消查询窗口。
   */
  private fun createManagedCalendar(accountId: String, ensureAuthorized: () -> Unit): Long? {
    val identity = currentIdentity(accountId)
    val incarnation = UUID.randomUUID().toString()
    val values = ContentValues().apply {
      put(CalendarContract.Calendars.ACCOUNT_NAME, identity.accountName)
      put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
      put(CalendarContract.Calendars.NAME, identity.calendarName)
      put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, DISPLAY_NAME)
      put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
      put(
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        CalendarContract.Calendars.CAL_ACCESS_OWNER
      )
      put(CalendarContract.Calendars.OWNER_ACCOUNT, identity.accountName)
      put(CalendarContract.Calendars.VISIBLE, 1)
      put(CalendarContract.Calendars.SYNC_EVENTS, 1)
      put(CalendarContract.Calendars.CAL_SYNC1, incarnation)
    }
    ensureAuthorized()
    val insertedUri =
      context.contentResolver.insert(calendarSyncAdapterUri(identity.accountName), values)
    // insert 已不可撤销；返回前复核以阻止调用方将撤销后的结果用于后续事件事务。
    ensureAuthorized()
    return insertedUri?.let { uri ->
      ContentUris.parseId(uri).takeIf { it > 0 }
    }
  }

  /** sync-adapter URI 必须绑定 ACCOUNT_NAME=目标账号、ACCOUNT_TYPE=CalendarContract.ACCOUNT_TYPE_LOCAL，避免 Provider 将写入解释为其他本地账号；日历 NAME 固定为 CURRENT_CALENDAR_NAME（"邮子清单"）。 */
  private fun calendarSyncAdapterUri(accountName: String) =
    CalendarContract.Calendars.CONTENT_URI.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
      .appendQueryParameter(
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.ACCOUNT_TYPE_LOCAL
      )
      .build()

  private fun currentIdentity(accountId: String): CalendarIdentity {
    require(accountId.isNotBlank()) { "accountId must not be blank" }
    return CalendarIdentity(accountId, CURRENT_CALENDAR_NAME)
  }

  /** 检查是否拥有读写日历的必要权限。 */
  private fun hasCalendarPermissions(): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

  private fun requireCalendarPermissions() {
    if (!hasCalendarPermissions()) throw SecurityException("Calendar read/write permissions are required")
  }

  private data class CalendarIdentity(val accountName: String, val calendarName: String)
  private data class ManagedCalendar(
    val id: Long,
    val identity: CalendarIdentity,
    val incarnation: String?,
  )

  /** 显式清理结果；目标不存在与重复执行均属于成功语义。 */
  sealed interface DeleteResult {
    data object AlreadyAbsent : DeleteResult
    data class Deleted(val calendarIds: List<Long>, val eventCount: Int) : DeleteResult
  }

  companion object {
    /**
     * 受管 Calendar 的稳定 Provider 名称。
     *
     * 快照采集器只读取该完整 identity，注册器的创建/清理也使用同一常量；不得改为用户可编辑显示名或 scope。
     */
    internal const val CURRENT_CALENDAR_NAME = "邮子清单"
    private const val DISPLAY_NAME = "邮子清单"
    private const val CALENDAR_COLOR = -0xbbcca
  }
}
