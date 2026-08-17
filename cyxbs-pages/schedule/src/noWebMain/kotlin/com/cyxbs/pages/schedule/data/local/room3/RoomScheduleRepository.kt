package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.pages.schedule.data.remote.v3.KtorScheduleV2Gateway
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDelete
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleV2CallResult
import com.cyxbs.pages.schedule.data.remote.v3.SyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResultCode
import com.cyxbs.pages.schedule.data.remote.v3.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.v3.ResultReason
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2ApplyResult
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2DailyMutationBridge
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2DailyMutationCapture
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2LocalCommandReducer
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2LocalCommandResult
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2RequestPlanner
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2ResponseApplier
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2SnapshotProjection
import com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2SnapshotProjector
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleMutationBusinessRejectionReason
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryFactory
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Room repository 所需的最小 Schedule v2 网络能力。
 *
 * 接口刻意不暴露重试、receipt 或队列状态：每个方法只代表一次已经绑定账号的 HTTP 尝试，失败后 pending 仍由
 * Room 双快照保留，下一次显式同步再收敛。
 */
internal interface ScheduleV2RepositoryGateway {
  /** 提交完整 inventory 与 pending 的一次 Sync 请求。 */
  suspend fun sync(accountId: String, request: SyncRequest): ScheduleV2CallResult<SyncResponse>

  /** 提交日常新增的完整 version=0 Schedule。 */
  suspend fun createSchedule(accountId: String, input: ScheduleInput): ScheduleV2CallResult<ScheduleUpsertResult>

  /** 提交日常修改的完整正版本 Schedule。 */
  suspend fun updateSchedule(accountId: String, input: ScheduleInput): ScheduleV2CallResult<ScheduleUpsertResult>

  /** 提交日常删除的 identity 与本地修改时刻。 */
  suspend fun deleteSchedule(accountId: String, input: ScheduleDelete): ScheduleV2CallResult<ScheduleDeleteResult>
}

/** 将现有 Ktor 网关适配为 repository 的可替换最小接口，不复制 wire DTO 或 HTTP 解释。 */
internal class KtorScheduleV2RepositoryGateway(
  private val delegate: KtorScheduleV2Gateway,
) : ScheduleV2RepositoryGateway {
  override suspend fun sync(accountId: String, request: SyncRequest) = delegate.sync(accountId, request)

  override suspend fun createSchedule(accountId: String, input: ScheduleInput) =
    delegate.createSchedule(accountId, input)

  override suspend fun updateSchedule(accountId: String, input: ScheduleInput) =
    delegate.updateSchedule(accountId, input)

  override suspend fun deleteSchedule(accountId: String, input: ScheduleDelete) =
    delegate.deleteSchedule(accountId, input)
}

/** 未完成平台网络接线时的安全默认实现；它绝不伪造已发送或已确认的结果。 */
internal object UnavailableScheduleV2RepositoryGateway : ScheduleV2RepositoryGateway {
  private fun unavailable(): ScheduleV2CallResult<Nothing> =
    ScheduleV2CallResult.TransportFailure(null, IllegalStateException("Schedule v2 gateway is unavailable"))

  override suspend fun sync(accountId: String, request: SyncRequest): ScheduleV2CallResult<SyncResponse> = unavailable()

  override suspend fun createSchedule(
    accountId: String,
    input: ScheduleInput,
  ): ScheduleV2CallResult<ScheduleUpsertResult> = unavailable()

  override suspend fun updateSchedule(
    accountId: String,
    input: ScheduleInput,
  ): ScheduleV2CallResult<ScheduleUpsertResult> = unavailable()

  override suspend fun deleteSchedule(
    accountId: String,
    input: ScheduleDelete,
  ): ScheduleV2CallResult<ScheduleDeleteResult> = unavailable()
}

/**
 * 新协议 Room local-first repository。
 *
 * 只协调四张双快照表、纯 reducer、planner/applier 与一次网络尝试。Mutex 只覆盖 Room 读写、归约和快照发布；网络
 * 请求始终在锁外执行，响应回来重新读取当前 state，再按 uploaded localRevision compare-and-clear，保证 R→U 不会被
 * 较早响应覆盖。这里不保留 cursor、outbox、receipt、retry 或旧 semantic 状态机。
 */
internal class RoomScheduleRepository(
  private val accountId: String,
  private val stateStore: ScheduleV2RoomStateStore,
  private val gateway: ScheduleV2RepositoryGateway,
  private val timeZone: TimeZone,
  private val nowMillis: () -> Long,
  private val reducer: ScheduleV2LocalCommandReducer = ScheduleV2LocalCommandReducer(),
  private val planner: ScheduleV2RequestPlanner = ScheduleV2RequestPlanner(),
  private val applier: ScheduleV2ResponseApplier = ScheduleV2ResponseApplier(),
  private val projector: ScheduleV2SnapshotProjector = ScheduleV2SnapshotProjector(),
  private val dailyBridge: ScheduleV2DailyMutationBridge = ScheduleV2DailyMutationBridge(),
) : ScheduleRepository {
  private val mutex = Mutex()
  private val changes = MutableSharedFlow<ScheduleCalendarChange>(extraBufferCapacity = 32)
  private val mutableSnapshot = MutableStateFlow(ScheduleSnapshot())
  private var initialized = false
  private var requestSequence = 0L
  /** 最近一次尚未被成功响应解除的远端错误；仅进程内保留，绝不写入 Room 或作为同步状态机。 */
  private var lastRemoteError: ScheduleRemoteError? = null

  override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
  override val calendarChanges: Flow<ScheduleCalendarChange> = changes

  /**
   * 先把本地四表完整投影为可读快照，再在锁外执行一次完整 Sync。
   *
   * 本地投影失败会 fail-closed 为 [ScheduleRepositoryStatus.Corrupted] 并停止；网络失败只改为 Unavailable，
   * 已发布的本地 pending 不会丢失。
   */
  override suspend fun initialize() {
    val shouldSync = mutex.withLock {
      if (initialized) return@withLock false
      val local = readCurrentState()
      publishOrThrow(local, null)
      initialized = true
      true
    }
    if (!shouldSync) return
    changes.emit(ScheduleCalendarChange.Initialized(accountId))
    synchronizeFull()
  }

  /**
   * 本地命令先分配纯本地 revision、归约并一次替换完整 state；只有符合条件的单条 Schedule pending 才额外走日常接口。
   *
   * 返回 `Failure` 时本地提交可能已经完成：transport 或业务拒绝只会保留 pending，不会回滚用户本地编辑。
   */
  override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? {
    if (command == ScheduleCommand.RequestSync) return synchronizeFull()
    check(initialized) { "RoomScheduleRepository must be initialized before execute" }

    var localEvent: ScheduleCalendarChange.SchedulesCommitted? = null
    var dailyCapture: ScheduleV2DailyMutationCapture? = null
    val localResult = mutex.withLock {
      val revision = stateStore.allocateLocalRevision(accountId)
      val before = readCurrentState()
      when (
        val reduced = reducer.reduce(
          categories = before.categories,
          schedules = before.schedules,
          occurrenceOverrides = before.occurrenceOverrides,
          command = command,
          nowMillis = nowMillis(),
          localRevision = revision,
        )
      ) {
        ScheduleV2LocalCommandResult.NoOp -> ScheduleSyncResult.Success(attempted = false)
        is ScheduleV2LocalCommandResult.Rejected -> {
          ScheduleSyncResult.Failure(
            ScheduleRemoteError.MutationRejected(ScheduleMutationBusinessRejectionReason.INVALID_REQUEST),
            attempted = false,
          )
        }
        is ScheduleV2LocalCommandResult.Applied -> {
          val after = ScheduleV2CommonAccountState(
            accountId,
            reduced.categories,
            reduced.schedules,
            reduced.occurrenceOverrides,
          )
          // 本地 Applied 不代表远端恢复；Category/atomic pending 也必须保留既有 Unavailable。
          persistAndPublish(after)
          val changedIds = changedScheduleIds(before, after)
          if (changedIds.isNotEmpty()) {
            localEvent = ScheduleCalendarChange.SchedulesCommitted(accountId, changedIds)
          }
          dailyCapture = captureDailyIfEligible(command, after)
          ScheduleSyncResult.Success(attempted = false)
        }
      }
    }
    if (localEvent != null) changes.emit(localEvent!!)
    if (localResult is ScheduleSyncResult.Failure || dailyCapture == null) return localResult
    return submitDaily(dailyCapture!!)
  }

  /**
   * 发起完整 Sync；请求 capture 与响应应用之间绝不持有 [mutex]。
   *
   * 响应应用总是重新读取 Room 当前状态，因而请求期间形成的 U 不会被 R 清除；applier 成功后再一次全量替换三类表。
   */
  private suspend fun synchronizeFull(): ScheduleSyncResult {
    val capture = try {
      mutex.withLock {
        val state = readCurrentState()
        planner.capture(nextSyncRequestId(), state.categories, state.schedules, state.occurrenceOverrides)
      }
    } catch (failure: Throwable) {
      return publishUnavailableAfterRead(ScheduleRemoteError.InvalidResponse(failure), attempted = false)
    }
    return when (val call = gateway.sync(accountId, capture.request)) {
      is ScheduleV2CallResult.Completed -> applySyncResponse(capture, requireNotNull(call.wrapper.rawData))
      is ScheduleV2CallResult.ApiFailure ->
        publishUnavailableAfterRead(ScheduleRemoteError.Server(call.status), true)
      is ScheduleV2CallResult.RequestInvalid ->
        publishUnavailableAfterRead(ScheduleRemoteError.InvalidResponse(IllegalArgumentException(call.body)), true)
      is ScheduleV2CallResult.TransportFailure -> publishUnavailableAfterRead(call.toRemoteError(), true)
    }
  }

  /** 使用 DailyMutationBridge 将单条日常结果复用到统一 applier；Category、Override 与 atomic 永不从这里发送。 */
  private suspend fun submitDaily(capture: ScheduleV2DailyMutationCapture): ScheduleSyncResult = when (capture) {
    is ScheduleV2DailyMutationCapture.Upsert -> {
      val call = if (capture.create) gateway.createSchedule(accountId, capture.input)
      else gateway.updateSchedule(accountId, capture.input)
      when (call) {
        is ScheduleV2CallResult.Completed -> applyDailyUpsert(capture, requireNotNull(call.wrapper.rawData))
        is ScheduleV2CallResult.ApiFailure ->
          publishUnavailableAfterRead(ScheduleRemoteError.Server(call.status), true)
        is ScheduleV2CallResult.RequestInvalid ->
          publishUnavailableAfterRead(ScheduleRemoteError.InvalidResponse(IllegalArgumentException(call.body)), true)
        is ScheduleV2CallResult.TransportFailure -> publishUnavailableAfterRead(call.toRemoteError(), true)
      }
    }
    is ScheduleV2DailyMutationCapture.Delete -> when (val call = gateway.deleteSchedule(accountId, capture.input)) {
      is ScheduleV2CallResult.Completed -> applyDailyDelete(capture, requireNotNull(call.wrapper.rawData))
      is ScheduleV2CallResult.ApiFailure ->
        publishUnavailableAfterRead(ScheduleRemoteError.Server(call.status), true)
      is ScheduleV2CallResult.RequestInvalid ->
        publishUnavailableAfterRead(ScheduleRemoteError.InvalidResponse(IllegalArgumentException(call.body)), true)
      is ScheduleV2CallResult.TransportFailure -> publishUnavailableAfterRead(call.toRemoteError(), true)
    }
    is ScheduleV2DailyMutationCapture.Failure -> ScheduleSyncResult.Success(attempted = false)
  }

  /** 把完整 Sync 返回的 canonical 状态一次性落库并发布远端提交事件。 */
  private suspend fun applySyncResponse(
    capture: com.cyxbs.pages.schedule.data.repository.v3.ScheduleV2SyncCapture,
    response: SyncResponse,
  ): ScheduleSyncResult {
    var event: ScheduleCalendarChange.RemoteCommitted? = null
    val result = mutex.withLock {
      val before = readCurrentState()
      when (val applied = applier.apply(capture, response, before.categories, before.schedules, before.occurrenceOverrides)) {
        is ScheduleV2ApplyResult.Failure -> {
          val error = ScheduleRemoteError.InvalidResponse(IllegalArgumentException(applied.message))
          publishUnavailable(before, error)
          ScheduleSyncResult.Failure(error, true)
        }
        is ScheduleV2ApplyResult.Success -> {
          val after = ScheduleV2CommonAccountState(accountId, applied.categories, applied.schedules, applied.occurrenceOverrides)
          val businessError = response.rejectionError()
          persistAndPublish(after, remoteError = businessError, clearRemoteError = businessError == null)
          event = ScheduleCalendarChange.RemoteCommitted(accountId, changedScheduleIds(before, after).takeIf { it.isNotEmpty() })
          businessError?.let { ScheduleSyncResult.Failure(it, true) } ?: ScheduleSyncResult.Success()
        }
      }
    }
    if (event != null) changes.emit(event!!)
    return result
  }

  /** 日常 upsert 响应按 capture revision 复用统一 R→U 规则，成功后完整替换三类 state。 */
  private suspend fun applyDailyUpsert(
    capture: ScheduleV2DailyMutationCapture.Upsert,
    result: ScheduleUpsertResult,
  ): ScheduleSyncResult = applyDailyResponse(result.rejectionError()) { before ->
    dailyBridge.applyUpsert(capture, result, before.categories, before.schedules, before.occurrenceOverrides)
  }

  /** 日常 delete 响应按 capture revision 复用 tombstone 与 R→U 规则。 */
  private suspend fun applyDailyDelete(
    capture: ScheduleV2DailyMutationCapture.Delete,
    result: ScheduleDeleteResult,
  ): ScheduleSyncResult = applyDailyResponse(result.rejectionError()) { before ->
    dailyBridge.applyDelete(capture, result, before.categories, before.schedules, before.occurrenceOverrides)
  }

  /** 对 Daily bridge 的纯 apply 结果进行单次 Room 替换和远端事件发布。 */
  private suspend fun applyDailyResponse(
    businessError: ScheduleRemoteError.MutationRejected?,
    apply: (ScheduleV2CommonAccountState) -> ScheduleV2ApplyResult,
  ): ScheduleSyncResult {
    var event: ScheduleCalendarChange.RemoteCommitted? = null
    val result = mutex.withLock {
      val before = readCurrentState()
      when (val applied = apply(before)) {
        is ScheduleV2ApplyResult.Failure -> {
          val error = ScheduleRemoteError.InvalidResponse(IllegalArgumentException(applied.message))
          publishUnavailable(before, error)
          ScheduleSyncResult.Failure(error, true)
        }
        is ScheduleV2ApplyResult.Success -> {
          val after = ScheduleV2CommonAccountState(accountId, applied.categories, applied.schedules, applied.occurrenceOverrides)
          persistAndPublish(after, remoteError = businessError, clearRemoteError = businessError == null)
          event = ScheduleCalendarChange.RemoteCommitted(accountId, changedScheduleIds(before, after).takeIf { it.isNotEmpty() })
          businessError?.let { ScheduleSyncResult.Failure(it, true) } ?: ScheduleSyncResult.Success()
        }
      }
    }
    if (event != null) changes.emit(event!!)
    return result
  }

  /** 从 Room 读取三类 state 并在 mapper 边界校验账号及 pending 形态。 */
  private suspend fun readCurrentState(): ScheduleV2CommonAccountState =
    stateStore.readAccountState(accountId).toCommonAccountState(accountId)

  /**
   * 在投影成功后原子替换 Room 三类 state，再发布同一份快照；投影失败不会写入部分状态。
   *
   * 本地 Applied 不改变 [lastRemoteError]；只有已成功应用且非 REJECTED 的 Completed 响应传入
   * [clearRemoteError]，才能重新显示 Ready。
   */
  private suspend fun persistAndPublish(
    state: ScheduleV2CommonAccountState,
    remoteError: ScheduleRemoteError? = null,
    clearRemoteError: Boolean = false,
  ) {
    require(!clearRemoteError || remoteError == null) { "successful response must not carry a remote error" }
    if (clearRemoteError) lastRemoteError = null
    if (remoteError != null) lastRemoteError = remoteError
    val projected = project(state)
    val roomState = state.toRoomAccountState()
    stateStore.replaceAccountState(accountId, roomState.categories, roomState.schedules, roomState.occurrenceOverrides)
    mutableSnapshot.value = projected.copy(status = projected.status.withUnavailable(lastRemoteError))
  }

  /** 发布现有 Room state 的 Unavailable 快照，不重建、清除或覆盖任何 pending。 */
  private fun publishUnavailable(state: ScheduleV2CommonAccountState, error: ScheduleRemoteError) {
    lastRemoteError = error
    val projected = project(state)
    mutableSnapshot.value = projected.copy(status = projected.status.withUnavailable(lastRemoteError))
  }

  /** 读取当前状态后发布不可用结果；读/投影本身损坏时转为 Corrupted。 */
  private suspend fun publishUnavailableAfterRead(
    error: ScheduleRemoteError,
    attempted: Boolean,
  ): ScheduleSyncResult = try {
    mutex.withLock { publishUnavailable(readCurrentState(), error) }
    ScheduleSyncResult.Failure(error, attempted)
  } catch (failure: Throwable) {
    mutableSnapshot.value = ScheduleSnapshot(
      status = ScheduleRepositoryStatus.Corrupted(failure),
      accountId = accountId,
    )
    ScheduleSyncResult.Failure(ScheduleRemoteError.InvalidResponse(failure), attempted)
  }

  /** 初始化本地快照；错误直接发布 Corrupted 并让调用方 fail-closed。 */
  private fun publishOrThrow(state: ScheduleV2CommonAccountState, unavailable: ScheduleRemoteError?) {
    try {
      val projected = project(state)
      mutableSnapshot.value = projected.copy(status = projected.status.withUnavailable(unavailable))
    } catch (failure: Throwable) {
      mutableSnapshot.value = ScheduleSnapshot(status = ScheduleRepositoryStatus.Corrupted(failure), accountId = accountId)
      throw failure
    }
  }

  /** SnapshotProjector 的 Failure 不允许降级成局部 UI；调用方必须保持 Room 数据并报告损坏。 */
  private fun project(state: ScheduleV2CommonAccountState): ScheduleSnapshot = when (
    val result = projector.project(accountId, timeZone, state.categories, state.schedules, state.occurrenceOverrides)
  ) {
    is ScheduleV2SnapshotProjection.Success -> result.snapshot
    is ScheduleV2SnapshotProjection.Failure -> throw IllegalArgumentException(result.message)
  }

  /** 只对普通单 Schedule command 尝试日常接口；其他资源及任意 atomic 批次均留给完整 Sync。 */
  private fun captureDailyIfEligible(
    command: ScheduleCommand,
    state: ScheduleV2CommonAccountState,
  ): ScheduleV2DailyMutationCapture? {
    val scheduleId = when (command) {
      is ScheduleCommand.Create -> command.schedule.id
      is ScheduleCommand.Update -> command.schedule.id
      is ScheduleCommand.Delete -> command.scheduleId
      is ScheduleCommand.CompleteNonRepeating -> command.scheduleId
      else -> return null
    }
    val schedule = state.schedules.firstOrNull { it.identity.id == scheduleId.value } ?: return null
    return dailyBridge.capture(schedule).takeUnless { it is ScheduleV2DailyMutationCapture.Failure }
  }

  /** 计算会影响单向日历投影的 Schedule identity，override 改动归属其 parent Schedule。 */
  private fun changedScheduleIds(
    before: ScheduleV2CommonAccountState,
    after: ScheduleV2CommonAccountState,
  ): Set<ScheduleId> {
    val scheduleIds = (before.schedules + after.schedules)
      .groupBy { it.identity.id }
      .filter { (_, values) -> values.distinct().size > 1 || values.size == 1 &&
        ((before.schedules.any { it.identity.id == values.first().identity.id }) !=
          (after.schedules.any { it.identity.id == values.first().identity.id })) }
      .keys
    val overrideParents = (before.occurrenceOverrides + after.occurrenceOverrides)
      .groupBy { it.identity }
      .filter { (_, values) -> values.distinct().size > 1 || values.size == 1 &&
        ((before.occurrenceOverrides.any { it.identity == values.first().identity }) !=
          (after.occurrenceOverrides.any { it.identity == values.first().identity })) }
      .keys
      .map { it.scheduleId }
    return (scheduleIds + overrideParents).mapTo(linkedSetOf()) { ScheduleId(it) }
  }

  /** 生成仅用于本次 Sync 关联的内存 requestId；它不是 mutationId、receipt 或持久状态。 */
  private fun nextSyncRequestId(): String {
    requestSequence += 1
    return "room-v2-$accountId-$requestSequence"
  }
}

/** 为 ScheduleRepositoryFactory 绑定数据库、时区、墙钟和账号专属 gateway；create 本身不执行 I/O。 */
internal class RoomScheduleRepositoryFactory(
  private val database: ScheduleRoomDatabase,
  private val gatewayFactory: (AccountSession) -> ScheduleV2RepositoryGateway = { UnavailableScheduleV2RepositoryGateway },
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ScheduleRepositoryFactory {
  /** 为登录会话创建独立 Mutex 与 gateway binding；非登录会话必须由上层 Provider 拒绝。 */
  override fun create(session: AccountSession): ScheduleRepository = RoomScheduleRepository(
    accountId = requireNotNull(session.accountId) { "RoomScheduleRepository requires a logged-in account" },
    stateStore = ScheduleV2RoomStateStore(database),
    gateway = gatewayFactory(session),
    timeZone = timeZone,
    nowMillis = nowMillis,
  )
}

/** 将底层一次 HTTP 失败收敛为现有公共错误模型，不把 Ktor 或 HTTP body 泄漏给 UI。 */
private fun ScheduleV2CallResult.TransportFailure.toRemoteError(): ScheduleRemoteError = when {
  status == 200 && cause != null -> ScheduleRemoteError.InvalidResponse(cause)
  status == null && cause is HttpRequestTimeoutException ->
    ScheduleRemoteError.Timeout
  status != null -> ScheduleRemoteError.Server(status)
  else -> ScheduleRemoteError.Unexpected(cause ?: IllegalStateException("Schedule v2 transport failed"))
}

/** 在保留 projector 计算出的 pendingCount 的同时，仅覆盖远端可用性状态。 */
private fun ScheduleRepositoryStatus.withUnavailable(error: ScheduleRemoteError?): ScheduleRepositoryStatus = when {
  error == null -> this
  this is ScheduleRepositoryStatus.Ready -> ScheduleRepositoryStatus.Unavailable(pendingCount, error)
  this is ScheduleRepositoryStatus.Unavailable -> ScheduleRepositoryStatus.Unavailable(pendingCount, error)
  else -> this
}

/** HTTP 200 外壳不是业务成功证明；普通或原子 REJECTED 都必须让上层看到稳定业务失败。 */
private fun SyncResponse.rejectionError(): ScheduleRemoteError.MutationRejected? {
  val ordinary = sequenceOf(
    categories.upsertResults.map { it.code to it.reason },
    categories.deleteResults.map { it.code to it.reason },
    schedules.upsertResults.map { it.code to it.reason },
    schedules.deleteResults.map { it.code to it.reason },
    occurrenceOverrides.upsertResults.map { it.code to it.reason },
    occurrenceOverrides.deleteResults.map { it.code to it.reason },
  ).flatten().firstOrNull { it.first == MutationResultCode.REJECTED }
  if (ordinary != null) return ordinary.second.toBusinessRejection()

  atomicBatchResults.forEach { batch ->
    if (batch.code == AtomicBatchResultCode.REJECTED) return batch.reason.toBusinessRejection()
    val member = sequenceOf(
      batch.categories.upsertResults.map { it.code to it.reason },
      batch.categories.deleteResults.map { it.code to it.reason },
      batch.schedules.upsertResults.map { it.code to it.reason },
      batch.schedules.deleteResults.map { it.code to it.reason },
      batch.occurrenceOverrides.upsertResults.map { it.code to it.reason },
      batch.occurrenceOverrides.deleteResults.map { it.code to it.reason },
    ).flatten().firstOrNull { it.first == AtomicBatchResultCode.REJECTED }
    if (member != null) return member.second.toBusinessRejection()
  }
  return null
}

/** 日常单条结果与完整 Sync 共用同一稳定 reason 映射。 */
private fun ScheduleUpsertResult.rejectionError(): ScheduleRemoteError.MutationRejected? =
  if (code == MutationResultCode.REJECTED) reason.toBusinessRejection() else null

/** 日常删除结果与完整 Sync 共用同一稳定 reason 映射。 */
private fun ScheduleDeleteResult.rejectionError(): ScheduleRemoteError.MutationRejected? =
  if (code == MutationResultCode.REJECTED) reason.toBusinessRejection() else null

/** 严格 wire reason 逐项映射到公共枚举；未知或缺失 reason fail-closed 为 INVALID_REQUEST。 */
private fun ResultReason?.toBusinessRejection(): ScheduleRemoteError.MutationRejected =
  ScheduleRemoteError.MutationRejected(
    when (this) {
      ResultReason.INVALID_REQUEST -> ScheduleMutationBusinessRejectionReason.INVALID_REQUEST
      ResultReason.RESOURCE_NOT_FOUND -> ScheduleMutationBusinessRejectionReason.RESOURCE_NOT_FOUND
      ResultReason.RESOURCE_DELETED -> ScheduleMutationBusinessRejectionReason.RESOURCE_DELETED
      ResultReason.CATEGORY_NOT_FOUND -> ScheduleMutationBusinessRejectionReason.CATEGORY_NOT_FOUND
      ResultReason.RESOURCE_CHANGED -> ScheduleMutationBusinessRejectionReason.RESOURCE_CHANGED
      ResultReason.FINAL_GRAPH_INVALID -> ScheduleMutationBusinessRejectionReason.FINAL_GRAPH_INVALID
      ResultReason.UNSUPPORTED_RECURRENCE -> ScheduleMutationBusinessRejectionReason.UNSUPPORTED_RECURRENCE
      null -> ScheduleMutationBusinessRejectionReason.INVALID_REQUEST
    },
  )
