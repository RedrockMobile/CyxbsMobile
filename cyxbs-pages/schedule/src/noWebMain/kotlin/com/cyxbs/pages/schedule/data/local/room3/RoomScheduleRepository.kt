package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.pages.schedule.data.failure.ScheduleFailureOperation
import com.cyxbs.pages.schedule.data.failure.ScheduleFailureRecordSink
import com.cyxbs.pages.schedule.data.failure.ScheduleFailureRecords
import com.cyxbs.pages.schedule.data.failure.createScheduleFailureRecords
import com.cyxbs.pages.schedule.data.failure.createRejectedScheduleFailureRecords
import com.cyxbs.pages.schedule.data.failure.scheduleIds
import com.cyxbs.pages.schedule.data.failure.toMutationRequest
import com.cyxbs.pages.schedule.data.failure.toFailureOperation
import com.cyxbs.pages.schedule.data.remote.KtorScheduleGateway
import com.cyxbs.pages.schedule.data.remote.MutationRequest
import com.cyxbs.pages.schedule.data.remote.MutationResponse
import com.cyxbs.pages.schedule.data.remote.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.ResultReason
import com.cyxbs.pages.schedule.data.remote.ScheduleCallResult
import com.cyxbs.pages.schedule.data.remote.SyncRequest
import com.cyxbs.pages.schedule.data.remote.SyncResponse
import com.cyxbs.pages.schedule.data.repository.ScheduleApplyResult
import com.cyxbs.pages.schedule.data.repository.ScheduleDailyMutationBridge
import com.cyxbs.pages.schedule.data.repository.ScheduleDailyMutationCapture
import com.cyxbs.pages.schedule.data.repository.ScheduleDailyMutationMethod
import com.cyxbs.pages.schedule.data.repository.ScheduleLocalCommandReducer
import com.cyxbs.pages.schedule.data.repository.ScheduleLocalCommandResult
import com.cyxbs.pages.schedule.data.repository.ScheduleRequestPlanner
import com.cyxbs.pages.schedule.data.repository.ScheduleResponseApplier
import com.cyxbs.pages.schedule.data.repository.ScheduleSnapshotProjection
import com.cyxbs.pages.schedule.data.repository.ScheduleSnapshotProjector
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncCapture
import com.cyxbs.pages.schedule.data.repository.UploadedOccurrenceAdjustmentPending
import com.cyxbs.pages.schedule.data.repository.UploadedPendingKind
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Room repository 所需的最小 Schedule 网络能力。
 *
 * 接口刻意不暴露重试、receipt 或队列状态：每个方法只代表一次已经绑定账号的 HTTP 尝试，失败后 pending 仍由
 * Room 双快照保留，下一次显式同步再收敛。
 */
internal interface ScheduleRepositoryGateway {
  /** 提交完整 inventory 与 pending 的一次 Sync 请求。 */
  suspend fun sync(accountId: String, request: SyncRequest): ScheduleCallResult<SyncResponse>

  /** 提交日常新增所涉及的逐资源变更。 */
  suspend fun createSchedule(
    accountId: String,
    input: MutationRequest
  ): ScheduleCallResult<MutationResponse>

  /** 提交日常修改所涉及的逐资源变更。 */
  suspend fun updateSchedule(
    accountId: String,
    input: MutationRequest
  ): ScheduleCallResult<MutationResponse>

  /** 提交日常删除所涉及的逐资源变更。 */
  suspend fun deleteSchedule(
    accountId: String,
    input: MutationRequest
  ): ScheduleCallResult<MutationResponse>
}

/** 将现有 Ktor 网关适配为 repository 的可替换最小接口，不复制 wire DTO 或 HTTP 解释。 */
internal class KtorScheduleRepositoryGateway(
  private val delegate: KtorScheduleGateway,
) : ScheduleRepositoryGateway {
  override suspend fun sync(accountId: String, request: SyncRequest) =
    delegate.sync(accountId, request)

  override suspend fun createSchedule(accountId: String, input: MutationRequest) =
    delegate.createSchedule(accountId, input)

  override suspend fun updateSchedule(accountId: String, input: MutationRequest) =
    delegate.updateSchedule(accountId, input)

  override suspend fun deleteSchedule(accountId: String, input: MutationRequest) =
    delegate.deleteSchedule(accountId, input)
}

/** 未完成平台网络接线时的安全默认实现；它绝不伪造已发送或已确认的结果。 */
internal object UnavailableScheduleRepositoryGateway : ScheduleRepositoryGateway {
  private fun unavailable(): ScheduleCallResult<Nothing> =
    ScheduleCallResult.TransportFailure(
      null,
      IllegalStateException("Schedule gateway is unavailable")
    )

  override suspend fun sync(
    accountId: String,
    request: SyncRequest
  ): ScheduleCallResult<SyncResponse> = unavailable()

  override suspend fun createSchedule(
    accountId: String,
    input: MutationRequest,
  ): ScheduleCallResult<MutationResponse> = unavailable()

  override suspend fun updateSchedule(
    accountId: String,
    input: MutationRequest,
  ): ScheduleCallResult<MutationResponse> = unavailable()

  override suspend fun deleteSchedule(
    accountId: String,
    input: MutationRequest,
  ): ScheduleCallResult<MutationResponse> = unavailable()
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
  private val stateStore: ScheduleRoomStateStore,
  private val gateway: ScheduleRepositoryGateway,
  private val timeZone: TimeZone,
  private val nowMillis: () -> Long,
  private val reducer: ScheduleLocalCommandReducer = ScheduleLocalCommandReducer(),
  private val planner: ScheduleRequestPlanner = ScheduleRequestPlanner(),
  private val applier: ScheduleResponseApplier = ScheduleResponseApplier(),
  private val projector: ScheduleSnapshotProjector = ScheduleSnapshotProjector(),
  private val dailyBridge: ScheduleDailyMutationBridge = ScheduleDailyMutationBridge(),
  private val failureRecords: ScheduleFailureRecordSink = ScheduleFailureRecords,
) : ScheduleRepository {
  private val mutex = Mutex()
  private val changes = MutableSharedFlow<ScheduleCalendarChange>(extraBufferCapacity = 32)
  private val mutableSnapshot = MutableStateFlow(ScheduleSnapshot())
  private var initialized = false

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
    val shouldSync = try {
      mutex.withLock {
        if (initialized) return@withLock false
        val local = readCurrentState()
        publishOrThrow(local)
        local.logScheduleState("LOCAL initialized", timeZone)
        initialized = true
        true
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Throwable) {
      // JSON 解码也可能在投影前失败；统一发布 Corrupted，避免 UI 永久停留在 Loading。
      mutableSnapshot.value = ScheduleSnapshot(
        status = ScheduleRepositoryStatus.Corrupted(failure),
        accountId = accountId,
      )
      throw failure
    }
    if (!shouldSync) return
    changes.emit(ScheduleCalendarChange.Initialized(accountId))
    synchronizeFull()
  }

  /**
   * 清空当前账号在 Room 与 Settings 中保存的日程事实，并立即发布空快照。
   *
   * 调用方必须先成功清空服务端；这里不会再发网络请求。操作与普通本地命令共用 [mutex]，避免并发修改穿插，
   * Room 三类状态与 revision 元数据则由同一个数据库事务删除。删除事件携带清理前全部 Schedule ID，使系统日历
   * 投影可以移除已经写入的事件。
   */
  override suspend fun clearLocalAccountData(expectedAccountId: String) {
    check(expectedAccountId == accountId) { "Cannot clear another account from RoomScheduleRepository" }
    val removedScheduleIds = mutex.withLock {
      // 该入口也负责从不可解码的旧开发数据中恢复，因此读取失败不能阻止物理清理。
      val ids = runCatching {
        readCurrentState().schedules.mapTo(linkedSetOf()) { ScheduleId(it.identity.id) }
      }.getOrElse {
        mutableSnapshot.value.schedules.mapTo(linkedSetOf()) { schedule -> schedule.id }
      }
      stateStore.clearAccountState(accountId)
      failureRecords.clear(accountId)
      lastRemoteError = null
      initialized = true
      publishOrThrow(ScheduleCommonAccountState(accountId, emptyList(), emptyList(), emptyList()))
      ids
    }
    if (removedScheduleIds.isNotEmpty()) {
      changes.emit(ScheduleCalendarChange.SchedulesCommitted(accountId, removedScheduleIds))
    }
  }

  /**
   * 本地命令先分配纯本地 revision、归约并一次替换完整 state；随后把本次 pending 及其 Schedule 关系闭包
   * 按逐资源请求立即提交。
   *
   * transport 等不确定失败保留 pending；HTTP 400 或 typed REJECTED 同样保留本地数据，但额外写入可修复的
   * 失败记录。请求期间形成的 U 始终继续保留。
   */
  override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult {
    if (command == ScheduleCommand.RequestSync) return synchronizeFull()
    check(initialized) { "RoomScheduleRepository must be initialized before execute" }

    var localEvent: ScheduleCalendarChange.SchedulesCommitted? = null
    var dailyCapture: ScheduleDailyMutationCapture? = null
    val localResult = mutex.withLock {
      val revision = stateStore.allocateLocalRevision(accountId)
      val before = readCurrentState()
      when (
        val reduced = reducer.reduce(
          categories = before.categories,
          schedules = before.schedules,
          occurrenceAdjustments = before.occurrenceAdjustments,
          command = command,
          nowMillis = nowMillis(),
          localRevision = revision,
        )
      ) {
        ScheduleLocalCommandResult.NoOp -> {
          log(REPOSITORY_LOG_TAG, "LOCAL noOp ${command.diagnosticLabel()}")
          ScheduleSyncResult.Success(attempted = false)
        }

        is ScheduleLocalCommandResult.Rejected -> {
          log(REPOSITORY_LOG_TAG, "LOCAL rejected ${command.diagnosticLabel()}")
          ScheduleSyncResult.Failure(
            ScheduleRemoteError.MutationRejected(ScheduleMutationBusinessRejectionReason.INVALID_REQUEST),
            attempted = false,
          )
        }

        is ScheduleLocalCommandResult.Applied -> {
          val after = ScheduleCommonAccountState(
            accountId,
            reduced.categories,
            reduced.schedules,
            reduced.occurrenceAdjustments,
          )
          // 本地 Applied 不代表远端恢复；发包和响应应用前仍保留既有 Unavailable。
          val changedIds = changedScheduleIds(before, after)
          val changedIdValues = changedIds.mapTo(linkedSetOf()) { it.value }
          before.logScheduleState(
            "LOCAL before ${command.diagnosticLabel()}",
            timeZone,
            changedIdValues,
          )
          persistAndPublish(after)
          after.logScheduleState(
            "LOCAL after ${command.diagnosticLabel()}",
            timeZone,
            changedIdValues,
          )
          log(
            REPOSITORY_LOG_TAG,
            "LOCAL persisted ${command.diagnosticLabel()} revision=$revision pendingCount=${after.pendingMutationCount()}",
          )
          if (changedIds.isNotEmpty()) {
            localEvent = ScheduleCalendarChange.SchedulesCommitted(accountId, changedIds)
          }
          safelyRemoveLocallyDeletedFailureRecords(changedIdValues, after)
          val captured = dailyBridge.capture(
            localRevision = revision,
            categories = after.categories,
            schedules = after.schedules,
            occurrenceAdjustments = after.occurrenceAdjustments,
          )
          when (captured) {
            is ScheduleDailyMutationCapture.Ready -> {
              safelyRefreshFailureSource(captured)
              log(
                REPOSITORY_LOG_TAG,
                "LOCAL captured method=${captured.method} " +
                  "operationCount=${captured.request.operationCount()}",
              )
            }

            is ScheduleDailyMutationCapture.Failure -> log(
              REPOSITORY_LOG_TAG,
              "LOCAL captureFailure ${command.diagnosticLabel()} reason=${captured.message}",
            )
          }
          dailyCapture = captured.takeUnless { it is ScheduleDailyMutationCapture.Failure }
          ScheduleSyncResult.Success(attempted = false)
        }
      }
    }
    localEvent?.let { changes.emit(it) }
    if (localResult is ScheduleSyncResult.Failure) return localResult
    return submitDaily(dailyCapture ?: return localResult)
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
        planner.capture(
          state.categories,
          state.schedules,
          state.occurrenceAdjustments
        )
      }
    } catch (failure: Throwable) {
      return publishUnavailableAfterRead(
        ScheduleRemoteError.InvalidResponse(failure),
        attempted = false
      )
    }
    val result = when (val call = gateway.sync(accountId, capture.request)) {
      is ScheduleCallResult.Completed -> applySyncResponse(
        capture,
        requireNotNull(call.wrapper.rawData)
      )

      is ScheduleCallResult.ApiFailure ->
        publishUnavailableAfterRead(ScheduleRemoteError.Server(call.status), true)

      is ScheduleCallResult.RequestInvalid -> retainFailedRequest(
        request = capture.request.toMutationRequest(),
        operation = ScheduleFailureOperation.SYNC,
        reasonCode = "HTTP_400",
        message = call.body,
        error =
          ScheduleRemoteError.InvalidResponse(IllegalArgumentException(call.body)),
      )

      is ScheduleCallResult.TransportFailure -> publishUnavailableAfterRead(
        call.toRemoteError(),
        true
      )
    }
    log(
      REPOSITORY_LOG_TAG,
      "REMOTE syncFinished result=${result::class.simpleName} " +
        "pendingCount=${mutableSnapshot.value.status.pendingCountForLog()}",
    )
    return result
  }

  /**
   * 提交本次命令产生的逐资源变更并记录最终 pending 数量；服务端允许其中一部分独立成功。
   */
  private suspend fun submitDaily(capture: ScheduleDailyMutationCapture): ScheduleSyncResult {
    val result = when (capture) {
      is ScheduleDailyMutationCapture.Ready -> {
        val call = when (capture.method) {
          ScheduleDailyMutationMethod.CREATE -> gateway.createSchedule(accountId, capture.request)
          ScheduleDailyMutationMethod.UPDATE -> gateway.updateSchedule(accountId, capture.request)
          ScheduleDailyMutationMethod.DELETE -> gateway.deleteSchedule(accountId, capture.request)
        }
        when (call) {
          is ScheduleCallResult.Completed ->
            applyDailyMutation(capture, requireNotNull(call.wrapper.rawData))

          is ScheduleCallResult.ApiFailure ->
            publishUnavailableAfterRead(ScheduleRemoteError.Server(call.status), true)

          is ScheduleCallResult.RequestInvalid -> retainFailedRequest(
            request = capture.request,
            operation = capture.method.toFailureOperation(),
            reasonCode = "HTTP_400",
            message = call.body,
            error = ScheduleRemoteError.InvalidResponse(IllegalArgumentException(call.body)),
          )

          is ScheduleCallResult.TransportFailure -> publishUnavailableAfterRead(
            call.toRemoteError(),
            true
          )
        }
      }

      is ScheduleDailyMutationCapture.Failure -> ScheduleSyncResult.Success(attempted = false)
    }
    log(
      REPOSITORY_LOG_TAG,
      "REMOTE dailyFinished method=${capture.methodForLog()} result=${result::class.simpleName} " +
          "pendingCount=${mutableSnapshot.value.status.pendingCountForLog()}",
    )
    return result
  }

  /** 把完整 Sync 返回的 canonical 状态一次性落库并发布远端提交事件。 */
  private suspend fun applySyncResponse(
    capture: ScheduleSyncCapture,
    response: SyncResponse,
  ): ScheduleSyncResult {
    val businessError = response.rejectionError()
    var event: ScheduleCalendarChange.RemoteCommitted? = null
    var confirmedIds = emptySet<String>()
    val result = mutex.withLock {
      val before = readCurrentState()
      val rejectedRecords = createRejectedScheduleFailureRecords(
        operation = ScheduleFailureOperation.SYNC,
        request = capture.request,
        response = response,
        currentCategories = before.categories,
        currentSchedules = before.schedules,
        adjustmentDeleteScheduleIds = capture.occurrenceAdjustments.adjustmentDeleteScheduleIds(),
        failedAt = nowMillis(),
      )
      when (val applied = applier.apply(
        capture,
        response,
        before.categories,
        before.schedules,
        before.occurrenceAdjustments
      )) {
        is ScheduleApplyResult.Failure -> {
          log(
            REPOSITORY_LOG_TAG,
            "REMOTE responseApplyFailed reason=${applied.reason} detail=${applied.message}",
          )
          val error = ScheduleRemoteError.InvalidResponse(IllegalArgumentException(applied.message))
          publishUnavailable(before, error)
          ScheduleSyncResult.Failure(error, true)
        }

        is ScheduleApplyResult.Success -> {
          val after = ScheduleCommonAccountState(
            accountId,
            applied.categories,
            applied.schedules,
            applied.occurrenceAdjustments
          )
          persistAndPublish(after, clearRemoteError = true)
          after.logScheduleState("LOCAL after SYNC merge", timeZone)
          event = ScheduleCalendarChange.RemoteCommitted(
            accountId,
            changedScheduleIds(before, after).takeIf { it.isNotEmpty() })
          safelyRecordFailures(rejectedRecords)
          confirmedIds = confirmedScheduleIds(capture.affectedScheduleIds(), after)
          businessError?.let { ScheduleSyncResult.Failure(it, true) }
            ?: ScheduleSyncResult.Success()
        }
      }
    }
    safelyRemoveFailureRecords(confirmedIds)
    event?.let { changes.emit(it) }
    return result
  }

  /** 日常逐资源响应复用 canonical 合并与 R→U 规则，REJECTED 项单独保留失败记录。 */
  private suspend fun applyDailyMutation(
    capture: ScheduleDailyMutationCapture.Ready,
    response: MutationResponse,
  ): ScheduleSyncResult {
    val businessError = response.rejectionError()
    var event: ScheduleCalendarChange.RemoteCommitted? = null
    var confirmedIds = emptySet<String>()
    val result = mutex.withLock {
      val before = readCurrentState()
      val rejectedRecords = createRejectedScheduleFailureRecords(
        operation = capture.method.toFailureOperation(),
        request = capture.request,
        response = response,
        currentCategories = before.categories,
        currentSchedules = before.schedules,
        adjustmentDeleteScheduleIds = capture.capture.occurrenceAdjustments.adjustmentDeleteScheduleIds(),
        failedAt = nowMillis(),
      )
      when (
        val applied = dailyBridge.apply(
          capture,
          response,
          before.categories,
          before.schedules,
          before.occurrenceAdjustments,
        )
      ) {
        is ScheduleApplyResult.Failure -> {
          log(
            REPOSITORY_LOG_TAG,
            "REMOTE responseApplyFailed reason=${applied.reason} detail=${applied.message}",
          )
          val error = ScheduleRemoteError.InvalidResponse(IllegalArgumentException(applied.message))
          publishUnavailable(before, error)
          ScheduleSyncResult.Failure(error, true)
        }

        is ScheduleApplyResult.Success -> {
          val after = ScheduleCommonAccountState(
            accountId,
            applied.categories,
            applied.schedules,
            applied.occurrenceAdjustments
          )
          persistAndPublish(after, clearRemoteError = true)
          after.logScheduleState("LOCAL after MUTATION merge", timeZone)
          event = ScheduleCalendarChange.RemoteCommitted(
            accountId,
            changedScheduleIds(before, after).takeIf { it.isNotEmpty() })
          safelyRecordFailures(rejectedRecords)
          confirmedIds = confirmedScheduleIds(capture.affectedScheduleIds(), after)
          businessError?.let { ScheduleSyncResult.Failure(it, true) }
            ?: ScheduleSyncResult.Success()
        }
      }
    }
    safelyRemoveFailureRecords(confirmedIds)
    event?.let { changes.emit(it) }
    return result
  }

  /**
   * 保留服务端明确拒绝的本地数据，并写入可修复记录。
   *
   * HTTP 400 没有 canonical data；typed REJECTED 也不是成功确认。两者都不能清除 pending，否则本地新建数据
   * 会直接消失。记录失败后只更新远端状态，Room 双快照保持不变，等待用户编辑或后续同步成功。
   */
  private suspend fun retainFailedRequest(
    request: MutationRequest,
    operation: ScheduleFailureOperation,
    reasonCode: String,
    message: String,
    error: ScheduleRemoteError,
  ): ScheduleSyncResult {
    mutex.withLock {
      val before = readCurrentState()
      val records = createScheduleFailureRecords(
        operation = operation,
        request = request,
        currentCategories = before.categories,
        currentSchedules = before.schedules,
        currentAdjustments = before.occurrenceAdjustments,
        failedAt = nowMillis(),
        reasonCode = reasonCode,
        message = message.take(MAX_FAILURE_MESSAGE_LENGTH),
      )
      safelyRecordFailures(records)
      publishUnavailable(before, error)
      before.logScheduleState("LOCAL retained after deterministic failure", timeZone)
    }
    return ScheduleSyncResult.Failure(error, true)
  }

  /** 已有失败项再次编辑或修改单次调整后刷新源请求；持久化失败不影响 Room 的 local-first 提交。 */
  private fun safelyRefreshFailureSource(capture: ScheduleDailyMutationCapture.Ready) {
    runCatching {
      failureRecords.refreshSources(
        accountId,
        capture.request,
        capture.capture.occurrenceAdjustments.adjustmentDeleteScheduleIds(),
      )
    }
      .onFailure {
        log(
          REPOSITORY_LOG_TAG,
          "LOCAL failureRecordRefreshFailed type=${it::class.simpleName}"
        )
      }
  }

  /** 失败记录持久化异常只写诊断日志，不回滚已经完成的 Room 状态应用。 */
  private fun safelyRecordFailures(records: List<com.cyxbs.pages.schedule.data.failure.ScheduleFailureRecord>) {
    if (records.isEmpty()) return
    runCatching { failureRecords.record(accountId, records) }
      .onFailure {
        log(
          REPOSITORY_LOG_TAG,
          "LOCAL failureRecordWriteFailed type=${it::class.simpleName}"
        )
      }
  }

  /** 成功 canonical 响应到达后删除对应失败记录；失败记录存储异常不回滚已确认的远端结果。 */
  private fun safelyRemoveFailureRecords(scheduleIds: Set<String>) {
    runCatching { failureRecords.remove(accountId, scheduleIds) }
      .onFailure {
        log(
          REPOSITORY_LOG_TAG,
          "LOCAL failureRecordRemoveFailed type=${it::class.simpleName}"
        )
      }
  }

  /**
   * 本地删除完成后立即移除旧的可编辑失败记录。
   *
   * 这一步必须发生在 capture 之前：从未上传成功的 CREATE 会被 reducer 与 DELETE 直接抵消，不会再生成网络
   * 批次。若普通 DELETE 随后被服务端明确拒绝，[retainFailedRequest] 会按新的删除请求重新生成记录。
   */
  private fun safelyRemoveLocallyDeletedFailureRecords(
    changedScheduleIds: Set<String>,
    state: ScheduleCommonAccountState,
  ) {
    if (changedScheduleIds.isEmpty()) return
    val visibleScheduleIds = state.schedules.mapNotNullTo(hashSetOf()) { scheduleState ->
      scheduleState.effectiveResource()?.let { scheduleState.identity.id }
    }
    safelyRemoveFailureRecords(changedScheduleIds - visibleScheduleIds)
  }

  /** 只返回当前已经没有 pending 变更的 Schedule identity。 */
  private fun confirmedScheduleIds(
    scheduleIds: Set<String>,
    state: ScheduleCommonAccountState,
  ): Set<String> {
    if (scheduleIds.isEmpty()) return emptySet()
    val pendingIds = state.schedules
      .asSequence()
      .filter { it.pending != null }
      .mapTo(hashSetOf()) { it.identity.id }
    state.occurrenceAdjustments
      .asSequence()
      .filter { it.pending != null }
      .mapTo(pendingIds) { it.identity.scheduleId }
    return scheduleIds - pendingIds
  }

  /** Sync 请求可能只删除单次调整；capture 仍能把数字远端 ID 关联回所属日程。 */
  private fun ScheduleSyncCapture.affectedScheduleIds(): Set<String> =
    request.toMutationRequest().scheduleIds() + occurrenceAdjustments.map { it.identity.scheduleId }

  /** 日常请求可能只删除单次调整；capture 保存了请求 JSON 中没有的所属日程。 */
  private fun ScheduleDailyMutationCapture.Ready.affectedScheduleIds(): Set<String> =
    request.scheduleIds() + capture.occurrenceAdjustments.map { it.identity.scheduleId }

  /** 只提取本轮单次调整 DELETE 的远端 ID 与父日程映射。 */
  private fun List<UploadedOccurrenceAdjustmentPending>.adjustmentDeleteScheduleIds(): Map<Long, String> =
    asSequence()
      .filter { it.kind == UploadedPendingKind.DELETE }
      .mapNotNull { pending -> pending.remoteId?.let { it to pending.identity.scheduleId } }
      .toMap()

  /** 从 Room 读取三类 state 并在 mapper 边界校验账号及 pending 形态。 */
  private suspend fun readCurrentState(): ScheduleCommonAccountState =
    stateStore.readAccountState(accountId).toCommonAccountState(accountId)

  /**
   * 在投影成功后原子替换 Room 三类 state，再发布同一份快照；投影失败不会写入部分状态。
   *
   * 本地 Applied 不改变 [lastRemoteError]；只有已成功应用且非 REJECTED 的 Completed 响应传入
   * [clearRemoteError]，才能重新显示 Ready。
   */
  private suspend fun persistAndPublish(
    state: ScheduleCommonAccountState,
    remoteError: ScheduleRemoteError? = null,
    clearRemoteError: Boolean = false,
  ) {
    require(!clearRemoteError || remoteError == null) { "successful response must not carry a remote error" }
    if (clearRemoteError) lastRemoteError = null
    if (remoteError != null) lastRemoteError = remoteError
    val projected = project(state)
    val roomState = state.toRoomAccountState()
    stateStore.replaceAccountState(
      accountId,
      roomState.categories,
      roomState.schedules,
      roomState.occurrenceAdjustments
    )
    mutableSnapshot.value =
      projected.copy(status = projected.status.withUnavailable(lastRemoteError))
  }

  /** 发布现有 Room state 的 Unavailable 快照，不重建、清除或覆盖任何 pending。 */
  private fun publishUnavailable(state: ScheduleCommonAccountState, error: ScheduleRemoteError) {
    lastRemoteError = error
    val projected = project(state)
    mutableSnapshot.value =
      projected.copy(status = projected.status.withUnavailable(lastRemoteError))
  }

  /** 读取当前状态后发布不可用结果；读/投影本身损坏时转为 Corrupted。 */
  private suspend fun publishUnavailableAfterRead(
    error: ScheduleRemoteError,
    attempted: Boolean,
  ): ScheduleSyncResult = try {
    mutex.withLock {
      val state = readCurrentState()
      publishUnavailable(state, error)
      state.logScheduleState("LOCAL retained after REMOTE failure", timeZone)
      log(
        REPOSITORY_LOG_TAG,
        "REMOTE unavailable type=${error::class.simpleName} pendingCount=${state.pendingMutationCount()}",
      )
    }
    ScheduleSyncResult.Failure(error, attempted)
  } catch (failure: Throwable) {
    mutableSnapshot.value = ScheduleSnapshot(
      status = ScheduleRepositoryStatus.Corrupted(failure),
      accountId = accountId,
    )
    ScheduleSyncResult.Failure(ScheduleRemoteError.InvalidResponse(failure), attempted)
  }

  /** 初始化本地快照；错误直接发布 Corrupted 并让调用方 fail-closed。 */
  private fun publishOrThrow(state: ScheduleCommonAccountState) {
    try {
      val projected = project(state)
      mutableSnapshot.value = projected
    } catch (failure: Throwable) {
      mutableSnapshot.value = ScheduleSnapshot(
        status = ScheduleRepositoryStatus.Corrupted(failure),
        accountId = accountId
      )
      throw failure
    }
  }

  /** SnapshotProjector 的 Failure 不允许降级成局部 UI；调用方必须保持 Room 数据并报告损坏。 */
  private fun project(state: ScheduleCommonAccountState): ScheduleSnapshot = when (
    val result = projector.project(
      accountId,
      timeZone,
      state.categories,
      state.schedules,
      state.occurrenceAdjustments
    )
  ) {
    is ScheduleSnapshotProjection.Success -> result.snapshot
    is ScheduleSnapshotProjection.Failure -> throw IllegalArgumentException(result.message)
  }

  /** 计算会影响单向日历投影的 Schedule identity，单次调整改动归属其 parent Schedule。 */
  private fun changedScheduleIds(
    before: ScheduleCommonAccountState,
    after: ScheduleCommonAccountState,
  ): Set<ScheduleId> {
    val scheduleIds = (before.schedules + after.schedules)
      .groupBy { it.identity.id }
      .filter { (_, values) ->
        values.distinct().size > 1 || values.size == 1 &&
            ((before.schedules.any { it.identity.id == values.first().identity.id }) !=
                (after.schedules.any { it.identity.id == values.first().identity.id }))
      }
      .keys
    val adjustmentParents = (before.occurrenceAdjustments + after.occurrenceAdjustments)
      .groupBy { it.identity }
      .filter { (_, values) ->
        values.distinct().size > 1 || values.size == 1 &&
            ((before.occurrenceAdjustments.any { it.identity == values.first().identity }) !=
                (after.occurrenceAdjustments.any { it.identity == values.first().identity }))
      }
      .keys
      .map { it.scheduleId }
    return (scheduleIds + adjustmentParents).mapTo(linkedSetOf()) { ScheduleId(it) }
  }

  private companion object {
    const val REPOSITORY_LOG_TAG = "ScheduleRepository"
    const val MAX_FAILURE_MESSAGE_LENGTH = 1_000
  }
}

/** 生成不包含标题、描述等用户内容的命令标识，便于把本地落库与后续网络请求对应起来。 */
private fun ScheduleCommand.diagnosticLabel(): String = when (this) {
  is ScheduleCommand.Create -> "Create scheduleId=${schedule.id.value}"
  is ScheduleCommand.Update -> "Update scheduleId=${schedule.id.value}"
  is ScheduleCommand.Delete -> "Delete scheduleId=${scheduleId.value}"
  is ScheduleCommand.CompleteNonRepeating -> "CompleteNonRepeating scheduleId=${scheduleId.value}"
  is ScheduleCommand.UpsertOccurrenceAdjustment ->
    "UpsertOccurrenceAdjustment scheduleId=${adjustment.scheduleId.value}"

  is ScheduleCommand.DeleteOccurrenceAdjustment -> "DeleteOccurrenceAdjustment scheduleId=${scheduleId.value}"
  is ScheduleCommand.SplitSeries ->
    "SplitSeries previousId=${previousSchedule.id.value} followingId=${followingSchedule.id.value}"

  is ScheduleCommand.DeleteThisAndFollowing -> "DeleteThisAndFollowing scheduleId=${previousSchedule.id.value}"
  is ScheduleCommand.CreateCategory -> "CreateCategory categoryId=${category.id.value}"
  is ScheduleCommand.UpdateCategory -> "UpdateCategory categoryId=${category.id.value}"
  is ScheduleCommand.ReorderCategories -> "ReorderCategories count=${categories.size}"
  is ScheduleCommand.SaveScheduleWithNewCategory ->
    "SaveScheduleWithNewCategory scheduleId=${schedule.id.value} categoryId=${category.id.value}"

  is ScheduleCommand.SaveOccurrenceWithNewCategory ->
    "SaveOccurrenceWithNewCategory scheduleId=${adjustment.scheduleId.value} categoryId=${category.id.value}"

  is ScheduleCommand.DeleteCategory -> "DeleteCategory categoryId=${categoryId.value}"
  ScheduleCommand.RequestSync -> "RequestSync"
}

/** 统计 Room 双快照状态中仍需远端确认的资源数量。 */
private fun ScheduleCommonAccountState.pendingMutationCount(): Int =
  categories.count { it.pending != null } +
      schedules.count { it.pending != null } +
      occurrenceAdjustments.count { it.pending != null }

/** 统计请求成员数，不展开或记录任何业务 payload。 */
private fun MutationRequest.operationCount(): Int =
  categories.upserts.size + categories.deletes.size +
      schedules.upserts.size + schedules.deletes.size +
      occurrenceAdjustments.upserts.size + occurrenceAdjustments.deletes.size

/** 日常 capture 无法形成有效请求时返回 NONE，避免诊断日志反向影响业务分支。 */
private fun ScheduleDailyMutationCapture.methodForLog(): String = when (this) {
  is ScheduleDailyMutationCapture.Ready -> method.name
  is ScheduleDailyMutationCapture.Failure -> "NONE"
}

/** 从已发布快照读取 pending 数量；加载或损坏状态没有可信计数。 */
private fun ScheduleRepositoryStatus.pendingCountForLog(): Int? = when (this) {
  is ScheduleRepositoryStatus.Ready -> pendingCount
  is ScheduleRepositoryStatus.Recovered -> pendingCount
  is ScheduleRepositoryStatus.Unavailable -> pendingCount
  ScheduleRepositoryStatus.Loading,
  is ScheduleRepositoryStatus.Corrupted,
    -> null
}

/** 为 ScheduleRepositoryFactory 绑定数据库、时区、墙钟和账号专属 gateway；create 本身不执行 I/O。 */
internal class RoomScheduleRepositoryFactory(
  private val database: ScheduleRoomDatabase,
  private val gatewayFactory: (AccountSession) -> ScheduleRepositoryGateway = { UnavailableScheduleRepositoryGateway },
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
  private val failureRecords: ScheduleFailureRecordSink = ScheduleFailureRecords,
) : ScheduleRepositoryFactory {
  /** 为登录会话创建独立 Mutex 与 gateway binding；非登录会话必须由上层 Provider 拒绝。 */
  override fun create(session: AccountSession): ScheduleRepository = RoomScheduleRepository(
    accountId = requireNotNull(session.accountId) { "RoomScheduleRepository requires a logged-in account" },
    stateStore = ScheduleRoomStateStore(database),
    gateway = gatewayFactory(session),
    timeZone = timeZone,
    nowMillis = nowMillis,
    failureRecords = failureRecords,
  )
}

/** 将底层一次 HTTP 失败收敛为现有公共错误模型，不把 Ktor 或 HTTP body 泄漏给 UI。 */
private fun ScheduleCallResult.TransportFailure.toRemoteError(): ScheduleRemoteError = when {
  status == 200 && cause != null -> ScheduleRemoteError.InvalidResponse(cause)
  status == null && cause is HttpRequestTimeoutException ->
    ScheduleRemoteError.Timeout

  status != null -> ScheduleRemoteError.Server(status)
  else -> ScheduleRemoteError.Unexpected(
    cause ?: IllegalStateException("Schedule transport failed")
  )
}

/** 在保留 projector 计算出的 pendingCount 的同时，仅覆盖远端可用性状态。 */
private fun ScheduleRepositoryStatus.withUnavailable(error: ScheduleRemoteError?): ScheduleRepositoryStatus =
  when {
    error == null -> this
    this is ScheduleRepositoryStatus.Ready -> ScheduleRepositoryStatus.Unavailable(
      pendingCount,
      error
    )

    this is ScheduleRepositoryStatus.Unavailable -> ScheduleRepositoryStatus.Unavailable(
      pendingCount,
      error
    )

    else -> this
  }

/** HTTP 200 允许部分成功；存在任一 REJECTED 时仍让当前命令调用方得到稳定业务失败。 */
private fun SyncResponse.rejectionError(): ScheduleRemoteError.MutationRejected? {
  val rejected = sequenceOf(
    categories.upsertResults.map { it.result to it.reason },
    categories.deleteResults.map { it.result to it.reason },
    schedules.upsertResults.map { it.result to it.reason },
    schedules.deleteResults.map { it.result to it.reason },
    occurrenceAdjustments.upsertResults.map { it.result to it.reason },
    occurrenceAdjustments.deleteResults.map { it.result to it.reason },
  ).flatten().firstOrNull { it.first == MutationResultCode.REJECTED }
  if (rejected == null) return null
  return rejected.second.toBusinessRejection()
}

/** 日常响应与完整 Sync 共用同一稳定 reason 映射。 */
private fun MutationResponse.rejectionError(): ScheduleRemoteError.MutationRejected? {
  val rejected = sequenceOf(
    categories.upsertResults.map { it.result to it.reason },
    categories.deleteResults.map { it.result to it.reason },
    schedules.upsertResults.map { it.result to it.reason },
    schedules.deleteResults.map { it.result to it.reason },
    occurrenceAdjustments.upsertResults.map { it.result to it.reason },
    occurrenceAdjustments.deleteResults.map { it.result to it.reason },
  ).flatten().firstOrNull { it.first == MutationResultCode.REJECTED }
  if (rejected == null) return null
  return rejected.second.toBusinessRejection()
}

/** 严格 wire reason 逐项映射到公共枚举；未知或缺失 reason fail-closed 为 INVALID_REQUEST。 */
private fun ResultReason?.toBusinessRejection(): ScheduleRemoteError.MutationRejected =
  ScheduleRemoteError.MutationRejected(
    when (this) {
      ResultReason.INVALID_REQUEST -> ScheduleMutationBusinessRejectionReason.INVALID_REQUEST
      ResultReason.CATEGORY_NOT_FOUND -> ScheduleMutationBusinessRejectionReason.CATEGORY_NOT_FOUND
      ResultReason.RESOURCE_CHANGED -> ScheduleMutationBusinessRejectionReason.RESOURCE_CHANGED
      ResultReason.DUPLICATE_CATEGORY_NAME ->
        ScheduleMutationBusinessRejectionReason.DUPLICATE_CATEGORY_NAME
      ResultReason.CATEGORY_IN_USE -> ScheduleMutationBusinessRejectionReason.CATEGORY_IN_USE
      ResultReason.SCHEDULE_NOT_FOUND -> ScheduleMutationBusinessRejectionReason.SCHEDULE_NOT_FOUND
      ResultReason.UNSUPPORTED_RECURRENCE -> ScheduleMutationBusinessRejectionReason.UNSUPPORTED_RECURRENCE
      null -> ScheduleMutationBusinessRejectionReason.INVALID_REQUEST
    },
  )
