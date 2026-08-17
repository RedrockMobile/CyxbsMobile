package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportAction
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportPlanner
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.calendar.ScheduleCalendarProjectionFactory
import com.cyxbs.pages.schedule.domain.calendar.ScheduleCalendarSource
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * 仅供 runtime 合同测试在真实副作用前制造可控迟到 completion 的窄挂起点。
 *
 * 生产实现不注入此端口，默认实现不挂起。它不暴露 repository、Settings、EventKit handle 或任何 CRUD，只让测试在
 * snapshot、偏好读取、EventKit 调用和 cache 写入完成后切换账号/代次并恢复旧 continuation。
 */
internal enum class IosScheduleCalendarRuntimeSuspensionPoint {
  PREFERENCE_READ,
  SNAPSHOT_READ,
  EVENTKIT_STORE,
  CACHE_WRITE,
}

/** runtime 在 [IosScheduleCalendarRuntimeSuspensionPoint] 前挂起的最窄测试端口。 */
internal fun interface IosScheduleCalendarRuntimeSuspensionBoundary {
  suspend fun await(point: IosScheduleCalendarRuntimeSuspensionPoint)
}

/** 生产默认值：不增加任何挂起、存储或平台调用。 */
private val NoOpIosScheduleCalendarRuntimeSuspensionBoundary =
  IosScheduleCalendarRuntimeSuspensionBoundary { }

/**
 * 仅供 runtime 合同测试固定“child 已登记、尚未 start”的并发窗口。
 *
 * 默认实现不挂起，也不接触 repository、Settings 或 EventKit；它只证明 replacement 在 child 获得 dispatcher 执行权前
 * 已经能观察并取消 [runningReconcile]。
 */
internal fun interface IosScheduleCalendarReconcileStartBoundary {
  suspend fun awaitRegisteredBeforeStart()
}

/** 生产默认值：登记 child 后立即启动。 */
private val NoOpIosScheduleCalendarReconcileStartBoundary =
  IosScheduleCalendarReconcileStartBoundary { }

/**
 * 同一 exact session 重注册时必须继承的自动重放栅栏。
 *
 * `automaticReplayBlocked` 表示旧 actor 已进入不确定终态，或正停在可能已提交 EventKit effect 的执行轮次；新 actor
 * 只能等待后续显式 intent，不能把相同 session 的 repository 初始化误当作新的用户操作。
 */
internal data class IosScheduleCalendarRuntimeReplacementFence(
  val explicitIntentPending: Boolean,
  val automaticReplayBlocked: Boolean,
)

/**
 * iOS Schedule → EventKit 的 process-resident 单向协调器。
 *
 * runtime 只持有 initializer 注册的 direct repository，不会按 accountId 重新查找 repository。每轮完整对账冻结
 * AccountSession、account scope、owner Job 和 generation；所有快照、偏好、EventKit 与 durable cache 操作前后都
 * 重新检查 exact session、账号、协程活性与 generation。它不监听 EventKit notification、不处理入站变化，也不会把
 * 平台事件回写 Schedule。
 */
internal class IosScheduleCalendarExportRuntime(
  private val accountService: IAccountService,
  private val repository: ScheduleRepository,
  private val session: AccountSession,
  private val scope: CoroutineScope,
  private val owner: Job,
  private val gatewayFactory: (com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope) -> IosScheduleCalendarRuntimeGateway =
    ::IosEventKitFullAccessGateway,
  private val preferences: IosScheduleCalendarPreferenceStore = IosScheduleCalendarExportSettings,
  private val suspensionBoundary: IosScheduleCalendarRuntimeSuspensionBoundary =
    NoOpIosScheduleCalendarRuntimeSuspensionBoundary,
  /** child 已在 [stateLock] 登记、尚未 start 的窄测试窗口；生产默认不挂起。 */
  private val reconcileStartBoundary: IosScheduleCalendarReconcileStartBoundary =
    NoOpIosScheduleCalendarReconcileStartBoundary,
  /** 同 exact-session runtime 被重注册时继承未完成的显式 intent fence，禁止新 actor 读取半写配置。 */
  private val initialExplicitIntentPending: Boolean = false,
  /** 旧 actor 的不确定终态或执行中 EventKit effect 会禁止本次重注册自动 Full。 */
  private val initialAutomaticReplayBlocked: Boolean = false,
) {
  private val accountId = requireNotNull(session.accountId)
  private val requests = Channel<Long>(Channel.CONFLATED)
  private val jobs = mutableListOf<Job>()
  /**
   * 控制面可从 controller、registry 与 account scope 的不同线程进入；所有 generation/actor 生命周期字段必须在同一锁内读写。
   *
   * 锁只保护内存状态和取消句柄，绝不跨越 repository、Settings 或 EventKit 调用。这样 controller 先失效旧代次后，
   * 任何迟到 completion 即使已经完成外部提交，也无法再进入 durable cache 写入。
   */
  private val stateLock = SynchronizedObject()

  /** 单调代次将 source switch、disable、同账号重新注册和不确定终态与旧 completion 隔离。 */
  private var generation = 0L
  /** handoff release 后才置为 true；register 阶段绝不能启动 collector、gateway 或 Full。 */
  private var started = false
  /** baseline 与 SharedFlow replay 的 Initialized 共同占用同一个初始 Full，避免依赖 conflated channel 的时序去重。 */
  private var initialFullRequested = false
  /**
   * controller 正在按 source → cache → enabled 改写 durable intent 时，拒绝所有 repository/snapshot-drift Full。
   *
   * 同一 exact session 的 registry replacement 会继承该值；旧 runtime 虽被停止，新 actor 在 controller signal 前仍
   * 不得读取 source、calendar、ledger 或 enabled 的中间持久化状态。
   */
  private var explicitIntentPending = initialExplicitIntentPending
  // 重注册继承的终态固定绑定新 actor 的初始 generation；只有后续显式 intent 才能清除，不能被 yield/repository signal 绕过。
  private var terminalUncertainGeneration: Long? = generation.takeIf { initialAutomaticReplayBlocked }
  private var runningReconcile: Job? = null

  /**
   * preflight strict lookup 为单个旧 managed target 签发的成对 capability。
   *
   * proof 与 binding 缺一不可；该对象只允许 Update/Delete 在普通 CRUD 前交给同一 gateway retirement。NoOp 必须忽略它并
   * 使用第二次 fresh lookup 的 latest proof，避免较新的签发被旧 preflight capability 覆盖。
   */
  private data class PreflightLocatorRecovery(
    val event: ManagedCalendarEvent,
    val binding: IosEventKitGatewayBinding,
    val proof: IosEventKitLocatorRecoveryProof,
  )

  /**
   * 在 repository initialize mutex 释放后的 handoff 中启动订阅与 actor。
   *
   * baseline 与 replayed `Initialized` 都经 [requestInitialFullOnce] 取得同一锁内标记，因此不会依赖 `Channel.CONFLATED`
   * 的调度时序而重复 Full；重复 handoff 不会启动第二套 collector/actor。
   */
  fun start() {
    val shouldStart = synchronized(stateLock) {
      if (started) false else {
        started = true
        true
      }
    }
    if (!shouldStart) return
    jobs += scope.launch {
      repository.calendarChanges.collect { change ->
        checkExactSessionBinding()
        if (change.accountId != accountId) return@collect
        when (change) {
          is ScheduleCalendarChange.Initialized -> requestInitialFullOnce()
          is ScheduleCalendarChange.SchedulesCommitted -> requestFullFromRepository()
          // remote receipt 不等于已落盘的 exportable snapshot，不能把它当成本地 Full 触发。
          is ScheduleCalendarChange.RemoteCommitted -> Unit
        }
      }
    }
    jobs += scope.launch {
      for (requestedGeneration in requests) {
        if (!canReconcile(requestedGeneration)) continue
        var snapshotDrift = false
        var cancelledOrFailed = false
        // actor 与单轮 reconcile 必须是不同 Job。显式 intent 只取消旧轮，actor 才能继续消费新 generation，不能因
        // `runningReconcile.cancel()` 把自己的 receiver 一并取消。
        // 必须 lazy 创建：若 child 先在 Default dispatcher 运行，exact-session replacement 可能在它登记到
        // runningReconcile 前看到 null，无法取消一轮已经开始 EventKit effect 的 reconcile。
        val reconcile = scope.launch(start = CoroutineStart.LAZY) {
          try {
            reconcileFull(requestedGeneration)
          } catch (_: SnapshotDriftException) {
            snapshotDrift = true
          } catch (_: CancellationException) {
            cancelledOrFailed = true
          } catch (_: Throwable) {
            // 未分类同步异常无法判断平台或 cache 是否已提交，按 terminal uncertain 收敛而非继续 replay。
            cancelledOrFailed = true
          }
        }
        val startReconcile = synchronized(stateLock) {
          if (generation != requestedGeneration || explicitIntentPending ||
            terminalUncertainGeneration == requestedGeneration
          ) {
            false
          } else {
            runningReconcile = reconcile
            true
          }
        }
        if (startReconcile) {
          // 该 test seam 位于登记锁之外：replacement 此时已能取消同一个 lazy Job；即使随后获得执行权，已取消 child
          // 也不会进入 reconcileFull 或触发 gateway/cache effect。
          reconcileStartBoundary.awaitRegisteredBeforeStart()
          reconcile.start()
        } else {
          reconcile.cancel()
        }
        reconcile.join()
        synchronized(stateLock) {
          if (runningReconcile === reconcile) runningReconcile = null
        }
        if (snapshotDrift) {
          // snapshot 漂移没有跨越平台写后回读；丢弃旧计划并从最新完整快照重建。
          requestFullFromRepository()
        } else if (cancelledOrFailed) {
          // 已进入外部调用后取消时不能假定 store/cache 的提交状态；旧 generation 不再自动 replay。
          markTerminalUncertain(requestedGeneration)
        }
      }
    }
    requestInitialFullOnce()
  }

  /** 停止此 exact-session 注册；registry replacement 后迟到 completion 会因 session/generation 检查被丢弃。 */
  fun stop() {
    val jobsToCancel = synchronized(stateLock) {
      generation += 1
      runningReconcile?.cancel()
      jobs.toList()
    }
    jobsToCancel.forEach(Job::cancel)
    requests.close()
  }

  /**
   * 在 controller 改写 source、enabled 或 locator 前同步失效旧 generation。
   *
   * 此调用刻意不排入 Full：新的 durable intent 尚未完整写入，不能让 runtime 读取中间配置；随后
   * [restartFromExplicitIntent] 才会从完整配置开始新代次。
   */
  fun invalidateForExplicitIntent() {
    synchronized(stateLock) {
      generation += 1
      // 先打开 pending fence，再取消旧轮；此后 repository change 或 snapshot-drift 即使先于 controller signal 到达，
      // 也不能排入读取半写 source/calendar/ref 的 Full。
      explicitIntentPending = true
      terminalUncertainGeneration = null
      runningReconcile?.cancel()
    }
  }

  /**
   * 在完整 intent durable 后解除 pending fence 并启动已失效的新 generation。
   *
   * signal 若早于 post-mutex handoff，只解除 fence；稍后的 [start] 会排入初始 Full 并读取完整持久化 intent。没有前置
   * invalidate 的重复 signal 不创建新 generation，也不能绕过 terminal uncertain。
   */
  fun restartFromExplicitIntent() {
    val requestedGeneration = synchronized(stateLock) {
      if (!explicitIntentPending) return@synchronized null
      explicitIntentPending = false
      generation.takeIf { started && terminalUncertainGeneration != generation }
    } ?: return
    requests.trySend(requestedGeneration)
  }

  /** baseline 与 replayed Initialized 共用该入口，确保同一次 runtime handoff 只排入一个初始 Full。 */
  private fun requestInitialFullOnce() {
    val requestedGeneration = synchronized(stateLock) {
      if (!started || initialFullRequested || explicitIntentPending || terminalUncertainGeneration == generation) {
        null
      } else {
        initialFullRequested = true
        generation
      }
    } ?: return
    requests.trySend(requestedGeneration)
  }

  /** repository 事件只在本 generation 非不确定、且 controller 未完成 durable intent 时合并为一个 Full 请求。 */
  private fun requestFullFromRepository() {
    val requestedGeneration = synchronized(stateLock) {
      generation.takeIf {
        started && !explicitIntentPending && terminalUncertainGeneration != generation
      }
    } ?: return
    requests.trySend(requestedGeneration)
  }

  /** 从单一锁读取 actor 是否仍可执行，避免 consumer 与 controller 交错观察半更新状态。 */
  private fun canReconcile(expectedGeneration: Long): Boolean = synchronized(stateLock) {
    started && generation == expectedGeneration && !explicitIntentPending && terminalUncertainGeneration != expectedGeneration
  }

  /**
   * 在每个可挂起的边界前后检查冻结生命周期。
   *
   * 默认端口不挂起；测试端口恢复旧 completion 后仍必须经过第二次检查，故 session/scope/owner/generation 任一变化
   * 都会阻止紧随其后的 snapshot、EventKit 或 Settings 操作。
   */
  private suspend fun awaitBoundary(
    expectedGeneration: Long,
    point: IosScheduleCalendarRuntimeSuspensionPoint,
  ) {
    ensureCurrent(expectedGeneration)
    suspensionBoundary.await(point)
    ensureCurrent(expectedGeneration)
  }

  /** 执行一轮完整、串行的 outbound reconcile。 */
  private suspend fun reconcileFull(expectedGeneration: Long) {
    ensureCurrent(expectedGeneration)
    val initialPreference = readPreference(expectedGeneration) ?: return
    val sourceIdentifier = initialPreference.sourceIdentifier ?: return
    if (!initialPreference.enabled) return
    val gateway = gatewayFactory(IosScheduleCalendarExportSettings.scopeForAccount(accountId))
    // 权限状态读取不产生 EventKit 对象副作用；真正的 store seam 都在 lookup/upsert/delete 提交后复核。
    ensureCurrent(expectedGeneration)
    if (gateway.fullAccessStatus() != IosEventKitFullAccessStatus.FULL_ACCESS) {
      ensureCurrent(expectedGeneration)
      return
    }
    val initialSnapshot = readExportableSnapshot(expectedGeneration) ?: return
    val projection = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(initialSnapshot.schedules, initialSnapshot.exceptions),
      IosScheduleCalendarExportSettings.scopeForAccount(accountId),
    )
    ensureCurrent(expectedGeneration)
    var hints = IosEventKitIdentifierHints(sourceIdentifier, initialPreference.calendarIdentifier)
    val verified = mutableListOf<ManagedCalendarEvent>()
    val preflightRecoveries = mutableMapOf<CalendarProjectionId, PreflightLocatorRecovery>()
    val absentReferences = mutableListOf<CalendarProjectionId>()
    // 先完整只读验证全部 locator，不能在此阶段写 cache。较早的 ref 即使已经确认缺失，后续 canonical
    // unsupported master 仍要求保留原 calendar 和整份 ledger，避免局部删除后用不完整事实重建投影。
    for ((projectionId, ref) in initialPreference.eventReferences) {
      ensureSnapshotCurrent(initialSnapshot, expectedGeneration)
      ensureCurrent(expectedGeneration)
      val lookup = gateway.lookupVerified(projectionId, ref, hints)
      awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
      when (lookup) {
        is IosEventKitVerifiedEventLookup.Managed -> {
          verified += lookup.event
          val proof = lookup.locatorRecoveryProof
          val binding = lookup.recoveryBinding
          if ((proof == null) != (binding == null)) {
            // gateway capability 必须原子成对返回；任一半包都不能进入 planner，更不能让 Update/Delete 绕过 retirement。
            markTerminalUncertain(expectedGeneration)
            return
          }
          if (proof != null && binding != null) {
            val previous = preflightRecoveries.put(
              projectionId,
              PreflightLocatorRecovery(lookup.event, binding, proof),
            )
            if (previous != null) {
              // 同一 projection 的重复 preflight capability 会使“哪一个是 latest issuer proof”不再可证明，必须零 CRUD 停机。
              markTerminalUncertain(expectedGeneration)
              return
            }
          }
        }
        is IosEventKitVerifiedEventLookup.UnsupportedManaged -> {
          // 已验证为当前 scope 的 unsupported master 不是 foreign event；停止本轮以保留 calendar 与所有 locator，
          // 不能删除/降级该 master 或用缺少该事实的 planner 重建投影。
          return
        }
        IosEventKitVerifiedEventLookup.KnownAbsent -> absentReferences += projectionId
        is IosEventKitVerifiedEventLookup.Blocked -> {
          stopForGatewayFailure(expectedGeneration, lookup.failure)
          return
        }
      }
      ensureCurrent(expectedGeneration)
    }
    // 只有整份 preflight 都未遇到 UnsupportedManaged/terminal failure 后，才能持久化缺失 ref 的移除并进入 planner。
    absentReferences.forEach { projectionId ->
      if (!removeReference(expectedGeneration, projectionId)) return
    }
    val plan = CalendarExportPlanner.plan(
      result = projection,
      managedEvents = verified,
      scope = IosScheduleCalendarExportSettings.scopeForAccount(accountId),
    )
    ensureSnapshotCurrent(initialSnapshot, expectedGeneration)
    for (action in plan.actions) {
      ensureSnapshotCurrent(initialSnapshot, expectedGeneration)
      when (action) {
        is CalendarExportAction.Create -> {
          ensureCurrent(expectedGeneration)
          val result = gateway.upsert(action.projection, hints)
          awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
          val upserted = result as? IosEventKitGatewayResult.Upserted
            ?: run {
              stopForGatewayFailure(expectedGeneration, (result as IosEventKitGatewayResult.Failed).reason)
              return
            }
          hints = updateHintsAfterUpsert(
            expectedGeneration = expectedGeneration,
            initialPreference = initialPreference,
            projection = action.projection,
            result = upserted,
            currentHints = hints,
            gateway = gateway,
          ) ?: return
        }

        is CalendarExportAction.Update -> {
          if (!retirePreflightRecoveryBeforeMutation(
              expectedGeneration = expectedGeneration,
              snapshot = initialSnapshot,
              recovery = preflightRecoveries.remove(action.projection.id),
              gateway = gateway,
            )
          ) return
          ensureCurrent(expectedGeneration)
          val result = gateway.upsert(
            action.projection,
            hints.copy(eventIdentifier = action.existingEventRef.value),
          )
          awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
          val upserted = result as? IosEventKitGatewayResult.Upserted
            ?: run {
              stopForGatewayFailure(expectedGeneration, (result as IosEventKitGatewayResult.Failed).reason)
              return
            }
          hints = updateHintsAfterUpsert(
            expectedGeneration = expectedGeneration,
            initialPreference = initialPreference,
            projection = action.projection,
            result = upserted,
            currentHints = hints,
            gateway = gateway,
          ) ?: return
        }

        is CalendarExportAction.NoOp -> {
          // NoOp 不能只相信 planner 的旧 observation；再次以 ref direct lookup 后才刷新 ledger。
          ensureCurrent(expectedGeneration)
          val lookup = gateway.lookupVerified(action.event.id, action.event.platformEventRef, hints)
          awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
          when (lookup) {
            is IosEventKitVerifiedEventLookup.Managed -> {
              if (!replaceReference(
                  expectedGeneration,
                  lookup.event.id,
                  lookup.event.platformEventRef,
                )
              ) return
              val proof = lookup.locatorRecoveryProof
              val binding = lookup.recoveryBinding
              if ((proof == null) != (binding == null)) {
                markTerminalUncertain(expectedGeneration)
                return
              }
              if (proof != null && binding != null) {
                val targetProjection = projection.events.singleOrNull { it.id == lookup.event.id } ?: run {
                  markTerminalUncertain(expectedGeneration)
                  return
                }
                if (!acknowledgeDurableLocator(
                    expectedGeneration = expectedGeneration,
                    projection = targetProjection,
                    binding = binding,
                    proof = proof,
                    gateway = gateway,
                  )
                ) return
              }
            }
            is IosEventKitVerifiedEventLookup.UnsupportedManaged -> {
              // 不能把 canonical 但 unsupported 的 master 误判为 ref 缺失或 foreign；不刷新 ledger、不删除也不降级。
              return
            }
            IosEventKitVerifiedEventLookup.KnownAbsent -> {
              if (!removeReference(expectedGeneration, action.event.id)) return
              requestFullFromRepository()
              return
            }
            is IosEventKitVerifiedEventLookup.Blocked -> {
              stopForGatewayFailure(expectedGeneration, lookup.failure)
              return
            }
          }
        }

        is CalendarExportAction.Delete -> {
          if (!retirePreflightRecoveryBeforeMutation(
              expectedGeneration = expectedGeneration,
              snapshot = initialSnapshot,
              recovery = preflightRecoveries.remove(action.event.id),
              gateway = gateway,
            )
          ) return
          ensureCurrent(expectedGeneration)
          val result = gateway.deleteKnown(
            projectionId = action.event.id,
            eventRef = action.event.platformEventRef,
            hints = hints,
          )
          awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.EVENTKIT_STORE)
          when (result) {
            is IosEventKitGatewayResult.Deleted -> if (!removeReference(expectedGeneration, action.event.id)) return
            is IosEventKitGatewayResult.Failed -> {
              stopForGatewayFailure(expectedGeneration, result.reason)
              return
            }
            is IosEventKitGatewayResult.Upserted -> error("deleteKnown cannot upsert")
          }
        }

        // occurrence exception 只保留已导出的 master，不会降级成单次 EventKit event 或删除旧 master。
        is CalendarExportAction.Unsupported -> Unit
      }
      ensureSnapshotCurrent(initialSnapshot, expectedGeneration)
    }
  }

  /**
   * 在 Update/Delete 普通 CRUD 前一次性废止 preflight 观察到的旧 target eligibility。
   *
   * 无 proof 的普通 managed event 不增加调用。存在 capability 时，先重验 Schedule snapshot，再在 exact
   * AccountSession/owner/generation 检查内调用同一 gateway；retirement 只消费进程内 capability，不访问 EventKit。
   * 抛异常、拒绝或调用前后 lifecycle/generation 漂移都终结本代且返回 false，调用方不得继续 CRUD 或自动 replay。
   */
  private suspend fun retirePreflightRecoveryBeforeMutation(
    expectedGeneration: Long,
    snapshot: ScheduleSnapshot,
    recovery: PreflightLocatorRecovery?,
    gateway: IosScheduleCalendarRuntimeGateway,
  ): Boolean {
    if (recovery == null) return true
    val retirement = try {
      ensureSnapshotCurrent(snapshot, expectedGeneration)
      ensureCurrent(expectedGeneration)
      gateway.retireLocatorRecoveryEligibility(
        event = recovery.event,
        binding = recovery.binding,
        proof = recovery.proof,
      ).also {
        // retirement 没有 suspend/store I/O，但 controller 可从其它线程推进 generation；返回后仍必须复核 exact session。
        ensureCurrent(expectedGeneration)
      }
    } catch (drift: SnapshotDriftException) {
      throw drift
    } catch (_: Throwable) {
      markTerminalUncertain(expectedGeneration)
      return false
    }
    if (retirement != IosEventKitLocatorEligibilityRetirement.RETIRED) {
      markTerminalUncertain(expectedGeneration)
      return false
    }
    return true
  }

  /**
   * 在 strict Upserted 后按 calendar → event ledger → acknowledgement 的两阶段合同持久化 locator。
   *
   * 成功 atomic pair 与 fresh recovery 都必须携带 gateway 签发的 process-resident proof；proof 签发本身不消费 eligibility。
   * runtime 先在 exact AccountSession/owner/generation 检查下 durable 写入 calendar hint（如有需要），完成 boundary 复核，
   * 再 durable 写入对应 event reference 并复核。只有 fresh preference 仍精确包含同 source/calendar/event 后才向同一 gateway
   * 显式 ack；ack 成功才终结资格。任一 cache/lifecycle/ack 异常都保留资格并终结本 generation，禁止自动 replay。
   */
  private suspend fun updateHintsAfterUpsert(
    expectedGeneration: Long,
    initialPreference: IosScheduleCalendarExportSettings.Preference,
    projection: com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection,
    result: IosEventKitGatewayResult.Upserted,
    currentHints: IosEventKitIdentifierHints,
    gateway: IosScheduleCalendarRuntimeGateway,
  ): IosEventKitIdentifierHints? {
    ensureCurrent(expectedGeneration)
    val bindingIsCompleteAndCurrent = result.binding.sourceIdentifier == currentHints.sourceIdentifier &&
      result.binding.calendarIdentifier.isNotBlank() && result.binding.eventIdentifier.isNotBlank()
    if (!bindingIsCompleteAndCurrent) {
      markTerminalUncertain(expectedGeneration)
      return null
    }
    val requiresRecoveryProof = currentHints.calendarIdentifier == null ||
      (!result.changed && currentHints.eventIdentifier == null)
    val proof = result.locatorRecoveryProof
    if (requiresRecoveryProof && proof == null) {
      // ordinary non-atomic existing event 与无资格 partial locator 不能只按字符串 ID/canonical equality 回填。
      markTerminalUncertain(expectedGeneration)
      return null
    }
    if (currentHints.calendarIdentifier != null &&
      result.binding.calendarIdentifier != currentHints.calendarIdentifier
    ) {
      markTerminalUncertain(expectedGeneration)
      return null
    }

    var updatedHints = currentHints
    if (currentHints.calendarIdentifier == null) {
      val reread = readPreference(expectedGeneration) ?: return null
      if (!reread.enabled || reread.sourceIdentifier != currentHints.sourceIdentifier ||
        reread.calendarIdentifier != null || initialPreference.sourceIdentifier != currentHints.sourceIdentifier
      ) {
        markTerminalUncertain(expectedGeneration)
        return null
      }
      try {
        writeCacheIfCurrent(expectedGeneration) {
          preferences.updateCalendarIdentifier(accountId, result.binding.calendarIdentifier)
        }
        awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE)
      } catch (_: Throwable) {
        markTerminalUncertain(expectedGeneration)
        return null
      }
      updatedHints = currentHints.copy(calendarIdentifier = result.binding.calendarIdentifier)
    }

    val eventRef = PlatformCalendarEventRef(result.binding.eventIdentifier)
    if (!replaceReference(expectedGeneration, projection.id, eventRef)) return null

    if (proof != null && !acknowledgeDurableLocator(
        expectedGeneration = expectedGeneration,
        projection = projection,
        binding = result.binding,
        proof = proof,
        gateway = gateway,
      )
    ) return null
    return updatedHints
  }

  /**
   * 在 exact durable locator reread 后回传 gateway capability；该步骤不执行 EventKit/store 写入。
   *
   * ack 抛出或返回丢失可能发生在 eligibility 已消费之后，因此一律保守终结当前 generation，不自动 replay；locator 已 durable
   * 时后续 generation 仍可走普通 hint，若资格尚在则 direct lookup 会 fresh 重签。
   */
  private suspend fun acknowledgeDurableLocator(
    expectedGeneration: Long,
    projection: com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
    gateway: IosScheduleCalendarRuntimeGateway,
  ): Boolean {
    val eventRef = PlatformCalendarEventRef(binding.eventIdentifier)
    val durable = readPreference(expectedGeneration) ?: return false
    if (!durable.enabled || durable.sourceIdentifier != binding.sourceIdentifier ||
      durable.calendarIdentifier != binding.calendarIdentifier || durable.eventReferences[projection.id] != eventRef
    ) {
      markTerminalUncertain(expectedGeneration)
      return false
    }
    val acknowledgement = try {
      ensureCurrent(expectedGeneration)
      gateway.acknowledgeLocatorPersistence(projection, binding, proof).also {
        ensureCurrent(expectedGeneration)
      }
    } catch (_: Throwable) {
      markTerminalUncertain(expectedGeneration)
      return false
    }
    if (acknowledgement != IosEventKitLocatorAcknowledgement.ACKNOWLEDGED) {
      markTerminalUncertain(expectedGeneration)
      return false
    }
    return true
  }

  /**
   * source、calendar 或 identity 的确定性失效按 fail-closed 顺序持久化关闭导出。
   *
   * 必须先在当前 generation gate 内写入 `enabled=false`，再清 calendar hint 与整份 locator ledger。这样任一后续清理
   * 失败时，设置页仍会把导出显示为关闭，而不会出现 locator 已清空但 enabled 仍为 true 的静默停导配置。若第一笔
   * disable 写入本身失败，原偏好完整保留；每种失败都会终结本代，只有未来显式用户 intent 或新 session 能开启新代。
   */
  private suspend fun clearInvalidSelection(expectedGeneration: Long) {
    try {
      writeCacheIfCurrent(expectedGeneration) { preferences.setEnabled(accountId, false) }
      awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE)
      writeCacheIfCurrent(expectedGeneration) { preferences.updateCalendarIdentifier(accountId, null) }
      awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE)
      writeCacheIfCurrent(expectedGeneration) { preferences.clearEventReferences(accountId) }
      awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE)
    } catch (_: Throwable) {
      markTerminalUncertain(expectedGeneration)
    }
  }

  /** 按失败类别停止：不确定提交、readback mismatch 和资源/source/identity 歧义均不在本 generation 自动重放。 */
  private suspend fun stopForGatewayFailure(expectedGeneration: Long, failure: IosEventKitGatewayFailure) {
    if (failure in setOf(
        IosEventKitGatewayFailure.SOURCE_DISAPPEARED,
        IosEventKitGatewayFailure.CALENDAR_DISAPPEARED,
        IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR,
        IosEventKitGatewayFailure.FOREIGN_IDENTITY,
      )
    ) {
      clearInvalidSelection(expectedGeneration)
    }
    if (failure in setOf(
        IosEventKitGatewayFailure.STORE_AMBIGUOUS,
        IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
        // duplicate canonical readback 与写后撤权都可能发生在平台 effect 已提交后；不能 replay 以免产生第二次 CRUD。
        IosEventKitGatewayFailure.AMBIGUOUS_EVENT,
        IosEventKitGatewayFailure.PERMISSION_REVOKED,
        IosEventKitGatewayFailure.SOURCE_DISAPPEARED,
        IosEventKitGatewayFailure.CALENDAR_DISAPPEARED,
        IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR,
        IosEventKitGatewayFailure.FOREIGN_IDENTITY,
      )
    ) {
      markTerminalUncertain(expectedGeneration)
    }
  }

  /** 读取偏好前后检查生命周期；disabled/no-source 不会读取 EventKit 或写 cache。 */
  private suspend fun readPreference(
    expectedGeneration: Long,
  ): IosScheduleCalendarExportSettings.Preference? = try {
    ensureCurrent(expectedGeneration)
    val preference = preferences.get(accountId)
    awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.PREFERENCE_READ)
    preference
  } catch (_: Throwable) {
    markTerminalUncertain(expectedGeneration)
    null
  }

  /** 只接受当前账号的 Ready/Recovered 可读快照，其他状态没有任何 EventKit/cache 副作用。 */
  private suspend fun readExportableSnapshot(expectedGeneration: Long): ScheduleSnapshot? {
    ensureCurrent(expectedGeneration)
    val snapshot = repository.snapshot.value
    awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.SNAPSHOT_READ)
    return snapshot.takeIf {
      it.accountId == accountId && (it.status is ScheduleRepositoryStatus.Ready || it.status is ScheduleRepositoryStatus.Recovered)
    }
  }

  /** snapshot 在任意 EventKit 调用期间漂移时废弃整轮，绝不拿旧事实继续写下一个 action。 */
  private suspend fun ensureSnapshotCurrent(expected: ScheduleSnapshot, expectedGeneration: Long) {
    ensureCurrent(expectedGeneration)
    val current = repository.snapshot.value
    awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.SNAPSHOT_READ)
    if (current != expected) throw SnapshotDriftException()
  }

  /** 所有 preference write 前后复核 exact session/scope/owner/generation；失败终止本代。 */
  private suspend fun replaceReference(
    expectedGeneration: Long,
    id: CalendarProjectionId,
    ref: PlatformCalendarEventRef,
  ): Boolean = try {
    writeCacheIfCurrent(expectedGeneration) { preferences.replaceEventReference(accountId, id, ref) }
    awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE)
    true
  } catch (_: Throwable) {
    markTerminalUncertain(expectedGeneration)
    false
  }

  /** 移除失效或已删除 ref 的 cache；写入故障也按不确定终态处理。 */
  private suspend fun removeReference(expectedGeneration: Long, id: CalendarProjectionId): Boolean = try {
    writeCacheIfCurrent(expectedGeneration) { preferences.removeEventReference(accountId, id) }
    awaitBoundary(expectedGeneration, IosScheduleCalendarRuntimeSuspensionPoint.CACHE_WRITE)
    true
  } catch (_: Throwable) {
    markTerminalUncertain(expectedGeneration)
    false
  }

  /** 计划只读阶段发现本地事实变化时使用，区别于平台调用后的不确定取消。 */
  private class SnapshotDriftException : CancellationException("Schedule snapshot changed during EventKit reconcile")

  /**
   * 将 cache 的 generation 检查与同步 Settings 写入置于同一锁内。
   *
   * controller 的预失效也持有该锁，因此两者只能排序为“旧 cache 完整写完后再失效”（source switch 随后会清空它），
   * 或“先失效后拒绝旧 cache 写入”；绝不能在失效与新 intent 写入之间插入旧 locator。
   */
  private suspend fun writeCacheIfCurrent(expectedGeneration: Long, write: () -> Unit) {
    ensureCurrent(expectedGeneration)
    synchronized(stateLock) {
      check(generation == expectedGeneration) { "iOS calendar runtime generation is stale" }
      write()
    }
    ensureCurrent(expectedGeneration)
  }

  /** generation 是受 [stateLock] 保护的单调值；旧 completion 永远不能改变当前 generation 的 terminal flag。 */
  private fun markTerminalUncertain(expectedGeneration: Long) {
    synchronized(stateLock) {
      if (generation == expectedGeneration) terminalUncertainGeneration = expectedGeneration
    }
  }

  /**
   * 为同一 exact session 的 repository 重注册冻结自动重放状态，并立即使旧 reconcile 失效。
   *
   * 正在执行的 reconcile 可能已越过 EventKit effect 却尚未写 ledger，不能仅因 `stop()` 取消而视为未提交。故其
   * replacement 一律继承 terminal fence；只有 controller 后续的明确用户 intent 才能新开 generation。
   */
  fun prepareForExactSessionReplacement(): IosScheduleCalendarRuntimeReplacementFence = synchronized(stateLock) {
    val fence = IosScheduleCalendarRuntimeReplacementFence(
      explicitIntentPending = explicitIntentPending,
      automaticReplayBlocked = terminalUncertainGeneration != null || runningReconcile != null,
    )
    generation += 1
    runningReconcile?.cancel()
    fence
  }

  /** 每个副作用边界统一检查 exact session、账号、owner active 与 generation。 */
  private suspend fun ensureCurrent(expectedGeneration: Long) {
    check(synchronized(stateLock) { generation == expectedGeneration }) {
      "iOS calendar runtime generation is stale"
    }
    checkExactSessionBinding()
  }

  /**
   * 复核冻结会话仍属于当前登录代次，且其 owner 与当前协程都未取消。
   *
   * 该检查只读取现有账号服务和 Job，不维护额外 gate 或状态机；用于拒绝切号、同账号重新登录后的迟到 EventKit completion。
   */
  private suspend fun checkExactSessionBinding() {
    currentCoroutineContext().ensureActive()
    check(owner.isActive) { "iOS calendar runtime owner is inactive" }
    check(session.accountId == accountId && accountService.session.value === session) {
      "iOS calendar runtime session is no longer current"
    }
  }
}

/**
 * process-lifetime runtime registry。
 *
 * initializer 以 exact session 注册 direct repository；进程内只保留当前账号绑定。换号、登出、同 session 的 source
 * switch/disable 或重复注册都会释放旧 runtime。registry 从不按 accountId 查询 repository，也不保存可供 controller
 * 访问的 repository。
 */
internal object IosScheduleCalendarExportRuntimeRegistry {
  private data class Entry(
    val session: AccountSession,
    val accountId: String,
    val scope: CoroutineScope,
    val owner: Job,
    val runtime: IosScheduleCalendarExportRuntime,
  )

  private val entries = mutableListOf<Entry>()
  /** registry 的 entries 只可在此锁内枚举、替换或读取，避免 register/signal 交错丢失 exact-session 信号。 */
  private val entriesLock = SynchronizedObject()

  /**
   * 在 repository initialize mutex 内注册当前 exact session/scope/owner，但不启动 runtime。
   *
   * 返回的 handoff 绑定本次创建的 [Entry] 身份：只有它仍是当前 entry 且 lifecycle 仍精确有效时才会启动一次。故同 session
   * 的第二次初始化、换号或 owner 取消若发生在 callback 与 post-mutex release 之间，旧 handoff 不会启动 stale runtime。
   */
  fun register(
    accountService: IAccountService,
    repository: ScheduleRepository,
    session: AccountSession,
    scope: CoroutineScope,
    owner: Job,
  ): com.cyxbs.pages.schedule.domain.calendar.ScheduleRepositoryInitializationHandoff = registerInternal(
    accountService = accountService,
    repository = repository,
    session = session,
    scope = scope,
    owner = owner,
    runtimeFactory = { explicitIntentPending, automaticReplayBlocked ->
      IosScheduleCalendarExportRuntime(
        accountService = accountService,
        repository = repository,
        session = session,
        scope = scope,
        owner = owner,
        initialExplicitIntentPending = explicitIntentPending,
        initialAutomaticReplayBlocked = automaticReplayBlocked,
      )
    },
  )

  /** 仅供 iosTest 注入纯内存 runtime；生产 initializer 一律使用 [register]。 */
  internal fun registerForTest(
    accountService: IAccountService,
    repository: ScheduleRepository,
    session: AccountSession,
    scope: CoroutineScope,
    owner: Job,
    runtimeFactory: (explicitIntentPending: Boolean, automaticReplayBlocked: Boolean) -> IosScheduleCalendarExportRuntime,
  ): com.cyxbs.pages.schedule.domain.calendar.ScheduleRepositoryInitializationHandoff = registerInternal(
    accountService = accountService,
    repository = repository,
    session = session,
    scope = scope,
    owner = owner,
    runtimeFactory = runtimeFactory,
  )

  private fun registerInternal(
    accountService: IAccountService,
    repository: ScheduleRepository,
    session: AccountSession,
    scope: CoroutineScope,
    owner: Job,
    runtimeFactory: (explicitIntentPending: Boolean, automaticReplayBlocked: Boolean) -> IosScheduleCalendarExportRuntime,
  ): com.cyxbs.pages.schedule.domain.calendar.ScheduleRepositoryInitializationHandoff = synchronized(entriesLock) {
    val accountId = session.accountId
    if (accountId == null || !isCurrentBinding(accountService, session, scope, owner)) {
      com.cyxbs.pages.schedule.domain.calendar.NoOpScheduleRepositoryInitializationHandoff
    } else {
      // 同一个 session 可能有第二个 repository initialization 迟到完成。先在旧 runtime 锁内冻结 pending/terminal/执行中
      // EventKit effect 的状态并使旧 generation 失效；新 entry 只有自己的 post-mutex handoff 才有资格 start。
      val replacementFence = entries.lastOrNull { it.session === session }
        ?.runtime
        ?.prepareForExactSessionReplacement()
      val runtime = runtimeFactory(
        replacementFence?.explicitIntentPending ?: false,
        replacementFence?.automaticReplayBlocked ?: false,
      )
      // 进程同时只允许一个登录账号。注册新 binding 时清理所有旧 entry，避免切号后继续强引用旧仓库与账号 scope。
      entries.toList().forEach { stale ->
        stale.runtime.stop()
        entries.remove(stale)
      }
      val entry = Entry(session, accountId, scope, owner, runtime)
      entries += entry
      owner.invokeOnCompletion {
        synchronized(entriesLock) {
          if (entries.remove(entry)) {
            entry.runtime.stop()
          }
        }
      }
      oneShotReleaseHandoff(accountService, entry)
    }
  }

  /** 将 start 权限固定在本次 exact entry；release 不执行 repository/Settings/EventKit I/O 以外的重注册。 */
  private fun oneShotReleaseHandoff(
    accountService: IAccountService,
    entry: Entry,
  ): com.cyxbs.pages.schedule.domain.calendar.ScheduleRepositoryInitializationHandoff {
    var released = false
    return com.cyxbs.pages.schedule.domain.calendar.ScheduleRepositoryInitializationHandoff {
      synchronized(entriesLock) {
        if (released) return@synchronized
        released = true
        if (entries.lastOrNull { it.session === entry.session } !== entry ||
          !isCurrentBinding(accountService, entry.session, entry.scope, entry.owner)
        ) {
          return@synchronized
        }
        entry.runtime.start()
      }
    }
  }

  /** 检查运行时注册的 exact session、账号与 owner 活性；失败只拒绝迟到注册，不触碰已有 entry。 */
  private fun isCurrentBinding(
    accountService: IAccountService,
    session: AccountSession,
    scope: CoroutineScope,
    owner: Job,
  ): Boolean = owner.isActive &&
    session.accountId != null &&
    accountService.session.value === session &&
    scope.coroutineContext[Job] === owner

  /**
   * controller 在改写 durable intent 前调用；registry 与 runtime 都同步串行，确保旧 completion 已先失去 cache 写入资格。
   *
   * 没有 initializer registration 时无副作用，controller 仍可独立保存设置。
   */
  fun invalidate(session: AccountSession) = synchronized(entriesLock) {
    entries.lastOrNull { it.session === session }?.runtime?.invalidateForExplicitIntent()
  }

  /** controller 只传 exact session 发信号；完整 intent durable 后才允许新 generation 读取。 */
  fun signal(session: AccountSession) = synchronized(entriesLock) {
    entries.lastOrNull { it.session === session }?.runtime?.restartFromExplicitIntent()
  }
}
