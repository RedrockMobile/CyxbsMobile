@file:OptIn(
  kotlinx.cinterop.ExperimentalForeignApi::class,
  kotlinx.cinterop.BetaInteropApi::class,
)

package com.cyxbs.pages.schedule.calendar

import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.EventKit.EKAlarm
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKAuthorizationStatusWriteOnly
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceDayOfWeek
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSource
import platform.EventKit.EKSourceType
import platform.EventKit.EKSpan
import platform.EventKit.EKWeekdayFriday
import platform.EventKit.EKWeekdayMonday
import platform.EventKit.EKWeekdaySaturday
import platform.EventKit.EKWeekdaySunday
import platform.EventKit.EKWeekdayThursday
import platform.EventKit.EKWeekdayTuesday
import platform.EventKit.EKWeekdayWednesday
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.numberWithInteger
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlin.coroutines.resume
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Instant

/** 首次 calendar + event 原子创建在 production bridge 内可观察的提交阶段。 */
internal enum class IosEventKitAtomicCreateStage {
  /** 尚未调用 `EKEventStore.commit`；configure/排队失败仍可证明没有进入提交边界。 */
  PRE_COMMIT,

  /** 已经开始调用 `commit`，其返回值、NSError 或异常都无法证明平台未落盘。 */
  COMMIT_ENTERED,

  /** `commit` 已明确成功，正在读取 identifier 与完整 source/calendar/event binding。 */
  POST_COMMIT_READBACK,
}

/**
 * 首次原子创建的纯阶段分类 seam。
 *
 * production bridge 在真正调用 `commit` 前推进到 [IosEventKitAtomicCreateStage.COMMIT_ENTERED]，并在明确成功后推进到
 * [IosEventKitAtomicCreateStage.POST_COMMIT_READBACK]。测试可直接驱动同一状态机而无需构造 `EKEventStore`；权限只影响
 * pre-commit 分类，不能把 commit-entered 或 post-commit 的未知终态降级成“可证明未提交”。
 */
internal class IosEventKitAtomicCreateFailureState {
  private var stage = IosEventKitAtomicCreateStage.PRE_COMMIT

  /** 仅可在即将调用 EventKit `commit` 时推进一次；提前推进会错误扩大 recovery eligibility。 */
  fun enterCommit() {
    check(stage == IosEventKitAtomicCreateStage.PRE_COMMIT)
    stage = IosEventKitAtomicCreateStage.COMMIT_ENTERED
  }

  /** 仅在 `commit` 已明确成功后推进；后续 identifier/readback 失败都属于提交终态未知。 */
  fun enterPostCommitReadback() {
    check(stage == IosEventKitAtomicCreateStage.COMMIT_ENTERED)
    stage = IosEventKitAtomicCreateStage.POST_COMMIT_READBACK
  }

  /**
   * 将当前阶段与最新权限事实收敛为 store failure。
   *
   * pre-commit 仍沿用普通 access 规则；进入 commit 后即使恰好观察到撤权，也不能由此推断提交没有发生。
   */
  fun failureFor(status: IosEventKitFullAccessStatus): IosEventKitStoreFailure = when (stage) {
    IosEventKitAtomicCreateStage.PRE_COMMIT ->
      if (status == IosEventKitFullAccessStatus.FULL_ACCESS) {
        IosEventKitStoreFailure.AMBIGUOUS
      } else {
        IosEventKitStoreFailure.ACCESS_LOST
      }

    IosEventKitAtomicCreateStage.COMMIT_ENTERED,
    IosEventKitAtomicCreateStage.POST_COMMIT_READBACK ->
      IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN
  }

  /** commit 仍可能留有本地待提交对象；post-commit readback 失败不需要再 reset 已成功提交的 store。 */
  fun shouldResetAfterException(): Boolean = stage != IosEventKitAtomicCreateStage.POST_COMMIT_READBACK
}

/**
 * 真实 EventKit bridge。
 *
 * 实例在 gateway 生命周期内只构造一个 [EKEventStore]。除 [requestFullAccess] 外所有方法都要求上层先通过
 * full-access 门禁；本类仍在平台调用失败时返回 typed failure，绝不依据 NSError 文本猜测“可安全重试”。
 */
internal class IosEventKitStoreBridge(
  private val eventStore: EKEventStore = EKEventStore(),
) : IosEventKitStorePort {
  override fun authorizationStatus(): IosEventKitFullAccessStatus =
    EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent).toGatewayStatus()

  /**
   * 唯一允许触发系统日历权限弹窗的入口。
   *
   * 部署目标为 iOS 15：iOS 17+ 请求 full access，旧系统调用 legacy event access（旧系统没有 write-only/full
   * access 区分）。completion 的 granted 仅作信号，最终以重新读取 authorization status 为准。等待系统弹窗
   * 期间若调用协程已取消，completion 只丢弃迟到结果，不能恢复已结束的设置页或账号会话。
   */
  override suspend fun requestFullAccess(): IosEventKitStoreResult<IosEventKitFullAccessStatus> =
    suspendCancellableCoroutine { continuation ->
      val completion: (Boolean, NSError?) -> Unit = { _, error ->
        val status = authorizationStatus()
        val result = if (error != null && status != IosEventKitFullAccessStatus.FULL_ACCESS) {
          IosEventKitStoreResult.Failure(IosEventKitStoreFailure.ACCESS_LOST)
        } else {
          IosEventKitStoreResult.Success(status)
        }
        // EventKit 没有可取消的系统授权请求；这里只阻止迟到 completion 恢复已取消 continuation。
        if (continuation.isActive) continuation.resume(result)
      }
      if (operatingSystemMajorVersion() >= IOS_17_MAJOR_VERSION) {
        eventStore.requestFullAccessToEventsWithCompletion(completion)
      } else {
        eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, completion)
      }
    }

  override fun sources(): IosEventKitStoreResult<List<IosEventKitSourceSnapshot>> = accessChecked {
    eventStore.refreshSourcesIfNecessary()
    IosEventKitStoreResult.Success(
      eventStore.sources.mapNotNull { value ->
        val source = value as? EKSource ?: return@mapNotNull null
        IosEventKitSourceSnapshot(
          identifier = source.sourceIdentifier,
          supportsEvents = source.sourceType != EKSourceType.EKSourceTypeSubscribed &&
              source.sourceType != EKSourceType.EKSourceTypeBirthdays,
          // 仅用于设置 picker 展示；gateway 仍只以 opaque identifier 做精确检查。
          displayName = source.title ?: source.sourceIdentifier,
        )
      },
    )
  }

  override fun calendars(): IosEventKitStoreResult<List<IosEventKitCalendarSnapshot>> =
    accessChecked {
      IosEventKitStoreResult.Success(
        eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeEvent).mapNotNull { value ->
          (value as? EKCalendar)?.toSnapshot()
        },
      )
    }

  override fun eventByIdentifier(
    identifier: String,
  ): IosEventKitStoreResult<IosEventKitStoreEventSnapshot?> = accessChecked {
    val event = eventStore.eventWithIdentifier(identifier)
    if (event == null) {
      IosEventKitStoreResult.Success(null)
    } else {
      event.toStoreSnapshot()?.let { snapshot -> IosEventKitStoreResult.Success(snapshot) }
        ?: IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
    }
  }

  /**
   * 在调用方给出的受限窗口内扫描 calendar，以恢复失效 identifier。
   *
   * EventKit 单个 predicate 最多覆盖四年；窗口由 gateway 围绕目标投影生成并在值对象中校验，禁止再传
   * `distantPast..distantFuture` 被系统静默截断。扫描只读，因此即使 calendar 已变成不可写，也必须继续暴露
   * canonical 身份给 gateway 阻止重复创建。周期事件可能按 occurrence 返回，按 calendarItemIdentifier 合并并
   * 保留最早 occurrence，后续 spanFuture 操作才从系列起点覆盖整个系列。
   */
  override fun events(
    calendarIdentifier: String,
    window: IosEventKitScanWindow,
  ): IosEventKitStoreResult<List<IosEventKitStoreEventSnapshot>> = accessChecked {
    val calendar = eventStore.calendarWithIdentifier(calendarIdentifier)
      ?: return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
    val predicate = eventStore.predicateForEventsWithStartDate(
      startDate = NSDate.dateWithTimeIntervalSince1970(window.startEpochSeconds.toDouble()),
      endDate = NSDate.dateWithTimeIntervalSince1970(window.endEpochSeconds.toDouble()),
      calendars = listOf(calendar),
    )
    val events = eventStore.eventsMatchingPredicate(predicate)
      .mapNotNull { it as? EKEvent }
      // 普通用户事件与 gateway 所有权无关；只提取可严格解析的 Schedule canonical URL，避免无关复杂 RRULE 阻断扫描。
      .filter { event ->
        event.URL?.absoluteString?.let(CalendarProjectionUriCodec::decodeOrNull) != null
      }
      .groupBy { it.calendarItemIdentifier }
      .values
      .mapNotNull { occurrences ->
        occurrences.minByOrNull {
          it.startDate?.timeIntervalSince1970 ?: Double.MAX_VALUE
        }
      }
      .map { event ->
        event.toStoreSnapshot()
          ?: return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      }
    IosEventKitStoreResult.Success(events)
  }

  /**
   * 将新隔离 calendar 与首个 canonical event 放入同一 EventKit 待提交队列并一次 commit。
   *
   * configure 或任一 `commit=false` 排队失败仍处于 pre-commit：best-effort reset 后按普通 AMBIGUOUS/access
   * 规则返回，绝不建立 commit-unknown eligibility。只有真正进入 `commit` 后的 false、NSError、异常，以及明确提交成功后
   * 的 identifier/readback/binding 异常，才返回 `ATOMIC_COMMIT_OUTCOME_UNKNOWN`。普通 CRUD 继续使用共享
   * [commitStore]，不会获得这条首次原子创建专属语义。
   */
  override fun createCalendarWithEvent(
    sourceIdentifier: String,
    displayTitle: String,
    payload: IosEventKitWritePayload,
  ): IosEventKitStoreResult<IosEventKitCreatedEventSnapshot> {
    val failureState = IosEventKitAtomicCreateFailureState()
    return try {
      if (authorizationStatus() != IosEventKitFullAccessStatus.FULL_ACCESS) {
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.ACCESS_LOST)
      }
      val source = eventStore.sourceWithIdentifier(sourceIdentifier)
        ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      if (source.sourceType == EKSourceType.EKSourceTypeSubscribed ||
        source.sourceType == EKSourceType.EKSourceTypeBirthdays
      ) {
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      }
      val calendar =
        EKCalendar.calendarForEntityType(EKEntityType.EKEntityTypeEvent, eventStore).apply {
          title = displayTitle
          this.source = source
        }
      val event = EKEvent.eventWithEventStore(eventStore)
      if (configureEvent(event, calendar, payload) is IosEventKitStoreResult.Failure) {
        return failAtomicCreate(failureState, reset = true)
      }
      val span =
        if (payload.recurrenceRule == null) EKSpan.EKSpanThisEvent else EKSpan.EKSpanFutureEvents
      val committed = saveCalendarAndEventAndCommit(calendar, event, span, failureState)
      if (committed is IosEventKitStoreResult.Failure) return committed

      val calendarIdentifier = calendar.calendarIdentifier.takeIf { it.isNotBlank() }
        ?: return failAtomicCreate(failureState, reset = false)
      val eventIdentifier = event.eventIdentifier?.takeIf { it.isNotBlank() }
        ?: return failAtomicCreate(failureState, reset = false)
      val readBackCalendar = eventStore.calendarWithIdentifier(calendarIdentifier)
        ?: return failAtomicCreate(failureState, reset = false)
      val readBackEvent = eventStore.eventWithIdentifier(eventIdentifier)
        ?: return failAtomicCreate(failureState, reset = false)
      if (!readBackCalendar.allowsContentModifications ||
        readBackCalendar.calendarIdentifier != calendarIdentifier ||
        readBackCalendar.source?.sourceIdentifier != sourceIdentifier ||
        readBackEvent.eventIdentifier != eventIdentifier ||
        readBackEvent.calendar?.calendarIdentifier != calendarIdentifier ||
        readBackEvent.URL?.absoluteString != payload.externalUri
      ) {
        return failAtomicCreate(failureState, reset = false)
      }
      IosEventKitStoreResult.Success(
        IosEventKitCreatedEventSnapshot(
          calendar = readBackCalendar.toSnapshot(),
          eventIdentifier = eventIdentifier,
        ),
      )
    } catch (_: Throwable) {
      failAtomicCreate(failureState, reset = failureState.shouldResetAfterException())
    }
  }

  /**
   * 创建/更新事件并显式 commit。
   *
   * 更新目标在读后写之间消失时不退化为 create，避免模糊失败后重复事件。RRULE、alarm、URL 和时间字段先完全
   * 替换，再用 series 起点的 spanFuture 提交；非周期事件使用 spanThisEvent。
   */
  override fun saveEvent(
    calendarIdentifier: String,
    existingEventIdentifier: String?,
    payload: IosEventKitWritePayload,
  ): IosEventKitStoreResult<String> = accessChecked {
    val calendar = eventStore.calendarWithIdentifier(calendarIdentifier)
      ?: return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
    if (!calendar.allowsContentModifications) {
      return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
    }
    val event = if (existingEventIdentifier == null) {
      EKEvent.eventWithEventStore(eventStore)
    } else {
      eventStore.eventWithIdentifier(existingEventIdentifier)
        ?: return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
    }
    if (existingEventIdentifier != null && event.calendar?.calendarIdentifier != calendarIdentifier) {
      return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
    }
    val configured = configureEvent(event, calendar, payload)
    if (configured is IosEventKitStoreResult.Failure) return@accessChecked configured
    val span =
      if (payload.recurrenceRule == null) EKSpan.EKSpanThisEvent else EKSpan.EKSpanFutureEvents
    val saved = saveEventAndCommit(event, span)
    if (saved is IosEventKitStoreResult.Failure) return@accessChecked saved
    event.eventIdentifier?.takeIf { it.isNotBlank() }
      ?.let { identifier -> IosEventKitStoreResult.Success(identifier) }
      ?: IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
  }

  /** 删除目标消失或 commit 结果不确定都返回失败，由 gateway canonical 重查决定后续状态。 */
  override fun removeEvent(eventIdentifier: String): IosEventKitStoreResult<Unit> = accessChecked {
    val event = eventStore.eventWithIdentifier(eventIdentifier)
      ?: return@accessChecked IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
    val span = if (event.hasRecurrenceRules) EKSpan.EKSpanFutureEvents else EKSpan.EKSpanThisEvent
    removeEventAndCommit(event, span)
  }

  /** 写入前完整替换 foundation 已验证的字段；任一平台对象无法构造都 fail-closed。 */
  private fun configureEvent(
    event: EKEvent,
    calendar: EKCalendar,
    payload: IosEventKitWritePayload,
  ): IosEventKitStoreResult<Unit> {
    val url = NSURL.URLWithString(payload.externalUri)
      ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
    event.calendar = calendar
    event.URL = url
    event.title = payload.title
    event.notes = payload.notes
    when (val timing = payload.timing) {
      is IosEventKitWriteTiming.Timed -> {
        val zone = NSTimeZone.timeZoneWithName(timing.timeZoneId)
          ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
        event.allDay = false
        event.startDate = timing.start.toNSDate()
        event.endDate = timing.endExclusive.toNSDate()
        event.timeZone = zone
      }

      is IosEventKitWriteTiming.AllDay -> {
        event.allDay = true
        event.startDate = timing.startDate.toUtcNSDate()
        event.endDate = timing.endExclusiveDate.toUtcNSDate()
        event.timeZone = null
      }
    }
    event.alarms.orEmpty().filterIsInstance<EKAlarm>().forEach(event::removeAlarm)
    payload.alarms.forEach { alarm ->
      event.addAlarm(EKAlarm.alarmWithRelativeOffset(alarm.relativeOffsetSeconds.toDouble()))
    }
    event.recurrenceRules.orEmpty().filterIsInstance<EKRecurrenceRule>()
      .forEach(event::removeRecurrenceRule)
    payload.recurrenceRule?.let { rule ->
      val eventKitRule = recurrenceRuleOrNull(rule, event.allDay)
        ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      event.addRecurrenceRule(eventKitRule)
    }
    return IosEventKitStoreResult.Success(Unit)
  }

  /** 将 EKEvent 原始字段无修复地提取给 foundation；桥接层不自行 trim、round 或认领身份。 */
  private fun EKEvent.toStoreSnapshot(): IosEventKitStoreEventSnapshot? {
    val calendarId = calendar?.calendarIdentifier ?: return null
    val start = startDate?.toRawMoment() ?: return null
    val end = endDate?.toRawMoment() ?: return null
    val rawRules = recurrenceRules.orEmpty().map { value ->
      val rule = value as? EKRecurrenceRule ?: return null
      // foundation 负责把不可逆规则分型为 UNSUPPORTED_RECURRENCE；bridge 不因无关规则让整个 calendar 扫描失败。
      rule.toCanonicalRRuleOrNull(allDay) ?: UNSUPPORTED_RECURRENCE_SENTINEL
    }
    val rawAlarms = alarms.orEmpty().map { value ->
      val alarm = value as? EKAlarm ?: return null
      IosEventKitRawAlarm(
        relativeOffsetSeconds = if (alarm.absoluteDate != null || alarm.structuredLocation != null) {
          null
        } else {
          alarm.relativeOffset
        },
      )
    }
    return IosEventKitStoreEventSnapshot(
      calendarIdentifier = calendarId,
      raw = IosEventKitRawEvent(
        eventIdentifier = eventIdentifier,
        externalUri = URL?.absoluteString,
        title = title,
        notes = notes,
        start = start,
        endExclusive = end,
        timeZoneId = timeZone?.name,
        allDay = allDay,
        recurrenceRules = rawRules,
        alarms = rawAlarms,
        hasOccurrenceException = isDetached,
      ),
    )
  }

  /**
   * 首次创建时先分别排队 calendar/event，随后只执行一次 commit。
   *
   * 任一排队步骤失败仍处于 pre-commit，reset 后只返回普通 AMBIGUOUS/access failure。只有 calendar/event 都已成功
   * 排队，且在真正调用 `eventStore.commit` 的前一刻推进 [failureState]，后续 false、NSError 或异常才具备
   * commit-outcome-unknown provenance。commit 明确成功后立即进入 post-commit readback，后续 identifier/binding 失败同样
   * 不能降级为 pre-commit AMBIGUOUS。
   */
  private fun saveCalendarAndEventAndCommit(
    calendar: EKCalendar,
    event: EKEvent,
    span: platform.EventKit.EKSpan,
    failureState: IosEventKitAtomicCreateFailureState,
  ): IosEventKitStoreResult<Unit> = memScoped {
    val calendarError = alloc<ObjCObjectVar<NSError?>>()
    calendarError.value = null
    val calendarSaved = eventStore.saveCalendar(calendar, commit = false, error = calendarError.ptr)
    if (!calendarSaved || calendarError.value != null) {
      return@memScoped failAtomicCreate(failureState, reset = true)
    }

    val eventError = alloc<ObjCObjectVar<NSError?>>()
    eventError.value = null
    val eventSaved =
      eventStore.saveEvent(event, span = span, commit = false, error = eventError.ptr)
    if (!eventSaved || eventError.value != null) {
      return@memScoped failAtomicCreate(failureState, reset = true)
    }

    val commitError = alloc<ObjCObjectVar<NSError?>>()
    commitError.value = null
    // 只有两项 queue 都成功后、紧邻真实 commit 调用才进入 COMMIT_ENTERED，避免 pre-commit failure 错获资格。
    failureState.enterCommit()
    val committed = eventStore.commit(commitError.ptr)
    if (!committed || commitError.value != null) {
      return@memScoped failAtomicCreate(failureState, reset = true)
    }
    failureState.enterPostCommitReadback()
    IosEventKitStoreResult.Success(Unit)
  }

  /** 保存 event 后显式 commit；不使用隐式单步提交，便于统一识别提交阶段的不确定失败。 */
  private fun saveEventAndCommit(
    event: EKEvent,
    span: platform.EventKit.EKSpan,
  ): IosEventKitStoreResult<Unit> = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    val saved = eventStore.saveEvent(event, span = span, commit = false, error = error.ptr)
    if (!saved || error.value != null) {
      return@memScoped resetAfterQueuedMutationFailure()
    }
    commitStore()
  }

  /** 删除也先排队后 commit；任何中间状态都不向 gateway 宣称成功。 */
  private fun removeEventAndCommit(
    event: EKEvent,
    span: platform.EventKit.EKSpan,
  ): IosEventKitStoreResult<Unit> = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    val removed = eventStore.removeEvent(event, span = span, commit = false, error = error.ptr)
    if (!removed || error.value != null) {
      return@memScoped resetAfterQueuedMutationFailure()
    }
    commitStore()
  }

  private fun commitStore(): IosEventKitStoreResult<Unit> = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    val committed = eventStore.commit(error.ptr)
    if (!committed || error.value != null) {
      resetAfterQueuedMutationFailure()
    } else {
      IosEventKitStoreResult.Success(Unit)
    }
  }

  /**
   * 首次原子创建失败的唯一阶段感知出口。
   *
   * reset 只清理当前 `EKEventStore` 的本地待提交队列，不能证明已经进入 commit 的 pair 未落盘；因此失败类型必须先由
   * [IosEventKitAtomicCreateFailureState] 决定，不能因 reset 成功或随后观察到撤权而降级。
   */
  private fun failAtomicCreate(
    failureState: IosEventKitAtomicCreateFailureState,
    reset: Boolean,
  ): IosEventKitStoreResult.Failure {
    // 授权状态读取本身也可能因平台异常失败；pre-commit 按无 full access 收敛，commit-entered 仍由阶段锁定为 unknown。
    val status = runCatching { authorizationStatus() }
      .getOrDefault(IosEventKitFullAccessStatus.UNKNOWN)
    val reason = failureState.failureFor(status)
    if (reset) runCatching { eventStore.reset() }
    return IosEventKitStoreResult.Failure(reason)
  }

  /**
   * save/remove 已进入 `commit=false` 队列却未得到确定终态时，先 reset 丢弃本地待提交对象。
   *
   * reset 不能回滚可能已经落盘的 commit，因此上层仍返回 AMBIGUOUS 并依赖 canonical 重查；它只防止下一次
   * 无关操作把上一次残留队列一起提交。撤权时 reset 本身也可能失败，故该清理必须 best-effort。
   */
  private fun resetAfterQueuedMutationFailure(): IosEventKitStoreResult.Failure {
    val reason = storeFailureForCurrentAccess()
    runCatching { eventStore.reset() }
    return IosEventKitStoreResult.Failure(reason)
  }

  /** 每个 store 入口同时做底层防线，避免未来误绕 gateway 直接访问真实日历。 */
  private inline fun <T> accessChecked(
    block: () -> IosEventKitStoreResult<T>,
  ): IosEventKitStoreResult<T> =
    if (authorizationStatus() == IosEventKitFullAccessStatus.FULL_ACCESS) {
      runCatching(block).getOrElse {
        IosEventKitStoreResult.Failure(storeFailureForCurrentAccess())
      }
    } else {
      IosEventKitStoreResult.Failure(IosEventKitStoreFailure.ACCESS_LOST)
    }

  private fun storeFailureForCurrentAccess(): IosEventKitStoreFailure =
    if (authorizationStatus() == IosEventKitFullAccessStatus.FULL_ACCESS) {
      IosEventKitStoreFailure.AMBIGUOUS
    } else {
      IosEventKitStoreFailure.ACCESS_LOST
    }

  private fun EKCalendar.toSnapshot(): IosEventKitCalendarSnapshot = IosEventKitCalendarSnapshot(
    identifier = calendarIdentifier,
    sourceIdentifier = source?.sourceIdentifier.orEmpty(),
    allowsContentModifications = allowsContentModifications && source != null,
  )

  private fun Long.toGatewayStatus(): IosEventKitFullAccessStatus = when (this) {
    EKAuthorizationStatusNotDetermined -> IosEventKitFullAccessStatus.NOT_DETERMINED
    EKAuthorizationStatusRestricted -> IosEventKitFullAccessStatus.RESTRICTED
    EKAuthorizationStatusDenied -> IosEventKitFullAccessStatus.DENIED
    EKAuthorizationStatusFullAccess -> IosEventKitFullAccessStatus.FULL_ACCESS
    EKAuthorizationStatusWriteOnly -> IosEventKitFullAccessStatus.WRITE_ONLY
    else -> IosEventKitFullAccessStatus.UNKNOWN
  }

  /** 用 CValue.useContents 读取系统主版本，避免依赖仅在 iOS 17 存在的辅助符号。 */
  private fun operatingSystemMajorVersion(): Long =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }

  /** NSDate 使用 Double 秒；保留其可表达的小数为纳秒，不先截断为整秒。 */
  private fun NSDate.toRawMoment(): IosEventKitRawMoment {
    val value = timeIntervalSince1970
    val seconds = floor(value).toLong()
    var nanos = ((value - seconds.toDouble()) * NANOS_PER_SECOND).roundToLong()
    var normalizedSeconds = seconds
    if (nanos == NANOS_PER_SECOND.toLong()) {
      normalizedSeconds += 1
      nanos = 0
    }
    return IosEventKitRawMoment(normalizedSeconds, nanos.toInt())
  }

  private fun IosEventKitRawMoment.toNSDate(): NSDate =
    NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble() + nanoseconds.toDouble() / NANOS_PER_SECOND)

  private fun com.cyxbs.components.config.time.Date.toUtcNSDate(): NSDate {
    val instant = LocalDate(year, monthNumber, dayOfMonth).atStartOfDayIn(TimeZone.UTC)
    return NSDate.dateWithTimeIntervalSince1970(
      instant.epochSeconds.toDouble() + instant.nanosecondsOfSecond.toDouble() / NANOS_PER_SECOND,
    )
  }

  /** foundation 已输出 canonical RRULE；这里仅把受限字段逐项映射为 EventKit 对象。 */
  private fun recurrenceRuleOrNull(
    canonical: String,
    allDay: Boolean,
  ): EKRecurrenceRule? {
    val fields = parseRRuleFields(canonical) ?: return null
    val frequency = when (fields["FREQ"]) {
      "DAILY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyDaily
      "WEEKLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly
      "MONTHLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly
      "YEARLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyYearly
      else -> return null
    }
    val interval = fields["INTERVAL"]?.toLongOrNull() ?: 1L
    if (interval <= 0) return null
    val daysOfWeek = fields["BYDAY"]?.split(',')?.map { day ->
      EKRecurrenceDayOfWeek(day.toEventKitWeekdayOrNull() ?: return null, 0)
    }
    val daysOfMonth = fields["BYMONTHDAY"]?.toNumberListOrNull()
      ?: if ("BYMONTHDAY" in fields) return null else null
    val monthsOfYear =
      fields["BYMONTH"]?.toNumberListOrNull() ?: if ("BYMONTH" in fields) return null else null
    val end = when {
      "COUNT" in fields -> fields.getValue("COUNT").toULongOrNull()?.takeIf { it > 0u }
        ?.let { count -> EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(count) }
        ?: return null

      "UNTIL" in fields -> parseUntil(fields.getValue("UNTIL"), allDay)
        ?.let { date -> EKRecurrenceEnd.recurrenceEndWithEndDate(date) }
        ?: return null

      else -> null
    }
    return EKRecurrenceRule(
      recurrenceWithFrequency = frequency,
      interval = interval,
      daysOfTheWeek = daysOfWeek,
      daysOfTheMonth = daysOfMonth,
      monthsOfTheYear = monthsOfYear,
      weeksOfTheYear = null,
      daysOfTheYear = null,
      setPositions = null,
      end = end,
    )
  }

  /** 将 EventKit recurrence 还原成 foundation 可严格校验的 RFC 5545 受限文本。 */
  private fun EKRecurrenceRule.toCanonicalRRuleOrNull(allDay: Boolean): String? {
    if (!weeksOfTheYear.isNullOrEmpty() || !daysOfTheYear.isNullOrEmpty() || !setPositions.isNullOrEmpty()) return null
    val frequencyText = when (frequency) {
      EKRecurrenceFrequency.EKRecurrenceFrequencyDaily -> "DAILY"
      EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly -> "WEEKLY"
      EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly -> "MONTHLY"
      EKRecurrenceFrequency.EKRecurrenceFrequencyYearly -> "YEARLY"
      else -> return null
    }
    if (interval <= 0) return null
    val byDay = daysOfTheWeek.orEmpty().map { value ->
      val day = value as? EKRecurrenceDayOfWeek ?: return null
      if (day.weekNumber != 0L) return null
      day.dayOfTheWeek.toRfcWeekdayOrNull() ?: return null
    }
    val byMonthDay = daysOfTheMonth.toIntListOrNull() ?: return null
    val byMonth = monthsOfTheYear.toIntListOrNull() ?: return null
    val endField = recurrenceEnd?.let { end ->
      when {
        end.occurrenceCount > 0u -> "COUNT=${end.occurrenceCount}"
        end.endDate != null -> "UNTIL=${end.endDate!!.formatUntil(allDay)}"
        else -> return null
      }
    }
    return buildList {
      add("FREQ=$frequencyText")
      if (interval != 1L) add("INTERVAL=$interval")
      if (byDay.isNotEmpty()) add("BYDAY=${byDay.joinToString(",")}")
      if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=${byMonthDay.sorted().joinToString(",")}")
      if (byMonth.isNotEmpty()) add("BYMONTH=${byMonth.sorted().joinToString(",")}")
      if (endField != null) add(endField)
    }.joinToString(";")
  }

  private fun parseRRuleFields(value: String): Map<String, String>? {
    val fields = linkedMapOf<String, String>()
    value.split(';').forEach { part ->
      val index = part.indexOf('=')
      if (index <= 0 || index == part.lastIndex) return null
      val key = part.substring(0, index)
      val fieldValue = part.substring(index + 1)
      if (fields.put(key, fieldValue) != null) return null
    }
    return fields
  }

  private fun String.toNumberListOrNull(): List<NSNumber>? = split(',').map { token ->
    val value = token.toLongOrNull() ?: return null
    NSNumber.numberWithInteger(value)
  }

  private fun List<*>?.toIntListOrNull(): List<Int>? = orEmpty().map { value ->
    val number = value as? NSNumber ?: return null
    val long = number.longLongValue
    if (long !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
    long.toInt()
  }

  private fun String.toEventKitWeekdayOrNull(): Long? = when (this) {
    "MO" -> EKWeekdayMonday
    "TU" -> EKWeekdayTuesday
    "WE" -> EKWeekdayWednesday
    "TH" -> EKWeekdayThursday
    "FR" -> EKWeekdayFriday
    "SA" -> EKWeekdaySaturday
    "SU" -> EKWeekdaySunday
    else -> null
  }

  private fun Long.toRfcWeekdayOrNull(): String? = when (this) {
    EKWeekdayMonday -> "MO"
    EKWeekdayTuesday -> "TU"
    EKWeekdayWednesday -> "WE"
    EKWeekdayThursday -> "TH"
    EKWeekdayFriday -> "FR"
    EKWeekdaySaturday -> "SA"
    EKWeekdaySunday -> "SU"
    else -> null
  }

  private fun parseUntil(value: String, allDay: Boolean): NSDate? = runCatching {
    val instant = if (allDay) {
      if (value.length != 8) return null
      LocalDate(
        value.substring(0, 4).toInt(),
        value.substring(4, 6).toInt(),
        value.substring(6, 8).toInt(),
      ).atStartOfDayIn(TimeZone.UTC)
    } else {
      if (value.length != 16 || value[8] != 'T' || value.last() != 'Z') return null
      LocalDateTime(
        year = value.substring(0, 4).toInt(),
        month = value.substring(4, 6).toInt(),
        day = value.substring(6, 8).toInt(),
        hour = value.substring(9, 11).toInt(),
        minute = value.substring(11, 13).toInt(),
        second = value.substring(13, 15).toInt(),
      ).toInstant(TimeZone.UTC)
    }
    NSDate.dateWithTimeIntervalSince1970(instant.epochSeconds.toDouble())
  }.getOrNull()

  private fun NSDate.formatUntil(allDay: Boolean): String {
    val instant =
      Instant.fromEpochSeconds(toRawMoment().epochSeconds, toRawMoment().nanoseconds.toLong())
    val utc = instant.toLocalDateTime(TimeZone.UTC)
    return buildString {
      append(utc.year.toString().padStart(4, '0'))
      append(utc.month.number.toString().padStart(2, '0'))
      append(utc.day.toString().padStart(2, '0'))
      if (!allDay) {
        append('T')
        append(utc.hour.toString().padStart(2, '0'))
        append(utc.minute.toString().padStart(2, '0'))
        append(utc.second.toString().padStart(2, '0'))
        append('Z')
      }
    }
  }

  private companion object {
    const val IOS_17_MAJOR_VERSION = 17L
    const val NANOS_PER_SECOND = 1_000_000_000.0
    const val UNSUPPORTED_RECURRENCE_SENTINEL = "UNSUPPORTED_EVENTKIT_RECURRENCE"
  }
}
