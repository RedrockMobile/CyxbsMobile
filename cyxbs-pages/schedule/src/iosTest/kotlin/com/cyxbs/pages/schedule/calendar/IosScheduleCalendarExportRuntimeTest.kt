package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.calendar.ScheduleCalendarProjectionFactory
import com.cyxbs.pages.schedule.domain.calendar.ScheduleCalendarSource
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCompletion
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S205-04 process-runtime 的纯内存合同测试。
 *
 * 测试只实现 runtime 窄 seam，绝不构造 EKEventStore、读取用户日历或依赖真实 AccountSettings。重点锁定 disabled、
 * 缺 source、权限不足和零投影时的零 CRUD/零 cache write，以及多个 repository signal 合并为一次 Full。
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
class IosScheduleCalendarExportRuntimeTest {
  @Test
  fun disabledMissingSourceAccessDeniedAndEmptyProjectionHaveNoPlatformOrCacheEffects() = runTest {
    val account = FakeAccount(backgroundScope, session("runtime-gate"))
    val repository = FakeRepository("runtime-gate")
    val gateway = FakeRuntimeGateway()

    fun run(preference: IosScheduleCalendarExportSettings.Preference) {
      val preferences = FakePreferences(preference)
      val runtime = IosScheduleCalendarExportRuntime(
        accountService = account,
        repository = repository,
        session = account.session.value,
        scope = account.accountCoroutineScope,
        owner = account.accountCoroutineScope.coroutineContext[Job]!!,
        gatewayFactory = { gateway },
        preferences = preferences,
      )
      runtime.start()
      runCurrent()
      runtime.stop()
      assertEquals(0, gateway.lookupCount + gateway.upsertCount + gateway.deleteCount)
      assertEquals(0, preferences.cacheWrites)
    }

    run(IosScheduleCalendarExportSettings.Preference(false, "selected", null))
    run(IosScheduleCalendarExportSettings.Preference(true, null, null))

    gateway.status = IosEventKitFullAccessStatus.DENIED
    run(IosScheduleCalendarExportSettings.Preference(true, "selected", null))
    gateway.status = IosEventKitFullAccessStatus.FULL_ACCESS
    run(IosScheduleCalendarExportSettings.Preference(true, "selected", null))
  }

  @Test
  fun initializedAndCommittedSignalsAreConflatedBeforeAnEmptyFullReconcile() = runTest {
    val account = FakeAccount(backgroundScope, session("runtime-coalesce"))
    val repository = FakeRepository("runtime-coalesce")
    val gateway = FakeRuntimeGateway()
    val preferences = FakePreferences(
      IosScheduleCalendarExportSettings.Preference(true, "selected", null),
    )
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gateway },
      preferences = preferences,
    )

    runtime.start()
    repository.emit(ScheduleCalendarChange.Initialized("runtime-coalesce"))
    repository.emit(ScheduleCalendarChange.SchedulesCommitted("runtime-coalesce", emptySet()))
    runCurrent()
    runtime.stop()

    // 空投影不会调用 CRUD；多信号只会合并成 actor 可处理的一份 Full，不能自动并发多个 reconcile。
    assertEquals(0, gateway.lookupCount + gateway.upsertCount + gateway.deleteCount)
    assertEquals(0, preferences.cacheWrites)
  }

  /** 远端响应持久化后必须重新读取最新仓库快照，不能等到下一次本地编辑才刷新 EventKit。 */
  @Test
  fun remoteCommittedRequestsFullReconcileFromPersistedSnapshot() = runTest {
    val accountId = "runtime-remote-committed"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId)
    val gateway = FakeRuntimeGateway()
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gateway },
      preferences = FakePreferences(
        IosScheduleCalendarExportSettings.Preference(true, "selected", null),
      ),
    )

    runtime.start()
    repository.awaitCalendarChangesCollector()
    runCurrent()
    val readsBeforeRemoteCommit = repository.snapshotReads

    repository.emitAwait(ScheduleCalendarChange.RemoteCommitted(accountId, scheduleIds = null))
    runCurrent()
    runtime.stop()

    assertTrue(repository.snapshotReads > readsBeforeRemoteCommit)
  }

  /**
   * iOS initializer 在 mutex 内只安装 entry：handoff release 前不能读取 preference/snapshot、检查 full access 或调用
   * gateway/cache；release 后 baseline 与 replayed Initialized 合并为一个 Full，重复 release 仍是 one-shot。
   */
  @Test
  fun registryHandoffDefersRuntimeAndMergesBaselineWithInitializedReplay() = runTest {
    val accountId = "runtime-post-mutex-handoff"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId, replay = 1).apply {
      replaceSchedules(listOf(schedule()))
      emit(ScheduleCalendarChange.Initialized(accountId))
    }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    var runtime: IosScheduleCalendarExportRuntime? = null
    val handoff = IosScheduleCalendarExportRuntimeRegistry.registerForTest(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
    ) { explicitIntentPending, automaticReplayBlocked ->
      IosScheduleCalendarExportRuntime(
        accountService = account,
        repository = repository,
        session = account.session.value,
        scope = account.accountCoroutineScope,
        owner = account.accountCoroutineScope.coroutineContext[Job]!!,
        gatewayFactory = { gateway },
        preferences = preferences,
        initialExplicitIntentPending = explicitIntentPending,
        initialAutomaticReplayBlocked = automaticReplayBlocked,
      ).also { runtime = it }
    }

    assertEquals(0, preferences.preferenceReads)
    assertEquals(0, repository.snapshotReads)
    assertEquals(0, gateway.fullAccessStarts)
    assertEquals(0, gateway.crudCount + preferences.cacheWrites)

    handoff.releaseAfterInitializationMutex()
    runCurrent()
    assertEquals(1, gateway.upsertCount)
    // 首次 atomic pair 在 calendar 写前 reread，并在 event ledger durable 后再次复核完整 locator 才执行 ack。
    assertEquals(3, preferences.preferenceReads)
    assertTrue(repository.snapshotReads > 0)
    assertEquals(1, gateway.fullAccessStarts)
    assertEquals(2, preferences.cacheWrites)
    assertEquals(1, gateway.acknowledgeCount)

    handoff.releaseAfterInitializationMutex()
    runCurrent()
    assertEquals(1, gateway.upsertCount)
    runtime?.stop()
  }

  /**
   * callback 与 post-mutex release 之间发生相同 exact session replacement 时，旧 handoff 必须 no-op；只有当前 entry
   * release 后允许一个 Full，不能由旧 repository 初始化抢跑。
   */
  @Test
  fun replacementBeforeHandoffReleaseStartsOnlyCurrentRuntimeOnce() = runTest {
    val accountId = "runtime-handoff-replacement"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val oldGateway = LedgerGateway()
    val currentGateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    var oldRuntime: IosScheduleCalendarExportRuntime? = null
    var currentRuntime: IosScheduleCalendarExportRuntime? = null
    fun register(gateway: LedgerGateway, capture: (IosScheduleCalendarExportRuntime) -> Unit) =
      IosScheduleCalendarExportRuntimeRegistry.registerForTest(
        accountService = account,
        repository = repository,
        session = account.session.value,
        scope = account.accountCoroutineScope,
        owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      ) { explicitIntentPending, automaticReplayBlocked ->
        IosScheduleCalendarExportRuntime(
          accountService = account,
          repository = repository,
          session = account.session.value,
          scope = account.accountCoroutineScope,
          owner = account.accountCoroutineScope.coroutineContext[Job]!!,
          gatewayFactory = { gateway },
          preferences = preferences,
          initialExplicitIntentPending = explicitIntentPending,
          initialAutomaticReplayBlocked = automaticReplayBlocked,
        ).also(capture)
      }

    val oldHandoff = register(oldGateway) { oldRuntime = it }
    val currentHandoff = register(currentGateway) { currentRuntime = it }
    oldHandoff.releaseAfterInitializationMutex()
    runCurrent()
    assertEquals(0, oldGateway.crudCount + preferences.cacheWrites)

    currentHandoff.releaseAfterInitializationMutex()
    runCurrent()
    assertEquals(1, currentGateway.upsertCount)
    assertEquals(0, oldGateway.crudCount)
    oldRuntime?.stop()
    currentRuntime?.stop()
  }

  /**
   * 一条投影的 Create → Update → NoOp → Delete 必须只以 ledger 中的严格 locator 继续操作。
   *
   * fake 记录每轮传入的 hints，证明首次 atomic pair 写回的 calendar/event hint 会被下一轮完整对账读取；
   * 没有 ref 时 planner 只能创建，删除也只能在已验证 ref 的前提下发生。
   */
  @Test
  fun atomicCreateThenUpdateNoOpAndDeleteUsePersistedVerifiedLedgerHints() = runTest {
    val accountId = "runtime-ledger"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(
      IosScheduleCalendarExportSettings.Preference(true, "source", null),
    )
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()
    assertEquals("calendar", preferences.value.calendarIdentifier)
    assertEquals(1, preferences.value.eventReferences.size)
    assertEquals(1, gateway.upsertCount)

    repository.replaceSchedules(listOf(schedule(title = "已更新")))
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(2, gateway.upsertCount)
    assertEquals("calendar", gateway.upsertHints.last().calendarIdentifier)
    assertEquals("event-1", gateway.upsertHints.last().eventIdentifier)

    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(2, gateway.upsertCount)
    // 每轮先以 ledger ref 做预验证，NoOp 还会再次确认同一 ref 后才刷新缓存。
    assertEquals(3, gateway.lookupCount)

    repository.replaceSchedules(emptyList())
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(1, gateway.deleteCount)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    runtime.stop()
  }

  /** preflight Managed lookup 的 proof 与 binding 必须成对；任一半包都在 planner CRUD 前终结当前 generation。 */
  @Test
  fun preflightRecoveryCapabilityRejectsProofOrBindingHalfPackagesBeforePlannerCrud() = runTest {
    val cases = listOf(
      IosEventKitLocatorRecoveryProof() to null,
      null to IosEventKitGatewayBinding("source", "calendar", "event-1"),
    )
    cases.forEachIndexed { index, recovery ->
      val accountId = "runtime-preflight-half-$index"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway()
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
      val runtime = runtime(account, repository, gateway, preferences)

      runtime.start()
      runCurrent()
      gateway.lookupRecoveryByCall[1] = recovery
      repository.replaceSchedules(listOf(schedule(title = "半包后不得更新")))
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()

      assertEquals(1, gateway.upsertCount)
      assertEquals(0, gateway.deleteCount)
      assertEquals(0, gateway.retirementCount)
      val lookups = gateway.lookupCount
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(lookups, gateway.lookupCount)
      runtime.stop()
    }
  }

  /**
   * NoOp 必须忽略 preflight proof，并消费第二次 fresh lookup 重签的 latest proof；旧 proof 不得触发 retirement 或 ack。
   */
  @Test
  fun noOpAcknowledgesOnlySecondFreshLookupProofWithoutRetiringPreflightProof() = runTest {
    val accountId = "runtime-noop-latest-proof"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()
    val preflightProof = IosEventKitLocatorRecoveryProof()
    val freshProof = IosEventKitLocatorRecoveryProof()
    val binding = IosEventKitGatewayBinding("source", "calendar", "event-1")
    gateway.lookupRecoveryByCall[1] = preflightProof to binding
    gateway.lookupRecoveryByCall[2] = freshProof to binding
    gateway.recoveryProof = freshProof

    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()

    assertEquals(2, gateway.lookupCount)
    assertEquals(0, gateway.retirementCount)
    assertEquals(2, gateway.acknowledgeCount, "首次 create ack + NoOp second fresh ack")
    assertEquals(1, gateway.upsertCount)
    runtime.stop()
  }

  /**
   * Update/Delete 只有携带 preflight capability 时才先 retirement；拒绝或异常必须零普通 CRUD 并禁止同代 replay。
   */
  @Test
  fun rejectedOrThrowingPreflightRetirementStopsUpdateAndDeleteBeforeCrud() = runTest {
    data class Case(val name: String, val delete: Boolean, val throws: Boolean)
    val cases = listOf(
      Case("update-rejected", delete = false, throws = false),
      Case("update-throw", delete = false, throws = true),
      Case("delete-rejected", delete = true, throws = false),
      Case("delete-throw", delete = true, throws = true),
    )
    cases.forEach { case ->
      val accountId = "runtime-retire-${case.name}"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway().apply {
        retirement = IosEventKitLocatorEligibilityRetirement.REJECTED
        throwOnRetirement = case.throws
      }
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
      val runtime = runtime(account, repository, gateway, preferences)

      runtime.start()
      runCurrent()
      val proof = IosEventKitLocatorRecoveryProof()
      gateway.lookupRecoveryByCall[1] = proof to IosEventKitGatewayBinding("source", "calendar", "event-1")
      repository.replaceSchedules(if (case.delete) emptyList() else listOf(schedule(title = "待更新")))
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()

      assertEquals(1, gateway.retirementCount)
      assertEquals(1, gateway.upsertCount, "${case.name} 不得进入 Update upsert")
      assertEquals(0, gateway.deleteCount, "${case.name} 不得进入 Delete CRUD")
      val calls = gateway.crudCount + gateway.retirementCount
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(calls, gateway.crudCount + gateway.retirementCount)
      runtime.stop()
    }
  }

  /** 有 proof 的 Update 成功 retirement 后才 CRUD；后续普通无 proof Update/Delete 不增加 retirement 调用。 */
  @Test
  fun updateRetiresPreflightEligibilityOnceWhileOrdinaryUpdateAndDeleteSkipRetirement() = runTest {
    val accountId = "runtime-retire-success"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()
    gateway.lookupRecoveryByCall[1] = IosEventKitLocatorRecoveryProof() to
      IosEventKitGatewayBinding("source", "calendar", "event-1")
    repository.replaceSchedules(listOf(schedule(title = "第一次更新")))
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(1, gateway.retirementCount)
    assertEquals(2, gateway.upsertCount)

    repository.replaceSchedules(listOf(schedule(title = "普通第二次更新")))
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(1, gateway.retirementCount)
    assertEquals(3, gateway.upsertCount)

    repository.replaceSchedules(emptyList())
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(1, gateway.retirementCount)
    assertEquals(1, gateway.deleteCount)
    runtime.stop()
  }

  /**
   * 缓存投影在 planner 中已是 NoOp 时，第二次 direct lookup 若发现 duplicate canonical sibling，不能借 NoOp 刷新
   * ledger 掩盖歧义；本代必须 terminal-uncertain，后续 signal 不得自动 replay。
   */
  @Test
  fun cachedNoOpDuplicateCanonicalEventStopsGenerationWithoutRefreshingLedger() = runTest {
    val accountId = "runtime-noop-duplicate"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()
    val writesBeforeDuplicate = preferences.cacheWrites
    // 第二轮的首个 lookup 是 planner 前 strict pre-verify；第二个才是 NoOp 的重复 direct ref 检查。
    gateway.failLookupOnCall = 2 to IosEventKitGatewayFailure.AMBIGUOUS_EVENT
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()

    assertEquals(1, gateway.upsertCount)
    assertEquals(0, gateway.deleteCount)
    assertEquals(writesBeforeDuplicate, preferences.cacheWrites)
    val lookupsAfterDuplicate = gateway.lookupCount
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(lookupsAfterDuplicate, gateway.lookupCount)
    assertEquals(writesBeforeDuplicate, preferences.cacheWrites)
    runtime.stop()
  }

  /**
   * source、calendar 与 identity 的确定性失效必须先 durable disable，再依次清 calendar hint 和整份 ledger。
   *
   * 每种终态都保留 source id 供设置页显示 SOURCE_MISSING；但 enabled=false 与空 calendar hint 会使 calendar 缺失时
   * 映射为 UNCONFIGURED/exportEnabled=false。终结当前 generation 后，同代 repository signal 不得再次 CRUD。
   */
  @Test
  fun deterministicInvalidSelectionsDisableBeforeClearingLocatorsAndStopSameGenerationReplay() = runTest {
    val failures = listOf(
      IosEventKitGatewayFailure.SOURCE_DISAPPEARED,
      IosEventKitGatewayFailure.CALENDAR_DISAPPEARED,
      IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR,
      IosEventKitGatewayFailure.FOREIGN_IDENTITY,
    )
    failures.forEach { failure ->
      val accountId = "runtime-invalid-${failure.name}"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway().apply { failLookup = failure }
      val id = projectionId(accountId)
      val preferences = FakePreferences(
        IosScheduleCalendarExportSettings.Preference(
          enabled = true,
          sourceIdentifier = "source",
          calendarIdentifier = "calendar",
          eventReferences = mapOf(id to PlatformCalendarEventRef("event-1")),
        ),
      )
      val runtime = runtime(account, repository, gateway, preferences)

      runtime.start()
      runCurrent()

      assertEquals(false, preferences.value.enabled, "$failure must durably turn export off")
      assertEquals(null, preferences.value.calendarIdentifier, "$failure must clear calendar hint")
      assertEquals(emptyMap(), preferences.value.eventReferences, "$failure must clear ledger")
      assertEquals(
        listOf("enabled:false", "calendar:null", "ledger:clear"),
        preferences.writeAttempts,
        "$failure must persist fail-closed ordering",
      )
      val callsAfterFailure = gateway.lookupCount + gateway.upsertCount + gateway.deleteCount

      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(
        callsAfterFailure,
        gateway.lookupCount + gateway.upsertCount + gateway.deleteCount,
        "$failure must not replay the terminal generation",
      )
      runtime.stop()
    }
  }

  /**
   * 无效 locator 的多步清理不是原子事务：disable 失败保留旧偏好；disable 已提交后，calendar 或 ledger 清理失败仍保持
   * enabled=false。三种情况都终结当前 generation，不能把失败误报为可安全自动恢复。
   */
  @Test
  fun invalidSelectionWriteFailuresPreserveFailClosedDurableBoundaryWithoutClaimingAtomicity() = runTest {
    data class Case(
      val name: String,
      val configureFailure: (FakePreferences) -> Unit,
      val expectedEnabled: Boolean,
      val expectedCalendar: String?,
      val expectedLedger: Boolean,
      val expectedAttempts: List<String>,
    )

    val cases = listOf(
      Case(
        name = "disable",
        configureFailure = { it.failEnabledWrite = true },
        expectedEnabled = true,
        expectedCalendar = "calendar",
        expectedLedger = true,
        expectedAttempts = listOf("enabled:false"),
      ),
      Case(
        name = "calendar cleanup",
        configureFailure = { it.failCalendarWrite = true },
        expectedEnabled = false,
        expectedCalendar = "calendar",
        expectedLedger = true,
        expectedAttempts = listOf("enabled:false", "calendar:null"),
      ),
      Case(
        name = "ledger cleanup",
        configureFailure = { it.failLedgerClearWrite = true },
        expectedEnabled = false,
        expectedCalendar = null,
        expectedLedger = true,
        expectedAttempts = listOf("enabled:false", "calendar:null", "ledger:clear"),
      ),
    )
    cases.forEach { case ->
      // CalendarExportScope 只接受规范账号字符，case 名仅用于断言文案，不能直接充当账号 scope。
      val accountId = "runtimeinvalidwrite${case.expectedAttempts.size}"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway().apply { failLookup = IosEventKitGatewayFailure.CALENDAR_DISAPPEARED }
      val id = projectionId(accountId)
      val originalReferences = mapOf(id to PlatformCalendarEventRef("event-1"))
      val preferences = FakePreferences(
        IosScheduleCalendarExportSettings.Preference(true, "source", "calendar", originalReferences),
      ).also(case.configureFailure)
      val runtime = runtime(account, repository, gateway, preferences)

      runtime.start()
      runCurrent()

      assertEquals(case.expectedEnabled, preferences.value.enabled, "${case.name} durable enabled state")
      assertEquals(case.expectedCalendar, preferences.value.calendarIdentifier, "${case.name} calendar state")
      assertEquals(
        originalReferences,
        preferences.value.eventReferences,
        "${case.name} must not claim cleanup steps that did not durably complete",
      )
      assertEquals(case.expectedAttempts, preferences.writeAttempts, "${case.name} write order")
      val callsAfterFailure = gateway.lookupCount + gateway.upsertCount + gateway.deleteCount

      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(
        callsAfterFailure,
        gateway.lookupCount + gateway.upsertCount + gateway.deleteCount,
        "${case.name} must terminate the same generation",
      )
      runtime.stop()
    }
  }

  /**
   * canonical 但 foundation 不支持 occurrence 的已受管 master 不是 foreign ref：runtime 必须保留 calendar 与全部
   * ledger，不能删除/降级 master 或因 incomplete planner 创建新的 single event。
   */
  @Test
  fun unsupportedManagedMasterPreservesCalendarAndAllLedgerEntries() = runTest {
    val accountId = "runtime-unsupported-master"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val masterId = projectionId(
      accountId,
      com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind.SERIES_MASTER,
    )
    val otherId = projectionId(accountId)
    val originalReferences = mapOf(
      masterId to PlatformCalendarEventRef("master-ref"),
      otherId to PlatformCalendarEventRef("other-ref"),
    )
    val gateway = LedgerGateway().apply { unsupportedManagedLookup = true }
    val preferences = FakePreferences(
      IosScheduleCalendarExportSettings.Preference(
        enabled = true,
        sourceIdentifier = "source",
        calendarIdentifier = "calendar",
        eventReferences = originalReferences,
      ),
    )
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()
    assertEquals("calendar", preferences.value.calendarIdentifier)
    assertEquals(originalReferences, preferences.value.eventReferences)
    assertEquals(0, preferences.cacheWrites)
    assertEquals(0, gateway.upsertCount + gateway.deleteCount)

    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals("calendar", preferences.value.calendarIdentifier)
    assertEquals(originalReferences, preferences.value.eventReferences)
    assertEquals(0, gateway.upsertCount + gateway.deleteCount)
    runtime.stop()
  }

  /**
   * preflight 必须先只读检查完整 ledger。即使 canonical URI 排序较早的 ref 已经缺失，较后的 unsupported master
   * 仍要保留 calendar 与整份原 ledger，不能让局部 remove 破坏保守停机语义。
   */
  @Test
  fun missingRefBeforeUnsupportedManagedPreservesEntireLedgerWithoutPlannerEffects() = runTest {
    val accountId = "runtime-mixed-ledger"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val firstId = CalendarProjectionId(
      scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId),
      scheduleId = ScheduleId("018f7d5a-0000-7abc-8def-1234567890ac"),
      kind = com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind.SINGLE,
    )
    val secondId = CalendarProjectionId(
      scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId),
      scheduleId = ScheduleId("018f7d5a-ffff-7abc-8def-1234567890ac"),
      kind = com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind.SINGLE,
    )
    assertTrue(
      CalendarProjectionUriCodec.encode(firstId) < CalendarProjectionUriCodec.encode(secondId),
      "fixture 必须按 canonical URI 先缺失、后 unsupported 排序",
    )
    val firstRef = PlatformCalendarEventRef("missing-ref")
    val secondRef = PlatformCalendarEventRef("unsupported-ref")
    val originalReferences = linkedMapOf(firstId to firstRef, secondId to secondRef)
    val gateway = LedgerGateway().apply { unsupportedManagedRefs = setOf(secondRef) }
    val preferences = FakePreferences(
      IosScheduleCalendarExportSettings.Preference(
        enabled = true,
        sourceIdentifier = "source",
        calendarIdentifier = "calendar",
        eventReferences = originalReferences,
      ),
    )
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()

    assertEquals("calendar", preferences.value.calendarIdentifier)
    assertEquals(originalReferences, preferences.value.eventReferences)
    assertEquals(0, preferences.cacheWrites)
    assertEquals(2, gateway.lookupCount)
    assertEquals(0, gateway.upsertCount + gateway.deleteCount)
    runtime.stop()
  }

  /** ordinary existing event 即使 `changed=false`，没有 fresh typed proof 也不能回填空 calendar hint。 */
  @Test
  fun nullCalendarHintRejectsOrdinaryNonAtomicExistingEventWithoutRecoveryProof() = runTest {
    val accountId = "runtime-provenance"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway().apply {
      ordinaryExistingWithoutProof = true
      recoveryProof = null
    }
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()
    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    val upserts = gateway.upsertCount
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(upserts, gateway.upsertCount)
    runtime.stop()
  }

  /**
   * 生产 gateway 与 runtime 的组合不能把普通 non-atomic exact event 当作 ambiguous atomic recovery。
   *
   * fixture 直接预置可写 calendar 与完全一致的 canonical event，但从未调用 `createCalendarWithEvent`，因此没有 unknown
   * attempt provenance。runtime 必须保留空 calendar/ledger 并终结当代，后续 repository signal 也不能重放。
   */
  @Test
  fun productionGatewayOrdinaryNonAtomicExactEventCannotBackfillNullLocator() = runTest {
    val accountId = "runtime-production-ordinary-provenance"
    val account = FakeAccount(backgroundScope, session(accountId))
    val targetSchedule = schedule()
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(targetSchedule)) }
    val scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId)
    val target = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(targetSchedule), emptyList()),
      scope,
    ).events.single()
    val store = AmbiguousCommitRecoveryStore().apply { seedOrdinaryExactEvent(target) }
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
    )

    runtime.start()
    runCurrent()

    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    assertEquals(0, preferences.cacheWrites)
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(0, store.createCalendarCount)
    assertEquals(0, store.saveCount)
    runtime.stop()
  }

  /**
   * production gateway + runtime 必须把 commit 前失败与 commit-outcome-unknown 分开。
   *
   * 首轮 atomic create 在 durable pair 产生前返回普通 AMBIGUOUS，runtime 终结旧 generation；随后同一 store 被外部注入
   * 完全一致的 ordinary event。显式 intent 打开的新 generation 可以 fresh 扫描，但因为没有 process-resident eligibility，
   * gateway 不得签发 proof，runtime 也不得 durable 回填 calendar/event locator 或重复 create/save。
   */
  @Test
  fun preCommitAtomicFailureCannotMintProofForLaterOrdinaryExactEvent() = runTest {
    val accountId = "runtime-precommit-provenance"
    val account = FakeAccount(backgroundScope, session(accountId))
    val targetSchedule = schedule()
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(targetSchedule)) }
    val target = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(targetSchedule), emptyList()),
      IosScheduleCalendarExportSettings.scopeForAccount(accountId),
    ).events.single()
    val store = AmbiguousCommitRecoveryStore(firstAtomicFailureBeforeCommit = true)
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
    )

    runtime.start()
    runCurrent()
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    assertEquals(0, store.calendarCount)
    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)

    store.seedOrdinaryExactEvent(target)
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()

    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    assertEquals(0, preferences.cacheWrites)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    runtime.stop()
  }

  /** recovery proof 不能绕过 source identity 或完整 locator 校验；伪造/partial binding 必须终结本代。 */
  @Test
  fun freshCanonicalRecoveryProofRejectsChangedSourceAndPartialBindings() = runTest {
    val invalidBindings = listOf(
      IosEventKitGatewayBinding("other-source", "calendar", "event-1"),
      IosEventKitGatewayBinding("source", "", "event-1"),
      IosEventKitGatewayBinding("source", "calendar", ""),
    )
    invalidBindings.forEachIndexed { index, invalidBinding ->
      val accountId = "runtime-invalid-recovery-binding-$index"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway().apply {
        ordinaryExistingWithoutProof = true
        recoveryProof = IosEventKitLocatorRecoveryProof()
        bindingOverride = invalidBinding
      }
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
      val runtime = runtime(account, repository, gateway, preferences)

      runtime.start()
      runCurrent()
      assertEquals(null, preferences.value.calendarIdentifier)
      assertEquals(emptyMap(), preferences.value.eventReferences)
      assertEquals(0, preferences.cacheWrites)
      val upserts = gateway.upsertCount
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(upserts, gateway.upsertCount, "invalid recovery binding must terminate generation: $invalidBinding")
      runtime.stop()
    }
  }

  /**
   * 第一次 atomic pair 已真实提交但返回模糊终态时，本代必须保持 terminal 且不写 locator。
   *
   * 同一个纯内存 store 只在后续显式 intent 打开的新 generation 中接受 fresh canonical 扫描；gateway 证明唯一 calendar、
   * 唯一目标 event、无 sibling 且 identity/fingerprint 完整一致后，runtime 才能回填 calendar 与 ledger，不重复创建或保存。
   */
  @Test
  fun ambiguousCommittedFirstPairRecoversOnLaterExplicitIntentUsingSameFakeStore() = runTest {
    val accountId = "runtime-ambiguous-canonical-recovery"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val store = AmbiguousCommitRecoveryStore()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { scope -> IosEventKitFullAccessGateway(scope, store) },
      preferences = preferences,
    )

    runtime.start()
    runCurrent()
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    assertEquals(1, store.calendarCount)
    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)

    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(1, store.createCalendarCount, "原失败代不得自动 retry/replay/recover")
    assertEquals(1, store.saveCount)

    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()

    assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
    assertEquals(
      setOf(PlatformCalendarEventRef("recovery-event")),
      preferences.value.eventReferences.values.toSet(),
    )
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount, "fresh canonical recovery 不得把既有 event 再次保存为普通非原子写入")
    runtime.stop()
  }

  /**
   * successful atomic commit 返回后若 generation 在 EVENTKIT_STORE boundary 失效，gateway 仍必须保留 eligibility。
   *
   * 旧 generation 不写 cache；后续显式 intent 在同一 fake store 中 fresh 恢复，create/save 总数保持一，证明成功返回不等于
   * locator 已 durable，也不能因 lifecycle invalidation 丢失恢复资格。
   */
  @Test
  fun successfulAtomicCommitSurvivesEventKitBoundaryInvalidationAndRecoversLater() = runTest {
    val accountId = "runtime-success-eventkit-invalidation"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val store = AmbiguousCommitRecoveryStore(firstAtomicCompletionLost = false)
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val boundary = LateCompletionBarrier(IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
      suspensionBoundary = boundary,
    )

    runtime.start()
    runCurrent()
    assertTrue(boundary.arrived.isCompleted)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    boundary.release.complete(Unit)
    runCurrent()

    assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
    assertEquals(setOf(PlatformCalendarEventRef("recovery-event")), preferences.value.eventReferences.values.toSet())
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    runtime.stop()
  }

  /**
   * calendar 与 event ledger 已 durable、但第二个 CACHE_WRITE boundary 后 generation 失效时，stale generation 不得 ack。
   *
   * 下一显式 generation 从完整 locator 走 ordinary hint 定位，但 gateway 发现同 target 仍有未 ack eligibility 后必须 fresh 重签，
   * runtime 再复核 durable locator 并完成 ack；全程不增加 create/save。
   */
  @Test
  fun lifecycleFailureAfterBothCacheWritesRetainsEligibilityForLaterAcknowledgement() = runTest {
    val accountId = "runtime-post-ledger-lifecycle"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val store = AmbiguousCommitRecoveryStore(firstAtomicCompletionLost = false)
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val boundary = LateCompletionBarrier(
      target = IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE,
      targetOccurrence = 2,
    )
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
      suspensionBoundary = boundary,
    )

    runtime.start()
    runCurrent()
    assertTrue(boundary.arrived.isCompleted)
    assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
    assertTrue(preferences.value.eventReferences.isNotEmpty())
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    boundary.release.complete(Unit)
    runCurrent()

    assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
    assertEquals(setOf(PlatformCalendarEventRef("recovery-event")), preferences.value.eventReferences.values.toSet())
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)

    // 第二代 ack 已消费资格；再次人为移除 locator 后不能靠同一字符串对象获得第三次 proof。
    preferences.value = preferences.value.copy(calendarIdentifier = null, eventReferences = emptyMap())
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()
    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    runtime.stop()
  }

  /**
   * production gateway + runtime 在 durable locator 已写但 ack pending 时，Schedule 改变必须先 retire 旧 proof 再 Update。
   *
   * 更新使用同一内存 store 的普通 save；随后人为清空 locator 时，更新后的 ordinary exact event 不得重新获得 recovery proof，
   * 证明旧 eligibility 已在 CRUD 前终结而非随 Update 留存。
   */
  @Test
  fun productionGatewayRetiresPendingEligibilityBeforeUpdateAndOrdinaryEventCannotRemintProof() = runTest {
    val accountId = "runtime-production-retire-update"
    val account = FakeAccount(backgroundScope, session(accountId))
    val original = schedule()
    val updated = schedule(title = "retirement 后更新")
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(original)) }
    val store = AmbiguousCommitRecoveryStore(
      firstAtomicCompletionLost = false,
      allowOrdinaryCrud = true,
    )
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val boundary = LateCompletionBarrier(
      target = IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE,
      targetOccurrence = 2,
    )
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
      suspensionBoundary = boundary,
    )

    runtime.start()
    runCurrent()
    assertTrue(boundary.arrived.isCompleted)
    assertTrue(preferences.value.eventReferences.isNotEmpty())
    repository.replaceSchedules(listOf(updated))
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    boundary.release.complete(Unit)
    runCurrent()

    assertEquals(1, store.createCalendarCount)
    assertEquals(2, store.saveCount, "retirement 成功后才允许一次普通 Update save")
    assertEquals(
      setOf(PlatformCalendarEventRef("ordinary-event-1")),
      preferences.value.eventReferences.values.toSet(),
    )

    // 普通 update 不建立新 eligibility；locator 丢失后 canonical equality 不能自行重签 proof。
    preferences.value = preferences.value.copy(calendarIdentifier = null, eventReferences = emptyMap())
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()
    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    assertEquals(2, store.saveCount)
    runtime.stop()
  }

  /**
   * production gateway + runtime 在 ack pending 后删除 Schedule 时，必须先 retire preflight proof 再 Delete。
   *
   * 删除后向同一 fake store 注入普通 exact event，并从空 locator 发起显式 generation；若旧 eligibility 未消费会错误回填，
   * 正确实现必须保持零 cache backfill，且不能增加 create/save。
   */
  @Test
  fun productionGatewayRetiresPendingEligibilityBeforeDeleteAndLaterOrdinaryEventCannotRemintProof() = runTest {
    val accountId = "runtime-production-retire-delete"
    val account = FakeAccount(backgroundScope, session(accountId))
    val original = schedule()
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(original)) }
    val scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId)
    val target = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(original), emptyList()),
      scope,
    ).events.single()
    val store = AmbiguousCommitRecoveryStore(
      firstAtomicCompletionLost = false,
      allowOrdinaryCrud = true,
    )
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val boundary = LateCompletionBarrier(
      target = IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE,
      targetOccurrence = 2,
    )
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
      suspensionBoundary = boundary,
    )

    runtime.start()
    runCurrent()
    assertTrue(boundary.arrived.isCompleted)
    repository.replaceSchedules(emptyList())
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    boundary.release.complete(Unit)
    runCurrent()

    assertEquals(1, store.removeCount, "retirement 成功后才允许一次普通 Delete")
    assertEquals(emptyMap(), preferences.value.eventReferences)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)

    store.seedOrdinaryExactEvent(target)
    preferences.value = preferences.value.copy(calendarIdentifier = null, eventReferences = emptyMap())
    repository.replaceSchedules(listOf(original))
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()

    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    assertEquals(1, store.removeCount)
    runtime.stop()
  }

  /** production gateway 的 retirement 被拒绝或抛异常时，runtime 必须在同一 fake store 上阻止 Update/Delete CRUD。 */
  @Test
  fun productionGatewayRetirementRejectionOrThrowBlocksUpdateAndDeleteCrud() = runTest {
    data class Case(
      val name: String,
      val delete: Boolean,
      val behavior: RetirementBehavior,
    )
    val cases = listOf(
      Case("update-rejected", delete = false, RetirementBehavior.REJECT),
      Case("update-throw", delete = false, RetirementBehavior.THROW),
      Case("delete-rejected", delete = true, RetirementBehavior.REJECT),
      Case("delete-throw", delete = true, RetirementBehavior.THROW),
    )
    cases.forEach { case ->
      val accountId = "runtime-production-${case.name}"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val store = AmbiguousCommitRecoveryStore(
        firstAtomicCompletionLost = false,
        allowOrdinaryCrud = true,
      )
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
      val boundary = LateCompletionBarrier(
        target = IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE,
        targetOccurrence = 2,
      )
      var retirementCalls = 0
      val runtime = IosScheduleCalendarExportRuntime(
        accountService = account,
        repository = repository,
        session = account.session.value,
        scope = account.accountCoroutineScope,
        owner = account.accountCoroutineScope.coroutineContext[Job]!!,
        gatewayFactory = { gatewayScope ->
          RetirementInterceptingGateway(
            delegate = IosEventKitFullAccessGateway(gatewayScope, store),
            behavior = case.behavior,
            onRetirement = { retirementCalls += 1 },
          )
        },
        preferences = preferences,
        suspensionBoundary = boundary,
      )

      runtime.start()
      runCurrent()
      repository.replaceSchedules(if (case.delete) emptyList() else listOf(schedule(title = "不得提交")))
      runtime.invalidateForExplicitIntent()
      runtime.restartFromExplicitIntent()
      boundary.release.complete(Unit)
      runCurrent()

      assertEquals(1, retirementCalls)
      assertEquals(1, store.saveCount, "${case.name} 不得进入普通 Update save")
      assertEquals(0, store.removeCount, "${case.name} 不得进入 Delete remove")
      val writes = preferences.cacheWrites
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(1, retirementCalls)
      assertEquals(writes, preferences.cacheWrites)
      runtime.stop()
    }
  }

  /**
   * successful atomic commit 后，第一次 calendar cache 写失败或 calendar 已写但 event ledger 写失败都必须保留 eligibility。
   *
   * 第二种 case 刻意留下 partial locator；只有同一 process/store、source、projection target 与 exact calendar/event binding 的
   * fresh proof 才能补齐 ledger。两种恢复都不得增加 create/save 计数。
   */
  @Test
  fun successfulAtomicCommitRecoversAfterCalendarOrEventLedgerPersistenceFailure() = runTest {
    val failurePoints = listOf("calendar" to true, "event-ledger" to false)

    for ((failureName, failCalendar) in failurePoints) {
      val accountId = "runtime-success-cache-$failureName"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val store = AmbiguousCommitRecoveryStore(firstAtomicCompletionLost = false)
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null)).apply {
        failCalendarWrite = failCalendar
        failEventWrite = !failCalendar
      }
      val runtime = IosScheduleCalendarExportRuntime(
        accountService = account,
        repository = repository,
        session = account.session.value,
        scope = account.accountCoroutineScope,
        owner = account.accountCoroutineScope.coroutineContext[Job]!!,
        gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
        preferences = preferences,
      )

      runtime.start()
      runCurrent()
      assertEquals(1, store.createCalendarCount)
      assertEquals(1, store.saveCount)
      if (failCalendar) {
        assertEquals(null, preferences.value.calendarIdentifier)
      } else {
        assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
      }
      assertEquals(emptyMap(), preferences.value.eventReferences)

      preferences.failCalendarWrite = false
      preferences.failEventWrite = false
      runtime.invalidateForExplicitIntent()
      runtime.restartFromExplicitIntent()
      runCurrent()

      assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
      assertEquals(
        setOf(PlatformCalendarEventRef("recovery-event")),
        preferences.value.eventReferences.values.toSet(),
      )
      assertEquals(1, store.createCalendarCount)
      assertEquals(1, store.saveCount)
      runtime.stop()
    }
  }

  /** fresh recovery proof 签发后 cache write 失败不能消费 eligibility；下一显式 generation 仍可重新签发并完成 ack。 */
  @Test
  fun recoveryProofIssuedBeforeCacheFailureRemainsEligibleForNextExplicitGeneration() = runTest {
    val accountId = "runtime-proof-cache-failure"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val store = AmbiguousCommitRecoveryStore()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
    )

    runtime.start()
    runCurrent()
    assertEquals(null, preferences.value.calendarIdentifier)
    preferences.failCalendarWrite = true
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()
    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)

    preferences.failCalendarWrite = false
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()

    assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
    assertEquals(setOf(PlatformCalendarEventRef("recovery-event")), preferences.value.eventReferences.values.toSet())
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    runtime.stop()
  }

  /** calendar + event ledger durable 后 successful ack 必须一次性消费 eligibility，后续人为清空 locator 不再获得 recovery proof。 */
  @Test
  fun durableLocatorAcknowledgementConsumesSuccessfulAtomicEligibilityExactlyOnce() = runTest {
    val accountId = "runtime-success-ack-consumes"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val store = AmbiguousCommitRecoveryStore(firstAtomicCompletionLost = false)
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gatewayScope -> IosEventKitFullAccessGateway(gatewayScope, store) },
      preferences = preferences,
    )

    runtime.start()
    runCurrent()
    assertEquals("recovery-calendar", preferences.value.calendarIdentifier)
    assertTrue(preferences.value.eventReferences.isNotEmpty())

    // 仅为验证 process capability 已被 ack 消费而模拟 locator 丢失；普通 canonical equality 不能重新生成资格。
    preferences.value = preferences.value.copy(calendarIdentifier = null, eventReferences = emptyMap())
    runtime.invalidateForExplicitIntent()
    runtime.restartFromExplicitIntent()
    runCurrent()

    assertEquals(null, preferences.value.calendarIdentifier)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    assertEquals(1, store.createCalendarCount)
    assertEquals(1, store.saveCount)
    runtime.stop()
  }

  /**
   * 缺失的 ledger locator 只能移除自身并让新的 Full 重新建立投影；没有经过 verified lookup 的 ref 绝不能删除。
   */
  @Test
  fun missingLedgerRefNeverDeletesAndIsRemovedBeforeFreshCreatePlan() = runTest {
    val accountId = "runtime-missing-ref"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId)
    val gateway = LedgerGateway()
    val preferences = FakePreferences(
      IosScheduleCalendarExportSettings.Preference(
        enabled = true,
        sourceIdentifier = "source",
        calendarIdentifier = "calendar",
        eventReferences = mapOf(projectionId(accountId) to PlatformCalendarEventRef("missing")),
      ),
    )
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()

    assertEquals(1, gateway.lookupCount)
    assertEquals(0, gateway.deleteCount)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    runtime.stop()
  }

  /** ack 被拒绝或返回丢失时 locator 已 durable，但当前 generation 仍必须 terminal，repository signal 不得自动 replay。 */
  @Test
  fun rejectedOrLostAcknowledgementStopsSameGenerationWithoutUndoingDurableLocator() = runTest {
    val cases = listOf("rejected" to false, "lost-return" to true)
    for ((name, throwAfterAck) in cases) {
      val accountId = "runtime-ack-$name"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway().apply {
        acknowledgement = IosEventKitLocatorAcknowledgement.REJECTED
        throwAfterAcknowledgement = throwAfterAck
      }
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
      val runtime = runtime(account, repository, gateway, preferences)

      runtime.start()
      runCurrent()
      assertEquals("calendar", preferences.value.calendarIdentifier)
      assertTrue(preferences.value.eventReferences.isNotEmpty())
      assertEquals(1, gateway.acknowledgeCount)
      val upsertsAfterAck = gateway.upsertCount
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(upsertsAfterAck, gateway.upsertCount)
      assertEquals(1, gateway.acknowledgeCount)
      runtime.stop()
    }
  }

  /**
   * store 模糊、写后回读不一致与 ledger 持久化失败都会结束当前 generation；同代后续 signal 不能重放 outbound。
   */
  @Test
  fun terminalGatewayAndLedgerFailuresDoNotReplaySameGeneration() = runTest {
    val failures = listOf(
      IosEventKitGatewayFailure.STORE_AMBIGUOUS,
      IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
      // EventKit effect 已落盘后，canonical readback 发现第二个同 URI sibling。
      IosEventKitGatewayFailure.AMBIGUOUS_EVENT,
      // EventKit effect 已落盘后，平台再读授权才发现 full access 已撤销。
      IosEventKitGatewayFailure.PERMISSION_REVOKED,
    )
    failures.forEach { failure ->
      val accountId = "runtime-terminal-${failure.name}"
      val account = FakeAccount(backgroundScope, session(accountId))
      val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
      val gateway = LedgerGateway().apply {
        failUpsert = failure
        commitEffectBeforeFailure = failure in setOf(
          IosEventKitGatewayFailure.AMBIGUOUS_EVENT,
          IosEventKitGatewayFailure.PERMISSION_REVOKED,
        )
      }
      val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
      val runtime = runtime(account, repository, gateway, preferences)
      runtime.start()
      runCurrent()
      if (failure in setOf(
          IosEventKitGatewayFailure.AMBIGUOUS_EVENT,
          IosEventKitGatewayFailure.PERMISSION_REVOKED,
        )
      ) {
        assertEquals(1, gateway.committedEffectCount)
      }
      val calls = gateway.upsertCount
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(calls, gateway.upsertCount)
      assertEquals(emptyMap(), preferences.value.eventReferences)
      runtime.stop()
    }

    val accountId = "runtime-cache-failure"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", "calendar")).apply {
      failEventWrite = true
    }
    val runtime = runtime(account, repository, gateway, preferences)
    runtime.start()
    runCurrent()
    val calls = gateway.upsertCount
    repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
    runCurrent()
    assertEquals(calls, gateway.upsertCount)
    assertEquals(emptyMap(), preferences.value.eventReferences)
    runtime.stop()
  }

  /**
   * outbound store completion 改变 snapshot 后，旧 Full 不能继续下一 action；actor 仅以新 snapshot 另起完整计划。
   */
  @Test
  fun snapshotDriftAbandonsOldPlanBeforeSecondOutboundAction() = runTest {
    val accountId = "runtime-snapshot-drift"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply {
      replaceSchedules(listOf(schedule(), schedule(title = "第二个投影", id = "018f7d5a-5678-7abc-8def-1234567890ac")))
    }
    val gateway = LedgerGateway().apply {
      afterFirstUpsert = { repository.replaceSchedules(emptyList()) }
    }
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val runtime = runtime(account, repository, gateway, preferences)

    runtime.start()
    runCurrent()

    assertEquals(1, gateway.upsertCount)
    runtime.stop()
  }

  /**
   * 对每个实际可能挂起的 runtime 边界穷举迟到 completion。
   *
   * barrier 在 snapshot/preference 读取、EventKit store 提交与 cache 写入实际完成后暂停，并在被取消后以
   * `NonCancellable` 模拟外部 completion 迟到送达。恢复后 runtime 必须先重验 exact session/scope/owner/generation，
   * 所以旧代次不能把已提交 store 的结果回填为 calendar/ledger cache，也不能产生后续 CRUD 或同代 replay。所有 fixture
   * 都是内存 fake。
   */
  @Test
  fun lateCompletionAtEveryRuntimeBoundaryCannotWritePastLifecycleFence() = runTest {
    val points = IosScheduleCalendarRuntimeSuspensionPoint.entries
    val invalidations = RuntimeInvalidation.entries
    for (point in points) {
      for (invalidation in invalidations) {
        val accountId = "runtime-race-${point.name}-${invalidation.name}"
        val account = FakeAccount(backgroundScope, session(accountId))
        val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
        val gateway = LedgerGateway()
        val preferences = FakePreferences(
          IosScheduleCalendarExportSettings.Preference(true, "source", null),
        )
        val barrier = LateCompletionBarrier(point)
        val runtime = IosScheduleCalendarExportRuntime(
          accountService = account,
          repository = repository,
          session = account.session.value,
          scope = account.accountCoroutineScope,
          owner = account.accountCoroutineScope.coroutineContext[Job]!!,
          gatewayFactory = { gateway },
          preferences = preferences,
          suspensionBoundary = barrier,
        )

        runtime.start()
        runCurrent()
        assertTrue(barrier.arrived.isCompleted, "未到达 $point / $invalidation 的可控挂起点")
        val crudBeforeInvalidation = gateway.crudCount
        val writesBeforeInvalidation = preferences.cacheWrites

        when (invalidation) {
          RuntimeInvalidation.OLD_EXACT_SESSION -> account.switchTo(session("$accountId-next", 2))
          RuntimeInvalidation.SAME_ACCOUNT_NEW_GENERATION -> account.switchTo(session(accountId, 2))
          RuntimeInvalidation.OWNER_CANCELLED -> account.cancelOwner()
          RuntimeInvalidation.DISABLED -> {
            // 与 controller 一致，必须先失效旧代次，才写入新的 durable intent 并启动新代次。
            runtime.invalidateForExplicitIntent()
            preferences.value = preferences.value.copy(enabled = false)
            gateway.status = IosEventKitFullAccessStatus.DENIED
            runtime.restartFromExplicitIntent()
          }

          RuntimeInvalidation.SOURCE_SWITCHED -> {
            runtime.invalidateForExplicitIntent()
            preferences.value = preferences.value.copy(sourceIdentifier = "new-source", calendarIdentifier = null)
            // 新 generation 在本测试只做权限门禁；计数断言因此只观察迟到旧 completion，不把正确的新 source 工作混入。
            gateway.status = IosEventKitFullAccessStatus.DENIED
            runtime.restartFromExplicitIntent()
          }
        }
        barrier.release.complete(Unit)
        runCurrent()

        assertEquals(
          crudBeforeInvalidation,
          gateway.crudCount,
          "$point / $invalidation 的迟到 completion 触发了 EventKit CRUD",
        )
        assertEquals(
          writesBeforeInvalidation,
          preferences.cacheWrites,
          "$point / $invalidation 的迟到 completion 写入了 calendar 或 event-ref cache",
        )
        repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
        runCurrent()
        assertEquals(
          crudBeforeInvalidation,
          gateway.crudCount,
          "$point / $invalidation 出现了同代 follower/replay",
        )
        runtime.stop()
      }
    }
  }

  /**
   * registry replacement 继承的 pending fence 在 Default dispatcher 上也必须继续丢弃 repository change。
   *
   * 此 fake 直接以 replacement 构造参数模拟同 exact-session runtime 接过旧 actor 的未完成 fence：新 runtime 的首次
   * yield 后仍不得读取半写 source/cache/enabled；只有 controller 最终 signal 才解除它并允许新 source 导出。
   */
  @Test
  fun inheritedExplicitIntentPendingFenceRejectsDefaultDispatcherRepositoryChangeUntilSignal() = runTest {
    val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val accountId = "runtime-intent-pending-default"
    val account = FakeAccount(defaultScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "old-source", null))
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gateway },
      preferences = preferences,
      initialExplicitIntentPending = true,
    )

    try {
      runtime.start()
      repository.awaitCalendarChangesCollector()
      repository.emitAwait(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      assertEquals(0, gateway.crudCount)
      assertEquals(0, preferences.cacheWrites)

      preferences.value = preferences.value.copy(sourceIdentifier = "new-source")
      runtime.restartFromExplicitIntent()
      withContext(Dispatchers.Default) {
        withTimeout(5_000) { gateway.firstUpsert.await() }
      }
      assertEquals(1, gateway.upsertCount)
      assertEquals("new-source", gateway.upsertHints.single().sourceIdentifier)
    } finally {
      runtime.stop()
      defaultScope.cancel()
    }
  }

  /**
   * Default dispatcher child 必须在启动前先登记为 runningReconcile。
   *
   * 该回归让 child 到达 preference 边界后立刻调用 exact-session replacement 的 prepare/invalidate 路径；旧实现在
   * `launch` 与登记之间可能让 prepare 看到 null，lazy 登记后必须取消该轮且不产生 EventKit/cache 副作用。
   */
  @Test
  fun defaultDispatcherReplacementCancelsRegisteredLazyReconcileBeforeEffects() = runTest {
    val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val accountId = "runtime-lazy-registration"
    val account = FakeAccount(defaultScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val boundary = LateCompletionBarrier(IosScheduleCalendarRuntimeSuspensionPoint.PREFERENCE_READ)
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gateway },
      preferences = preferences,
      suspensionBoundary = boundary,
    )

    try {
      runtime.start()
      withContext(Dispatchers.Default) { withTimeout(5_000) { boundary.arrived.await() } }
      assertTrue(runtime.prepareForExactSessionReplacement().automaticReplayBlocked)
      runtime.stop()
      boundary.release.complete(Unit)
      withContext(Dispatchers.Default) {
        withTimeout(5_000) { boundary.resumed.await() }
        yield()
      }
      assertEquals(0, gateway.crudCount)
      assertEquals(0, preferences.cacheWrites)
    } finally {
      boundary.release.complete(Unit)
      runtime.stop()
      defaultScope.cancel()
    }
  }

  /**
   * exact-session replacement 必须在 child 获得 dispatcher 执行权前观察到已登记的取消句柄。
   *
   * 该测试在 Default dispatcher 固定 child 的“stateLock 内已登记、尚未 start”窗口；replacement 继承
   * automatic-replay fence 并取消 lazy child，随后即使释放旧 actor，child 也不能抢跑 preference/EventKit/cache。
   */
  @Test
  fun defaultDispatcherReplacementCancelsChildRegisteredBeforeStart() = runTest {
    val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val accountId = "runtime-registration-window"
    val account = FakeAccount(defaultScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val gateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val startBarrier = ReconcileStartBarrier()
    val runtime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { gateway },
      preferences = preferences,
      reconcileStartBoundary = startBarrier,
    )

    try {
      runtime.start()
      withContext(Dispatchers.Default) { withTimeout(5_000) { startBarrier.arrived.await() } }
      val replacementFence = runtime.prepareForExactSessionReplacement()
      assertTrue(replacementFence.automaticReplayBlocked)

      startBarrier.release.complete(Unit)
      withContext(Dispatchers.Default) { withTimeout(5_000) { startBarrier.resumed.await() } }
      assertEquals(0, gateway.crudCount)
      assertEquals(0, preferences.cacheWrites)
    } finally {
      startBarrier.release.complete(Unit)
      runtime.stop()
      defaultScope.cancel()
    }
  }

  /**
   * 同一 exact session 的第二次 repository 初始化不能绕过旧 actor 的执行中 EventKit effect。
   *
   * 旧 actor 在 store 返回后、写入 ledger 前被重注册取消；replacement 从旧 runtime 冻结的 fence 启动，即使收到相同
   * session 的 repository signal 也零 CRUD。只有随后模拟 controller 的明确 intent 才能解除栅栏并新开 generation。
   */
  @Test
  fun exactSessionReregistrationInheritsRunningEffectFenceUntilExplicitIntent() = runTest {
    val accountId = "runtime-reregister-running-effect"
    val account = FakeAccount(backgroundScope, session(accountId))
    val repository = FakeRepository(accountId).apply { replaceSchedules(listOf(schedule())) }
    val oldGateway = LedgerGateway()
    val preferences = FakePreferences(IosScheduleCalendarExportSettings.Preference(true, "source", null))
    val eventKitBoundary = LateCompletionBarrier(IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
    val oldRuntime = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { oldGateway },
      preferences = preferences,
      suspensionBoundary = eventKitBoundary,
    )
    val replacementGateway = LedgerGateway()

    oldRuntime.start()
    runCurrent()
    assertTrue(eventKitBoundary.arrived.isCompleted)
    assertEquals(1, oldGateway.upsertCount)
    val replacementFence = oldRuntime.prepareForExactSessionReplacement()
    assertTrue(replacementFence.automaticReplayBlocked)
    oldRuntime.stop()
    val replacement = IosScheduleCalendarExportRuntime(
      accountService = account,
      repository = repository,
      session = account.session.value,
      scope = account.accountCoroutineScope,
      owner = account.accountCoroutineScope.coroutineContext[Job]!!,
      gatewayFactory = { replacementGateway },
      preferences = preferences,
      initialExplicitIntentPending = replacementFence.explicitIntentPending,
      initialAutomaticReplayBlocked = replacementFence.automaticReplayBlocked,
    )

    try {
      replacement.start()
      runCurrent()
      repository.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, emptySet()))
      runCurrent()
      assertEquals(0, replacementGateway.crudCount)

      replacement.invalidateForExplicitIntent()
      replacement.restartFromExplicitIntent()
      runCurrent()
      assertEquals(1, replacementGateway.upsertCount)
    } finally {
      eventKitBoundary.release.complete(Unit)
      oldRuntime.stop()
      replacement.stop()
    }
  }

  /**
   * 损坏的 Settings ledger 只能整份 fail-closed，不能抛出或保留可错位的部分 locator。
   *
   * 测试直接执行无副作用的窄 codec seam，不创建 `AccountSettings`、EventKit 或账号持久化对象。
   */
  @Test
  fun eventReferenceLedgerDecoderRejectsMalformedPrefixesAndRoundTripsValidLedger() {
    val accountId = "runtime-ledger-codec"
    val scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId)
    val firstId = CalendarProjectionId(
      scope = scope,
      scheduleId = ScheduleId("018f7d5a-1111-7abc-8def-1234567890ac"),
      kind = com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind.SINGLE,
    )
    val secondId = CalendarProjectionId(
      scope = scope,
      scheduleId = ScheduleId("018f7d5a-2222-7abc-8def-1234567890ac"),
      kind = com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind.SERIES_MASTER,
    )
    val values = linkedMapOf(
      firstId to PlatformCalendarEventRef("event:one"),
      secondId to PlatformCalendarEventRef("event:two"),
    )
    val serialized = IosScheduleCalendarExportSettings.encodeEventReferenceLedgerForTest(values)

    assertEquals(
      values,
      IosScheduleCalendarExportSettings.decodeEventReferenceLedgerForTest(serialized, scope),
    )
    val malformedLedgers = listOf(
      "-1:",
      "not-a-number:",
      "10:short",
      serialized + serialized,
      "2147483648:",
    )
    malformedLedgers.forEach { malformed ->
      assertEquals(
        emptyMap(),
        IosScheduleCalendarExportSettings.decodeEventReferenceLedgerForTest(malformed, scope),
        "ledger must fail closed: $malformed",
      )
    }
  }

  /** 通过纯内存 seam 构造 runtime，禁止触及 EventKit、notification 或真实 settings。 */
  private fun runtime(
    account: FakeAccount,
    repository: FakeRepository,
    gateway: IosScheduleCalendarRuntimeGateway,
    preferences: FakePreferences,
  ) = IosScheduleCalendarExportRuntime(
    accountService = account,
    repository = repository,
    session = account.session.value,
    scope = account.accountCoroutineScope,
    owner = account.accountCoroutineScope.coroutineContext[Job]!!,
    gatewayFactory = { gateway },
    preferences = preferences,
  )

  /** 生成满足 projection factory 校验的定时日程。 */
  private fun schedule(
    title: String = "导出测试",
    id: String = "018f7d5a-1234-7abc-8def-1234567890ac",
  ): Schedule = Schedule(
    id = ScheduleId(id),
    revision = 1,
    title = title,
    description = "runtime fake",
    categoryId = null,
    timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 8, 1, 9, 30), 60, "Asia/Shanghai"),
    recurrence = null,
    reminders = emptyList(),
    completion = ScheduleCompletion.PENDING,
    createdAt = kotlin.time.Instant.parse("2026-08-01T00:00:00Z"),
    updatedAt = kotlin.time.Instant.parse("2026-08-01T00:00:00Z"),
  )

  /** 由 stable projection identity 构造 ledger key，避免 fake 使用 title 或默认 calendar 认领。 */
  private fun projectionId(
    accountId: String,
    kind: com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind =
      com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind.SINGLE,
  ): CalendarProjectionId =
    com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId(
      scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId),
      scheduleId = ScheduleId("018f7d5a-1234-7abc-8def-1234567890ac"),
      kind = kind,
    )

  /** 触发 lifecycle fence 的五类独立变化：session identity、同账号新代次、owner、disable 与 source switch。 */
  private enum class RuntimeInvalidation {
    OLD_EXACT_SESSION,
    SAME_ACCOUNT_NEW_GENERATION,
    OWNER_CANCELLED,
    DISABLED,
    SOURCE_SWITCHED,
  }

  /**
   * 以非可取消等待模拟平台在 Kotlin owner 已被取消后才交付 completion。
   *
   * runtime 必须在实际读写完成并返回此 port 后立即 gate；此 fake 不持有或暴露真实 EventKit/Settings/repository 对象。
   */
  private class LateCompletionBarrier(
    private val target: IosScheduleCalendarRuntimeSuspensionPoint,
    /** 同类 boundary 的第几次完成后挂起；用于精确落在 calendar 或 event-ledger durable 之后。 */
    private val targetOccurrence: Int = 1,
  ) : IosScheduleCalendarRuntimeSuspensionBoundary {
    val arrived = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    /** 仅表示旧 continuation 已离开 external completion 等待；随后 runtime gate 必须拒绝其副作用。 */
    val resumed = CompletableDeferred<Unit>()
    private var occurrence = 0

    override suspend fun await(point: IosScheduleCalendarRuntimeSuspensionPoint) {
      if (point != target) return
      occurrence += 1
      if (occurrence != targetOccurrence) return
      arrived.complete(Unit)
      withContext(NonCancellable) { release.await() }
      resumed.complete(Unit)
    }
  }

  /**
   * 固定 child 已登记但尚未 `start()` 的 actor 窗口。
   *
   * 它只用于检验 replacement 能取消 lazy Job；不模拟外部 effect，也不以 sleep 推测 Default dispatcher 时序。
   */
  private class ReconcileStartBarrier : IosScheduleCalendarReconcileStartBoundary {
    val arrived = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val resumed = CompletableDeferred<Unit>()

    override suspend fun awaitRegisteredBeforeStart() {
      arrived.complete(Unit)
      release.await()
      resumed.complete(Unit)
    }
  }

  /** retirement 故障注入只包裹 capability 方法，其余 lookup/CRUD/ack 全部委托 production gateway。 */
  private enum class RetirementBehavior {
    PASS,
    REJECT,
    THROW,
  }

  /**
   * 只拦截 production gateway 的 process-resident retirement 返回路径；不伪造 lookup proof，也不接触 fake store 状态。
   */
  private class RetirementInterceptingGateway(
    private val delegate: IosScheduleCalendarRuntimeGateway,
    private val behavior: RetirementBehavior,
    private val onRetirement: () -> Unit,
  ) : IosScheduleCalendarRuntimeGateway {
    override fun fullAccessStatus(): IosEventKitFullAccessStatus = delegate.fullAccessStatus()

    override fun lookupVerified(
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitVerifiedEventLookup = delegate.lookupVerified(projectionId, eventRef, hints)

    override fun upsert(
      projection: CalendarEventProjection,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitGatewayResult = delegate.upsert(projection, hints)

    override fun retireLocatorRecoveryEligibility(
      event: ManagedCalendarEvent,
      binding: IosEventKitGatewayBinding,
      proof: IosEventKitLocatorRecoveryProof,
    ): IosEventKitLocatorEligibilityRetirement {
      onRetirement()
      return when (behavior) {
        RetirementBehavior.PASS -> delegate.retireLocatorRecoveryEligibility(event, binding, proof)
        RetirementBehavior.REJECT -> IosEventKitLocatorEligibilityRetirement.REJECTED
        RetirementBehavior.THROW -> error("simulated production retirement return loss")
      }
    }

    override fun acknowledgeLocatorPersistence(
      projection: CalendarEventProjection,
      binding: IosEventKitGatewayBinding,
      proof: IosEventKitLocatorRecoveryProof,
    ): IosEventKitLocatorAcknowledgement =
      delegate.acknowledgeLocatorPersistence(projection, binding, proof)

    override fun deleteKnown(
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitGatewayResult = delegate.deleteKnown(projectionId, eventRef, hints)
  }

  /**
   * 只为 ambiguous-commit → explicit-intent recovery 场景保存同一份内存 EventKit 状态。
   *
   * 默认首次 `createCalendarWithEvent` 会原子加入唯一 calendar/event 后返回 commit-outcome-unknown，模拟平台提交成功但
   * bridge 丢失确定 completion；[firstAtomicFailureBeforeCommit] 则在 durable pair 产生前返回普通 AMBIGUOUS，用于证明该结果
   * 只能终结 generation，不能建立 eligibility。后续读取均来自同一 store；它不构造 EKEventStore、不访问系统日历。
   */
  private class AmbiguousCommitRecoveryStore(
    private var firstAtomicCompletionLost: Boolean = true,
    private var firstAtomicFailureBeforeCommit: Boolean = false,
    /** production gateway + runtime retirement 串联测试允许普通 update/delete 提交；其它恢复测试默认保持模糊失败。 */
    private val allowOrdinaryCrud: Boolean = false,
  ) : IosEventKitStorePort {
    private val calendars = mutableListOf<IosEventKitCalendarSnapshot>()
    private val events = linkedMapOf<String, MutableList<IosEventKitStoreEventSnapshot>>()
    var createCalendarCount = 0
      private set
    var saveCount = 0
      private set
    var removeCount = 0
      private set
    val calendarCount: Int get() = calendars.size
    private var nextOrdinaryEvent = 1

    override fun authorizationStatus(): IosEventKitFullAccessStatus = IosEventKitFullAccessStatus.FULL_ACCESS

    override suspend fun requestFullAccess(): IosEventKitStoreResult<IosEventKitFullAccessStatus> =
      IosEventKitStoreResult.Success(IosEventKitFullAccessStatus.FULL_ACCESS)

    override fun sources(): IosEventKitStoreResult<List<IosEventKitSourceSnapshot>> =
      IosEventKitStoreResult.Success(
        listOf(IosEventKitSourceSnapshot("source", supportsEvents = true)),
      )

    override fun calendars(): IosEventKitStoreResult<List<IosEventKitCalendarSnapshot>> =
      IosEventKitStoreResult.Success(calendars.toList())

    override fun eventByIdentifier(
      identifier: String,
    ): IosEventKitStoreResult<IosEventKitStoreEventSnapshot?> = IosEventKitStoreResult.Success(
      events.values.flatten().singleOrNull { it.raw.eventIdentifier == identifier },
    )

    override fun events(
      calendarIdentifier: String,
      window: IosEventKitScanWindow,
    ): IosEventKitStoreResult<List<IosEventKitStoreEventSnapshot>> {
      val stored = events[calendarIdentifier]
        ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      return IosEventKitStoreResult.Success(
        stored.filter { it.raw.start.epochSeconds in window.startEpochSeconds..<window.endEpochSeconds },
      )
    }

    /**
     * 预置一个普通 non-atomic exact event；不经过 atomic port，也不增加 create/save 计数。
     *
     * 该 fixture 专门证明 canonical equality 不是 provenance，不能单独授权 runtime 回填空 locator。
     */
    fun seedOrdinaryExactEvent(projection: CalendarEventProjection) {
      val payload = when (val mapped = IosEventKitCalendarAdapterFoundation.toWritePayload(projection)) {
        is IosEventKitMappingResult.Mapped -> mapped.value
        is IosEventKitMappingResult.Unsupported -> error("ordinary fixture must be mappable: ${mapped.error}")
      }
      val calendar = calendars.singleOrNull { it.identifier == "recovery-calendar" }
        ?: IosEventKitCalendarSnapshot(
          identifier = "recovery-calendar",
          sourceIdentifier = "source",
          allowsContentModifications = true,
        ).also(calendars::add)
      events[calendar.identifier] = mutableListOf(
        IosEventKitStoreEventSnapshot(calendar.identifier, payload.toRaw("recovery-event")),
      )
    }

    override fun createCalendarWithEvent(
      sourceIdentifier: String,
      displayTitle: String,
      payload: IosEventKitWritePayload,
    ): IosEventKitStoreResult<IosEventKitCreatedEventSnapshot> {
      createCalendarCount += 1
      saveCount += 1
      if (firstAtomicFailureBeforeCommit) {
        firstAtomicFailureBeforeCommit = false
        // configure/commit=false 排队阶段失败没有 durable pair，只能返回不具备 recovery provenance 的普通 AMBIGUOUS。
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      }
      val calendar = IosEventKitCalendarSnapshot(
        identifier = "recovery-calendar",
        sourceIdentifier = sourceIdentifier,
        allowsContentModifications = true,
      )
      val eventIdentifier = "recovery-event"
      calendars += calendar
      events[calendar.identifier] = mutableListOf(
        IosEventKitStoreEventSnapshot(calendar.identifier, payload.toRaw(eventIdentifier)),
      )
      return if (firstAtomicCompletionLost) {
        firstAtomicCompletionLost = false
        IosEventKitStoreResult.Failure(IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN)
      } else {
        IosEventKitStoreResult.Success(IosEventKitCreatedEventSnapshot(calendar, eventIdentifier))
      }
    }

    override fun saveEvent(
      calendarIdentifier: String,
      existingEventIdentifier: String?,
      payload: IosEventKitWritePayload,
    ): IosEventKitStoreResult<String> {
      saveCount += 1
      if (!allowOrdinaryCrud) return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.AMBIGUOUS)
      val stored = events[calendarIdentifier]
        ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      if (existingEventIdentifier != null && stored.none { it.raw.eventIdentifier == existingEventIdentifier }) {
        return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      }
      stored.removeAll { it.raw.eventIdentifier == existingEventIdentifier }
      val committedIdentifier = "ordinary-event-${nextOrdinaryEvent++}"
      stored += IosEventKitStoreEventSnapshot(calendarIdentifier, payload.toRaw(committedIdentifier))
      return IosEventKitStoreResult.Success(committedIdentifier)
    }

    override fun removeEvent(eventIdentifier: String): IosEventKitStoreResult<Unit> {
      if (!allowOrdinaryCrud) return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      val stored = events.values.singleOrNull { values ->
        values.any { it.raw.eventIdentifier == eventIdentifier }
      } ?: return IosEventKitStoreResult.Failure(IosEventKitStoreFailure.NOT_FOUND)
      stored.removeAll { it.raw.eventIdentifier == eventIdentifier }
      removeCount += 1
      return IosEventKitStoreResult.Success(Unit)
    }

    /** 将测试投影的 Timed payload 无损保存为 gateway foundation 可重新验证的 raw snapshot。 */
    private fun IosEventKitWritePayload.toRaw(identifier: String): IosEventKitRawEvent {
      val timed = timing as? IosEventKitWriteTiming.Timed
        ?: error("ambiguous recovery fixture only supports Timed payload")
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
  }

  /**
   * 保留一个已验证的 in-memory event，模拟 direct lookup 与 outbound CRUD 的严格边界。
   *
   * lookup 不存在时返回 KnownAbsent；delete 只有当前 ref 与已验证 event 相等时才计数，防止测试误把 ref 当 title
   * 或默认 calendar 所有权。
   */
  private class LedgerGateway : IosScheduleCalendarRuntimeGateway {
    var status = IosEventKitFullAccessStatus.FULL_ACCESS
    var atomicFirstCreate = true
    /** 返回普通历史 existing event 的 NoOp 形状；是否携带 proof 由测试显式配置，默认保持 NONE。 */
    var ordinaryExistingWithoutProof = false
    var recoveryProof: IosEventKitLocatorRecoveryProof? = IosEventKitLocatorRecoveryProof()
    /** direct lookup 可按调用序号返回成对或故意半包的 capability，覆盖 preflight 与 NoOp fresh lookup。 */
    val lookupRecoveryByCall = mutableMapOf<
      Int,
      Pair<IosEventKitLocatorRecoveryProof?, IosEventKitGatewayBinding?>,
      >()
    var retirement = IosEventKitLocatorEligibilityRetirement.RETIRED
    var throwOnRetirement = false
    var retirementCount = 0
    var acknowledgement = IosEventKitLocatorAcknowledgement.ACKNOWLEDGED
    /** ack 内部可能已消费但返回路径丢失；runtime 必须保守终结且不能自动 replay。 */
    var throwAfterAcknowledgement = false
    var acknowledgeCount = 0
    /** 覆盖完整 binding，专门验证 proof 不能授权 changed source 或 partial locator。 */
    var bindingOverride: IosEventKitGatewayBinding? = null
    var failLookup: IosEventKitGatewayFailure? = null
    /** 指定第几次 direct lookup 报 duplicate canonical sibling，覆盖 pre-verify 后的 NoOp 二次确认。 */
    var failLookupOnCall: Pair<Int, IosEventKitGatewayFailure>? = null
    /** 模拟 canonical ref 的 foundation occurrence 映射不支持；它不是 foreign identity。 */
    var unsupportedManagedLookup = false
    /** 仅让指定 locator 返回 unsupported，用于验证完整 ledger preflight 不会先删除较早的缺失 ref。 */
    var unsupportedManagedRefs: Set<PlatformCalendarEventRef> = emptySet()
    var failUpsert: IosEventKitGatewayFailure? = null
    /** 模拟 EventKit 已提交 effect、随后 canonical readback 才发现 duplicate 或权限丢失。 */
    var commitEffectBeforeFailure = false
    /** 用于在 store 返回后立即改变 repository 快照，固定复现写后计划过期。 */
    var afterFirstUpsert: (() -> Unit)? = null
    var lookupCount = 0
    var upsertCount = 0
    var deleteCount = 0
    var fullAccessStarts = 0
    var committedEffectCount = 0
    val crudCount: Int get() = lookupCount + upsertCount + deleteCount
    val firstUpsert = CompletableDeferred<Unit>()
    val upsertHints = mutableListOf<IosEventKitIdentifierHints>()
    private var currentProjection: CalendarEventProjection? = null
    private var currentRef: PlatformCalendarEventRef? = null

    override fun fullAccessStatus(): IosEventKitFullAccessStatus {
      fullAccessStarts += 1
      return status
    }

    override fun lookupVerified(
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitVerifiedEventLookup {
      lookupCount += 1
      failLookup?.let { return IosEventKitVerifiedEventLookup.Blocked(it) }
      failLookupOnCall?.takeIf { it.first == lookupCount }?.let { (_, failure) ->
        return IosEventKitVerifiedEventLookup.Blocked(failure)
      }
      if (unsupportedManagedLookup || eventRef in unsupportedManagedRefs) {
        return IosEventKitVerifiedEventLookup.UnsupportedManaged(
          projectionId = projectionId,
          mappingError = IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION,
        )
      }
      val projection = currentProjection
      return if (projection?.id == projectionId && currentRef == eventRef && hints.calendarIdentifier == "calendar") {
        val recovery = lookupRecoveryByCall[lookupCount]
        IosEventKitVerifiedEventLookup.Managed(
          event = ManagedCalendarEvent(projectionId, projection.fingerprint, eventRef),
          locatorRecoveryProof = recovery?.first,
          recoveryBinding = recovery?.second,
        )
      } else {
        IosEventKitVerifiedEventLookup.KnownAbsent
      }
    }

    override fun upsert(
      projection: CalendarEventProjection,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitGatewayResult {
      upsertCount += 1
      upsertHints += hints
      firstUpsert.complete(Unit)
      failUpsert?.let { failure ->
        if (commitEffectBeforeFailure) {
          currentProjection = projection
          currentRef = currentRef ?: PlatformCalendarEventRef("event-1")
          committedEffectCount += 1
        }
        return IosEventKitGatewayResult.Failed(failure)
      }
      currentProjection = projection
      if (upsertCount == 1) afterFirstUpsert?.invoke()
      val ref = currentRef ?: PlatformCalendarEventRef("event-1")
      currentRef = ref
      return IosEventKitGatewayResult.Upserted(
        binding = bindingOverride ?: IosEventKitGatewayBinding("source", "calendar", ref.value),
        changed = !ordinaryExistingWithoutProof,
        atomicCalendarAndFirstEvent = atomicFirstCreate && !ordinaryExistingWithoutProof &&
          hints.calendarIdentifier == null,
        locatorRecoveryProof = recoveryProof,
      )
    }

    /** fake retirement 只计数且不接触 store；拒绝/异常用于验证 runtime 在普通 CRUD 前 fail-closed。 */
    override fun retireLocatorRecoveryEligibility(
      event: ManagedCalendarEvent,
      binding: IosEventKitGatewayBinding,
      proof: IosEventKitLocatorRecoveryProof,
    ): IosEventKitLocatorEligibilityRetirement {
      retirementCount += 1
      if (throwOnRetirement) error("simulated locator eligibility retirement failure")
      return retirement
    }

    /** fake 只认可自己当前返回的 proof；用于锁定 runtime durable 两步完成后才 ack。 */
    override fun acknowledgeLocatorPersistence(
      projection: CalendarEventProjection,
      binding: IosEventKitGatewayBinding,
      proof: IosEventKitLocatorRecoveryProof,
    ): IosEventKitLocatorAcknowledgement {
      acknowledgeCount += 1
      if (throwAfterAcknowledgement) error("simulated acknowledgement return loss")
      return if (proof === recoveryProof) acknowledgement else IosEventKitLocatorAcknowledgement.REJECTED
    }

    override fun deleteKnown(
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitGatewayResult = if (
      currentProjection?.id == projectionId && currentRef == eventRef && hints.calendarIdentifier == "calendar"
    ) {
      deleteCount += 1
      currentProjection = null
      currentRef = null
      IosEventKitGatewayResult.Deleted("calendar", changed = true)
    } else {
      IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.FOREIGN_IDENTITY)
    }
  }

  private class FakeRuntimeGateway : IosScheduleCalendarRuntimeGateway {
    var status = IosEventKitFullAccessStatus.FULL_ACCESS
    var lookupCount = 0
    var upsertCount = 0
    var deleteCount = 0

    override fun fullAccessStatus(): IosEventKitFullAccessStatus = status

    override fun lookupVerified(
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitVerifiedEventLookup {
      lookupCount += 1
      return IosEventKitVerifiedEventLookup.KnownAbsent
    }

    override fun upsert(
      projection: com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitGatewayResult {
      upsertCount += 1
      error("empty snapshot must not upsert")
    }

    override fun retireLocatorRecoveryEligibility(
      event: ManagedCalendarEvent,
      binding: IosEventKitGatewayBinding,
      proof: IosEventKitLocatorRecoveryProof,
    ): IosEventKitLocatorEligibilityRetirement = error("empty snapshot must not retire locator eligibility")

    override fun acknowledgeLocatorPersistence(
      projection: CalendarEventProjection,
      binding: IosEventKitGatewayBinding,
      proof: IosEventKitLocatorRecoveryProof,
    ): IosEventKitLocatorAcknowledgement = error("empty snapshot must not acknowledge locator")

    override fun deleteKnown(
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
      hints: IosEventKitIdentifierHints,
    ): IosEventKitGatewayResult {
      deleteCount += 1
      error("empty snapshot must not delete")
    }
  }

  private class FakePreferences(
    var value: IosScheduleCalendarExportSettings.Preference,
  ) : IosScheduleCalendarPreferenceStore {
    var cacheWrites = 0
    var preferenceReads = 0
    /** 记录每次 durable 写入尝试，验证 invalid locator 的 fail-closed 顺序而不把多步清理伪装成原子事务。 */
    val writeAttempts = mutableListOf<String>()
    /** 在实际写入前抛出，模拟 durable cache 已不可用而非半成功提交。 */
    var failEventWrite = false
    var failEnabledWrite = false
    var failCalendarWrite = false
    var failLedgerClearWrite = false

    override fun get(accountId: String): IosScheduleCalendarExportSettings.Preference {
      preferenceReads += 1
      return value
    }

    override fun updateSourceIdentifier(accountId: String, sourceIdentifier: String) {
      writeAttempts += "source:$sourceIdentifier"
      value = value.copy(sourceIdentifier = sourceIdentifier)
      cacheWrites += 1
    }

    override fun updateCalendarIdentifier(accountId: String, calendarIdentifier: String?) {
      writeAttempts += "calendar:${calendarIdentifier ?: "null"}"
      if (failCalendarWrite) error("simulated calendar cache persistence failure")
      value = value.copy(calendarIdentifier = calendarIdentifier)
      cacheWrites += 1
    }

    override fun setEnabled(accountId: String, enabled: Boolean) {
      writeAttempts += "enabled:$enabled"
      if (failEnabledWrite) error("simulated enabled persistence failure")
      value = value.copy(enabled = enabled)
      cacheWrites += 1
    }

    override fun replaceEventReference(
      accountId: String,
      projectionId: CalendarProjectionId,
      eventRef: PlatformCalendarEventRef,
    ) {
      if (failEventWrite) error("simulated ledger persistence failure")
      value = value.copy(eventReferences = value.eventReferences + (projectionId to eventRef))
      cacheWrites += 1
    }

    override fun removeEventReference(accountId: String, projectionId: CalendarProjectionId) {
      value = value.copy(eventReferences = value.eventReferences - projectionId)
      cacheWrites += 1
    }

    override fun clearEventReferences(accountId: String) {
      writeAttempts += "ledger:clear"
      if (failLedgerClearWrite) error("simulated ledger clear persistence failure")
      value = value.copy(eventReferences = emptyMap())
      cacheWrites += 1
    }
  }

  /** 仅统计 runtime 的快照 `.value` 读取；collect/replay 仍完全委托给原始 StateFlow。 */
  private class CountingStateFlow<T>(
    private val delegate: StateFlow<T>,
    private val onValueRead: () -> Unit,
  ) : StateFlow<T> by delegate {
    override val value: T
      get() {
        onValueRead()
        return delegate.value
      }
  }

  private class FakeRepository(accountId: String, replay: Int = 0) : ScheduleRepository {
    private val values = MutableStateFlow(
      ScheduleSnapshot(accountId = accountId, status = ScheduleRepositoryStatus.Ready(0, false)),
    )
    private val changes = MutableSharedFlow<ScheduleCalendarChange>(replay = replay, extraBufferCapacity = 8)
    private val calendarChangesSubscribed = CompletableDeferred<Unit>()
    var snapshotReads = 0
      private set

    override val snapshot: StateFlow<ScheduleSnapshot> = CountingStateFlow(values) { snapshotReads += 1 }
    override val calendarChanges: Flow<ScheduleCalendarChange> = changes.onSubscription {
      calendarChangesSubscribed.complete(Unit)
    }

    override suspend fun initialize() = Unit

    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? = null

    fun emit(change: ScheduleCalendarChange) {
      changes.tryEmit(change)
    }

    /** 等待 runtime 的 repository collector 确实订阅后再投递，避免 Default dispatcher 测试依赖时间猜测。 */
    suspend fun awaitCalendarChangesCollector() {
      calendarChangesSubscribed.await()
    }

    /** 与已订阅的 collector 同步交付一条变更，复现后台 repository 与 controller 写 intent 的并发交错。 */
    suspend fun emitAwait(change: ScheduleCalendarChange) {
      changes.emit(change)
    }

    /** 在测试中发布新的可信快照，驱动 runtime 的 snapshot drift 与完整对账边界。 */
    fun replaceSchedules(schedules: List<Schedule>) {
      values.value = values.value.copy(schedules = schedules)
    }
  }

  private class FakeAccount(
    private val parentScope: CoroutineScope,
    initial: AccountSession,
  ) : IAccountService {
    override val session = MutableStateFlow(initial)
    override val state = MutableStateFlow(initial.state)
    private var owner = SupervisorJob(parentScope.coroutineContext[Job])
    private var scopedAccountCoroutineScope = CoroutineScope(parentScope.coroutineContext + owner)
    override val accountCoroutineScope: CoroutineScope
      get() = scopedAccountCoroutineScope

    override fun accountCoroutineScopeFor(expectedSession: AccountSession): CoroutineScope? =
      accountCoroutineScope.takeIf { session.value === expectedSession }

    /** 切换 session 时先失效旧 owner，再建立新 scope，模拟账号服务的 exact-session replacement。 */
    fun switchTo(next: AccountSession) {
      owner.cancel()
      owner = SupervisorJob(parentScope.coroutineContext[Job])
      scopedAccountCoroutineScope = CoroutineScope(parentScope.coroutineContext + owner)
      state.value = next.state
      session.value = next
    }

    /** 保持同一 session、仅取消 owner，覆盖页面/账号 scope 单独结束的迟到 completion。 */
    fun cancelOwner() {
      owner.cancel()
    }
  }

  /** 生成不可按值复用的 Login session，确保 runtime 的 identity gate 不退化为 accountId 比较。 */
  private fun session(accountId: String, generation: Long = 1): AccountSession =
    AccountSession(generation, AccountState.Login(accountId))
}
