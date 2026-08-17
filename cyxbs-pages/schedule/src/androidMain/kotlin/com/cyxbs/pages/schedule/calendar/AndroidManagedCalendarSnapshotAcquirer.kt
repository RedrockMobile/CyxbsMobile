package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifier
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifierCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionOperation
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarProviderTimingCanonicalizer
import com.cyxbs.pages.schedule.domain.calendar.CalendarRecurrenceCanonicalizer
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.calendar.CanonicalCalendarFields
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.eygraber.uri.Uri
import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.io.IOException

/**
 * Android 受管日历 Provider 快照的唯一采集边界。
 *
 * 该类只读取 Calendar、Events 与 Reminders，并把 Provider 原始行复制为普通 Kotlin 快照；不缓存、不注册运行时
 * 回调，也不携带写入确认、运行令牌或重试职责。gateway 仍保留写 API，所有生产快照读取都必须委派到这里，避免
 * cursor 解析、canonicalization 与 fingerprint 形成第二套行为。
 */
internal class AndroidManagedCalendarSnapshotAcquirer internal constructor(
  private val platform: AndroidManagedCalendarSnapshotReadPlatform,
  private val accountId: String,
) {
  /** 生产构造器将 Context 与受管 Calendar lookup 保留在私有 Android 边界，不向调用方暴露。 */
  internal constructor(
    context: Context,
    accountId: String,
  ) : this(
    platform = AndroidContentResolverManagedCalendarSnapshotReadPlatform(context),
    accountId = accountId,
  )

  /**
   * 采集当前账号的严格 canonical 快照。
   *
   * [ensureAuthorized] 是调用方传入的窄生命周期/currentness 门禁。它只在每一次实际 Provider 读取前后执行，
   * 且刻意位于 Provider 错误包装之外：取消、currentness 撤销和 [SecurityException] 保持原有语义；只有真实
   * Provider 读取失败才归类为 [AndroidScheduleCalendarGateway.CalendarProviderReadException]。所有实际 Events 与
   * Reminders 读取完成后还会重读 Calendar identity，避免同一次返回混合旧 incarnation 的事件与新 Calendar row。
   *
   * @param scheduleIds 可选的 Schedule ID 子集；空集合仍读取 Calendar row，以区分 row 缺失与空事件集。
   */
  fun acquire(
    scope: CalendarExportScope,
    scheduleIds: Set<ScheduleId>? = null,
    ensureAuthorized: () -> Unit = {},
  ): AndroidManagedCalendarSnapshot {
    ensureAuthorized()
    platform.requireCalendarPermissions()
    val initialCalendarIdentity = readCurrentCalendar(ensureAuthorized)
      ?: return AndroidManagedCalendarSnapshot.CalendarAbsent
    val calendarIdentifier = AndroidManagedCalendarIdentifierCodec.encode(
      initialCalendarIdentity.calendarRowId,
      initialCalendarIdentity.incarnation,
    )

    if (scheduleIds != null && scheduleIds.isEmpty()) {
      return AndroidManagedCalendarSnapshot.Present(calendarIdentifier, emptyList())
    }
    // buildList 已脱离任何 Cursor/Provider 行；toList 再固定为普通调用结果，绝不保留平台缓存或回调。
    val events = readSnapshotEvents(
      initialCalendarIdentity.calendarRowId,
      scope,
      scheduleIds,
      ensureAuthorized,
    ).toList()
    requireCalendarIdentityUnchanged(initialCalendarIdentity, ensureAuthorized)
    return AndroidManagedCalendarSnapshot.Present(calendarIdentifier, events)
  }

  /**
   * Calendar row query 本身是阻塞边界；Cursor 返回后、读取 row 前及复制/关闭完成后都必须复核 currentness。
   *
   * 完整 identity 只接受当前账号、LOCAL account type、受管名称、正数 `_ID` 与 canonical `CAL_SYNC1` UUID。空 row
   * 是 [AndroidManagedCalendarSnapshot.CalendarAbsent]；null cursor、重复 identity、tokenless/v1 遗留 row 与畸形 token
   * 都保持 typed Provider read failure。该迁移边界刻意不自动 backfill，否则一次读取就会把未知旧 row 升级为可写所有者。
   */
  private fun readCurrentCalendar(
    ensureAuthorized: () -> Unit,
  ): AndroidManagedCalendarIdentifier? {
    ensureAuthorized()
    val cursor = readProvider("Cannot read managed calendar identity") {
      platform.queryCurrentManagedCalendar(accountId)
    }
    val rows = if (cursor == null) {
      ensureAuthorized()
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Cannot read managed calendar identity",
        IOException("Calendar Provider returned a null cursor while reading managed calendar"),
      )
    } else {
      copyRowsAndClose(
        cursor = cursor,
        message = "Cannot read managed calendar identity",
        ensureAuthorized = ensureAuthorized,
      )
    }
    if (rows.isEmpty()) return null
    if (rows.size != 1) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Cannot read managed calendar identity",
        IllegalStateException("Multiple managed calendars share the same identity"),
      )
    }
    val row = rows.single()
    val calendarId = row.calendarId
    if (calendarId <= 0) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Invalid managed Provider calendar ID: $calendarId",
      )
    }
    val incarnation =
      row.incarnation ?: throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Managed Provider calendar is missing CAL_SYNC1 incarnation",
      )
    return runCatching {
      AndroidManagedCalendarIdentifier(calendarId, incarnation)
    }.getOrElse { cause ->
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Invalid managed Provider calendar CAL_SYNC1 incarnation",
        cause,
      )
    }
  }

  /**
   * 在所有 Events/Reminders 都复制并规范化后重读受管 Calendar identity，拒绝返回跨 row/incarnation 的混合快照。
   *
   * Provider 查询不是事务快照，因此这里是返回前的漂移门禁而不是 CAS：row 消失、重复、tokenless/畸形 token 由
   * [readCurrentCalendar] 统一归类为 Provider 读取失败；合法但 `_ID` 或 `CAL_SYNC1` 已变化时也必须失败关闭。
   */
  private fun requireCalendarIdentityUnchanged(
    expected: AndroidManagedCalendarIdentifier,
    ensureAuthorized: () -> Unit,
  ) {
    val current = readCurrentCalendar(ensureAuthorized)
      ?: throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Managed Provider calendar disappeared during snapshot acquisition",
      )
    if (current != expected) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Managed Provider calendar identity changed during snapshot acquisition",
      )
    }
  }


  /**
   * Calendar-wide 读取事件，并把请求范围内的原生 occurrence rows 严格聚合回唯一 series master。
   *
   * 例外行不会作为第二个顶层 managed event 暴露给既有 planner；缺失/重复 master、错误 ORIGINAL_ID、原始实例
   * 时间漂移或重复 occurrence identity 都会在返回任何快照前失败关闭。
   */
  private fun readSnapshotEvents(
    calendarId: Long,
    scope: CalendarExportScope,
    scheduleIds: Set<ScheduleId>?,
    ensureAuthorized: () -> Unit,
  ): List<AndroidManagedCalendarSnapshotEvent> {
    // 增量读取也必须取得同一 Calendar 的完整 URI 候选集；若先用 canonical 前缀 LIKE，参数重排或非必要转义的
    // app-owned v2 行会在进入 decoder 前消失，随后被误判为缺失并重复 Create。请求 ID 仅在内存结构化分类后过滤。
    val requestedScheduleIds = scheduleIds?.toList()
    val canonicalRows = buildList {
      val rows = readEventRows(calendarId, scope, requestedScheduleIds, ensureAuthorized)
      rows.forEach { row ->
        canonicalizeEventOrNull(row, scope, requestedScheduleIds, ensureAuthorized)?.let { event ->
          add(CanonicalizedSnapshotRow(row, event))
        }
      }
    }
    val occurrenceRows = canonicalRows.filter { it.event.projectionId.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION }
    val ordinaryRows = canonicalRows.filterNot { it.event.projectionId.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION }
    val occurrencesBySchedule = occurrenceRows.groupBy { it.event.projectionId.scheduleId }

    occurrencesBySchedule.forEach { (scheduleId, rows) ->
      val masters = ordinaryRows.filter {
        it.event.projectionId.scheduleId == scheduleId &&
            it.event.projectionId.kind == CalendarProjectionKind.SERIES_MASTER
      }
      if (masters.size != 1) {
        throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
          "Occurrence exceptions require exactly one managed series master: $scheduleId",
        )
      }
      if (rows.map { it.event.projectionId }.distinct().size != rows.size) {
        throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
          "Duplicate managed occurrence exception identity: $scheduleId",
        )
      }
    }

    return ordinaryRows.map { masterRow ->
      val nativeExceptions = occurrencesBySchedule[masterRow.event.projectionId.scheduleId].orEmpty()
        .map { occurrenceRow -> canonicalizeOccurrenceRelationship(masterRow, occurrenceRow) }
        .sortedBy { it.projection.externalUri }
      if (nativeExceptions.isEmpty()) masterRow.event else {
        val master = masterRow.event
        master.copy(
          providerFingerprint = CalendarProjectionFingerprint.compute(
            externalUri = CalendarProjectionUriCodec.encode(master.projectionId),
            title = master.canonicalFields.title,
            description = master.canonicalFields.description,
            timing = master.canonicalFields.timing,
            recurrenceRule = master.canonicalFields.recurrenceRule,
            reminderMinutes = master.canonicalFields.deviceReminderMinutes,
            nativeOccurrenceExceptions = nativeExceptions.map { it.projection },
          ),
          occurrenceExceptions = nativeExceptions,
        )
      }
    }
  }

  /**
   * Events query 返回 Cursor 后、任何 moveToNext 前立即复核 currentness。
   *
   * [copyRowsAndClose] 同时阻断 query→cursor 读取与 copyRows→canonicalization 窗口；gate 保持在
   * [readProvider] 外部，失败时仍会关闭真实 Cursor。
   */
  private fun readEventRows(
    calendarId: Long,
    scope: CalendarExportScope,
    scheduleIds: List<ScheduleId>?,
    ensureAuthorized: () -> Unit,
  ): List<AndroidManagedCalendarSnapshotEventRow> {
    ensureAuthorized()
    val cursor = readProvider("Cannot read managed calendar events") {
      platform.queryEvents(calendarId, scope, scheduleIds)
    }
    return if (cursor == null) {
      ensureAuthorized()
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Calendar Provider returned a null event cursor",
      )
    } else {
      copyRowsAndClose(
        cursor = cursor,
        message = "Cannot copy managed calendar event cursor",
        ensureAuthorized = ensureAuthorized,
      )
    }
  }

  /**
   * 将一个已复制的 Events 行规范化为受管快照。
   *
   * selection 不按 package 预过滤，因此具有 canonical v2 URI 但 owner 被外部改写的行会 fail closed；不属于当前
   * scope 的行仍遵守旧单向导出边界，不会被认领为本应用事件。
   */
  private fun canonicalizeEventOrNull(
    row: AndroidManagedCalendarSnapshotEventRow,
    scope: CalendarExportScope,
    requestedScheduleIds: List<ScheduleId>?,
    ensureAuthorized: () -> Unit,
  ): AndroidManagedCalendarSnapshotEvent? {
    val customUri = row.customAppUri ?: return null
    val projectionId = CalendarProjectionUriCodec.decodeOrNull(customUri) ?: run {
      if (row.customAppPackage == platform.packageName &&
        isRequestedManagedV2Candidate(customUri, scope, requestedScheduleIds)
      ) {
        throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
          "Managed event uses noncanonical CUSTOM_APP_URI: ${row.eventId}",
        )
      }
      return null
    }
    if (projectionId.scope != scope || requestedScheduleIds != null && projectionId.scheduleId !in requestedScheduleIds) {
      return null
    }
    val eventId = row.eventId
    val isOccurrence = projectionId.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION
    val hasCompleteOriginalIdentity = row.originalId != null && row.originalInstanceTime != null &&
        row.originalAllDay != null
    if (isOccurrence != hasCompleteOriginalIdentity ||
      !isOccurrence && (row.originalId != null || row.originalInstanceTime != null || row.originalAllDay != null)
    ) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Malformed managed occurrence relationship: $eventId",
      )
    }
    if (isOccurrence && row.recurrenceRule != null) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Managed occurrence exception must not carry RRULE: $eventId",
      )
    }
    // RDATE 会引入本投影不支持的 occurrence；即使它是空串也必须拒绝，不能伪装成无外部变化。
    if (row.rDate != null) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException("Unsupported managed event RDATE: $eventId")
    }
    if (row.customAppPackage != platform.packageName) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Managed event owner does not match this application: $eventId",
      )
    }
    val providerRule = row.recurrenceRule
    val recurring = providerRule != null
    val allDay = when (row.allDay) {
      0 -> false
      1 -> true
      else -> null
    } ?: throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
      "Invalid managed event ALL_DAY value: $eventId",
    )
    val timing = CalendarProviderTimingCanonicalizer.reconstructOrNull(
      dtStart = row.dtStart,
      dtEnd = row.dtEnd,
      duration = row.duration,
      timeZoneId = row.eventTimeZone,
      allDay = allDay,
      recurring = recurring,
      projectionKind = projectionId.kind,
    ) ?: throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
      "Cannot reconstruct managed event timing: $eventId",
    )
    val reminders = readReminderMinutes(eventId, ensureAuthorized)
    val recurrenceRule = if (!recurring) null else {
      // Provider 可以重排字段；仅接受明确支持的 RFC 5545 子集，未知语义不可继续生成 fingerprint。
      CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
        requireNotNull(providerRule),
        timing is CalendarTiming.AllDay,
      ) ?: throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Unsupported managed event RRULE: $eventId",
      )
    }
    val canonicalFields = try {
      CanonicalCalendarFields(
        title = row.title.orEmpty(),
        description = row.description.orEmpty(),
        timing = timing,
        recurrenceRule = recurrenceRule,
        deviceReminderMinutes = reminders,
      )
    } catch (e: IllegalArgumentException) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Cannot canonicalize managed event fields: $eventId",
        e,
      )
    }
    val providerFingerprint = CalendarProjectionFingerprint.compute(
      externalUri = customUri,
      title = canonicalFields.title,
      description = canonicalFields.description,
      timing = canonicalFields.timing,
      recurrenceRule = canonicalFields.recurrenceRule,
      reminderMinutes = canonicalFields.deviceReminderMinutes,
    )
    val platformEventRef = runCatching { AndroidCalendarEventRefCodec.encode(eventId) }
      .getOrElse {
        throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
          "Invalid managed Provider event ID: $eventId",
          it,
        )
      }
    return AndroidManagedCalendarSnapshotEvent(
      projectionId = projectionId,
      platformEventRef = platformEventRef,
      canonicalFields = canonicalFields,
      providerFingerprint = providerFingerprint,
    )
  }

  /**
   * 验证 occurrence row 与唯一 master 的 Provider 原生关系，并恢复可参与 aggregate fingerprint 的目标。
   */
  private fun canonicalizeOccurrenceRelationship(
    masterRow: CanonicalizedSnapshotRow,
    occurrenceRow: CanonicalizedSnapshotRow,
  ): AndroidManagedCalendarSnapshotOccurrenceException {
    val masterEventId = AndroidCalendarEventRefCodec.decodeOrNull(masterRow.event.platformEventRef)
      ?: throw AndroidScheduleCalendarGateway.CalendarProviderReadException("Invalid managed series master reference")
    val row = occurrenceRow.row
    val occurrenceId = requireNotNull(occurrenceRow.event.projectionId.recurrenceId)
    val expectedOriginalTime = AndroidOccurrenceExceptionWritePlanner.originalInstanceTimeMillis(occurrenceId)
    val expectedAllDay = if (occurrenceId.allDay) 1 else 0
    if (row.originalId != masterEventId || row.originalInstanceTime != expectedOriginalTime ||
      row.originalAllDay != expectedAllDay
    ) {
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Managed occurrence relationship drifted from canonical identity: ${row.eventId}",
      )
    }
    val operation = when (row.status) {
      CalendarContract.Events.STATUS_CONFIRMED -> CalendarOccurrenceExceptionOperation.UPSERT
      CalendarContract.Events.STATUS_CANCELED -> CalendarOccurrenceExceptionOperation.CANCEL
      else -> throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Unsupported managed occurrence status: ${row.eventId}",
      )
    }
    val event = occurrenceRow.event
    val projection = CalendarOccurrenceExceptionProjection(
      id = event.projectionId,
      externalUri = CalendarProjectionUriCodec.encode(event.projectionId),
      title = event.canonicalFields.title,
      description = event.canonicalFields.description,
      timing = event.canonicalFields.timing,
      deviceReminderMinutes = event.canonicalFields.deviceReminderMinutes,
      operation = operation,
      fingerprint = CalendarProjectionFingerprint.computeOccurrenceException(
        externalUri = CalendarProjectionUriCodec.encode(event.projectionId),
        title = event.canonicalFields.title,
        description = event.canonicalFields.description,
        timing = event.canonicalFields.timing,
        reminderMinutes = event.canonicalFields.deviceReminderMinutes,
        operation = operation,
      ),
    )
    return AndroidManagedCalendarSnapshotOccurrenceException(
      projection = projection,
      platformEventRef = event.platformEventRef,
      masterEventRef = masterRow.event.platformEventRef,
      originalInstanceTimeMillis = expectedOriginalTime,
    )
  }

  /**
   * 在严格 decoder 之外识别“应由本轮负责、但文本不 canonical”的 app-owned v2 候选。
   *
   * 这里只按解码后的 scheme/authority/version/scope/Schedule 身份分类，不接受它作为业务身份；参数顺序、合法的
   * 非必要 percent-escape 与重复参数因此都无法绕过 fail-closed。完整读取对当前 scope 的任意 scheduleId 参数负责，
   * 增量读取只对请求集合负责，避免一个无关 Schedule 的损坏行阻断窄对账。
   */
  private fun isRequestedManagedV2Candidate(
    customUri: String,
    scope: CalendarExportScope,
    requestedScheduleIds: List<ScheduleId>?,
  ): Boolean = runCatching {
    val parsed = Uri.parse(customUri)
    if (parsed.scheme != "cyxbs" || parsed.authority != "schedule" || !parsed.isHierarchical) {
      return@runCatching false
    }
    if ("2" !in parsed.getQueryParameters("v") ||
      scope.value !in parsed.getQueryParameters("scope")
    ) return@runCatching false
    val scheduleIdValues = parsed.getQueryParameters("scheduleId")
    requestedScheduleIds == null && scheduleIdValues.isNotEmpty() ||
        requestedScheduleIds != null && requestedScheduleIds.any { it.value in scheduleIdValues }
  }.getOrDefault(false)

  /** 规范化后的普通值行；保留原始关系字段供聚合阶段校验，不泄漏 Cursor。 */
  private data class CanonicalizedSnapshotRow(
    val row: AndroidManagedCalendarSnapshotEventRow,
    val event: AndroidManagedCalendarSnapshotEvent,
  )

  /** Reminders query 返回后，在行复制前及复制/关闭后复核 currentness；外部 reminder method/value 不得被静默折叠。 */
  private fun readReminderMinutes(eventId: Long, ensureAuthorized: () -> Unit): List<Int> {
    ensureAuthorized()
    val cursor = readProvider("Cannot read managed event reminders") {
      platform.queryReminders(eventId)
    }
    val rows = if (cursor == null) {
      ensureAuthorized()
      throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
        "Calendar Provider returned a null reminder cursor for event $eventId",
      )
    } else {
      copyRowsAndClose(
        cursor = cursor,
        message = "Cannot copy managed event reminder cursor",
        ensureAuthorized = ensureAuthorized,
      )
    }
    return rows.map { row ->
      val minutes = row.minutes
      val method = row.method
      if (minutes == null || method == null) {
        throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
          "Incomplete managed event reminder: $eventId",
        )
      }
      if (minutes < 0 || method != CalendarContract.Reminders.METHOD_ALERT) {
        throw AndroidScheduleCalendarGateway.CalendarProviderReadException(
          "Unsupported managed event reminder: $eventId",
        )
      }
      minutes
    }.distinct().sorted()
  }

  /**
   * 复制并关闭已经由 Provider query 返回的 cursor。
   *
   * [ensureAuthorized] 会在触及 cursor 前以及完成行复制、关闭资源后各复核一次，阻断 `query → copyRows` 与
   * `copyRows → canonicalization` 两个 currentness 窗口。只有 [AndroidManagedCalendarSnapshotRowCursor.copyRows] 和
   * [Closeable.close] 的真实 Provider 失败交给 [readProvider] 分类；门禁异常保持原类型。若门禁或复制已失败，
   * 仍尽力关闭 cursor，并把额外关闭失败作为 suppressed 原因附加，避免泄漏资源或改变取消/撤销分型。
   */
  private fun <T> copyRowsAndClose(
    cursor: AndroidManagedCalendarSnapshotRowCursor<T>,
    message: String,
    ensureAuthorized: () -> Unit,
  ): List<T> {
    var rows: List<T>? = null
    var failure: Throwable? = null

    try {
      ensureAuthorized()
      rows = readProvider(message) { cursor.copyRows() }
    } catch (e: Throwable) {
      failure = e
    }
    try {
      readProvider(message) { cursor.close() }
    } catch (closeFailure: Throwable) {
      val existingFailure = failure
      if (existingFailure == null) {
        failure = closeFailure
      } else {
        existingFailure.addSuppressed(closeFailure)
      }
    }
    failure?.let { throw it }
    ensureAuthorized()
    return requireNotNull(rows)
  }

  /**
   * 只归类真实 Provider 调用抛出的失败。
   *
   * 每个 `ensureAuthorized` 门禁都在调用方外层执行，故不会被这里重写为 Provider 异常；取消与安全异常同样保持调用方
   * 可分型的语义，不能变成自动重试候选。
   */
  private inline fun <T> readProvider(message: String, block: () -> T): T = try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: SecurityException) {
    throw e
  } catch (e: AndroidScheduleCalendarGateway.CalendarProviderReadException) {
    throw e
  } catch (e: Exception) {
    throw AndroidScheduleCalendarGateway.CalendarProviderReadException(message, e)
  }

}

/**
 * 快照采集器使用的窄 Android 读取端口。
 *
 * 它只有 Calendar identity、Events 与 Reminders 三类读取，不提供插入、更新、删除、运行时注册或通用回调；production
 * 实现私有地封装 Context/ContentResolver，Android JVM host 测试则以同构 fake 验证单一采集算法。
 */
internal interface AndroidManagedCalendarSnapshotReadPlatform {
  /** 当前应用包名，用于 fail-closed 校验 Events.CUSTOM_APP_PACKAGE。 */
  val packageName: String

  /** 读取快照所需的权限校验；缺失时必须抛出 [SecurityException]。 */
  fun requireCalendarPermissions()

  /** 仅发起当前账号受管 Calendar identity 的 query；返回 cursor 后由采集器先复核 currentness 再读取 row。 */
  fun queryCurrentManagedCalendar(
    accountId: String,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotCalendarRow>?

  /** 仅发起同一 Calendar 的 Events query；请求范围在采集器 post-query 结构化分类，不能由 canonical URI 预过滤。 */
  fun queryEvents(
    calendarId: Long,
    scope: CalendarExportScope,
    scheduleIds: List<ScheduleId>?,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotEventRow>?

  /** 仅发起一个 event 的 Reminders query；返回的 cursor 由采集器在 post-query gate 后复制并关闭。 */
  fun queryReminders(eventId: Long): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotReminderRow>?
}

/**
 * 受信采集器持有的只读 Provider cursor。
 *
 * 该端口不接受回调，也不提供任意写操作；[copyRows] 只能在采集器完成 post-query currentness gate 后调用，确保
 * query 返回与 cursor move/read 之间不会绕过撤销检查。
 */
internal interface AndroidManagedCalendarSnapshotRowCursor<T> : Closeable {
  /** 将当前位置到结尾的 Provider 行复制为普通 Kotlin 值；调用方随后不再持有 platform cursor。 */
  fun copyRows(): List<T>
}

/**
 * 已从 Android Cursor 复制的受管 Calendar identity 行。
 *
 * `incarnation` 保留 Provider 的 nullable 原值，采集器才能把 tokenless/畸形旧行分型为 read failure，而不是在
 * cursor adapter 中丢失迁移边界或自动补值。
 */
internal data class AndroidManagedCalendarSnapshotCalendarRow(
  val calendarId: Long,
  val incarnation: String?,
)

/** 已从 Android Cursor 复制的 Events 原始字段；采集器离开 Provider 后只处理该普通值对象。 */
internal data class AndroidManagedCalendarSnapshotEventRow(
  val eventId: Long,
  val customAppPackage: String?,
  val customAppUri: String?,
  val title: String?,
  val description: String?,
  val dtStart: Long?,
  val dtEnd: Long?,
  val duration: String?,
  val eventTimeZone: String?,
  val allDay: Int?,
  val recurrenceRule: String?,
  val rDate: String?,
  val originalId: Long? = null,
  val originalInstanceTime: Long? = null,
  val originalAllDay: Int? = null,
  val status: Int? = null,
)

/** 已从 Android Cursor 复制的 Reminders 原始字段；null 值仍由采集器 fail closed。 */
internal data class AndroidManagedCalendarSnapshotReminderRow(
  val minutes: Int?,
  val method: Int?,
)

/**
 * 唯一生产 ContentResolver 适配器。
 *
 * 它只发起 Provider query 并创建受信 cursor 包装，不进行 canonicalization、缓存或写操作；授权/currentness gate
 * 由采集器在 query 返回与 cursor 读取之间统一执行，因此不能在这里额外吞掉、包装或切换 dispatcher。
 */
private class AndroidContentResolverManagedCalendarSnapshotReadPlatform(
  private val context: Context,
) : AndroidManagedCalendarSnapshotReadPlatform {
  override val packageName: String
    get() = context.packageName

  /** 维持既有快照权限合同：读取前同时要求 Calendar 的读写权限。 */
  override fun requireCalendarPermissions() {
    if (
      context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
      context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
    ) {
      throw AndroidScheduleCalendarGateway.CalendarPermissionException()
    }
  }

  /**
   * 发起严格 Calendar identity query；row 复制与重复行拒绝由采集器在 post-query gate 后统一处理。
   *
   * 选择条件与注册器的创建/清理身份完全一致，防止 snapshot 读取范围偏离可写的本应用 local Calendar row。
   */
  override fun queryCurrentManagedCalendar(
    accountId: String,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotCalendarRow>? {
    require(accountId.isNotBlank()) { "accountId must not be blank" }
    val projection = arrayOf(
      CalendarContract.Calendars._ID,
      CalendarContract.Calendars.CAL_SYNC1,
    )
    val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
    return context.contentResolver.query(
      CalendarContract.Calendars.CONTENT_URI,
      projection,
      selection,
      arrayOf(
        accountId,
        CalendarContract.ACCOUNT_TYPE_LOCAL,
        AndroidManagedCalendarRegistry.CURRENT_CALENDAR_NAME,
      ),
      null,
    )?.let(::AndroidContentResolverCalendarRowCursor)
  }

  /**
   * 发起 Calendar-wide Events query；行复制必须留给采集器的 post-query gate 之后。
   *
   * [scope] 与 [scheduleIds] 只描述采集器随后负责的结构化分类范围，不能下沉为 canonical URI `LIKE`：否则合法
   * percent-escape 或参数重排的 app-owned v2 候选会在 fail-closed decoder 之前被 Provider 静默排除。
   */
  override fun queryEvents(
    calendarId: Long,
    scope: CalendarExportScope,
    scheduleIds: List<ScheduleId>?,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotEventRow>? {
    val projection = arrayOf(
      CalendarContract.Events._ID,
      CalendarContract.Events.CUSTOM_APP_PACKAGE,
      CalendarContract.Events.CUSTOM_APP_URI,
      CalendarContract.Events.TITLE,
      CalendarContract.Events.DESCRIPTION,
      CalendarContract.Events.DTSTART,
      CalendarContract.Events.DTEND,
      CalendarContract.Events.DURATION,
      CalendarContract.Events.EVENT_TIMEZONE,
      CalendarContract.Events.ALL_DAY,
      CalendarContract.Events.RRULE,
      CalendarContract.Events.RDATE,
      CalendarContract.Events.ORIGINAL_ID,
      CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
      CalendarContract.Events.ORIGINAL_ALL_DAY,
      CalendarContract.Events.STATUS,
    )
    return context.contentResolver.query(
      CalendarContract.Events.CONTENT_URI,
      projection,
      "${CalendarContract.Events.CALENDAR_ID} = ?",
      arrayOf(calendarId.toString()),
      null,
    )?.let(::AndroidContentResolverEventRowCursor)
  }

  /** 发起一个 event 的 Reminders query；语义校验与行复制仍集中在采集器。 */
  override fun queryReminders(
    eventId: Long,
  ): AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotReminderRow>? {
    val projection = arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD)
    return context.contentResolver.query(
      CalendarContract.Reminders.CONTENT_URI,
      projection,
      "${CalendarContract.Reminders.EVENT_ID} = ?",
      arrayOf(eventId.toString()),
      null,
    )?.let(::AndroidContentResolverReminderRowCursor)
  }
}

/** Calendar identity Cursor 的受信复制包装；只在 acquirer 已完成 post-query gate 后移动 Cursor。 */
private class AndroidContentResolverCalendarRowCursor(
  private val cursor: Cursor,
) : AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotCalendarRow> {
  /** 同时复制 row id 与 nullable `CAL_SYNC1`；完整校验集中在采集器的 fail-closed 迁移边界。 */
  override fun copyRows(): List<AndroidManagedCalendarSnapshotCalendarRow> = buildList {
    while (cursor.moveToNext()) {
      add(
        AndroidManagedCalendarSnapshotCalendarRow(
          calendarId = cursor.getLong(0),
          incarnation = cursor.getString(1),
        ),
      )
    }
  }

  /** currentness gate 失败时也必须关闭 Provider cursor。 */
  override fun close() {
    cursor.close()
  }
}

/** Events Cursor 的受信复制包装；只在 acquirer 已完成 post-query gate 后移动 Cursor。 */
private class AndroidContentResolverEventRowCursor(
  private val cursor: Cursor,
) : AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotEventRow> {
  /** 将 Provider 行复制为 ordinary Kotlin 值，随后调用方无法再取得 Cursor。 */
  override fun copyRows(): List<AndroidManagedCalendarSnapshotEventRow> = buildList {
    while (cursor.moveToNext()) {
      add(
        AndroidManagedCalendarSnapshotEventRow(
          eventId = cursor.getLong(0),
          customAppPackage = cursor.getString(1),
          customAppUri = cursor.getString(2),
          title = cursor.getString(3),
          description = cursor.getString(4),
          dtStart = cursor.getLongOrNull(5),
          dtEnd = cursor.getLongOrNull(6),
          duration = cursor.getString(7),
          eventTimeZone = cursor.getString(8),
          allDay = cursor.getIntOrNull(9),
          recurrenceRule = cursor.getString(10),
          rDate = cursor.getString(11),
          originalId = cursor.getLongOrNull(12),
          originalInstanceTime = cursor.getLongOrNull(13),
          originalAllDay = cursor.getIntOrNull(14),
          status = cursor.getIntOrNull(15),
        ),
      )
    }
  }

  /** Cursor 只能在复制完成或 currentness gate 抛错后关闭，不能泄漏到普通快照外。 */
  override fun close() {
    cursor.close()
  }
}

/** Reminders Cursor 的受信复制包装；null 数值必须原样带到采集器执行 fail-closed 校验。 */
private class AndroidContentResolverReminderRowCursor(
  private val cursor: Cursor,
) : AndroidManagedCalendarSnapshotRowCursor<AndroidManagedCalendarSnapshotReminderRow> {
  /** 将 Provider reminder 行复制为 ordinary Kotlin 值。 */
  override fun copyRows(): List<AndroidManagedCalendarSnapshotReminderRow> = buildList {
    while (cursor.moveToNext()) {
      add(
        AndroidManagedCalendarSnapshotReminderRow(
          minutes = cursor.getIntOrNull(0),
          method = cursor.getIntOrNull(1),
        ),
      )
    }
  }

  /** Cursor 关闭属于平台资源回收，不参与采集结果或运行时注册。 */
  override fun close() {
    cursor.close()
  }
}

/** Android Cursor 对 null int 返回 0，必须显式保留 null 才能 fail closed。 */
private fun Cursor.getIntOrNull(columnIndex: Int): Int? =
  if (isNull(columnIndex)) null else getInt(columnIndex)

/** Android Cursor 对 null long 返回 0，必须显式保留 null 才能重建 timing。 */
private fun Cursor.getLongOrNull(columnIndex: Int): Long? =
  if (isNull(columnIndex)) null else getLong(columnIndex)
