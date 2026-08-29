package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifierCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionOperation
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.calendar.CanonicalCalendarFields
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.time.LocalDateTimeResolution
import com.cyxbs.pages.schedule.domain.time.ScheduleDstResolver
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Android 受管日历的只读快照结果。
 *
 * Calendar row 缺失是可恢复的资源状态，而 Provider 查询或 canonicalization 失败会抛出
 * [AndroidScheduleCalendarGateway.CalendarProviderReadException]，两者不能折叠为空事件集合。
 */
sealed interface AndroidManagedCalendarSnapshot {
  /** 当前账号不存在受管 Calendar row；读取过程不会隐式创建任何 Provider 资源。 */
  data object CalendarAbsent : AndroidManagedCalendarSnapshot

  /**
   * 当前账号存在受管 Calendar row。
   *
   * @property calendarIdentifier stable 的受管 Calendar row + incarnation 身份；数字 row id 可能复用，只有与
   * `CAL_SYNC1` ownership token 组合后才能供 durable link 比对，event ref/fingerprint 仍不能替代它。
   * @property events 全部通过严格校验的 Provider 事件行。
   */
  data class Present(
    val calendarIdentifier: String,
    val events: List<AndroidManagedCalendarSnapshotEvent>,
  ) : AndroidManagedCalendarSnapshot
}

/**
 * 一个已验证受管 Provider 事件的 canonical 只读快照。
 *
 * [providerFingerprint] 必须由同一个 [canonicalFields] 与 canonical v2 URI 计算，平台引用仅用于后续精确定位，
 * 不参与业务身份，也不能替代 [projectionId]。
 */
data class AndroidManagedCalendarSnapshotEvent(
  val projectionId: CalendarProjectionId,
  val platformEventRef: PlatformCalendarEventRef,
  val canonicalFields: CanonicalCalendarFields,
  val providerFingerprint: String,
  /** 已严格验证并聚合到 series master 的 Provider occurrence rows；不作为第二个顶层 managed event 暴露。 */
  val occurrenceExceptions: List<AndroidManagedCalendarSnapshotOccurrenceException> = emptyList(),
)

/** fresh snapshot 中一个拥有精确 master relation 的原生 occurrence exception。 */
data class AndroidManagedCalendarSnapshotOccurrenceException(
  val projection: CalendarOccurrenceExceptionProjection,
  val platformEventRef: PlatformCalendarEventRef,
  val masterEventRef: PlatformCalendarEventRef,
  val originalInstanceTimeMillis: Long,
)

/**
 * gateway 写路径所需的 Android 依赖。
 *
 * 生产构造器始终完整提供它；仅快照的 JVM host 测试刻意不构造 [Context]，因为读取会直接委派给
 * [AndroidManagedCalendarSnapshotAcquirer]，不会触发写路径。
 */
private data class AndroidScheduleCalendarGatewayWriteDependencies(
  val context: Context,
  val registry: AndroidManagedCalendarRegistry,
)

/** Android host 测试可直接检查的 occurrence 写入事实；不包含 Context、Cursor 或真实 Provider 副作用。 */
internal data class AndroidPreparedOccurrenceExceptionWrite(
  val projection: CalendarOccurrenceExceptionProjection,
  val originalInstanceTimeMillis: Long,
  val originalAllDay: Int,
  val providerStatus: Int,
  val recurrenceRule: String? = null,
  val rDate: String? = null,
)

/**
 * create batch 中一个 occurrence event 与其 reminder 的实际 operation 索引关系。
 *
 * 该普通值计划由 production append 路径直接消费，使 host test 能证明 master-first、`ORIGINAL_ID` back-reference
 * 与 reminder event back-reference，而无需构造 Context 或访问真实 Calendar Provider。
 */
internal data class AndroidPreparedOccurrenceExceptionProviderInsert(
  val prepared: AndroidPreparedOccurrenceExceptionWrite,
  val eventOperationIndex: Int,
  val masterOriginalIdBackReference: Int,
  val reminderEventBackReferences: List<Int>,
)

/**
 * fresh snapshot 例外删除的完整 compare-and-delete 条件。
 *
 * [selectionArgs] 同时冻结 event/master 身份、Provider ownership 与原 recurrence identity；调用方必须配合
 * `withExpectedCount(1)`，任何字段在 preflight 后漂移都会让整个 Provider batch 回滚。
 */
internal data class AndroidOccurrenceExceptionDeleteSelection(
  val selection: String,
  val selectionArgs: List<String>,
  val expectedCount: Int,
)

/**
 * Android 原生 occurrence exception 的纯预检与时间映射。
 *
 * 所有 identity、fingerprint、有效时间、reminder、排序与 master 归属在任何 registry/Calendar Provider 调用前完成；
 * 失败时调用方必须保持 Calendar、Event 与 Reminder 零写入。
 */
internal object AndroidOccurrenceExceptionWritePlanner {
  fun prepare(master: CalendarEventProjection): List<AndroidPreparedOccurrenceExceptionWrite> {
    val exceptions = master.nativeOccurrenceExceptions
    if (exceptions.isEmpty()) return emptyList()
    require(master.id.kind == CalendarProjectionKind.SERIES_MASTER && master.recurrenceRule != null) {
      "Native occurrence exceptions require a recurring series master"
    }
    require(CalendarProjectionUriCodec.encode(master.id) == master.externalUri) {
      "Native occurrence exception master URI is not canonical"
    }
    requireCanonicalFields(
      title = master.title,
      description = master.description,
      timing = master.timing,
      recurrenceRule = master.recurrenceRule,
      reminderMinutes = master.deviceReminderMinutes,
    )
    require(master.fingerprint == CalendarProjectionFingerprint.compute(
      externalUri = master.externalUri,
      title = master.title,
      description = master.description,
      timing = master.timing,
      recurrenceRule = master.recurrenceRule,
      reminderMinutes = master.deviceReminderMinutes,
      nativeOccurrenceExceptions = exceptions,
    )) { "Native occurrence exception master fingerprint is not canonical" }
    require(exceptions == exceptions.sortedBy { it.externalUri }) {
      "Native occurrence exceptions must be sorted by canonical identity"
    }
    require(exceptions.map { it.id }.distinct().size == exceptions.size) {
      "Native occurrence exception identities must be unique"
    }
    return exceptions.map { exception ->
      require(exception.id.scope == master.id.scope && exception.id.scheduleId == master.id.scheduleId &&
          exception.id.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION
      ) { "Native occurrence exception does not belong to master" }
      require(CalendarProjectionUriCodec.encode(exception.id) == exception.externalUri) {
        "Native occurrence exception URI is not canonical"
      }
      requireCanonicalFields(
        title = exception.title,
        description = exception.description,
        timing = exception.timing,
        recurrenceRule = null,
        reminderMinutes = exception.deviceReminderMinutes,
      )
      require(exception.fingerprint == CalendarProjectionFingerprint.computeOccurrenceException(
        externalUri = exception.externalUri,
        title = exception.title,
        description = exception.description,
        timing = exception.timing,
        reminderMinutes = exception.deviceReminderMinutes,
        operation = exception.operation,
      )) { "Native occurrence exception fingerprint is not canonical" }
      val recurrenceId = requireNotNull(exception.id.recurrenceId)
      require(recurrenceId.allDay == (exception.timing is CalendarTiming.AllDay)) {
        "Occurrence identity kind does not match projected timing"
      }
      require(master.timing::class == exception.timing::class) {
        "Occurrence timing kind must match series master"
      }
      val masterTimeZoneId = master.timing.timeZoneIdOrNull()
      require(recurrenceId.timeZoneId == masterTimeZoneId &&
          exception.timing.timeZoneIdOrNull() == masterTimeZoneId
      ) { "Occurrence identity and effective timing timezone must match series master" }
      AndroidPreparedOccurrenceExceptionWrite(
        projection = exception,
        originalInstanceTimeMillis = originalInstanceTimeMillis(recurrenceId),
        originalAllDay = if (recurrenceId.allDay) 1 else 0,
        providerStatus = when (exception.operation) {
          CalendarOccurrenceExceptionOperation.UPSERT -> CalendarContract.Events.STATUS_CONFIRMED
          CalendarOccurrenceExceptionOperation.CANCEL -> CalendarContract.Events.STATUS_CANCELED
        },
      )
    }
  }

  /**
   * 用同一生产 preflight 包住后续 registry/Provider 访问；host fake 可据此证明失败时依赖回调完全不会执行。
   */
  fun <T> withPreparedWrite(
    master: CalendarEventProjection,
    accessWriteDependencies: (List<AndroidPreparedOccurrenceExceptionWrite>) -> T,
  ): T = accessWriteDependencies(prepare(master))

  /**
   * 冻结 create batch 的 occurrence operation 索引与 back-reference。
   *
   * [firstOperationIndex] 是 master 及其 reminders 已加入后的下一个索引；[masterInsertBackReference] 必须指向更早的
   * master insert。每个 exception event 后紧跟自己的 reminders，后者只能回指该 exception，不能误绑 master。
   */
  fun prepareCreateProviderInserts(
    preparedExceptions: List<AndroidPreparedOccurrenceExceptionWrite>,
    firstOperationIndex: Int,
    masterInsertBackReference: Int,
  ): List<AndroidPreparedOccurrenceExceptionProviderInsert> {
    require(firstOperationIndex >= 0 && masterInsertBackReference in 0 until firstOperationIndex) {
      "Occurrence Provider inserts require an earlier master operation"
    }
    var nextOperationIndex = firstOperationIndex
    return preparedExceptions.map { prepared ->
      val eventOperationIndex = nextOperationIndex++
      AndroidPreparedOccurrenceExceptionProviderInsert(
        prepared = prepared,
        eventOperationIndex = eventOperationIndex,
        masterOriginalIdBackReference = masterInsertBackReference,
        reminderEventBackReferences = List(prepared.projection.deviceReminderMinutes.size) {
          eventOperationIndex
        },
      ).also {
        nextOperationIndex += it.reminderEventBackReferences.size
      }
    }
  }

  /** 构造旧例外删除的完整 fresh-snapshot identity 条件，禁止只凭 eventId 与 URI 删除漂移行。 */
  fun replacementDeleteSelection(
    existing: AndroidManagedCalendarSnapshotOccurrenceException,
    calendarId: Long,
    masterEventId: Long,
    packageName: String,
  ): AndroidOccurrenceExceptionDeleteSelection {
    val recurrenceId = requireNotNull(existing.projection.id.recurrenceId)
    return AndroidOccurrenceExceptionDeleteSelection(
      selection = "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.CALENDAR_ID} = ? AND " +
          "${CalendarContract.Events.ORIGINAL_ID} = ? AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} = ? AND " +
          "${CalendarContract.Events.ORIGINAL_ALL_DAY} = ? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND " +
          "${CalendarContract.Events.CUSTOM_APP_URI} = ?",
      selectionArgs = listOf(
        requireNotNull(AndroidCalendarEventRefCodec.decodeOrNull(existing.platformEventRef)).toString(),
        calendarId.toString(),
        masterEventId.toString(),
        existing.originalInstanceTimeMillis.toString(),
        if (recurrenceId.allDay) "1" else "0",
        packageName,
        existing.projection.externalUri,
      ),
      expectedCount = 1,
    )
  }

  /** 复用 durable canonical fields 约束，并额外证明 Android DST resolver 能在 Provider 写前解析墙上时间。 */
  private fun requireCanonicalFields(
    title: String,
    description: String,
    timing: CalendarTiming,
    recurrenceRule: String?,
    reminderMinutes: List<Int>,
  ) {
    CanonicalCalendarFields(title, description, timing, recurrenceRule, reminderMinutes)
    when (timing) {
      is CalendarTiming.Timed -> requireResolvedTiming(timing.start, timing.timeZoneId)
      is CalendarTiming.Deadline -> requireResolvedTiming(timing.due, timing.timeZoneId)
      is CalendarTiming.AllDay -> Unit
    }
  }

  /** 非法时区、未能冻结的 DST transition 均必须在 Calendar row lookup 之前失败。 */
  private fun requireResolvedTiming(
    local: com.cyxbs.components.config.time.MinuteTimeDate,
    timeZoneId: String,
  ) {
    require(ScheduleDstResolver.resolve(local, timeZoneId) is LocalDateTimeResolution.Resolved) {
      "Cannot resolve native occurrence wall time"
    }
  }

  /** 全天无时区；Timed/Deadline 的 recurrence identity 与有效时间必须共同继承 master 时区。 */
  private fun CalendarTiming.timeZoneIdOrNull(): String? = when (this) {
    is CalendarTiming.Timed -> timeZoneId
    is CalendarTiming.Deadline -> timeZoneId
    is CalendarTiming.AllDay -> null
  }

  /** 原始 recurrence identity 的 Provider instant；移动后的内容绝不能改写此值。 */
  fun originalInstanceTimeMillis(recurrenceId: RecurrenceId): Long = if (recurrenceId.allDay) {
    val date = recurrenceId.originalDateTime.date
    // 避免调用项目 Date.toLocalDate() 触发 Android main Looper 静态初始化，host 纯合同直接构造 kotlinx 日期。
    kotlinx.datetime.LocalDate(date.year, date.monthNumber, date.dayOfMonth)
      .atStartOfDayIn(TimeZone.UTC)
      .toEpochMilliseconds()
  } else {
    when (val resolved = ScheduleDstResolver.resolve(
      recurrenceId.originalDateTime,
      requireNotNull(recurrenceId.timeZoneId),
    )) {
      is LocalDateTimeResolution.Resolved -> resolved.instant.toEpochMilliseconds()
      is LocalDateTimeResolution.InvalidTimeZone,
      is LocalDateTimeResolution.GapAdjustmentNotMinuteAligned,
      is LocalDateTimeResolution.TransitionNotResolved ->
        throw IllegalArgumentException("Cannot resolve occurrence recurrence identity")
    }
  }
}

/**
 * Android 日历 gateway，负责读写 Provider 托管事件。
 *
 * 只操作当前受管日历中、应用包名与 v2 URI 已验证的事件；绝不扫描、认领或删除第三方日历内容。
 * update/delete 前再次核验 calendar membership、package、URI 与 scope，避免误操作。
 */
class AndroidScheduleCalendarGateway private constructor(
  private val writeDependencies: AndroidScheduleCalendarGatewayWriteDependencies?,
  private val accountId: String,
  private val snapshotAcquirer: AndroidManagedCalendarSnapshotAcquirer,
) {
  /**
   * 取得生产写路径依赖。
   *
   * 仅快照 host 构造器不会配置该依赖；若它误调用写入口，立即失败以避免把测试 seam 扩展为可用的写入通道。
   */
  private val context: Context
    get() = checkNotNull(writeDependencies) { "仅快照的 host gateway 不支持日历写入" }.context

  /** 生产写入口使用的受管 Calendar registry；其缺失与 [context] 一样代表仅快照 host seam。 */
  private val registry: AndroidManagedCalendarRegistry
    get() = checkNotNull(writeDependencies) { "仅快照的 host gateway 不支持日历写入" }.registry

  /**
   * 生产 gateway 保留既有读写 API；只读快照统一交给受信采集器，不能在此处重建第二套 Provider cursor 流程。
   */
  constructor(
    context: Context,
    registry: AndroidManagedCalendarRegistry,
    accountId: String,
  ) : this(
    writeDependencies = AndroidScheduleCalendarGatewayWriteDependencies(context, registry),
    accountId = accountId,
    snapshotAcquirer = AndroidManagedCalendarSnapshotAcquirer(context, accountId),
  )

  /**
   * Android JVM host 测试注入同构只读端口的构造器。
   *
   * 它不构造任何 Android [Context]，仅验证 gateway 对唯一采集器的委派；不提供 production runtime、写入或
   * Provider 注册扩展点。
   */
  internal constructor(
    accountId: String,
    snapshotReadPlatform: AndroidManagedCalendarSnapshotReadPlatform,
  ) : this(
    writeDependencies = null,
    accountId = accountId,
    snapshotAcquirer = AndroidManagedCalendarSnapshotAcquirer(snapshotReadPlatform, accountId),
  )

  /**
   * 读取当前账号受管日历的严格 canonical 快照。
   *
   * gateway 只委派给 [AndroidManagedCalendarSnapshotAcquirer]；Calendar row lookup、Events/Reminders 的 Cursor 读取、
   * canonicalization 与 fingerprint 都只有该受信边界一份生产实现。本方法不创建或修改 Calendar、Events、Reminders。
   *
   * @param scope 仅接受该导出 scope 的 canonical v2 URI。
   * @param scheduleIds 可选的 Schedule ID 子集；空集合仍会先确认 Calendar row 是否存在。
   * @return Calendar row 缺失或存在时的 typed 快照结果。
   */
  fun queryManagedCalendarSnapshot(
    scope: CalendarExportScope,
    scheduleIds: Set<ScheduleId>? = null,
    ensureAuthorized: () -> Unit = {},
  ): AndroidManagedCalendarSnapshot = snapshotAcquirer.acquire(
    scope = scope,
    scheduleIds = scheduleIds,
    ensureAuthorized = ensureAuthorized,
  )

  /**
   * 查询当前受管日历中所有已验证的 v2 托管事件。
   *
   * 兼容旧单向导出合同：Calendar row 缺失映射为空集合，后续 Create 动作仍由写入口显式创建日历；事件
   * identity、fingerprint 与平台引用全部直接来自只读快照，不再维护第二套 cursor 解析。
   */
  fun queryManagedEvents(
    scope: CalendarExportScope,
    scheduleIds: Set<ScheduleId>? = null,
    ensureAuthorized: () -> Unit = {},
  ): List<ManagedCalendarEvent> = when (
    val snapshot = queryManagedCalendarSnapshot(scope, scheduleIds, ensureAuthorized)
  ) {
    AndroidManagedCalendarSnapshot.CalendarAbsent -> emptyList()
    is AndroidManagedCalendarSnapshot.Present -> snapshot.events.map { event ->
      ManagedCalendarEvent(event.projectionId, event.providerFingerprint, event.platformEventRef)
    }
  }

  /**
   * 删除并重建完整身份可信的当前受管日历，供协调器从不可兼容投射中恢复。
   *
   * 本方法不会处理 tokenless 或身份异常的 Calendar row；成功后日历为空，调用方必须立刻使用完整 Schedule
   * 快照全量回写，不能沿用触发恢复的增量范围。
   */
  internal fun recreateManagedCalendarForRecovery(
    ensureAuthorized: () -> Unit = {},
  ): Boolean = registry.recreateCurrentManagedCalendar(accountId, ensureAuthorized) != null

  /**
   * 创建新的日历事件。
   *
   * Provider 的等价 RRULE 重排在下一轮查询时统一规范化；本方法只负责原子写入事件与提醒。
   */
  fun createEvent(
    projection: CalendarEventProjection,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit = {},
  ): Long? = AndroidOccurrenceExceptionWritePlanner.withPreparedWrite(projection) { preparedExceptions ->
    ensureAuthorized()
    requireCalendarPermissions()
    val calendarId = registry.getOrCreateManagedCalendar(accountId, scope, ensureAuthorized)
      ?: throw CalendarProviderReadException("Managed calendar is temporarily unavailable")

    val operations = arrayListOf(
      ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
        .withValues(buildEventContentValues(projection, calendarId))
        .build(),
    )
    projection.deviceReminderMinutes.forEach { minutes ->
      operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
        .withValue(CalendarContract.Reminders.MINUTES, minutes)
        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        .build()
    }
    appendOccurrenceInsertOperations(
      operations = operations,
      preparedExceptions = preparedExceptions,
      calendarId = calendarId,
      masterInsertBackReference = 0,
    )
    // Events 与 Reminders 属于同一 authority；back-reference 让 Provider 在单个事务中创建完整投影。
    // applyBatch 一旦发出无法被 coroutine cancel 中断；本检查保证撤销后不会再发起新的事件/提醒事务。
    ensureAuthorized()
    val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
    // batch 返回后先复核，避免调用方在撤销后的同一轮继续处理新事件引用。
    ensureAuthorized()
    results.firstOrNull()?.uri?.let(ContentUris::parseId)?.takeIf { it > 0 }
      ?: throw CalendarProviderReadException("Calendar Provider did not return the inserted event ID")
  }

  /**
   * 在 finalized 缺失-link runtime 指定的固定 Calendar incarnation 中创建事件，绝不创建、替换或认领其他日历。
   *
   * [expectedCalendarIdentifier] 必须来自同一轮 strict Provider preflight，并完整包含 row id 与 `CAL_SYNC1` ownership
   * token。入口先严格解码 v2 identity，再通过 registry 的非创建查询确认账号/类型/名称/row/token 全部仍匹配；构造
   * batch 后先复核一次，[beforeInsert] 记录“可能已经 Create”的 retry 边界后再执行最终复核，专门覆盖 callback
   * 可能阻塞期间的 Calendar replacement/token 漂移。Calendar Provider 不提供把 Calendars 读取与 Events batch 原子
   * 绑定的通用 CAS，因此这里只缩小 read→write 窗口，不伪造 assert-query 保证。任何 v1/畸形标识、row 缺失或同
   * id replacement 都在 Event/Reminder 写前阻断，绝不回退 get-or-create。
   */
  fun createEventInExistingManagedCalendar(
    projection: CalendarEventProjection,
    scope: CalendarExportScope,
    expectedCalendarIdentifier: String,
    ensureAuthorized: () -> Unit = {},
    beforeInsert: () -> Unit = {},
  ): Long? {
    val preparedExceptions = AndroidOccurrenceExceptionWritePlanner.prepare(projection)
    ensureAuthorized()
    requireCalendarPermissions()
    require(projection.id.scope == scope) { "projection scope must match fixed-row Create scope" }
    val expectedCalendarIdentity =
      AndroidManagedCalendarIdentifierCodec.decodeOrNull(expectedCalendarIdentifier)
        ?: throw FixedCalendarCreateBlockedException(FixedCalendarCreateBlockedReason.INVALID_IDENTIFIER)
    val calendarId = registry.findCurrentManagedCalendarMatching(
      accountId = accountId,
      expectedCalendarIdentity = expectedCalendarIdentity,
      ensureAuthorized = ensureAuthorized,
    )
      ?: throw FixedCalendarCreateBlockedException(FixedCalendarCreateBlockedReason.CALENDAR_ROW_NOT_CURRENT)

    val operations = arrayListOf(
      ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
        .withValues(buildEventContentValues(projection, calendarId))
        .build(),
    )
    projection.deviceReminderMinutes.forEach { minutes ->
      operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
        .withValue(CalendarContract.Reminders.MINUTES, minutes)
        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        .build()
    }
    appendOccurrenceInsertOperations(
      operations = operations,
      preparedExceptions = preparedExceptions,
      calendarId = calendarId,
      masterInsertBackReference = 0,
    )

    // callback 之前仍比较完整 token，避免已知失效的固定身份被误记为可能执行过 Create。
    if (
      registry.findCurrentManagedCalendarMatching(
        accountId = accountId,
        expectedCalendarIdentity = expectedCalendarIdentity,
        ensureAuthorized = ensureAuthorized,
      ) != calendarId
    ) {
      throw FixedCalendarCreateBlockedException(FixedCalendarCreateBlockedReason.CALENDAR_ROW_NOT_CURRENT)
    }
    ensureAuthorized()
    beforeInsert()
    // beforeInsert 可能持久化 retry 状态并发生阻塞；返回后必须重新读取 row + CAL_SYNC1，不能沿用 callback 前授权。
    if (
      registry.findCurrentManagedCalendarMatching(
        accountId = accountId,
        expectedCalendarIdentity = expectedCalendarIdentity,
        ensureAuthorized = ensureAuthorized,
      ) != calendarId
    ) {
      throw FixedCalendarCreateBlockedException(FixedCalendarCreateBlockedReason.CALENDAR_ROW_NOT_CURRENT)
    }
    ensureAuthorized()
    val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
    ensureAuthorized()
    return results.firstOrNull()?.uri?.let(ContentUris::parseId)?.takeIf { it > 0 }
      ?: throw CalendarProviderReadException("Calendar Provider did not return the inserted event ID")
  }

  /**
   * 以 finalized 流程冻结的完整 Calendar incarnation 更新既有事件，绝不创建或替换受管日历。
   *
   * [expectedCalendarIdentifier] 必须来自同一轮 fresh pre-write Provider snapshot，而非旧 link 或调用方猜测。本方法
   * 先严格解析 row + `CAL_SYNC1` token，再以 registry 非创建查询确认完整受管身份；随后核验 event 仍属于这个 row、
   * 当前应用包名、scope 与完整 projection，并在最靠近 batch 的既有 read-window seam 再次比较完整 token。v1、畸形
   * identity、同数字 id replacement 或 ownership 漂移都返回 `false`，且不会发起 Event/Reminder 写。Provider 不支持
   * 将 Calendars preflight 与 Events collection update 原子化，因此仍保留 finalized selection/expectedCount 边界，
   * 不增加推测性的 CAS 或 assert-query。每个阻塞查询和 applyBatch 前后都复核 [ensureAuthorized]。
   */
  fun updateExistingManagedEvent(
    projection: CalendarEventProjection,
    eventRef: PlatformCalendarEventRef,
    scope: CalendarExportScope,
    expectedCalendarIdentifier: String,
    ensureAuthorized: () -> Unit = {},
  ): Boolean {
    val preparedExceptions = AndroidOccurrenceExceptionWritePlanner.prepare(projection)
    ensureAuthorized()
    requireCalendarPermissions()
    val eventId = AndroidCalendarEventRefCodec.decodeOrNull(eventRef) ?: return false
    val expectedCalendarIdentity =
      AndroidManagedCalendarIdentifierCodec.decodeOrNull(expectedCalendarIdentifier)
        ?: return false
    val calendarId = registry.findCurrentManagedCalendarMatching(
      accountId = accountId,
      expectedCalendarIdentity = expectedCalendarIdentity,
      ensureAuthorized = ensureAuthorized,
    ) ?: return false

    if (!verifyEventOwnership(
        eventId,
        calendarId,
        scope,
        projection.id,
        ensureAuthorized
      )
    ) return false
    // ownership 查询后仍比较 incarnation，防止同一数字 id 在两个阻塞读取之间被删除重建。
    if (
      registry.findCurrentManagedCalendarMatching(
        accountId = accountId,
        expectedCalendarIdentity = expectedCalendarIdentity,
        ensureAuthorized = ensureAuthorized,
      ) != calendarId
    ) return false
    val existingExceptions = readVerifiedOccurrenceExceptions(
      projection = projection,
      masterEventRef = eventRef,
      scope = scope,
      expectedCalendarIdentifier = expectedCalendarIdentifier,
      ensureAuthorized = ensureAuthorized,
    ) ?: return false

    val canonicalExternalUri = CalendarProjectionUriCodec.encode(projection.id)
    val operations = arrayListOf(
      // 必须使用 Events collection URI：selection 会在同一 applyBatch 事务内重新验证 event identity，
      // 因此 ownership/membership 在预检后漂移时，后续 reminder 替换也会整体回滚。
      ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI)
        .withSelection(
          "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.CALENDAR_ID} = ? AND " +
              "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND ${CalendarContract.Events.CUSTOM_APP_URI} = ?",
          arrayOf(
            eventId.toString(),
            calendarId.toString(),
            context.packageName,
            canonicalExternalUri,
          ),
        )
        // finalized update 不写 CALENDAR_ID，禁止借更新操作迁移 event membership。
        .withValues(
          buildEventContentValues(
            projection,
            calendarId = null,
            customAppUri = canonicalExternalUri,
          ),
        )
        .withExpectedCount(1)
        .build(),
      ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
        .withSelection("${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        .build(),
    )
    projection.deviceReminderMinutes.forEach { minutes ->
      operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
        .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
        .withValue(CalendarContract.Reminders.MINUTES, minutes)
        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        .build()
    }
    appendOccurrenceReplacementOperations(
      operations = operations,
      preparedExceptions = preparedExceptions,
      existingExceptions = existingExceptions,
      calendarId = calendarId,
      masterEventId = eventId,
    )
    ensureAuthorized()
    context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
    ensureAuthorized()
    return true
  }

  /**
   * 更新已有事件。
   *
   * 此入口仅保留给 W15 启动/legacy exporter：它可在旧单向导出语义下 get-or-create 受管日历。finalized W16
   * confirmation 必须调用 [updateExistingManagedEvent]，不能通过本方法创建或替换 Provider 日历。
   */
  fun updateEvent(
    projection: CalendarEventProjection,
    eventRef: PlatformCalendarEventRef,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit = {},
  ): Boolean {
    val preparedExceptions = AndroidOccurrenceExceptionWritePlanner.prepare(projection)
    ensureAuthorized()
    requireCalendarPermissions()
    val eventId = AndroidCalendarEventRefCodec.decodeOrNull(eventRef) ?: return false
    val calendarId = registry.getOrCreateManagedCalendar(accountId, scope, ensureAuthorized)
      ?: throw CalendarProviderReadException("Managed calendar is temporarily unavailable")

    if (!verifyEventOwnership(
        eventId,
        calendarId,
        scope,
        projection.id,
        ensureAuthorized
      )
    ) return false
    val legacySnapshot = queryManagedCalendarSnapshot(
      scope = scope,
      scheduleIds = setOf(projection.id.scheduleId),
      ensureAuthorized = ensureAuthorized,
    ) as? AndroidManagedCalendarSnapshot.Present ?: return false
    val snapshotCalendarId = AndroidManagedCalendarIdentifierCodec.decodeOrNull(
      legacySnapshot.calendarIdentifier,
    )?.calendarRowId ?: return false
    if (snapshotCalendarId != calendarId) return false
    val snapshotMaster = legacySnapshot.events.singleOrNull {
      it.projectionId == projection.id && it.platformEventRef == eventRef
    } ?: return false
    val existingExceptions = snapshotMaster.occurrenceExceptions

    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    val operations = arrayListOf(
      ContentProviderOperation.newUpdate(eventUri)
        .withValues(buildEventContentValues(projection, calendarId))
        .withExpectedCount(1)
        .build(),
      ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
        .withSelection("${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        .build(),
    )
    projection.deviceReminderMinutes.forEach { minutes ->
      operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
        .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
        .withValue(CalendarContract.Reminders.MINUTES, minutes)
        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        .build()
    }
    appendOccurrenceReplacementOperations(
      operations = operations,
      preparedExceptions = preparedExceptions,
      existingExceptions = existingExceptions,
      calendarId = calendarId,
      masterEventId = eventId,
    )
    // ownership 查询可阻塞；其返回后必须再次复核，才允许替换事件与提醒。
    ensureAuthorized()
    context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
    // batch 完成后不能直接向计划循环报告成功，需先确认生命周期仍未撤销。
    ensureAuthorized()
    return true
  }

  /**
   * 删除托管事件。
   *
   * 删除前再次核验所有权；只删除确认属于当前 scope 的事件。
   */
  fun deleteEvent(
    event: ManagedCalendarEvent,
    scope: CalendarExportScope,
    ensureAuthorized: () -> Unit = {},
  ): Boolean {
    ensureAuthorized()
    requireCalendarPermissions()
    val eventId = AndroidCalendarEventRefCodec.decodeOrNull(event.platformEventRef) ?: return false
    val calendarId = registry.getOrCreateManagedCalendar(accountId, scope, ensureAuthorized)
      ?: throw CalendarProviderReadException("Managed calendar is temporarily unavailable")

    if (!verifyEventOwnership(eventId, calendarId, scope, event.id, ensureAuthorized)) return false

    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    // ownership 读取之后的最后一次同步复核，阻止撤销后继续发起 delete。
    ensureAuthorized()
    val deleted = context.contentResolver.delete(eventUri, null, null)
    // 已发出的 delete 只能 best-effort；复核保证不会把撤销后的结果继续交给下一项 action。
    ensureAuthorized()
    return deleted > 0
  }

  /** fresh aggregate snapshot 必须仍绑定调用方冻结的 Calendar incarnation 与同一 master event。 */
  private fun readVerifiedOccurrenceExceptions(
    projection: CalendarEventProjection,
    masterEventRef: PlatformCalendarEventRef,
    scope: CalendarExportScope,
    expectedCalendarIdentifier: String,
    ensureAuthorized: () -> Unit,
  ): List<AndroidManagedCalendarSnapshotOccurrenceException>? {
    val snapshot = queryManagedCalendarSnapshot(
      scope = scope,
      scheduleIds = setOf(projection.id.scheduleId),
      ensureAuthorized = ensureAuthorized,
    ) as? AndroidManagedCalendarSnapshot.Present ?: return null
    if (snapshot.calendarIdentifier != expectedCalendarIdentifier) return null
    return snapshot.events.singleOrNull {
      it.projectionId == projection.id && it.platformEventRef == masterEventRef
    }?.occurrenceExceptions
  }

  /** 创建 master 的 batch 中，所有 exception insert 都通过 operation 0 的 back-reference 精确绑定主事件。 */
  private fun appendOccurrenceInsertOperations(
    operations: ArrayList<ContentProviderOperation>,
    preparedExceptions: List<AndroidPreparedOccurrenceExceptionWrite>,
    calendarId: Long,
    masterInsertBackReference: Int,
  ) {
    val providerInserts = AndroidOccurrenceExceptionWritePlanner.prepareCreateProviderInserts(
      preparedExceptions = preparedExceptions,
      firstOperationIndex = operations.size,
      masterInsertBackReference = masterInsertBackReference,
    )
    providerInserts.forEach { insert ->
      check(operations.size == insert.eventOperationIndex) {
        "Occurrence Provider operation order drifted after preflight"
      }
      operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
        .withValues(buildOccurrenceExceptionContentValues(insert.prepared, calendarId))
        .withValueBackReference(
          CalendarContract.Events.ORIGINAL_ID,
          insert.masterOriginalIdBackReference,
        )
        .build()
      insert.prepared.projection.deviceReminderMinutes.zip(insert.reminderEventBackReferences)
        .forEach { (minutes, eventBackReference) ->
          operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
            .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventBackReference)
            .withValue(CalendarContract.Reminders.MINUTES, minutes)
            .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            .build()
        }
    }
  }

  /**
   * update 先按 fresh snapshot 的精确 event ref 删除旧 exception，再按 canonical 顺序重建目标；整个替换与 master
   * update 位于同一 Provider batch，任何 expectedCount 漂移都会整体失败，不能把旧例外静默遗留。
   */
  private fun appendOccurrenceReplacementOperations(
    operations: ArrayList<ContentProviderOperation>,
    preparedExceptions: List<AndroidPreparedOccurrenceExceptionWrite>,
    existingExceptions: List<AndroidManagedCalendarSnapshotOccurrenceException>,
    calendarId: Long,
    masterEventId: Long,
  ) {
    existingExceptions.forEach { existing ->
      val eventId = AndroidCalendarEventRefCodec.decodeOrNull(existing.platformEventRef)
        ?: throw CalendarProviderReadException("Invalid managed occurrence event reference")
      val deleteSelection = AndroidOccurrenceExceptionWritePlanner.replacementDeleteSelection(
        existing = existing,
        calendarId = calendarId,
        masterEventId = masterEventId,
        packageName = context.packageName,
      )
      operations += ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
        .withSelection("${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        .build()
      operations += ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
        .withSelection(deleteSelection.selection, deleteSelection.selectionArgs.toTypedArray())
        .withExpectedCount(deleteSelection.expectedCount)
        .build()
    }
    preparedExceptions.forEach { prepared ->
      val eventOperationIndex = operations.size
      operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
        .withValues(buildOccurrenceExceptionContentValues(prepared, calendarId))
        .withValue(CalendarContract.Events.ORIGINAL_ID, masterEventId)
        .build()
      prepared.projection.deviceReminderMinutes.forEach { minutes ->
        operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
          .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOperationIndex)
          .withValue(CalendarContract.Reminders.MINUTES, minutes)
          .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
          .build()
      }
    }
  }

  /** occurrence row 始终使用 canonical URI、原始实例时间和显式状态，且 RRULE/RDATE 保持 null。 */
  private fun buildOccurrenceExceptionContentValues(
    prepared: AndroidPreparedOccurrenceExceptionWrite,
    calendarId: Long,
  ): ContentValues {
    check(prepared.recurrenceRule == null && prepared.rDate == null) {
      "Occurrence exception RRULE/RDATE must remain unset"
    }
    val exception = prepared.projection
    val synthetic = CalendarEventProjection(
      id = exception.id,
      externalUri = exception.externalUri,
      title = exception.title,
      description = exception.description,
      timing = exception.timing,
      recurrenceRule = prepared.recurrenceRule,
      deviceReminderMinutes = exception.deviceReminderMinutes,
      fingerprint = exception.fingerprint,
    )
    return buildEventContentValues(synthetic, calendarId).apply {
      put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, prepared.originalInstanceTimeMillis)
      put(CalendarContract.Events.ORIGINAL_ALL_DAY, prepared.originalAllDay)
      put(CalendarContract.Events.STATUS, prepared.providerStatus)
      putNull(CalendarContract.Events.RRULE)
      putNull(CalendarContract.Events.RDATE)
    }
  }

  /** 核验事件确实属于当前受管日历、应用包名且 scope 匹配。 */
  private fun verifyEventOwnership(
    eventId: Long,
    expectedCalendarId: Long,
    expectedScope: CalendarExportScope,
    expectedProjectionId: CalendarProjectionId,
    ensureAuthorized: () -> Unit,
  ): Boolean {
    val projection = arrayOf(
      CalendarContract.Events.CALENDAR_ID,
      CalendarContract.Events.CUSTOM_APP_PACKAGE,
      CalendarContract.Events.CUSTOM_APP_URI,
    )
    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    // ownership 是更新/删除前的阻塞读取，必须在调用 Provider 前后都复核当前 lifecycle。
    ensureAuthorized()
    val cursor = context.contentResolver.query(eventUri, projection, null, null, null)
    // event 已不存在与调用期间被撤销必须区分：先复核再将 null 解释为未拥有；有 cursor 时 use 负责异常关闭。
    return if (cursor == null) {
      ensureAuthorized()
      false
    } else {
      cursor.use {
        ensureAuthorized()
        if (it.moveToFirst()) {
          val calendarId = cursor.getLong(0)
          val pkg = cursor.getString(1)
          val uri = cursor.getString(2)
          val projectionId = uri?.let(CalendarProjectionUriCodec::decodeOrNull)
          calendarId == expectedCalendarId &&
              pkg == context.packageName &&
              projectionId?.scope == expectedScope &&
              projectionId == expectedProjectionId
        } else {
          false
        }
      }
    }
  }

  /**
   * 将投影转换为 ContentValues；区分单次/重复、Timed/AllDay/Deadline。
   *
   * [calendarId] 仅供创建和 legacy W15 get-or-create 更新写入。finalized W16 传入 `null`，使已有 event 的
   * Calendar membership 不可被字段更新迁移；[customAppUri] 则允许该路径始终写回由 projection identity 编码的
   * canonical URI。RDATE 会增加独立 occurrence，而当前投影仅支持由 canonical RRULE 描述的 series master；
   * 创建和更新都无条件清空它，避免外部残留值在更新后继续改变回读语义。
   */
  private fun buildEventContentValues(
    projection: CalendarEventProjection,
    calendarId: Long?,
    customAppUri: String = projection.externalUri,
  ): ContentValues {
    return ContentValues().apply {
      calendarId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }
      put(CalendarContract.Events.TITLE, projection.title)
      put(CalendarContract.Events.DESCRIPTION, projection.description)
      put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
      put(CalendarContract.Events.CUSTOM_APP_URI, customAppUri)
      putNull(CalendarContract.Events.RDATE)

      when (val timing = projection.timing) {
        is CalendarTiming.Timed -> {
          val startMillis = resolveCalendarInstant(timing.start, timing.timeZoneId)
          put(CalendarContract.Events.DTSTART, startMillis)
          put(CalendarContract.Events.EVENT_TIMEZONE, timing.timeZoneId)
          put(CalendarContract.Events.ALL_DAY, 0)

          if (projection.recurrenceRule == null) {
            // 单次：使用 DTEND
            val endMillis = startMillis + timing.durationMinutes * 60_000L
            put(CalendarContract.Events.DTEND, endMillis)
            putNull(CalendarContract.Events.DURATION)
          } else {
            // 重复：使用 DURATION
            put(CalendarContract.Events.DURATION, formatDurationMinutes(timing.durationMinutes))
            putNull(CalendarContract.Events.DTEND)
          }
        }

        is CalendarTiming.AllDay -> {
          val startUtcMillis =
            timing.startDate.toLocalDate().atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
          put(CalendarContract.Events.DTSTART, startUtcMillis)
          put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.UTC.id)
          put(CalendarContract.Events.ALL_DAY, 1)

          if (projection.recurrenceRule == null) {
            val endUtcMillis = timing.startDate.plusDays(timing.durationDays).toLocalDate()
              .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            put(CalendarContract.Events.DTEND, endUtcMillis)
            putNull(CalendarContract.Events.DURATION)
          } else {
            put(CalendarContract.Events.DURATION, "P${timing.durationDays}D")
            putNull(CalendarContract.Events.DTEND)
          }
        }

        is CalendarTiming.Deadline -> {
          // Deadline 与 Timed 使用同一显式 DST resolver，不能依赖平台 gap/overlap 默认。
          val dueMillis = resolveCalendarInstant(timing.due, timing.timeZoneId)
          put(CalendarContract.Events.DTSTART, dueMillis)
          put(CalendarContract.Events.EVENT_TIMEZONE, timing.timeZoneId)
          put(CalendarContract.Events.ALL_DAY, 0)
          if (projection.recurrenceRule == null) {
            // Calendar Provider 允许 DTEND 与 DTSTART 相等，避免把时间点伪装成一分钟时间段。
            put(CalendarContract.Events.DTEND, dueMillis)
            putNull(CalendarContract.Events.DURATION)
          } else {
            // CalendarContract 要求重复事件使用 DURATION 而非 DTEND；零分钟保留时间点语义。
            putNull(CalendarContract.Events.DTEND)
            put(CalendarContract.Events.DURATION, formatDurationMinutes(0))
          }
        }
      }

      if (projection.recurrenceRule != null) {
        put(CalendarContract.Events.RRULE, projection.recurrenceRule)
      } else {
        putNull(CalendarContract.Events.RRULE)
      }
    }
  }

  /**
   * 使用 common DST resolver 产生唯一投影 instant。
   *
   * gap/overlap 按冻结策略处理；无法证明 transition 或 IANA 标识非法时 fail closed。历史秒级 offset 可由
   * CalendarContract epoch millis 无损表达，因此不会仅因 instant 不落整分钟而拒绝。
   */
  private fun resolveCalendarInstant(
    local: com.cyxbs.components.config.time.MinuteTimeDate,
    timeZoneId: String
  ): Long {
    return when (val resolved = ScheduleDstResolver.resolve(local, timeZoneId)) {
      is LocalDateTimeResolution.Resolved -> resolved.instant.toEpochMilliseconds()
      is LocalDateTimeResolution.InvalidTimeZone,
      is LocalDateTimeResolution.GapAdjustmentNotMinuteAligned,
      is LocalDateTimeResolution.TransitionNotResolved ->
        throw CalendarProviderReadException("Cannot resolve managed event wall time")
    }
  }

  /** 按 RFC 5545 输出分钟时长；分钟属于 time 部分，必须使用 `PT...M`。 */
  private fun formatDurationMinutes(minutes: Int): String = "PT${minutes}M"

  /** fixed-row Create 在任何 Events insert 前可稳定分型的阻断原因。 */
  enum class FixedCalendarCreateBlockedReason {
    INVALID_IDENTIFIER,
    CALENDAR_ROW_NOT_CURRENT,
  }

  /**
   * finalized Create 的预期 Calendar row 不再可写。
   *
   * 此异常只表示副作用前的 typed blocked；调用方不得捕获后回退到 legacy get-or-create 或改写其他 row。
   */
  class FixedCalendarCreateBlockedException(
    val reason: FixedCalendarCreateBlockedReason,
  ) : IllegalStateException("Fixed Calendar row Create blocked: $reason")

  /** 已验证为本应用托管事件，但 Provider 无法完整回读时终止本轮，避免重复创建或覆盖。 */
  open class CalendarProviderReadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)


  /**
   * 写入口的 Calendar 权限门禁。
   *
   * 快照读取使用采集器内同构检查；写操作仍在这里同步复核，避免权限在上一个 Provider 调用后撤销时继续发起副作用。
   */
  private fun requireCalendarPermissions() {
    if (
      context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
      context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
    ) {
      throw CalendarPermissionException()
    }
  }

  /** 日历权限缺失；只能由用户授权恢复，不参与进程内自动重试。 */
  class CalendarPermissionException :
    SecurityException("Calendar read/write permissions are required")
}

/**
 * 受管 Calendar 身份可信，但投射版本或其中的应用托管数据已无法按当前协议解释。
 *
 * 协调器只捕获这一分型执行一次整表重建；普通 Provider I/O、权限和生命周期异常不得转换为自动删除。
 */
internal class ManagedCalendarRebuildRequiredException(
  message: String,
  cause: Throwable? = null,
) : AndroidScheduleCalendarGateway.CalendarProviderReadException(message, cause)
