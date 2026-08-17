package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * EventKit gateway 的 fake 合同测试。
 *
 * 所有 fixture 只存在内存中，不构造 `EKEventStore`、不请求系统权限，也不读取或修改模拟器/真机日历。
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
class IosEventKitFullAccessGatewayTest {
  /** 构造 gateway 不得请求权限；只有显式方法才调用 store 的 full-access 请求。 */
  @Test
  fun permissionIsRequestedOnlyByExplicitGatewayCall() = runTest {
    val store = FakeEventKitStore(status = IosEventKitFullAccessStatus.NOT_DETERMINED)
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    assertEquals(0, store.permissionRequestCount)
    store.requestResult = IosEventKitFullAccessStatus.FULL_ACCESS
    assertIs<IosEventKitPermissionResult.Granted>(gateway.requestFullAccess())
    assertEquals(1, store.permissionRequestCount)
    assertEquals(IosEventKitFullAccessStatus.FULL_ACCESS, store.status)
  }

  /** 设置页/账号会话取消后，显式授权请求必须随 owner 取消，不能等待迟到平台 completion 恢复旧逻辑。 */
  @Test
  fun cancelledPermissionRequestDoesNotResumeOwner() = runTest {
    val store = FakeEventKitStore(IosEventKitFullAccessStatus.NOT_DETERMINED).apply {
      suspendPermissionRequest = true
    }
    val job = launch {
      IosEventKitFullAccessGateway(SCOPE, store).requestFullAccess()
    }

    runCurrent()
    assertEquals(1, store.permissionRequestCount)
    job.cancel()
    job.join()
    assertTrue(job.isCancelled)
  }


  /** 首次创建后必须 canonical 回读；第二次相同投影为 NoOp，不重复 save。 */
  @Test
  fun firstUpsertCreatesIsolatedCalendarAndSecondUpsertIsIdempotent() {
    val store = authorizedStore()
    val target = projection(reminders = listOf(0, 15))
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    val first = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertTrue(first.changed)
    assertTrue(first.atomicCalendarAndFirstEvent)
    assertTrue(first.locatorRecoveryProof != null)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    assertEquals(target.externalUri, store.lastPayload?.externalUri)
    assertEquals(
      listOf(IosEventKitRelativeAlarm(0), IosEventKitRelativeAlarm(-900)),
      store.lastPayload?.alarms,
    )

    val second = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(
        target,
        IosEventKitIdentifierHints(
          sourceIdentifier = SOURCE_ID,
          calendarIdentifier = first.binding.calendarIdentifier,
          eventIdentifier = first.binding.eventIdentifier,
        ),
      ),
    )
    assertEquals(false, second.changed)
    assertEquals(false, second.atomicCalendarAndFirstEvent)
    val secondProof = requireNotNull(second.locatorRecoveryProof)
    assertEquals(first.binding, second.binding)
    assertEquals(
      IosEventKitLocatorAcknowledgement.ACKNOWLEDGED,
      gateway.acknowledgeLocatorPersistence(target, second.binding, secondProof),
    )
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
  }

  /**
   * 两个 gateway 同时读到 Absent 时，首个 atomic pair commit 后必须重新检查 source/scope；若并发方也创建了
   * canonical calendar，不能只确认自己的 event 后报告 Upserted。
   */
  @Test
  fun concurrentCreatorAfterAtomicFirstPairFailsAmbiguousCalendar() {
    val store = authorizedStore().apply { createConcurrentCanonicalCalendar = true }
    val target = projection()

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR, result.reason)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
  }


  /** canonical 回读必须使用覆盖目标时刻且不超过 EventKit 四年上限的窗口，不能从 distantPast 被截断。 */
  @Test
  fun canonicalScanUsesBoundedWindowAroundProjection() {
    val store = authorizedStore()
    val target = projection()

    assertIs<IosEventKitGatewayResult.Upserted>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    val anchor =
      assertIs<IosEventKitWriteTiming.Timed>(store.lastPayload?.timing).start.epochSeconds
    assertTrue(store.scanWindows.isNotEmpty())
    assertTrue(store.scanWindows.any { window ->
      anchor in window.startEpochSeconds..<window.endEpochSeconds
    })
    store.scanWindows.forEach { window ->
      assertTrue(
        window.endEpochSeconds - window.startEpochSeconds <=
            IosEventKitScanWindow.MAX_EVENTKIT_SCAN_SECONDS,
      )
    }
  }

  /**
   * 当前恢复窗口必须补充目标投影窗口：较早的同 scope canonical event 即使超出目标窗口，也必须认领其 calendar，
   * 不能重复创建隔离 calendar。
   */
  @Test
  fun historicalCanonicalScopeEventOutsideTargetWindowPreventsDuplicateCalendar() {
    val historical = projection(
      scheduleId = SECOND_SCHEDULE_ID,
      start = MinuteTimeDate(2026, 8, 1, 9, 30),
    )
    val target = projection(start = MinuteTimeDate(2030, 8, 1, 9, 30))
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(historical, EVENT_ID))
    }

    val result = assertIs<IosEventKitGatewayResult.Upserted>(
      IosEventKitFullAccessGateway(
        scope = SCOPE,
        store = store,
        scopeRecoveryAnchorEpochSeconds = AUTHORITY_ANCHOR_EPOCH_SECONDS,
      ).upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(CALENDAR_ID, result.binding.calendarIdentifier)
    assertTrue(result.changed)
    assertEquals(false, result.atomicCalendarAndFirstEvent)
    assertEquals(null, result.locatorRecoveryProof)
    assertEquals(0, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    assertTrue(store.scanWindows.any { window ->
      rawFor(
        historical,
        EVENT_ID
      ).start.epochSeconds in window.startEpochSeconds..<window.endEpochSeconds
    })
  }

  /**
   * 两个有限 EventKit predicate 都无法证明 selected source 的既有 calendar 从未承载过当前 scope 时，首次创建必须
   * 停在歧义，不能把不完整历史当作 Absent。
   */
  @Test
  fun historicallyUnprovableSelectedCalendarBlocksCreation() {
    val store = authorizedStore().apply { addCalendar(CALENDAR_ID) }

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(
        scope = SCOPE,
        store = store,
        scopeRecoveryAnchorEpochSeconds = AUTHORITY_ANCHOR_EPOCH_SECONDS,
      ).upsert(projection(), IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR, result.reason)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }


  /** 受限 RRULE 必须经 foundation payload 进入 store，并在 canonical 回读后保持同一 fingerprint。 */
  @Test
  fun recurrenceUrlAndAlarmProjectionRoundTripThroughGatewayContract() {
    val recurrence = "FREQ=WEEKLY;BYDAY=MO,WE;COUNT=3"
    val target = projection(
      kind = CalendarProjectionKind.SERIES_MASTER,
      recurrence = recurrence,
      reminders = listOf(10),
    )
    val store = authorizedStore()
    val result = assertIs<IosEventKitGatewayResult.Upserted>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertTrue(result.changed)
    assertEquals(target.externalUri, store.lastPayload?.externalUri)
    assertEquals(recurrence, store.lastPayload?.recurrenceRule)
    assertEquals(listOf(IosEventKitRelativeAlarm(-600)), store.lastPayload?.alarms)
  }


  /** event/calendar identifier miss 时通过 canonical URL + scope 恢复，不按 title 创建重复日历。 */
  @Test
  fun staleIdentifiersRecoverThroughCanonicalScan() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, "fresh-event-id", rawFor(target, "fresh-event-id"))
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    val result = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(
        target,
        IosEventKitIdentifierHints(
          sourceIdentifier = SOURCE_ID,
          calendarIdentifier = "stale-calendar-id",
          eventIdentifier = "stale-event-id",
        ),
      ),
    )

    assertEquals(false, result.changed)
    assertEquals(CALENDAR_ID, result.binding.calendarIdentifier)
    assertEquals("fresh-event-id", result.binding.eventIdentifier)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }

  /** 普通 non-atomic fixture 即使 canonical 字段完全一致，也没有 ambiguous atomic attempt 资格，不能签发 recovery proof。 */
  @Test
  fun ordinaryNonAtomicExactEventDoesNotProduceLocatorRecoveryProof() {
    val target = projection(scheduleId = ORDINARY_RECOVERY_SCHEDULE_ID)
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    listOf(
      IosEventKitIdentifierHints(SOURCE_ID),
      IosEventKitIdentifierHints(SOURCE_ID, calendarIdentifier = CALENDAR_ID),
    ).forEach { hints ->
      val result = assertIs<IosEventKitGatewayResult.Failed>(gateway.upsert(target, hints))
      assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, result.reason)
    }
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }

  /** 同一 scope 出现多个 calendar 时拒绝任选；任何 title 都不能降低歧义。 */
  @Test
  fun duplicateCanonicalCalendarsFailClosedWithoutMutation() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar("calendar-a")
      addCalendar("calendar-b")
      addEvent("calendar-a", "event-a", rawFor(target, "event-a"))
      addEvent("calendar-b", "event-b", rawFor(target, "event-b"))
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID, "calendar-a", "event-a")),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR, result.reason)
    assertEquals(0, store.saveCount)
    assertEquals(0, store.removeCount)
  }

  /** 同 scope identity 位于其他 source 时只能报歧义，绝不能跨账号认领或在所选 source 再建重复 calendar。 */
  @Test
  fun canonicalCalendarInDifferentSourceFailsClosed() {
    val target = projection()
    val store = authorizedStore().apply {
      sourceSnapshots += IosEventKitSourceSnapshot(OTHER_SOURCE_ID, supportsEvents = true)
      addCalendar(CALENDAR_ID, sourceIdentifier = OTHER_SOURCE_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
    }

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR, result.reason)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }

  /** 所选 source 已消失时，即使其他 source 有同 scope identity，也必须保留 SOURCE_DISAPPEARED 边界。 */
  @Test
  fun missingSelectedSourceDoesNotRecoverCalendarFromAnotherSource() {
    val target = projection()
    val store = FakeEventKitStore(IosEventKitFullAccessStatus.FULL_ACCESS).apply {
      sourceSnapshots += IosEventKitSourceSnapshot(OTHER_SOURCE_ID, supportsEvents = true)
      addCalendar(CALENDAR_ID, sourceIdentifier = OTHER_SOURCE_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
    }

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.SOURCE_DISAPPEARED, result.reason)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }

  /** 已变只读的 canonical calendar 仍是身份事实，必须阻止创建第二个隔离 calendar。 */
  @Test
  fun readOnlyCanonicalCalendarBlocksDuplicateCreation() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID, allowsContentModifications = false)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
    }

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, result.reason)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }


  /** identifier 若被平台复用到 foreign URL，必须失败，不能退回 canonical create/update。 */
  @Test
  fun foreignEventIdentifierFailsClosed() {
    val target = projection()
    val foreign = projection(scheduleId = FOREIGN_SCHEDULE_ID)
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, "owned-event", rawFor(target, "owned-event"))
      addEvent(CALENDAR_ID, "foreign-event", rawFor(foreign, "foreign-event"))
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(
        target,
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID, "foreign-event"),
      ),
    )

    assertEquals(IosEventKitGatewayFailure.FOREIGN_IDENTITY, result.reason)
    assertEquals(0, store.saveCount)
  }

  /**
   * runtime direct seam 只通过 event ref 定位，再核验 source/calendar/canonical URI/scope；foreign ref 不能触发删除。
   */
  @Test
  fun verifiedDirectLookupAndDeleteKnownRequireStrictRefOwnership() {
    val target = projection()
    val foreign = projection(scheduleId = FOREIGN_SCHEDULE_ID)
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
      addEvent(CALENDAR_ID, "foreign-event", rawFor(foreign, "foreign-event"))
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)
    val hints = IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID)

    val owned = assertIs<IosEventKitVerifiedEventLookup.Managed>(
      gateway.lookupVerified(target.id, PlatformCalendarEventRef(EVENT_ID), hints),
    )
    assertEquals(target.fingerprint, owned.event.fingerprint)
    val foreignLookup = assertIs<IosEventKitVerifiedEventLookup.Blocked>(
      gateway.lookupVerified(target.id, PlatformCalendarEventRef("foreign-event"), hints),
    )
    assertEquals(IosEventKitGatewayFailure.FOREIGN_IDENTITY, foreignLookup.failure)
    assertEquals(0, store.removeCount)

    val deleted = assertIs<IosEventKitGatewayResult.Deleted>(
      gateway.deleteKnown(target.id, PlatformCalendarEventRef(EVENT_ID), hints),
    )
    assertTrue(deleted.changed)
    assertEquals(1, store.removeCount)
  }

  /**
   * 删除后旧 identifier 消失仍不足以确认成功：系列拆分或并发 actor 可能留下相同 canonical URI 的新 identifier。
   *
   * fake 仅在 removeEvent 已提交后插入 E2，因此删除前的 strict lookup 仍唯一；gateway 必须在 E1 原有界窗口
   * 回读 canonical absence，返回不确定失败而非让 runtime 清除 ledger。
   */
  @Test
  fun deleteKnownRejectsPostCommitCanonicalSiblingWhenOldIdentifierIsAbsent() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
      afterRemove = { addEvent(CALENDAR_ID, "replacement-event", rawFor(target, "replacement-event")) }
    }

    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store).deleteKnown(
        target.id,
        PlatformCalendarEventRef(EVENT_ID),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID),
      ),
    )

    assertEquals(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH, result.reason)
    assertEquals(1, store.removeCount)
  }

  /** direct ref 即使命中，也必须在同一受管 calendar 的有界 canonical 窗口拒绝第二个有效 sibling。 */
  @Test
  fun directLookupRejectsDuplicateCanonicalSiblingAsAmbiguousEvent() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
      addEvent(CALENDAR_ID, "duplicate-event", rawFor(target, "duplicate-event"))
    }

    val result = assertIs<IosEventKitVerifiedEventLookup.Blocked>(
      IosEventKitFullAccessGateway(SCOPE, store).lookupVerified(
        target.id,
        PlatformCalendarEventRef(EVENT_ID),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID),
      ),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_EVENT, result.failure)
    assertEquals(0, store.saveCount)
    assertEquals(0, store.removeCount)
  }

  /**
   * canonical URI/source/calendar/scope/ref 全部正确但 foundation 无法表示 occurrence 的受管 master 时，direct seam
   * 必须返回保守结果而非 foreign identity，供 runtime 保留 ledger 和 calendar。
   */
  @Test
  fun directLookupReturnsUnsupportedManagedForCanonicalOccurrenceMaster() {
    val target = projection(
      kind = CalendarProjectionKind.SERIES_MASTER,
      recurrence = "FREQ=WEEKLY;COUNT=2",
    )
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(
        CALENDAR_ID,
        EVENT_ID,
        rawFor(target, EVENT_ID).copy(hasOccurrenceException = true),
      )
    }

    val result = assertIs<IosEventKitVerifiedEventLookup.UnsupportedManaged>(
      IosEventKitFullAccessGateway(SCOPE, store).lookupVerified(
        target.id,
        PlatformCalendarEventRef(EVENT_ID),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID),
      ),
    )

    assertEquals(target.id, result.projectionId)
    assertEquals(IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION, result.mappingError)
    assertEquals(0, store.saveCount)
    assertEquals(0, store.removeCount)
  }

  /**
   * direct ref 自身为 UnsupportedManaged 时也必须继续扫描 canonical sibling；不能让 occurrence master 的保守返回
   * 掩盖第二个同 URI event，否则 runtime 会保留歧义 ledger 并继续同代自动读取。
   */
  @Test
  fun unsupportedDirectRefStillRejectsDuplicateCanonicalSibling() {
    val target = projection(
      kind = CalendarProjectionKind.SERIES_MASTER,
      recurrence = "FREQ=WEEKLY;COUNT=2",
    )
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID).copy(hasOccurrenceException = true))
      addEvent(CALENDAR_ID, "duplicate-event", rawFor(target, "duplicate-event"))
    }

    val result = assertIs<IosEventKitVerifiedEventLookup.Blocked>(
      IosEventKitFullAccessGateway(SCOPE, store).lookupVerified(
        target.id,
        PlatformCalendarEventRef(EVENT_ID),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID),
      ),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_EVENT, result.failure)
    assertEquals(0, store.removeCount)
  }

  /**
   * occurrence exception 的 foundation 早退不能掩盖 raw canonical identity 或 canonical 字段已经损坏的 ref。
   *
   * 纯内存 store 分别构造 scope/projection 不匹配和移除 occurrence 标志后仍无法映射的事件；两者都不是可保留的
   * UnsupportedManaged，必须 fail-closed 为 foreign identity。
   */
  @Test
  fun directLookupRejectsOccurrenceExceptionWithoutStrictCanonicalIdentityAndFields() {
    val target = projection(
      kind = CalendarProjectionKind.SERIES_MASTER,
      recurrence = "FREQ=WEEKLY;COUNT=2",
    )
    val foreign = projection(
      scheduleId = FOREIGN_SCHEDULE_ID,
      kind = CalendarProjectionKind.SERIES_MASTER,
      recurrence = "FREQ=WEEKLY;COUNT=2",
    )
    val hints = IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID)

    fun lookup(raw: IosEventKitRawEvent): IosEventKitVerifiedEventLookup {
      val store = authorizedStore().apply {
        addCalendar(CALENDAR_ID)
        addEvent(CALENDAR_ID, EVENT_ID, raw)
      }
      return IosEventKitFullAccessGateway(SCOPE, store).lookupVerified(
        target.id,
        PlatformCalendarEventRef(EVENT_ID),
        hints,
      )
    }

    val foreignIdentity = assertIs<IosEventKitVerifiedEventLookup.Blocked>(
      lookup(rawFor(foreign, EVENT_ID).copy(hasOccurrenceException = true)),
    )
    assertEquals(IosEventKitGatewayFailure.FOREIGN_IDENTITY, foreignIdentity.failure)
    val invalidCanonicalFields = assertIs<IosEventKitVerifiedEventLookup.Blocked>(
      lookup(rawFor(target, EVENT_ID).copy(hasOccurrenceException = true, title = " ")),
    )
    assertEquals(IosEventKitGatewayFailure.FOREIGN_IDENTITY, invalidCanonicalFields.failure)
  }

  /** 只要 canonical URI 的第二个 sibling 存在，即使它因 occurrence 不受 foundation 支持也必须报告歧义。 */
  @Test
  fun directLookupRejectsUnsupportedCanonicalSiblingAsAmbiguousEvent() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
      addEvent(
        CALENDAR_ID,
        "unsupported-duplicate",
        rawFor(target, "unsupported-duplicate").copy(hasOccurrenceException = true),
      )
    }

    val result = assertIs<IosEventKitVerifiedEventLookup.Blocked>(
      IosEventKitFullAccessGateway(SCOPE, store).lookupVerified(
        target.id,
        PlatformCalendarEventRef(EVENT_ID),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID),
      ),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_EVENT, result.failure)
    assertEquals(0, store.saveCount)
    assertEquals(0, store.removeCount)
  }

  /**
   * 缺失 ref 只会得到 KnownAbsent，不扫描日历或按 title 恢复；同一 URI 但字段被平台改写时只可返回新的
   * fingerprint，runtime 必须走 Update，不能把旧 fingerprint 当成 NoOp 或删除该事件。
   */
  @Test
  fun directLookupTreatsMissingRefAndFingerprintDriftAsStrictNonDeleteStates() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(
        CALENDAR_ID,
        EVENT_ID,
        rawFor(target, EVENT_ID).copy(title = "平台修改后的标题"),
      )
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)
    val hints = IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID)

    assertEquals(
      IosEventKitVerifiedEventLookup.KnownAbsent,
      gateway.lookupVerified(target.id, PlatformCalendarEventRef("missing-ref"), hints),
    )
    // direct lookup 不可借缺失 ref 扫描 canonical URL 扩大认领范围。
    assertEquals(0, store.scanWindows.size)
    val drifted = assertIs<IosEventKitVerifiedEventLookup.Managed>(
      gateway.lookupVerified(target.id, PlatformCalendarEventRef(EVENT_ID), hints),
    )
    assertTrue(drifted.event.fingerprint != target.fingerprint)
    assertEquals(0, store.removeCount)
  }

  /** delete 首次 commit 后 canonical URL 必须消失；再次删除保持成功且不重复 remove。 */
  @Test
  fun deleteIsIdempotentAndUsesCanonicalReadAfterWrite() {
    val target = projection()
    val store = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
    }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)
    val hints = IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID, EVENT_ID)

    val first = assertIs<IosEventKitGatewayResult.Deleted>(gateway.delete(target, hints))
    assertTrue(first.changed)
    assertEquals(1, store.removeCount)

    val second = assertIs<IosEventKitGatewayResult.Deleted>(gateway.delete(target, hints))
    assertEquals(false, second.changed)
    assertEquals(1, store.removeCount)
  }


  /** foundation 不支持的 occurrence exception 必须在任何 source/calendar/store 副作用前被拒绝。 */
  @Test
  fun unsupportedProjectionFailsBeforeEventKitMutation() {
    val store = authorizedStore()
    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store).upsert(
        projection(kind = CalendarProjectionKind.OCCURRENCE_EXCEPTION),
        IosEventKitIdentifierHints(SOURCE_ID),
      ),
    )

    assertEquals(IosEventKitGatewayFailure.UNSUPPORTED_PROJECTION, result.reason)
    assertEquals(IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION, result.mappingError)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }

  /** 用户选择的 source 消失时不得回退默认日历，也不得创建或保存事件。 */
  @Test
  fun missingSelectedSourceFailsClosedWithoutDefaultCalendarFallback() {
    val store = FakeEventKitStore(IosEventKitFullAccessStatus.FULL_ACCESS)
    val result = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, store)
        .upsert(projection(), IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.SOURCE_DISAPPEARED, result.reason)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
  }


  /** 首次原子提交无论确定未提交还是模糊已提交，都不能在重试时累积空 calendar。 */
  @Test
  fun failedFirstUpsertDoesNotLeakOrDuplicateCalendar() {
    val target = projection()
    val queuedFailureStore = authorizedStore().apply { failNextSave = true }
    val gateway = IosEventKitFullAccessGateway(SCOPE, queuedFailureStore)

    val failed = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, failed.reason)
    assertEquals(0, queuedFailureStore.calendarCount)

    assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(1, queuedFailureStore.calendarCount)

    val committedAmbiguouslyStore = authorizedStore().apply {
      failNextSave = true
      ambiguousAtomicCreateCommits = true
    }
    val ambiguousGateway = IosEventKitFullAccessGateway(SCOPE, committedAmbiguouslyStore)
    assertIs<IosEventKitGatewayResult.Failed>(
      ambiguousGateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    val recovered = assertIs<IosEventKitGatewayResult.Upserted>(
      ambiguousGateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(false, recovered.changed)
    assertEquals(false, recovered.atomicCalendarAndFirstEvent)
    assertTrue(recovered.locatorRecoveryProof != null)
    assertEquals(1, committedAmbiguouslyStore.createCalendarCount)
    assertEquals(1, committedAmbiguouslyStore.calendarCount)
  }

  /**
   * atomic create 在 commit 前失败时，即使 port 只能返回普通 AMBIGUOUS，也不能为随后出现的 ordinary exact event
   * 建立 eligibility。canonical equality 不是提交 provenance，因此第二次 fresh scan 必须拒绝 proof，且不能重复 create/save。
   */
  @Test
  fun preCommitAmbiguousFailureCannotAuthorizeLaterOrdinaryExactEvent() {
    val target = projection(scheduleId = PRE_COMMIT_RECOVERY_SCHEDULE_ID)
    val store = authorizedStore().apply { failNextSave = true }
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)

    val failed = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, failed.reason)
    assertEquals(0, store.calendarCount)

    store.addCalendar(CALENDAR_ID)
    store.addEvent(CALENDAR_ID, EVENT_ID, rawFor(target, EVENT_ID))
    val ordinary = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, ordinary.reason)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
  }

  /**
   * proof 签发不能消费 eligibility；只有同一 gateway issuer 的 latest proof + exact target/binding ack 才能一次性终结。
   *
   * wrong source/calendar/event、changed target、wrong gateway、stale proof 与重复 ack 都必须 REJECTED，且不能误消费当前资格。
   */
  @Test
  fun locatorAcknowledgementRequiresLatestExactProofAndConsumesEligibilityOnce() {
    val target = projection(scheduleId = ACK_RECOVERY_SCHEDULE_ID)
    val store = authorizedStore()
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)
    val first = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    val firstProof = requireNotNull(first.locatorRecoveryProof)

    listOf(
      first.binding.copy(sourceIdentifier = "wrong-source"),
      first.binding.copy(calendarIdentifier = "wrong-calendar"),
      first.binding.copy(eventIdentifier = "wrong-event"),
    ).forEach { wrongBinding ->
      assertEquals(
        IosEventKitLocatorAcknowledgement.REJECTED,
        gateway.acknowledgeLocatorPersistence(target, wrongBinding, firstProof),
      )
    }
    assertEquals(
      IosEventKitLocatorAcknowledgement.REJECTED,
      gateway.acknowledgeLocatorPersistence(
        projection(scheduleId = ACK_RECOVERY_SCHEDULE_ID, reminders = listOf(5)),
        first.binding,
        firstProof,
      ),
    )
    assertEquals(
      IosEventKitLocatorAcknowledgement.REJECTED,
      IosEventKitFullAccessGateway(SCOPE, store)
        .acknowledgeLocatorPersistence(target, first.binding, firstProof),
    )

    // 未 ack 的 eligibility 仍可在 fresh authority 扫描后重签；新 proof 会让旧 proof stale。
    val second = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    val secondProof = requireNotNull(second.locatorRecoveryProof)
    assertTrue(secondProof !== firstProof)
    assertEquals(
      IosEventKitLocatorAcknowledgement.REJECTED,
      gateway.acknowledgeLocatorPersistence(target, first.binding, firstProof),
    )
    assertEquals(
      IosEventKitLocatorAcknowledgement.ACKNOWLEDGED,
      gateway.acknowledgeLocatorPersistence(target, second.binding, secondProof),
    )
    assertEquals(
      IosEventKitLocatorAcknowledgement.REJECTED,
      gateway.acknowledgeLocatorPersistence(target, second.binding, secondProof),
    )
    assertEquals(
      IosEventKitLocatorEligibilityRetirement.REJECTED,
      gateway.retireLocatorRecoveryEligibility(
        ManagedCalendarEvent(target.id, target.fingerprint, PlatformCalendarEventRef(second.binding.eventIdentifier)),
        second.binding,
        secondProof,
      ),
      "ack 与 retirement 必须互斥消费同一 eligibility",
    )

    val afterAck = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, afterAck.reason)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
  }

  /**
   * retirement 只接受同一 gateway 签发的 latest proof、旧 target 与 exact binding，并与 ack 互斥地一次性消费。
   *
   * stale/wrong gateway/source/calendar/event/target 与 duplicate retirement 都不得误消费当前 eligibility；成功 retirement 后，
   * 同一普通 exact event 即使 canonical 字段仍一致，也不能再次签发 proof。
   */
  @Test
  fun locatorEligibilityRetirementRequiresLatestExactCapabilityAndConsumesOnce() {
    val target = projection(scheduleId = RETIRE_RECOVERY_SCHEDULE_ID)
    val store = authorizedStore()
    val gateway = IosEventKitFullAccessGateway(SCOPE, store)
    val first = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    val firstProof = requireNotNull(first.locatorRecoveryProof)
    val managed = ManagedCalendarEvent(
      target.id,
      target.fingerprint,
      PlatformCalendarEventRef(first.binding.eventIdentifier),
    )

    listOf(
      first.binding.copy(sourceIdentifier = "wrong-source"),
      first.binding.copy(calendarIdentifier = "wrong-calendar"),
      first.binding.copy(eventIdentifier = "wrong-event"),
    ).forEach { wrongBinding ->
      assertEquals(
        IosEventKitLocatorEligibilityRetirement.REJECTED,
        gateway.retireLocatorRecoveryEligibility(managed, wrongBinding, firstProof),
      )
    }
    assertEquals(
      IosEventKitLocatorEligibilityRetirement.REJECTED,
      gateway.retireLocatorRecoveryEligibility(
        managed.copy(fingerprint = "wrong-target-fingerprint"),
        first.binding,
        firstProof,
      ),
    )
    assertEquals(
      IosEventKitLocatorEligibilityRetirement.REJECTED,
      IosEventKitFullAccessGateway(SCOPE, store)
        .retireLocatorRecoveryEligibility(managed, first.binding, firstProof),
    )

    // fresh lookup 重签 latest proof；旧 proof 变 stale，但上述 mismatch 均不得提前消费资格。
    val second = assertIs<IosEventKitGatewayResult.Upserted>(
      gateway.upsert(
        target,
        IosEventKitIdentifierHints(
          SOURCE_ID,
          first.binding.calendarIdentifier,
          first.binding.eventIdentifier,
        ),
      ),
    )
    val secondProof = requireNotNull(second.locatorRecoveryProof)
    assertTrue(secondProof !== firstProof)
    assertEquals(
      IosEventKitLocatorEligibilityRetirement.REJECTED,
      gateway.retireLocatorRecoveryEligibility(managed, second.binding, firstProof),
    )
    assertEquals(
      IosEventKitLocatorEligibilityRetirement.RETIRED,
      gateway.retireLocatorRecoveryEligibility(managed, second.binding, secondProof),
    )
    assertEquals(
      IosEventKitLocatorEligibilityRetirement.REJECTED,
      gateway.retireLocatorRecoveryEligibility(managed, second.binding, secondProof),
    )
    assertEquals(
      IosEventKitLocatorAcknowledgement.REJECTED,
      gateway.acknowledgeLocatorPersistence(target, second.binding, secondProof),
    )

    val ordinaryAfterRetirement = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, ordinaryAfterRetirement.reason)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
  }

  /**
   * fresh recovery 的唯一性必须覆盖 target 与 recovery authority 窗口全集。
   *
   * 首次 2031 atomic pair 已提交但 completion 丢失；随后在 2026 recovery window 注入同 URI 的第二个 committed event。
   * target-only 扫描只能看到原 event，但完整 authority 聚合必须拒绝签发 proof。
   */
  @Test
  fun freshRecoveryRejectsCanonicalSiblingVisibleOnlyInRecoveryAuthorityWindow() {
    val target = projection(
      scheduleId = SHIFTED_RECOVERY_SCHEDULE_ID,
      start = MinuteTimeDate(2031, 8, 1, 9, 30),
    )
    val shifted = projection(
      scheduleId = SHIFTED_RECOVERY_SCHEDULE_ID,
      start = MinuteTimeDate(2026, 8, 1, 9, 30),
    )
    val store = authorizedStore().apply {
      failNextSave = true
      ambiguousAtomicCreateCommits = true
    }
    val gateway = IosEventKitFullAccessGateway(
      scope = SCOPE,
      store = store,
      scopeRecoveryAnchorEpochSeconds = AUTHORITY_ANCHOR_EPOCH_SECONDS,
    )

    val ambiguous = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, ambiguous.reason)
    store.addEvent(
      calendarIdentifier = "created-calendar-1",
      identifier = "shifted-recovery-sibling",
      raw = rawFor(shifted, "shifted-recovery-sibling").copy(externalUri = target.externalUri),
    )

    val recovered = assertIs<IosEventKitGatewayResult.Failed>(
      gateway.upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )

    assertEquals(IosEventKitGatewayFailure.AMBIGUOUS_EVENT, recovered.reason)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
  }

  /** NOT_FOUND 后的 source/calendar 诊断查询若失败，必须保留 STORE_AMBIGUOUS，不能把失败列表当成空。 */
  @Test
  fun failedDiagnosticRereadPreservesAmbiguousOutcome() {
    fun preparedStore(): FakeEventKitStore = authorizedStore().apply {
      addCalendar(CALENDAR_ID)
      addEvent(CALENDAR_ID, EVENT_ID, rawFor(projection(), EVENT_ID))
      eventLookupFailure = IosEventKitStoreFailure.NOT_FOUND
    }

    val sourceFailureStore = preparedStore().apply { failSourcesOnCall = 2 }
    val sourceResult = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, sourceFailureStore).upsert(
        projection(),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID, EVENT_ID),
      ),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, sourceResult.reason)

    val calendarFailureStore = preparedStore().apply { failCalendarsOnCall = 2 }
    val calendarResult = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, calendarFailureStore).upsert(
        projection(),
        IosEventKitIdentifierHints(SOURCE_ID, CALENDAR_ID, EVENT_ID),
      ),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, calendarResult.reason)
    assertEquals(0, sourceFailureStore.saveCount)
    assertEquals(0, calendarFailureStore.saveCount)
  }

  /** 权限拒绝/写中撤销与 EventKit 模糊保存都只返回平台失败，不进行补偿删除。 */
  @Test
  fun revokedPermissionAndAmbiguousSaveNeverDeleteScheduleOrPlatformEvent() {
    val target = projection()
    val deniedStore = authorizedStore().apply { status = IosEventKitFullAccessStatus.DENIED }
    val denied = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, deniedStore)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.PERMISSION_DENIED, denied.reason)
    assertEquals(0, deniedStore.saveCount)

    val revokedStore = authorizedStore().apply { revokeOnNextSave = true }
    val revoked = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, revokedStore)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.PERMISSION_REVOKED, revoked.reason)
    assertEquals(0, revokedStore.removeCount)

    val ambiguousStore = authorizedStore().apply { failNextSave = true }
    val ambiguous = assertIs<IosEventKitGatewayResult.Failed>(
      IosEventKitFullAccessGateway(SCOPE, ambiguousStore)
        .upsert(target, IosEventKitIdentifierHints(SOURCE_ID)),
    )
    assertEquals(IosEventKitGatewayFailure.STORE_AMBIGUOUS, ambiguous.reason)
    assertEquals(0, ambiguousStore.removeCount)
  }

  private fun authorizedStore(): FakeEventKitStore = FakeEventKitStore(
    status = IosEventKitFullAccessStatus.FULL_ACCESS,
  ).apply {
    sourceSnapshots += IosEventKitSourceSnapshot(SOURCE_ID, supportsEvents = true)
  }

  /** 构造 canonical 投影，所有 fingerprint 都从最终字段重新计算，避免测试绕过 foundation 门禁。 */
  private fun projection(
    scheduleId: ScheduleId = SCHEDULE_ID,
    kind: CalendarProjectionKind = CalendarProjectionKind.SINGLE,
    recurrence: String? = null,
    reminders: List<Int> = emptyList(),
    start: MinuteTimeDate = MinuteTimeDate(2026, 8, 1, 9, 30),
  ): CalendarEventProjection {
    val timing = CalendarTiming.Timed(
      start = start,
      durationMinutes = 60,
      timeZoneId = ZONE,
    )
    val id = CalendarProjectionId(
      scope = SCOPE,
      scheduleId = scheduleId,
      kind = kind,
      recurrenceId = if (kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION) {
        RecurrenceId(
          originalDateTime = MinuteTimeDate(2026, 8, 1, 9, 30),
          timeZoneId = ZONE,
          allDay = false,
        )
      } else {
        null
      },
    )
    val uri = CalendarProjectionUriCodec.encode(id)
    return CalendarEventProjection(
      id = id,
      externalUri = uri,
      title = "EventKit gateway",
      description = "fake contract",
      timing = timing,
      recurrenceRule = recurrence,
      deviceReminderMinutes = reminders,
      fingerprint = CalendarProjectionFingerprint.compute(
        externalUri = uri,
        title = "EventKit gateway",
        description = "fake contract",
        timing = timing,
        recurrenceRule = recurrence,
        reminderMinutes = reminders,
      ),
    )
  }

  private fun rawFor(
    projection: CalendarEventProjection,
    identifier: String,
  ): IosEventKitRawEvent {
    val payload = IosEventKitCalendarAdapterFoundation.toWritePayload(projection)
      .let { assertIs<IosEventKitMappingResult.Mapped<IosEventKitWritePayload>>(it).value }
    return payload.toRaw(identifier)
  }

  private fun IosEventKitWritePayload.toRaw(identifier: String): IosEventKitRawEvent {
    val timed = assertIs<IosEventKitWriteTiming.Timed>(timing)
    return IosEventKitRawEvent(
      eventIdentifier = identifier,
      externalUri = externalUri,
      title = title,
      notes = notes,
      start = timed.start,
      endExclusive = timed.endExclusive,
      timeZoneId = timed.timeZoneId,
      allDay = false,
      recurrenceRules = listOfNotNull(recurrenceRule),
      alarms = alarms.map { IosEventKitRawAlarm(it.relativeOffsetSeconds.toDouble()) },
      hasOccurrenceException = false,
    )
  }

  /**
   * 内存 fake 同时记录副作用次数，并模拟四年窗口过滤、原子首次提交、诊断 reread 失败与 identifier 变化。
   */
  private inner class FakeEventKitStore(
    var status: IosEventKitFullAccessStatus,
  ) : IosEventKitStorePort {
    var requestResult: IosEventKitFullAccessStatus = status
    var permissionRequestCount = 0
    var suspendPermissionRequest = false
    var createCalendarCount = 0
    var saveCount = 0
    var removeCount = 0
    var failNextSave = false
    var ambiguousAtomicCreateCommits = false
    var createConcurrentCanonicalCalendar = false
    var revokeOnNextSave = false
    var eventLookupFailure: IosEventKitStoreFailure? = null
    /** 删除提交后注入并发/系列分裂遗留，验证 canonical absence 的写后回读。 */
    var afterRemove: (() -> Unit)? = null
    var failSourcesOnCall: Int? = null
    var failCalendarsOnCall: Int? = null
    var lastPayload: IosEventKitWritePayload? = null
    val sourceSnapshots = mutableListOf<IosEventKitSourceSnapshot>()
    val scanWindows = mutableListOf<IosEventKitScanWindow>()
    val calendarCount: Int get() = calendarSnapshots.size
    private val calendarSnapshots = mutableListOf<IosEventKitCalendarSnapshot>()
    private val eventsByCalendar = linkedMapOf<String, MutableList<IosEventKitStoreEventSnapshot>>()
    private var sourceCallCount = 0
    private var calendarCallCount = 0
    private var nextCalendar = 1
    private var nextEvent = 1

    override fun authorizationStatus(): IosEventKitFullAccessStatus = status

    override suspend fun requestFullAccess(): IosEventKitStoreResult<IosEventKitFullAccessStatus> {
      permissionRequestCount += 1
      if (suspendPermissionRequest) awaitCancellation()
      status = requestResult
      return IosEventKitStoreResult.Success(status)
    }

    override fun sources(): IosEventKitStoreResult<List<IosEventKitSourceSnapshot>> {
      sourceCallCount += 1
      return if (failSourcesOnCall == sourceCallCount) {
        IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      } else {
        IosEventKitStoreResult.Success(sourceSnapshots.toList())
      }
    }

    override fun calendars(): IosEventKitStoreResult<List<IosEventKitCalendarSnapshot>> {
      calendarCallCount += 1
      return if (failCalendarsOnCall == calendarCallCount) {
        IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      } else {
        IosEventKitStoreResult.Success(calendarSnapshots.toList())
      }
    }

    override fun eventByIdentifier(
      identifier: String,
    ): IosEventKitStoreResult<IosEventKitStoreEventSnapshot?> {
      eventLookupFailure?.let { return IosEventKitStoreResult.Failure(it) }
      return IosEventKitStoreResult.Success(
        eventsByCalendar.values.flatten().singleOrNull { it.raw.eventIdentifier == identifier },
      )
    }

    override fun events(
      calendarIdentifier: String,
      window: IosEventKitScanWindow,
    ): IosEventKitStoreResult<List<IosEventKitStoreEventSnapshot>> {
      scanWindows += window
      val events = eventsByCalendar[calendarIdentifier]
        ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      return IosEventKitStoreResult.Success(
        events.filter { event ->
          event.raw.start.epochSeconds in window.startEpochSeconds..<window.endEpochSeconds
        },
      )
    }

    override fun createCalendarWithEvent(
      sourceIdentifier: String,
      displayTitle: String,
      payload: IosEventKitWritePayload,
    ): IosEventKitStoreResult<IosEventKitCreatedEventSnapshot> {
      createCalendarCount += 1
      saveCount += 1
      lastPayload = payload
      if (revokeOnNextSave) {
        revokeOnNextSave = false
        status = IosEventKitFullAccessStatus.DENIED
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.ACCESS_LOST)
      }
      val calendar = IosEventKitCalendarSnapshot(
        identifier = "created-calendar-${nextCalendar++}",
        sourceIdentifier = sourceIdentifier,
        allowsContentModifications = true,
      )
      val eventIdentifier = "committed-event-${nextEvent++}"
      if (failNextSave) {
        failNextSave = false
        val failure = if (ambiguousAtomicCreateCommits) {
          ambiguousAtomicCreateCommits = false
          commitCreatedPair(calendar, eventIdentifier, payload)
          // 只有 calendar/event 已进入同一次 commit 且终态未知，才向 gateway 提供 recovery provenance。
          IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN
        } else {
          // 模拟 configure/commit=false 排队阶段失败：没有 durable pair，普通 AMBIGUOUS 不得建立 eligibility。
          IosEventKitStoreFailure.AMBIGUOUS
        }
        return IosEventKitStoreResult.Failure(failure)
      }
      commitCreatedPair(calendar, eventIdentifier, payload)
      if (createConcurrentCanonicalCalendar) {
        createConcurrentCanonicalCalendar = false
        commitCreatedPair(
          calendar = calendar.copy(identifier = "concurrent-calendar-${nextCalendar++}"),
          eventIdentifier = "concurrent-event-${nextEvent++}",
          payload = payload,
        )
      }
      return IosEventKitStoreResult.Success(
        IosEventKitCreatedEventSnapshot(calendar, eventIdentifier),
      )
    }

    override fun saveEvent(
      calendarIdentifier: String,
      existingEventIdentifier: String?,
      payload: IosEventKitWritePayload,
    ): IosEventKitStoreResult<String> {
      saveCount += 1
      lastPayload = payload
      if (revokeOnNextSave) {
        revokeOnNextSave = false
        status = IosEventKitFullAccessStatus.DENIED
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.ACCESS_LOST)
      }
      if (failNextSave) {
        failNextSave = false
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      }
      val events = eventsByCalendar[calendarIdentifier]
        ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      val committedIdentifier = "committed-event-${nextEvent++}"
      if (existingEventIdentifier != null) {
        events.removeAll { it.raw.eventIdentifier == existingEventIdentifier }
      }
      events += IosEventKitStoreEventSnapshot(
        calendarIdentifier = calendarIdentifier,
        raw = payload.toRaw(committedIdentifier),
      )
      return IosEventKitStoreResult.Success(committedIdentifier)
    }

    override fun removeEvent(eventIdentifier: String): IosEventKitStoreResult<Unit> {
      val events = eventsByCalendar.values.singleOrNull { values ->
        values.any { it.raw.eventIdentifier == eventIdentifier }
      } ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      removeCount += 1
      events.removeAll { it.raw.eventIdentifier == eventIdentifier }
      afterRemove?.invoke()
      return IosEventKitStoreResult.Success(Unit)
    }

    /** 测试专用 fixture；source 与可写性显式传入，避免 title 暗中成为身份。 */
    fun addCalendar(
      identifier: String,
      sourceIdentifier: String = SOURCE_ID,
      allowsContentModifications: Boolean = true,
    ) {
      calendarSnapshots += IosEventKitCalendarSnapshot(
        identifier = identifier,
        sourceIdentifier = sourceIdentifier,
        allowsContentModifications = allowsContentModifications,
      )
      eventsByCalendar[identifier] = mutableListOf()
    }

    fun addEvent(
      calendarIdentifier: String,
      identifier: String,
      raw: IosEventKitRawEvent,
    ) {
      eventsByCalendar.getValue(calendarIdentifier) += IosEventKitStoreEventSnapshot(
        calendarIdentifier = calendarIdentifier,
        raw = raw.copy(eventIdentifier = identifier),
      )
    }

    private fun commitCreatedPair(
      calendar: IosEventKitCalendarSnapshot,
      eventIdentifier: String,
      payload: IosEventKitWritePayload,
    ) {
      calendarSnapshots += calendar
      eventsByCalendar[calendar.identifier] = mutableListOf(
        IosEventKitStoreEventSnapshot(
          calendarIdentifier = calendar.identifier,
          raw = payload.toRaw(eventIdentifier),
        ),
      )
    }
  }

  /** #280 设置构造/普通刷新只能读取 fake 状态，不能请求权限或调用任何 EventKit CRUD。 */
  @Test
  fun settingsConstructionAndRefreshNeverRequestOrMutateEventKit() = runTest {
    val account = FakeSettingsAccount(backgroundScope, session("settings-no-popup", 1))
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.NOT_DETERMINED)
    val controller = IosScheduleCalendarSettingsController(account, { gateway }, FakeSettingsPreferences())

    assertEquals(0, gateway.requestCount)
    controller.refresh()
    runCurrent()

    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED, controller.state.value.status)
    assertEquals(0, gateway.requestCount)
    assertEquals(0, gateway.upsertDeleteCount)
  }

  /** 只有显式 controller 操作请求权限；取消仅作用于发起请求的冻结会话并保留恢复提示。 */
  @Test
  fun settingsExplicitRequestAndCancellationAreAccountScoped() = runTest {
    val account = FakeSettingsAccount(backgroundScope, session("settings-request", 1))
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.NOT_DETERMINED).apply {
      suspendRequest = true
    }
    val controller = IosScheduleCalendarSettingsController(account, { gateway }, FakeSettingsPreferences())

    controller.requestFullAccess()
    runCurrent()
    assertEquals(1, gateway.requestCount)
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_REQUESTING, controller.state.value.status)

    controller.cancelFullAccessRequest()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_CANCELLED, controller.state.value.status)
    assertEquals(0, gateway.upsertDeleteCount)
  }

  /** 权限状态和 source/calendar cache 的不存在、迁移及歧义均以 fail-closed 状态呈现。 */
  @Test
  fun settingsMapsPermissionAndExactCacheFailuresWithoutFallback() = runTest {
    val accountId = "settings-cache-${testScheduler.currentTime}"
    val account = FakeSettingsAccount(backgroundScope, session(accountId, 1))
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.FULL_ACCESS)
    val preferences = FakeSettingsPreferences()
    val controller = IosScheduleCalendarSettingsController(account, { gateway }, preferences)
    preferences.updateSourceIdentifier(accountId, "source-a")
    preferences.updateCalendarIdentifier(accountId, "calendar-a")
    preferences.setEnabled(accountId, true)

    gateway.cachedSelection = IosEventKitCachedSelection.CalendarMissing
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.CALENDAR_MISSING, controller.state.value.status)

    gateway.cachedSelection = IosEventKitCachedSelection.CalendarMovedToOtherSource
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.CALENDAR_MOVED_TO_OTHER_SOURCE, controller.state.value.status)

    gateway.cachedSelection = IosEventKitCachedSelection.Ambiguous
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION, controller.state.value.status)

    gateway.status = IosEventKitFullAccessStatus.WRITE_ONLY
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_WRITE_ONLY, controller.state.value.status)

    gateway.status = IosEventKitFullAccessStatus.DENIED
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_DENIED, controller.state.value.status)

    gateway.status = IosEventKitFullAccessStatus.RESTRICTED
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_RESTRICTED, controller.state.value.status)

    gateway.status = IosEventKitFullAccessStatus.FULL_ACCESS
    gateway.cachedSelection = IosEventKitCachedSelection.SourceMissing
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.SOURCE_MISSING, controller.state.value.status)

    // typed 请求终态优先于随后仍可能停留在 NOT_DETERMINED 的只读授权状态。
    gateway.status = IosEventKitFullAccessStatus.NOT_DETERMINED
    gateway.requestResult = IosEventKitPermissionResult.Rejected(IosEventKitGatewayFailure.PERMISSION_DENIED)
    controller.requestFullAccess()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_DENIED, controller.state.value.status)

    gateway.requestResult = IosEventKitPermissionResult.Rejected(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
    controller.requestFullAccess()
    runCurrent()
    assertEquals(
      IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION,
      controller.state.value.status,
    )
    assertEquals(0, gateway.upsertDeleteCount)
  }

  /**
   * 启动授权 Job 后、记录取消上下文前若切换到 B，取消必须仍绑定 A，不能向 B 发布取消状态。
   *
   * fake 将第三次 session 读取安排在旧实现的“启动后重读 session”位置；修复后的 controller 不再发生该重读，
   * 随后的取消路径发现 A 已失效并 fail-closed，不会污染 B。
   */
  @Test
  fun settingsCancellationDoesNotPublishToSessionReadAfterRequestLaunch() = runTest {
    val accountA = session("settings-request-race-a", 1)
    val accountB = session("settings-request-race-b", 2)
    val service = FakeSettingsAccount(backgroundScope, accountA).apply {
      switchSessionOnRead(readCount = 3, next = accountB)
    }
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.NOT_DETERMINED).apply {
      suspendRequest = true
    }
    val controller = IosScheduleCalendarSettingsController(service, { gateway }, FakeSettingsPreferences())

    controller.requestFullAccess()
    controller.cancelFullAccessRequest()
    runCurrent()

    assertEquals(IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE, controller.state.value.status)
    assertEquals(0, gateway.upsertDeleteCount)
  }

  /** A/B 与同账号新 generation 均隔离偏好和取消后的旧授权等待；登出/游客不产生写入。 */
  @Test
  fun settingsIsolatesAccountsGenerationsLogoutAndTourist() = runTest {
    val accountA = "settings-a-${testScheduler.currentTime}"
    val accountB = "settings-b-${testScheduler.currentTime}"
    val service = FakeSettingsAccount(backgroundScope, session(accountA, 1))
    val oldGateway = FakeSettingsGateway(IosEventKitFullAccessStatus.NOT_DETERMINED).apply { suspendRequest = true }
    val newGateway = FakeSettingsGateway(IosEventKitFullAccessStatus.FULL_ACCESS)
    val preferences = FakeSettingsPreferences()
    val controller = IosScheduleCalendarSettingsController(service, { scope ->
      if (scope.value == accountA.lowercase()) oldGateway else newGateway
    }, preferences)

    controller.requestFullAccess()
    runCurrent()
    service.switchTo(session(accountA, 2))
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED, controller.state.value.status)

    service.switchTo(session(accountB, 3))
    controller.selectSource("source-a")
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.CONFIGURED, controller.state.value.status)
    assertEquals(null, preferences.get(accountA).sourceIdentifier)
    assertEquals("source-a", preferences.get(accountB).sourceIdentifier)

    service.switchTo(AccountSession(4, AccountState.Logout(null)))
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE, controller.state.value.status)
    service.switchTo(AccountSession(5, AccountState.Tourist))
    controller.refresh()
    runCurrent()
    assertEquals(IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE, controller.state.value.status)
    assertEquals(0, oldGateway.upsertDeleteCount + newGateway.upsertDeleteCount)
  }

  /** 用户明确选择 source 时 source 先落盘、无效 calendar cache 被清空、enabled 最后生效。 */
  @Test
  fun sourceSelectionClearsInvalidCalendarCacheAndEnablesOnlyAfterSelection() = runTest {
    val accountId = "settings-order-${testScheduler.currentTime}"
    val service = FakeSettingsAccount(backgroundScope, session(accountId, 1))
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.FULL_ACCESS).apply {
      cachedSelection = IosEventKitCachedSelection.CalendarMovedToOtherSource
    }
    val preferences = FakeSettingsPreferences()
    preferences.updateSourceIdentifier(accountId, "old-source")
    preferences.updateCalendarIdentifier(accountId, "old-calendar")
    preferences.setEnabled(accountId, true)
    preferences.setEnabled(accountId, false)
    preferences.writes.clear()
    val controller = IosScheduleCalendarSettingsController(service, { gateway }, preferences)

    controller.selectSource("source-a")
    runCurrent()

    assertEquals(
      IosScheduleCalendarExportSettings.Preference(true, "source-a", null),
      preferences.get(accountId),
    )
    assertEquals(listOf("source", "calendar-clear", "event-clear", "enabled"), preferences.writes)
    assertEquals(IosScheduleCalendarSettingsStatus.CONFIGURED, controller.state.value.status)
    assertEquals(0, gateway.upsertDeleteCount)
  }

  /**
   * source/disable 的第一笔 durable intent 写之前必须先失效 runtime generation。
   *
   * 该顺序使旧 EventKit completion 在 source、calendar、ledger 或 enabled 改写期间只能失败关闭，不能把旧 source locator
   * 写回新配置；完整 intent 后才 signal 新 generation。
   */
  @Test
  fun settingsInvalidatesRuntimeBeforeDurableIntentAndSignalsAfterIt() = runTest {
    val accountId = "settings-runtime-order-${testScheduler.currentTime}"
    val service = FakeSettingsAccount(backgroundScope, session(accountId, 1))
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.FULL_ACCESS)
    val preferences = FakeSettingsPreferences()
    val order = mutableListOf<String>()
    preferences.writeObserver = { order += it }
    val controller = IosScheduleCalendarSettingsController(
      accountService = service,
      gatewayFactory = { gateway },
      preferences = preferences,
      runtimeIntentInvalidate = { order += "invalidate" },
      runtimeIntentSignal = { order += "signal" },
    )

    controller.selectSource("source-a")
    runCurrent()

    assertEquals(
      listOf("invalidate", "source", "calendar-clear", "event-clear", "enabled", "signal"),
      order,
    )
    assertEquals(listOf("source", "calendar-clear", "event-clear", "enabled"), preferences.writes)

    preferences.writes.clear()
    order.clear()
    controller.disable()
    runCurrent()

    assertEquals(listOf("invalidate", "disabled", "signal"), order)
    assertEquals(listOf("disabled"), preferences.writes)
  }

  /**
   * source 选择与 disable 即使同在 account scope 的不同 Default worker 上启动，也必须以完整显式 intent 事务串行。
   *
   * barrier 在第一次 invalidate 后保持 source 事务的 mutex；第二个 disable 已启动却不能执行自身 invalidate，更不能
   * 让第一个 signal 释放它的 pending fence。fixture 只记录内存设置与 callback 顺序，不创建 EventKit 对象。
   */
  @Test
  fun overlappingSourceSelectionAndDisableDoNotReleaseEachOthersIntentFence() = runTest {
    val accountId = "settings-intent-transaction-${testScheduler.currentTime}"
    val service = FakeSettingsAccount(backgroundScope, session(accountId, 1))
    val gateway = FakeSettingsGateway(IosEventKitFullAccessStatus.FULL_ACCESS)
    val preferences = FakeSettingsPreferences()
    val order = mutableListOf<String>()
    val arrived = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val boundary = IosScheduleCalendarIntentMutationBoundary {
      arrived.complete(Unit)
      release.await()
    }
    val controller = IosScheduleCalendarSettingsController(
      accountService = service,
      gatewayFactory = { gateway },
      preferences = preferences,
      runtimeIntentInvalidate = { order += "invalidate" },
      runtimeIntentSignal = { order += "signal" },
      intentMutationBoundary = boundary,
    )

    controller.selectSource("source-a")
    runCurrent()
    assertTrue(arrived.isCompleted)
    controller.disable()
    runCurrent()
    // disable 已发起但仍等待 source 事务；旧实现会在此出现第二个 invalidate，随后第一个 signal 错放第二个 fence。
    assertEquals(listOf("invalidate"), order)

    release.complete(Unit)
    runCurrent()

    assertEquals(listOf("invalidate", "signal", "invalidate", "signal"), order)
    assertEquals(listOf("source", "calendar-clear", "event-clear", "enabled", "disabled"), preferences.writes)
    assertEquals(false, preferences.get(accountId).enabled)
    assertEquals(0, gateway.upsertDeleteCount)
  }

  /** 纯内存只读 seam；故意没有 upsert/delete 方法，防止设置 controller 获得 CRUD 能力。 */
  private class FakeSettingsGateway(
    var status: IosEventKitFullAccessStatus,
  ) : IosEventKitSettingsGateway {
    var requestCount = 0
    var suspendRequest = false
    var requestResult: IosEventKitPermissionResult = IosEventKitPermissionResult.Granted
    var cachedSelection = IosEventKitCachedSelection.NoCalendarHint
    var upsertDeleteCount = 0
    var sources = listOf(IosEventKitSettingsSource("source-a", "测试日历账户"))

    override fun fullAccessStatus(): IosEventKitFullAccessStatus = status

    override suspend fun requestFullAccess(): IosEventKitPermissionResult {
      requestCount += 1
      if (suspendRequest) awaitCancellation()
      if (requestResult is IosEventKitPermissionResult.Granted) {
        status = IosEventKitFullAccessStatus.FULL_ACCESS
      }
      return requestResult
    }

    override fun sources(): IosEventKitSettingsReadResult<List<IosEventKitSettingsSource>> =
      IosEventKitSettingsReadResult.Available(sources)

    override fun checkCachedSelection(
      sourceIdentifier: String,
      calendarIdentifier: String?,
    ): IosEventKitCachedSelection = cachedSelection
  }

  /** 纯内存账号偏好 fake，记录 source → calendar → enabled 的写入顺序且不触碰真实平台存储。 */
  private class FakeSettingsPreferences : IosScheduleCalendarPreferenceStore {
    private val values = mutableMapOf<String, IosScheduleCalendarExportSettings.Preference>()
    val writes = mutableListOf<String>()
    /** 用于顺序测试观察 intent 写入，默认不影响其他纯内存 fixture。 */
    var writeObserver: ((String) -> Unit)? = null

    private fun recordWrite(value: String) {
      writes += value
      writeObserver?.invoke(value)
    }

    override fun get(accountId: String): IosScheduleCalendarExportSettings.Preference =
      values[accountId.lowercase()] ?: IosScheduleCalendarExportSettings.Preference(false, null, null)

    override fun updateSourceIdentifier(accountId: String, sourceIdentifier: String) {
      val key = accountId.lowercase()
      values[key] = get(key).copy(sourceIdentifier = sourceIdentifier)
      recordWrite("source")
    }

    override fun updateCalendarIdentifier(accountId: String, calendarIdentifier: String?) {
      val key = accountId.lowercase()
      values[key] = get(key).copy(calendarIdentifier = calendarIdentifier)
      recordWrite(if (calendarIdentifier == null) "calendar-clear" else "calendar-retain")
    }

    override fun setEnabled(accountId: String, enabled: Boolean) {
      val key = accountId.lowercase()
      values[key] = get(key).copy(enabled = enabled)
      recordWrite(if (enabled) "enabled" else "disabled")
    }

    override fun replaceEventReference(
      accountId: String,
      projectionId: CalendarProjectionId,
      eventRef: com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef,
    ) {
      val key = accountId.lowercase()
      values[key] = get(key).copy(eventReferences = get(key).eventReferences + (projectionId to eventRef))
      recordWrite("event-replace")
    }

    override fun removeEventReference(accountId: String, projectionId: CalendarProjectionId) {
      val key = accountId.lowercase()
      values[key] = get(key).copy(eventReferences = get(key).eventReferences - projectionId)
      recordWrite("event-remove")
    }

    override fun clearEventReferences(accountId: String) {
      val key = accountId.lowercase()
      values[key] = get(key).copy(eventReferences = emptyMap())
      recordWrite("event-clear")
    }
  }

  /** 最小账户生命周期 fake：每次切换都取消旧 scope，复现同账号新 generation 的迟到回调边界。 */
  private class FakeSettingsAccount(
    private val parentScope: CoroutineScope,
    initial: AccountSession,
  ) : IAccountService {
    private val sessionDelegate = MutableStateFlow(initial)
    private var sessionReadCount = 0
    private var scheduledSessionSwitch: Pair<Int, AccountSession>? = null

    override val session: StateFlow<AccountSession> = object : StateFlow<AccountSession> by sessionDelegate {
      override val value: AccountSession
        get() {
          sessionReadCount += 1
          scheduledSessionSwitch?.takeIf { it.first == sessionReadCount }?.let { (_, next) ->
            scheduledSessionSwitch = null
            switchTo(next)
          }
          return sessionDelegate.value
        }
    }
    override val state = MutableStateFlow(initial.state)
    private var owner = SupervisorJob(parentScope.coroutineContext[Job])
    private var scope = CoroutineScope(parentScope.coroutineContext + owner)

    override val accountCoroutineScope: CoroutineScope
      get() = scope

    override fun accountCoroutineScopeFor(expectedSession: AccountSession): CoroutineScope? =
      scope.takeIf { session.value === expectedSession }

    /** 在指定的权威 session 读取点切号，以固定复现启动后重读 session 的竞态。 */
    fun switchSessionOnRead(readCount: Int, next: AccountSession) {
      scheduledSessionSwitch = readCount to next
    }

    fun switchTo(next: AccountSession) {
      owner.cancel()
      owner = SupervisorJob(parentScope.coroutineContext[Job])
      scope = CoroutineScope(parentScope.coroutineContext + owner)
      state.value = next.state
      sessionDelegate.value = next
    }
  }

  /** 生成不可按值复用的 Login state，保证测试覆盖 exact session identity。 */
  private fun session(accountId: String, generation: Long): AccountSession =
    AccountSession(generation, AccountState.Login(accountId))

  private companion object {
    val SCOPE = CalendarExportScope("ios_eventkit_gateway")
    val SCHEDULE_ID = ScheduleId("018f7d5a-1234-7abc-8def-1234567890ab")
    val SECOND_SCHEDULE_ID = ScheduleId("018f7d5a-5678-7abc-8def-1234567890ab")
    val ORDINARY_RECOVERY_SCHEDULE_ID = ScheduleId("018f7d5a-7777-7abc-8def-1234567890ab")
    val PRE_COMMIT_RECOVERY_SCHEDULE_ID = ScheduleId("018f7d5a-bbbb-7abc-8def-1234567890ab")
    val SHIFTED_RECOVERY_SCHEDULE_ID = ScheduleId("018f7d5a-8888-7abc-8def-1234567890ab")
    val ACK_RECOVERY_SCHEDULE_ID = ScheduleId("018f7d5a-aaaa-7abc-8def-1234567890ab")
    val RETIRE_RECOVERY_SCHEDULE_ID = ScheduleId("018f7d5a-cccc-7abc-8def-1234567890ab")
    val FOREIGN_SCHEDULE_ID = ScheduleId("018f7d5a-9999-7abc-8def-1234567890ab")
    const val AUTHORITY_ANCHOR_EPOCH_SECONDS = 1_800_000_000L
    const val SOURCE_ID = "selected-source"
    const val OTHER_SOURCE_ID = "other-source"
    const val CALENDAR_ID = "managed-calendar"
    const val EVENT_ID = "managed-event"
    const val ZONE = "Asia/Shanghai"
  }
}
