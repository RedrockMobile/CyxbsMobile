package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.ConfirmedResult
import com.cyxbs.pages.schedule.data.remote.ConfirmedResultCode
import com.cyxbs.pages.schedule.data.remote.DeleteResult
import com.cyxbs.pages.schedule.data.remote.MutationResponse
import com.cyxbs.pages.schedule.data.remote.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.SyncResponse
import com.cyxbs.pages.schedule.data.remote.UpsertResult
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentSyncState
import com.cyxbs.pages.schedule.domain.sync.PendingChange
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.uuid.UuidV7Generator

/** 整次响应无法安全应用时的原因；单条 REJECTED 不属于结构失败。 */
enum class ScheduleApplyFailureReason {
  RESPONSE_CORRELATION,
  INVALID_PAYLOAD,
  INVALID_LOCAL_STATE,
}

/** 响应应用结果；结构失败时调用方必须保留原始 Room 状态。 */
sealed interface ScheduleApplyResult {
  data class Success(
    val categories: List<CategorySyncState>,
    val schedules: List<ScheduleSyncState>,
    val occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ) : ScheduleApplyResult

  data class Failure(
    val reason: ScheduleApplyFailureReason,
    val message: String,
  ) : ScheduleApplyResult
}

/**
 * 把同步与日常逐资源响应合并回本地双快照。
 *
 * 成功项只在 capture 的 localRevision 仍是当前 pending 时清理 pending；请求期间形成的更新继续保留。
 * 分类和单次调整的服务端自增 ID 通过 capture 位置映射回本地 UUID，日程则直接使用双方共享的 UUID。
 */
class ScheduleResponseApplier(
  private val uuidGenerator: UuidV7Generator = UuidV7Generator(),
) {

  /** 应用完整同步响应：先建立分类映射，再处理日程和单次调整。 */
  suspend fun apply(
    capture: ScheduleSyncCapture,
    response: SyncResponse,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleApplyResult = guardedApply {
    validateSyncShape(capture, response)
    val states = LocalStates(categories, schedules, occurrenceAdjustments)
    // 父日程的远端删除会在处理 schedule 时级联移除本地单次调整。提前保存 confirmed ID 到本地 identity
    // 的映射，后续才能把服务端同时返回的 adjustment DELETED 识别为同一次级联删除，而非未知响应。
    val confirmedAdjustmentIdentities = states.adjustmentIdentitiesByRemoteId()
    applyCategorySync(states, capture, response)
    applyScheduleSync(states, capture, response)
    applyAdjustmentSync(states, capture, response, confirmedAdjustmentIdentities)
    states.toResult()
  }

  /** 应用日常新增、更新或删除响应。 */
  suspend fun applyMutation(
    capture: ScheduleMutationCapture,
    response: MutationResponse,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleApplyResult = guardedApply {
    validateMutationShape(capture, response)
    val states = LocalStates(categories, schedules, occurrenceAdjustments)
    applyCategoryMutation(states, capture, response)
    applyScheduleMutation(states, capture, response)
    applyAdjustmentMutation(states, capture, response)
    states.toResult()
  }

  /**
   * HTTP 400 没有任何逐项处理结论，所有 pending 原样保留。
   *
   * 该入口只统一 repository 的失败分支，不伪造成功、拒绝或远端删除。
   */
  fun discardUploaded(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleApplyResult = ScheduleApplyResult.Success(categories, schedules, occurrenceAdjustments)

  private suspend fun guardedApply(
    block: suspend () -> ScheduleApplyResult.Success,
  ): ScheduleApplyResult = try {
    block()
  } catch (failure: ApplyAbort) {
    ScheduleApplyResult.Failure(failure.reason, failure.message ?: "schedule response apply failed")
  } catch (failure: IllegalArgumentException) {
    ScheduleApplyResult.Failure(
      ScheduleApplyFailureReason.INVALID_PAYLOAD,
      failure.message ?: "schedule response contains invalid payload",
    )
  }

  /** 校验完整同步的七组结果与请求数组一一对应。 */
  private fun validateSyncShape(capture: ScheduleSyncCapture, response: SyncResponse) {
    requireCount(response.categories.confirmedResults, capture.request.categories.confirmed, "category confirmed")
    requireCount(response.categories.upsertResults, capture.request.categories.upserts, "category upsert")
    requireCount(response.categories.deleteResults, capture.request.categories.deletes, "category delete")
    requireCount(response.schedules.confirmedResults, capture.request.schedules.confirmed, "schedule confirmed")
    requireCount(response.schedules.upsertResults, capture.request.schedules.upserts, "schedule upsert")
    requireCount(response.schedules.deleteResults, capture.request.schedules.deletes, "schedule delete")
    requireCount(
      response.occurrenceAdjustments.confirmedResults,
      capture.request.occurrenceAdjustments.confirmed,
      "occurrence adjustment confirmed",
    )
    requireCount(
      response.occurrenceAdjustments.upsertResults,
      capture.request.occurrenceAdjustments.upserts,
      "occurrence adjustment upsert",
    )
    requireCount(
      response.occurrenceAdjustments.deleteResults,
      capture.request.occurrenceAdjustments.deletes,
      "occurrence adjustment delete",
    )
    response.categories.confirmedResults.forEachIndexed { index, result ->
      requireIdentity(result.id == capture.request.categories.confirmed[index].id, "category confirmed")
    }
    response.schedules.confirmedResults.forEachIndexed { index, result ->
      requireIdentity(result.id == capture.request.schedules.confirmed[index].id, "schedule confirmed")
    }
    response.occurrenceAdjustments.confirmedResults.forEachIndexed { index, result ->
      requireIdentity(
        result.id == capture.request.occurrenceAdjustments.confirmed[index].id,
        "occurrence adjustment confirmed",
      )
    }
    validateDeleteIdentities(response.categories.deleteResults, capture.request.categories.deletes.map { it.id })
    validateDeleteIdentities(response.schedules.deleteResults, capture.request.schedules.deletes.map { it.id })
    validateDeleteIdentities(
      response.occurrenceAdjustments.deleteResults,
      capture.request.occurrenceAdjustments.deletes.map { it.id },
    )
  }

  /** 校验日常响应的 upsert/delete 数量及删除 ID。 */
  private fun validateMutationShape(capture: ScheduleMutationCapture, response: MutationResponse) {
    requireCount(response.categories.upsertResults, capture.request.categories.upserts, "category upsert")
    requireCount(response.categories.deleteResults, capture.request.categories.deletes, "category delete")
    requireCount(response.schedules.upsertResults, capture.request.schedules.upserts, "schedule upsert")
    requireCount(response.schedules.deleteResults, capture.request.schedules.deletes, "schedule delete")
    requireCount(
      response.occurrenceAdjustments.upsertResults,
      capture.request.occurrenceAdjustments.upserts,
      "occurrence adjustment upsert",
    )
    requireCount(
      response.occurrenceAdjustments.deleteResults,
      capture.request.occurrenceAdjustments.deletes,
      "occurrence adjustment delete",
    )
    validateDeleteIdentities(response.categories.deleteResults, capture.request.categories.deletes.map { it.id })
    validateDeleteIdentities(response.schedules.deleteResults, capture.request.schedules.deletes.map { it.id })
    validateDeleteIdentities(
      response.occurrenceAdjustments.deleteResults,
      capture.request.occurrenceAdjustments.deletes.map { it.id },
    )
  }

  private fun requireCount(actual: List<*>, expected: List<*>, label: String) {
    abortUnless(
      actual.size == expected.size,
      ScheduleApplyFailureReason.RESPONSE_CORRELATION,
      "$label result count does not match request",
    )
  }

  private fun <ID, T> validateDeleteIdentities(
    results: List<DeleteResult<ID, T>>,
    expected: List<ID>,
  ) {
    results.forEachIndexed { index, result ->
      requireIdentity(result.id == expected[index], "delete")
    }
  }

  private suspend fun applyCategorySync(
    states: LocalStates,
    capture: ScheduleSyncCapture,
    response: SyncResponse,
  ) {
    response.categories.confirmedResults.forEach { result ->
      states.applyCategoryConfirmed(result)
    }
    applyCategoryUpserts(states, capture.categories, response.categories.upsertResults)
    applyCategoryDeletes(states, capture.categories, response.categories.deleteResults)
    response.categories.discoveredResults.forEach { states.putCategory(it, preferred = null, clear = false) }
  }

  private suspend fun applyCategoryMutation(
    states: LocalStates,
    capture: ScheduleMutationCapture,
    response: MutationResponse,
  ) {
    applyCategoryUpserts(states, capture.categories, response.categories.upsertResults)
    applyCategoryDeletes(states, capture.categories, response.categories.deleteResults)
  }

  private suspend fun applyCategoryUpserts(
    states: LocalStates,
    uploaded: List<UploadedCategoryPending>,
    results: List<UpsertResult<CategoryInput>>,
  ) {
    val upserts = uploaded.filter { it.kind == UploadedPendingKind.UPSERT }
    requireCount(results, upserts, "category upsert capture")
    results.forEachIndexed { index, result ->
      val captured = upserts[index]
      when (result.result) {
        MutationResultCode.SUCCESS -> states.putCategory(
          requireNotNull(result.resource) { "successful category upsert requires resource" },
          captured.identity,
          states.categories[captured.identity].matches(captured),
        )
        MutationResultCode.REJECTED -> result.resource?.let {
          states.putCategory(it, captured.identity, clear = false)
        }
        MutationResultCode.DELETED -> states.deleteCategory(captured.identity, captured)
      }
    }
  }

  private suspend fun applyCategoryDeletes(
    states: LocalStates,
    uploaded: List<UploadedCategoryPending>,
    results: List<DeleteResult<Long, CategoryInput>>,
  ) {
    val deletes = uploaded.filter { it.kind == UploadedPendingKind.DELETE }
    requireCount(results, deletes, "category delete capture")
    results.forEachIndexed { index, result ->
      val captured = deletes[index]
      when (result.result) {
        MutationResultCode.SUCCESS, MutationResultCode.DELETED ->
          states.deleteCategory(captured.identity, captured)
        MutationResultCode.REJECTED -> result.resource?.let {
          states.putCategory(it, captured.identity, clear = false)
        }
      }
    }
  }

  private suspend fun LocalStates.applyCategoryConfirmed(
    result: ConfirmedResult<Long, CategoryInput>,
  ) {
    val identity = categoryIdentityForRemoteId(result.id)
      ?: abort(ScheduleApplyFailureReason.RESPONSE_CORRELATION, "unknown confirmed category")
    when (result.result) {
      ConfirmedResultCode.CONFIRMED -> requireNoResource(result.resource, "confirmed category")
      ConfirmedResultCode.CHANGED -> putCategory(
        requireNotNull(result.resource) { "changed category requires resource" },
        identity,
        clear = false,
      )
      ConfirmedResultCode.DELETED -> deleteCategory(identity, uploaded = null)
    }
  }

  private suspend fun applyScheduleSync(
    states: LocalStates,
    capture: ScheduleSyncCapture,
    response: SyncResponse,
  ) {
    response.schedules.confirmedResults.forEach { states.applyScheduleConfirmed(it) }
    applyScheduleUpserts(states, capture.schedules, response.schedules.upsertResults)
    applyScheduleDeletes(states, capture.schedules, response.schedules.deleteResults)
    response.schedules.discoveredResults.forEach { states.putSchedule(it, uploaded = null, clear = false) }
  }

  private suspend fun applyScheduleMutation(
    states: LocalStates,
    capture: ScheduleMutationCapture,
    response: MutationResponse,
  ) {
    applyScheduleUpserts(states, capture.schedules, response.schedules.upsertResults)
    applyScheduleDeletes(states, capture.schedules, response.schedules.deleteResults)
  }

  private suspend fun applyScheduleUpserts(
    states: LocalStates,
    uploaded: List<UploadedSchedulePending>,
    results: List<UpsertResult<ScheduleInput>>,
  ) {
    val upserts = uploaded.filter { it.kind == UploadedPendingKind.UPSERT }
    requireCount(results, upserts, "schedule upsert capture")
    results.forEachIndexed { index, result ->
      val captured = upserts[index]
      when (result.result) {
        MutationResultCode.SUCCESS -> states.putSchedule(
          requireNotNull(result.resource) { "successful schedule upsert requires resource" },
          captured,
          states.schedules[captured.identity].matches(captured),
        )
        MutationResultCode.REJECTED -> result.resource?.let {
          states.putSchedule(it, uploaded = null, clear = false)
        }
        MutationResultCode.DELETED -> states.deleteSchedule(captured.identity, captured)
      }
    }
  }

  private suspend fun applyScheduleDeletes(
    states: LocalStates,
    uploaded: List<UploadedSchedulePending>,
    results: List<DeleteResult<String, ScheduleInput>>,
  ) {
    val deletes = uploaded.filter { it.kind == UploadedPendingKind.DELETE }
    requireCount(results, deletes, "schedule delete capture")
    results.forEachIndexed { index, result ->
      val captured = deletes[index]
      when (result.result) {
        MutationResultCode.SUCCESS, MutationResultCode.DELETED ->
          states.deleteSchedule(captured.identity, captured)
        MutationResultCode.REJECTED -> result.resource?.let {
          states.putSchedule(it, uploaded = null, clear = false)
        }
      }
    }
  }

  private suspend fun LocalStates.applyScheduleConfirmed(
    result: ConfirmedResult<String, ScheduleInput>,
  ) {
    val identity = ScheduleIdentity(result.id)
    abortUnless(
      schedules.containsKey(identity),
      ScheduleApplyFailureReason.RESPONSE_CORRELATION,
      "unknown confirmed schedule",
    )
    when (result.result) {
      ConfirmedResultCode.CONFIRMED -> requireNoResource(result.resource, "confirmed schedule")
      ConfirmedResultCode.CHANGED -> putSchedule(
        requireNotNull(result.resource) { "changed schedule requires resource" },
        uploaded = null,
        clear = false,
      )
      ConfirmedResultCode.DELETED -> deleteSchedule(identity, uploaded = null)
    }
  }

  private suspend fun applyAdjustmentSync(
    states: LocalStates,
    capture: ScheduleSyncCapture,
    response: SyncResponse,
    confirmedIdentities: Map<Long, OccurrenceAdjustmentIdentity>,
  ) {
    response.occurrenceAdjustments.confirmedResults.forEach { result ->
      states.applyAdjustmentConfirmed(result, confirmedIdentities[result.id])
    }
    applyAdjustmentUpserts(
      states,
      capture.occurrenceAdjustments,
      response.occurrenceAdjustments.upsertResults,
    )
    applyAdjustmentDeletes(
      states,
      capture.occurrenceAdjustments,
      response.occurrenceAdjustments.deleteResults,
    )
    response.occurrenceAdjustments.discoveredResults.forEach {
      states.putAdjustment(it, preferred = null, clear = false)
    }
  }

  private suspend fun applyAdjustmentMutation(
    states: LocalStates,
    capture: ScheduleMutationCapture,
    response: MutationResponse,
  ) {
    applyAdjustmentUpserts(
      states,
      capture.occurrenceAdjustments,
      response.occurrenceAdjustments.upsertResults,
    )
    applyAdjustmentDeletes(
      states,
      capture.occurrenceAdjustments,
      response.occurrenceAdjustments.deleteResults,
    )
  }

  private suspend fun applyAdjustmentUpserts(
    states: LocalStates,
    uploaded: List<UploadedOccurrenceAdjustmentPending>,
    results: List<UpsertResult<OccurrenceAdjustmentInput>>,
  ) {
    val upserts = uploaded.filter { it.kind == UploadedPendingKind.UPSERT }
    requireCount(results, upserts, "occurrence adjustment upsert capture")
    results.forEachIndexed { index, result ->
      val captured = upserts[index]
      when (result.result) {
        MutationResultCode.SUCCESS -> states.putAdjustment(
          requireNotNull(result.resource) { "successful occurrence adjustment upsert requires resource" },
          captured.identity,
          states.adjustments[captured.identity].matches(captured),
        )
        MutationResultCode.REJECTED -> result.resource?.let {
          states.putAdjustment(it, captured.identity, clear = false)
        }
        MutationResultCode.DELETED -> states.deleteAdjustment(captured.identity, captured)
      }
    }
  }

  private suspend fun applyAdjustmentDeletes(
    states: LocalStates,
    uploaded: List<UploadedOccurrenceAdjustmentPending>,
    results: List<DeleteResult<Long, OccurrenceAdjustmentInput>>,
  ) {
    val deletes = uploaded.filter { it.kind == UploadedPendingKind.DELETE }
    requireCount(results, deletes, "occurrence adjustment delete capture")
    results.forEachIndexed { index, result ->
      val captured = deletes[index]
      when (result.result) {
        MutationResultCode.SUCCESS, MutationResultCode.DELETED ->
          states.deleteAdjustment(captured.identity, captured)
        MutationResultCode.REJECTED -> result.resource?.let {
          states.putAdjustment(it, captured.identity, clear = false)
        }
      }
    }
  }

  private suspend fun LocalStates.applyAdjustmentConfirmed(
    result: ConfirmedResult<Long, OccurrenceAdjustmentInput>,
    capturedIdentity: OccurrenceAdjustmentIdentity?,
  ) {
    val currentIdentity = adjustmentIdentityForRemoteId(result.id)
    val identity = currentIdentity ?: capturedIdentity
      ?: abort(ScheduleApplyFailureReason.RESPONSE_CORRELATION, "unknown confirmed occurrence adjustment")
    when (result.result) {
      ConfirmedResultCode.CONFIRMED -> {
        // 只有 DELETED 可以在父日程先被删除后缺少当前状态；CONFIRMED 仍必须能关联到现存本地资源。
        abortUnless(
          currentIdentity != null,
          ScheduleApplyFailureReason.RESPONSE_CORRELATION,
          "confirmed occurrence adjustment is missing locally",
        )
        requireNoResource(result.resource, "confirmed occurrence adjustment")
      }
      ConfirmedResultCode.CHANGED -> putAdjustment(
        requireNotNull(result.resource) { "changed occurrence adjustment requires resource" },
        identity,
        clear = false,
      )
      ConfirmedResultCode.DELETED -> deleteAdjustment(identity, uploaded = null)
    }
  }

  /** 当前三类状态的可变索引；只在一次响应应用期间存在。 */
  private inner class LocalStates(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ) {
    val categories = uniqueStates(categories.map { it.identity to it }, "Category")
    val schedules = uniqueStates(schedules.map { it.identity to it }, "Schedule")
    val adjustments = uniqueStates(occurrenceAdjustments.map { it.identity to it }, "OccurrenceAdjustment")

    init {
      val slots = occurrenceAdjustments.map { it.identity.scheduleId to it.identity.originalOccurrenceDate }
      abortUnless(
        slots.size == slots.toSet().size,
        ScheduleApplyFailureReason.INVALID_LOCAL_STATE,
        "occurrence adjustments contain duplicate logical slots",
      )
    }

    fun categoryIdentityForRemoteId(remoteId: Long): CategoryIdentity? =
      categories.values.firstOrNull { it.remoteSnapshot?.resource?.remoteId == remoteId }?.identity

    fun adjustmentIdentityForRemoteId(remoteId: Long): OccurrenceAdjustmentIdentity? =
      adjustments.values.firstOrNull { it.remoteSnapshot?.resource?.remoteId == remoteId }?.identity

    /** 在父日程级联删除之前冻结远端 ID 映射，供同一 Sync 的 adjustment DELETED 完成关联。 */
    fun adjustmentIdentitiesByRemoteId(): Map<Long, OccurrenceAdjustmentIdentity> =
      adjustments.values.mapNotNull { state ->
        state.remoteSnapshot?.resource?.remoteId?.let { it to state.identity }
      }.toMap()

    suspend fun putCategory(input: CategoryInput, preferred: CategoryIdentity?, clear: Boolean) {
      val remoteId = requireNotNull(input.id) { "remote category must carry id" }
      val existing = categoryIdentityForRemoteId(remoteId)
      val identity = preferred ?: existing ?: CategoryIdentity(uuidGenerator.nextString())
      if (existing != null && existing != identity) {
        remapCategory(existing.id, identity.id)
        categories.remove(existing)
      }
      val old = categories[identity] ?: CategorySyncState(identity, remoteSnapshot = null)
      val remote = CategoryRemoteSnapshot(input.toDomain(identity))
      categories[identity] = old.copy(
        remoteSnapshot = remote,
        pending = if (clear) null else old.pending,
      )
    }

    fun deleteCategory(identity: CategoryIdentity, uploaded: UploadedCategoryPending?) {
      val old = categories[identity] ?: return
      val matches = uploaded == null || old.matches(uploaded)
      val pending = old.pending
      if (!matches && pending is PendingUpsert) {
        categories[identity] = old.copy(
          remoteSnapshot = null,
          pending = pending.copy(resource = pending.resource.copy(remoteId = null, version = 0)),
        )
      } else {
        categories.remove(identity)
      }
    }

    suspend fun putSchedule(
      input: ScheduleInput,
      uploaded: UploadedSchedulePending?,
      clear: Boolean,
    ) {
      val identity = ScheduleIdentity(input.id)
      uploaded?.let { requireIdentity(it.identity == identity, "schedule upsert") }
      val old = schedules[identity] ?: ScheduleSyncState(identity, remoteSnapshot = null)
      val remote = ScheduleRemoteSnapshot(input.toDomain(::categoryLocalId))
      schedules[identity] = old.copy(
        remoteSnapshot = remote,
        pending = if (clear) null else old.pending,
      )
    }

    fun deleteSchedule(identity: ScheduleIdentity, uploaded: UploadedSchedulePending?) {
      val old = schedules[identity] ?: return
      val matches = uploaded == null || old.matches(uploaded)
      val pending = old.pending
      if (!matches && pending is PendingUpsert) {
        schedules[identity] = old.copy(
          remoteSnapshot = null,
          pending = pending.copy(resource = pending.resource.copy(version = 0)),
        )
        resetChildRemotes(identity.id)
      } else {
        schedules.remove(identity)
        adjustments.entries.removeAll { it.key.scheduleId == identity.id }
      }
    }

    suspend fun putAdjustment(
      input: OccurrenceAdjustmentInput,
      preferred: OccurrenceAdjustmentIdentity?,
      clear: Boolean,
    ) {
      val remoteId = requireNotNull(input.id) { "remote occurrence adjustment must carry id" }
      abortUnless(
        schedules.containsKey(ScheduleIdentity(input.scheduleId)),
        ScheduleApplyFailureReason.INVALID_PAYLOAD,
        "occurrence adjustment parent schedule is missing",
      )
      val byRemote = adjustmentIdentityForRemoteId(remoteId)
      val bySlot = adjustments.keys.firstOrNull {
        it.scheduleId == input.scheduleId &&
          it.originalOccurrenceDate == input.originalOccurrenceDate
      }
      val identity = preferred ?: byRemote ?: bySlot ?: OccurrenceAdjustmentIdentity(
        localId = uuidGenerator.nextString(),
        scheduleId = input.scheduleId,
        originalOccurrenceDate = input.originalOccurrenceDate,
      )
      requireIdentity(
        identity.scheduleId == input.scheduleId &&
          identity.originalOccurrenceDate == input.originalOccurrenceDate,
        "occurrence adjustment slot",
      )
      listOfNotNull(byRemote, bySlot).filter { it != identity }.forEach(adjustments::remove)
      val old = adjustments[identity] ?: OccurrenceAdjustmentSyncState(identity, remoteSnapshot = null)
      val remote = OccurrenceAdjustmentRemoteSnapshot(input.toDomain(identity, ::categoryLocalId))
      adjustments[identity] = old.copy(
        remoteSnapshot = remote,
        pending = if (clear) null else old.pending,
      )
    }

    fun deleteAdjustment(
      identity: OccurrenceAdjustmentIdentity,
      uploaded: UploadedOccurrenceAdjustmentPending?,
    ) {
      val old = adjustments[identity] ?: return
      val matches = uploaded == null || old.matches(uploaded)
      val pending = old.pending
      if (!matches && pending is PendingUpsert) {
        adjustments[identity] = old.copy(
          remoteSnapshot = null,
          pending = pending.copy(resource = pending.resource.copy(remoteId = null, version = 0)),
        )
      } else {
        adjustments.remove(identity)
      }
    }

    /** 分类合并时将所有本地分类引用迁移到保留下来的 UUID。 */
    private fun remapCategory(from: String, to: String) {
      schedules.keys.toList().forEach { identity ->
        val state = requireNotNull(schedules[identity])
        schedules[identity] = state.copy(
          remoteSnapshot = state.remoteSnapshot?.let {
            ScheduleRemoteSnapshot(it.resource.remapCategory(from, to))
          },
          pending = state.pending.remapScheduleCategory(from, to),
        )
      }
      adjustments.keys.toList().forEach { identity ->
        val state = requireNotNull(adjustments[identity])
        adjustments[identity] = state.copy(
          remoteSnapshot = state.remoteSnapshot?.let {
            OccurrenceAdjustmentRemoteSnapshot(it.resource.remapCategory(from, to))
          },
          pending = state.pending.remapAdjustmentCategory(from, to),
        )
      }
    }

    /** 父日程被远端物理删除但本地有更新时，子调整也必须按首次创建重传。 */
    private fun resetChildRemotes(scheduleId: String) {
      adjustments.keys.toList().forEach { identity ->
        if (identity.scheduleId != scheduleId) return@forEach
        val state = requireNotNull(adjustments[identity])
        val pending = state.pending
        if (pending is PendingUpsert) {
          adjustments[identity] = state.copy(
            remoteSnapshot = null,
            pending = pending.copy(resource = pending.resource.copy(remoteId = null, version = 0)),
          )
        }
      }
    }

    private fun categoryLocalId(remoteId: Long): String? =
      categoryIdentityForRemoteId(remoteId)?.id

    fun toResult(): ScheduleApplyResult.Success = ScheduleApplyResult.Success(
      categories = categories.values.toList(),
      schedules = schedules.values.toList(),
      occurrenceAdjustments = adjustments.values.toList(),
    )
  }
}

private fun <K, V> uniqueStates(values: List<Pair<K, V>>, label: String): LinkedHashMap<K, V> {
  val result = linkedMapOf<K, V>()
  values.forEach { (key, value) ->
    abortUnless(
      result.put(key, value) == null,
      ScheduleApplyFailureReason.INVALID_LOCAL_STATE,
      "$label states contain duplicate identities",
    )
  }
  return result
}

private fun requireIdentity(condition: Boolean, label: String) {
  abortUnless(
    condition,
    ScheduleApplyFailureReason.RESPONSE_CORRELATION,
    "$label identity does not match request",
  )
}

private fun requireNoResource(resource: Any?, label: String) {
  abortUnless(
    resource == null,
    ScheduleApplyFailureReason.INVALID_PAYLOAD,
    "$label must not carry resource",
  )
}

private fun CategorySyncState?.matches(uploaded: UploadedCategoryPending): Boolean =
  this?.pending?.matches(uploaded.localRevision, uploaded.kind) == true

private fun ScheduleSyncState?.matches(uploaded: UploadedSchedulePending): Boolean =
  this?.pending?.matches(uploaded.localRevision, uploaded.kind) == true

private fun OccurrenceAdjustmentSyncState?.matches(
  uploaded: UploadedOccurrenceAdjustmentPending,
): Boolean = this?.pending?.matches(uploaded.localRevision, uploaded.kind) == true

private fun PendingChange<*, *>.matches(revision: Long, kind: UploadedPendingKind): Boolean =
  localRevision == revision && when (kind) {
    UploadedPendingKind.UPSERT -> this is PendingUpsert
    UploadedPendingKind.DELETE -> this is PendingDelete
  }

private fun ScheduleResource.remapCategory(from: String, to: String): ScheduleResource =
  if (categoryId.data == from) copy(categoryId = categoryId.copy(data = to)) else this

private fun OccurrenceAdjustmentResource.remapCategory(
  from: String,
  to: String,
): OccurrenceAdjustmentResource {
  val patch = categoryId.data
  return if (patch is FieldPatch.Replace && patch.value == from) {
    copy(categoryId = categoryId.copy(data = FieldPatch.Replace(to)))
  } else {
    this
  }
}

private fun PendingChange<ScheduleIdentity, ScheduleResource>?.remapScheduleCategory(
  from: String,
  to: String,
): PendingChange<ScheduleIdentity, ScheduleResource>? = when (this) {
  is PendingUpsert -> copy(resource = resource.remapCategory(from, to))
  else -> this
}

private fun PendingChange<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>?.remapAdjustmentCategory(
  from: String,
  to: String,
): PendingChange<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>? = when (this) {
  is PendingUpsert -> copy(resource = resource.remapCategory(from, to))
  else -> this
}

private class ApplyAbort(
  val reason: ScheduleApplyFailureReason,
  message: String,
) : IllegalStateException(message)

private fun abortUnless(
  condition: Boolean,
  reason: ScheduleApplyFailureReason,
  message: String,
) {
  if (!condition) abort(reason, message)
}

private fun abort(reason: ScheduleApplyFailureReason, message: String): Nothing =
  throw ApplyAbort(reason, message)
