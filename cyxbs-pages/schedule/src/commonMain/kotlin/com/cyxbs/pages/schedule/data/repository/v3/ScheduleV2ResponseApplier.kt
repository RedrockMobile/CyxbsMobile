package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResult
import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResultCode
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.CategoryDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.CategoryUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ResultReason
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.SyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.PendingChange
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ResourceIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.SyncResource

/** fail-closed 拒绝整次应用时的最小原因分类。 */
enum class ScheduleV2ApplyFailureReason {
  REQUEST_ID_MISMATCH,
  RESPONSE_CORRELATION,
  SAME_VERSION_CONFLICT,
  REMOTE_VERSION_REGRESSION,
  INVALID_PAYLOAD,
  INVALID_LOCAL_STATE,
}

/** 响应应用结果；任何歧义都返回 Failure，调用方必须保持原状态。 */
sealed interface ScheduleV2ApplyResult {
  /** 全部校验通过后一次性返回的三类新状态。 */
  data class Success(
    val categories: List<CategorySyncState>,
    val schedules: List<ScheduleSyncState>,
    val occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ) : ScheduleV2ApplyResult

  /** 校验失败；调用方必须继续使用传入的完整旧状态。 */
  data class Failure(
    val reason: ScheduleV2ApplyFailureReason,
    val message: String,
  ) : ScheduleV2ApplyResult
}

/**
 * 把一次同步响应原子地投影回三类纯状态集合。
 *
 * 本层只消费 gateway 已解码的 data，不解释 HTTP/status。先完成关联、canonical 候选选择和版本校验，
 * 全部成功后才返回新集合；Failure 时调用方不得提交任何局部结果。
 */
class ScheduleV2ResponseApplier {
  /**
   * 对 [capture] 对应的 [response] 做完整校验并生成新状态。
   *
   * 请求期间形成的更高 revision U 只接收 remote 更新，不会被 R 的响应清除；原子批次则必须所有成员
   * revision 都仍匹配才整体清除，否则全部保留。
   */
  fun apply(
    capture: ScheduleV2SyncCapture,
    response: SyncResponse,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2ApplyResult = try {
    applyOrThrow(capture, response, categories, schedules, occurrenceOverrides)
  } catch (failure: ApplyAbort) {
    ScheduleV2ApplyResult.Failure(failure.reason, failure.message ?: "Schedule v2 apply failed")
  } catch (failure: IllegalArgumentException) {
    ScheduleV2ApplyResult.Failure(
      ScheduleV2ApplyFailureReason.INVALID_PAYLOAD,
      failure.message ?: "Schedule v2 response contains invalid payload",
    )
  }

  /**
   * 丢弃服务端已经明确判定为无效的本次 uploaded pending。
   *
   * HTTP 400 没有 typed data 可合并，因此只生成内存中的 REJECTED 对齐结果并复用同一套 compare-and-clear；
   * 请求期间形成的更高 revision U 或不同 localBatchId 仍会保留，remoteSnapshot 也不会被伪造或覆盖。
   */
  fun discardUploaded(
    capture: ScheduleV2SyncCapture,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2ApplyResult = apply(
    capture = capture,
    response = rejectedResponse(capture.request),
    categories = categories,
    schedules = schedules,
    occurrenceOverrides = occurrenceOverrides,
  )

  private fun applyOrThrow(
    capture: ScheduleV2SyncCapture,
    response: SyncResponse,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2ApplyResult.Success {
    abortUnless(
      response.syncRequestId == capture.request.syncRequestId,
      ScheduleV2ApplyFailureReason.REQUEST_ID_MISMATCH,
      "syncRequestId does not match capture",
    )
    val categoryStates = uniqueCategoryStates(categories)
    val scheduleStates = uniqueScheduleStates(schedules)
    val overrideStates = uniqueOverrideStates(occurrenceOverrides)

    val ordinaryClears = correlateOrdinary(capture, response)
    val atomicClears = correlateAtomic(
      capture,
      response.atomicBatchResults,
      categoryStates,
      scheduleStates,
      overrideStates,
    )
    val candidates = collectCandidates(response)
    val categoryUpdates = resolveCategoryUpdates(candidates, categoryStates)
    val scheduleUpdates = resolveScheduleUpdates(candidates, scheduleStates)
    val overrideUpdates = resolveOverrideUpdates(candidates, overrideStates)

    return try {
      ScheduleV2ApplyResult.Success(
        categories = applyCategoryStates(
          categoryStates,
          categoryUpdates,
          ordinaryClears.category,
          atomicClears.category,
        ),
        schedules = applyScheduleStates(
          scheduleStates,
          scheduleUpdates,
          ordinaryClears.schedule,
          atomicClears.schedule,
        ),
        occurrenceOverrides = applyOverrideStates(
          overrideStates,
          overrideUpdates,
          ordinaryClears.override,
          atomicClears.override,
        ),
      )
    } catch (failure: IllegalArgumentException) {
      throw ApplyAbort(
        ScheduleV2ApplyFailureReason.INVALID_LOCAL_STATE,
        failure.message ?: "response would create an invalid local state",
      )
    }
  }

  private fun correlateOrdinary(
    capture: ScheduleV2SyncCapture,
    response: SyncResponse,
  ): OrdinaryClears {
    val categoryUpserts = capture.ordinaryCategories.filter { it.kind == UploadedPendingKind.UPSERT }
    val categoryDeletes = capture.ordinaryCategories.filter { it.kind == UploadedPendingKind.DELETE }
    val scheduleUpserts = capture.ordinarySchedules.filter { it.kind == UploadedPendingKind.UPSERT }
    val scheduleDeletes = capture.ordinarySchedules.filter { it.kind == UploadedPendingKind.DELETE }
    val overrideUpserts =
      capture.ordinaryOccurrenceOverrides.filter { it.kind == UploadedPendingKind.UPSERT }
    val overrideDeletes =
      capture.ordinaryOccurrenceOverrides.filter { it.kind == UploadedPendingKind.DELETE }

    abortUnless(
      categoryUpserts.size == response.categories.upsertResults.size &&
        categoryDeletes.size == response.categories.deleteResults.size &&
        scheduleUpserts.size == response.schedules.upsertResults.size &&
        scheduleDeletes.size == response.schedules.deleteResults.size &&
        overrideUpserts.size == response.occurrenceOverrides.upsertResults.size &&
        overrideDeletes.size == response.occurrenceOverrides.deleteResults.size,
      ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
      "ordinary result counts do not match capture",
    )

    val category = linkedMapOf<CategoryIdentity, UploadedCategoryPending>()
    response.categories.upsertResults.forEachIndexed { index, result ->
      val uploaded = categoryUpserts[index]
      abortUnless(
        result.id == uploaded.identity.id,
        ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
        "Category upsert result identity mismatch",
      )
      if (result.code.canClearPending()) category[uploaded.identity] = uploaded
    }
    response.categories.deleteResults.forEachIndexed { index, result ->
      val uploaded = categoryDeletes[index]
      abortUnless(
        result.id == uploaded.identity.id,
        ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
        "Category delete result identity mismatch",
      )
      if (result.code.canClearPending()) category[uploaded.identity] = uploaded
    }

    val schedule = linkedMapOf<ScheduleIdentity, UploadedSchedulePending>()
    response.schedules.upsertResults.forEachIndexed { index, result ->
      val uploaded = scheduleUpserts[index]
      abortUnless(
        result.id == uploaded.identity.id,
        ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
        "Schedule upsert result identity mismatch",
      )
      if (result.code.canClearPending()) schedule[uploaded.identity] = uploaded
    }
    response.schedules.deleteResults.forEachIndexed { index, result ->
      val uploaded = scheduleDeletes[index]
      abortUnless(
        result.id == uploaded.identity.id,
        ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
        "Schedule delete result identity mismatch",
      )
      if (result.code.canClearPending()) schedule[uploaded.identity] = uploaded
    }

    val override = linkedMapOf<OccurrenceOverrideIdentity, UploadedOccurrenceOverridePending>()
    response.occurrenceOverrides.upsertResults.forEachIndexed { index, result ->
      val uploaded = overrideUpserts[index]
      abortUnless(
        result.scheduleId == uploaded.identity.scheduleId &&
          result.occurrenceDate == uploaded.identity.occurrenceDate,
        ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
        "OccurrenceOverride upsert result identity mismatch",
      )
      if (result.code.canClearPending()) override[uploaded.identity] = uploaded
    }
    response.occurrenceOverrides.deleteResults.forEachIndexed { index, result ->
      val uploaded = overrideDeletes[index]
      abortUnless(
        result.scheduleId == uploaded.identity.scheduleId &&
          result.occurrenceDate == uploaded.identity.occurrenceDate,
        ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
        "OccurrenceOverride delete result identity mismatch",
      )
      if (result.code.canClearPending()) override[uploaded.identity] = uploaded
    }
    return OrdinaryClears(category, schedule, override)
  }

  private fun correlateAtomic(
    capture: ScheduleV2SyncCapture,
    results: List<AtomicBatchResult>,
    categories: Map<CategoryIdentity, CategorySyncState>,
    schedules: Map<ScheduleIdentity, ScheduleSyncState>,
    overrides: Map<OccurrenceOverrideIdentity, OccurrenceOverrideSyncState>,
  ): AtomicClears {
    val resultsById = results.associateBy { it.batchId }
    abortUnless(
      resultsById.size == results.size &&
        resultsById.keys == capture.atomicBatches.map { it.batchId }.toSet(),
      ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
      "atomic batch results do not match capture",
    )
    val categoryClears = linkedMapOf<CategoryIdentity, UploadedCategoryPending>()
    val scheduleClears = linkedMapOf<ScheduleIdentity, UploadedSchedulePending>()
    val overrideClears =
      linkedMapOf<OccurrenceOverrideIdentity, UploadedOccurrenceOverridePending>()

    capture.atomicBatches.forEach { batch ->
      val result = checkNotNull(resultsById[batch.batchId])
      validateAtomicMembers(batch, result)
      val itemRejected = result.categories.upsertResults.any { it.code == AtomicBatchResultCode.REJECTED } ||
        result.categories.deleteResults.any { it.code == AtomicBatchResultCode.REJECTED } ||
        result.schedules.upsertResults.any { it.code == AtomicBatchResultCode.REJECTED } ||
        result.schedules.deleteResults.any { it.code == AtomicBatchResultCode.REJECTED } ||
        result.occurrenceOverrides.upsertResults.any { it.code == AtomicBatchResultCode.REJECTED } ||
        result.occurrenceOverrides.deleteResults.any { it.code == AtomicBatchResultCode.REJECTED }
      if (result.code != AtomicBatchResultCode.REJECTED) {
        abortUnless(
          !itemRejected,
          ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
          "non-rejected atomic batch contains rejected member",
        )
      }
      val allRevisionsMatch =
        batch.categories.all { categories[it.identity].matches(it) } &&
          batch.schedules.all { schedules[it.identity].matches(it) } &&
          batch.occurrenceOverrides.all { overrides[it.identity].matches(it) }
      // APPLIED 与明确 REJECTED 都已得到确定结论；只有所有成员仍为 R 时才整批清除，任一 U 出现则全部保留。
      if (allRevisionsMatch) {
        batch.categories.forEach { categoryClears[it.identity] = it }
        batch.schedules.forEach { scheduleClears[it.identity] = it }
        batch.occurrenceOverrides.forEach { overrideClears[it.identity] = it }
      }
    }
    return AtomicClears(categoryClears, scheduleClears, overrideClears)
  }

  private fun validateAtomicMembers(capture: AtomicBatchCapture, result: AtomicBatchResult) {
    val categoryUpserts = capture.categories.filter { it.kind == UploadedPendingKind.UPSERT }
    val categoryDeletes = capture.categories.filter { it.kind == UploadedPendingKind.DELETE }
    val scheduleUpserts = capture.schedules.filter { it.kind == UploadedPendingKind.UPSERT }
    val scheduleDeletes = capture.schedules.filter { it.kind == UploadedPendingKind.DELETE }
    val overrideUpserts = capture.occurrenceOverrides.filter { it.kind == UploadedPendingKind.UPSERT }
    val overrideDeletes = capture.occurrenceOverrides.filter { it.kind == UploadedPendingKind.DELETE }
    abortUnless(
      categoryUpserts.size == result.categories.upsertResults.size &&
        categoryDeletes.size == result.categories.deleteResults.size &&
        scheduleUpserts.size == result.schedules.upsertResults.size &&
        scheduleDeletes.size == result.schedules.deleteResults.size &&
        overrideUpserts.size == result.occurrenceOverrides.upsertResults.size &&
        overrideDeletes.size == result.occurrenceOverrides.deleteResults.size,
      ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
      "atomic member result counts do not match capture",
    )
    result.categories.upsertResults.forEachIndexed { i, value ->
      requireAtomicIdentity(value.id == categoryUpserts[i].identity.id)
    }
    result.categories.deleteResults.forEachIndexed { i, value ->
      requireAtomicIdentity(value.id == categoryDeletes[i].identity.id)
    }
    result.schedules.upsertResults.forEachIndexed { i, value ->
      requireAtomicIdentity(value.id == scheduleUpserts[i].identity.id)
    }
    result.schedules.deleteResults.forEachIndexed { i, value ->
      requireAtomicIdentity(value.id == scheduleDeletes[i].identity.id)
    }
    result.occurrenceOverrides.upsertResults.forEachIndexed { i, value ->
      requireAtomicIdentity(
        value.scheduleId == overrideUpserts[i].identity.scheduleId &&
          value.occurrenceDate == overrideUpserts[i].identity.occurrenceDate,
      )
    }
    result.occurrenceOverrides.deleteResults.forEachIndexed { i, value ->
      requireAtomicIdentity(
        value.scheduleId == overrideDeletes[i].identity.scheduleId &&
          value.occurrenceDate == overrideDeletes[i].identity.occurrenceDate,
      )
    }
  }

  private fun collectCandidates(response: SyncResponse): RemoteCandidates {
    val candidates = RemoteCandidates()
    response.categories.upserts.forEach { candidates.add(it.toDomain()) }
    response.categories.deletes.forEach { candidates.delete(CategoryIdentity(it.id)) }
    response.categories.upsertResults.forEach {
      it.current?.let { value -> candidates.add(value.toDomain()) }
      it.tombstone?.let { value -> candidates.delete(CategoryIdentity(value.id)) }
    }
    response.categories.deleteResults.forEach {
      it.current?.let { value -> candidates.add(value.toDomain()) }
      it.tombstone?.let { value -> candidates.delete(CategoryIdentity(value.id)) }
    }

    response.schedules.upserts.forEach { candidates.add(it.toDomain()) }
    response.schedules.deletes.forEach { candidates.delete(ScheduleIdentity(it.id)) }
    response.schedules.upsertResults.forEach {
      it.current?.let { value -> candidates.add(value.toDomain()) }
      it.tombstone?.let { value -> candidates.delete(ScheduleIdentity(value.id)) }
    }
    response.schedules.deleteResults.forEach {
      it.current?.let { value -> candidates.add(value.toDomain()) }
      it.tombstone?.let { value -> candidates.delete(ScheduleIdentity(value.id)) }
    }

    response.occurrenceOverrides.upserts.forEach { candidates.add(it.toDomain()) }
    response.occurrenceOverrides.deletes.forEach {
      candidates.delete(OccurrenceOverrideIdentity(it.scheduleId, it.occurrenceDate))
    }
    response.occurrenceOverrides.upsertResults.forEach {
      it.current?.let { value -> candidates.add(value.toDomain()) }
      it.tombstone?.let { value ->
        candidates.delete(OccurrenceOverrideIdentity(value.scheduleId, value.occurrenceDate))
      }
    }
    response.occurrenceOverrides.deleteResults.forEach {
      it.current?.let { value -> candidates.add(value.toDomain()) }
      it.tombstone?.let { value ->
        candidates.delete(OccurrenceOverrideIdentity(value.scheduleId, value.occurrenceDate))
      }
    }

    response.atomicBatchResults.forEach { batch ->
      batch.categories.relatedUpserts.forEach { candidates.add(it.toDomain()) }
      batch.categories.relatedDeletes.forEach { candidates.delete(CategoryIdentity(it.id)) }
      batch.schedules.relatedUpserts.forEach { candidates.add(it.toDomain()) }
      batch.schedules.relatedDeletes.forEach { candidates.delete(ScheduleIdentity(it.id)) }
      batch.occurrenceOverrides.relatedUpserts.forEach { candidates.add(it.toDomain()) }
      batch.occurrenceOverrides.relatedDeletes.forEach {
        candidates.delete(OccurrenceOverrideIdentity(it.scheduleId, it.occurrenceDate))
      }
    }
    return candidates
  }

  private fun resolveCategoryUpdates(
    candidates: RemoteCandidates,
    states: Map<CategoryIdentity, CategorySyncState>,
  ): Map<CategoryIdentity, CategoryRemoteSnapshot?> {
    val updates = linkedMapOf<CategoryIdentity, CategoryRemoteSnapshot?>()
    (candidates.categoryLive.keys + candidates.categoryDeleted).forEach { identity ->
      if (identity in candidates.categoryDeleted) {
        updates[identity] = null
      } else {
        updates[identity] = selectCategory(identity, candidates.categoryLive.getValue(identity), states[identity])
      }
    }
    return updates
  }

  private fun resolveScheduleUpdates(
    candidates: RemoteCandidates,
    states: Map<ScheduleIdentity, ScheduleSyncState>,
  ): Map<ScheduleIdentity, ScheduleRemoteSnapshot?> {
    val updates = linkedMapOf<ScheduleIdentity, ScheduleRemoteSnapshot?>()
    (candidates.scheduleLive.keys + candidates.scheduleDeleted).forEach { identity ->
      if (identity in candidates.scheduleDeleted) {
        updates[identity] = null
      } else {
        updates[identity] = selectSchedule(identity, candidates.scheduleLive.getValue(identity), states[identity])
      }
    }
    return updates
  }

  private fun resolveOverrideUpdates(
    candidates: RemoteCandidates,
    states: Map<OccurrenceOverrideIdentity, OccurrenceOverrideSyncState>,
  ): Map<OccurrenceOverrideIdentity, OccurrenceOverrideRemoteSnapshot?> {
    val updates = linkedMapOf<OccurrenceOverrideIdentity, OccurrenceOverrideRemoteSnapshot?>()
    (candidates.overrideLive.keys + candidates.overrideDeleted).forEach { identity ->
      if (identity in candidates.overrideDeleted) {
        updates[identity] = null
      } else {
        updates[identity] = selectOverride(identity, candidates.overrideLive.getValue(identity), states[identity])
      }
    }
    return updates
  }

  private fun selectCategory(
    identity: CategoryIdentity,
    values: List<CategoryRemoteSnapshot>,
    state: CategorySyncState?,
  ): CategoryRemoteSnapshot {
    val selected = values.maxBy { it.version }
    ensureNoConflict(identity.id, selected, values.filter { it.version == selected.version })
    ensureNoRegression(identity.id, selected.version, state?.remoteSnapshot?.version)
    ensureNoConflictWithExisting(identity.id, selected, state?.remoteSnapshot)
    return selected
  }

  private fun selectSchedule(
    identity: ScheduleIdentity,
    values: List<ScheduleRemoteSnapshot>,
    state: ScheduleSyncState?,
  ): ScheduleRemoteSnapshot {
    val selected = values.maxBy { it.version }
    ensureNoConflict(identity.id, selected, values.filter { it.version == selected.version })
    ensureNoRegression(identity.id, selected.version, state?.remoteSnapshot?.version)
    ensureNoConflictWithExisting(identity.id, selected, state?.remoteSnapshot)
    return selected
  }

  private fun selectOverride(
    identity: OccurrenceOverrideIdentity,
    values: List<OccurrenceOverrideRemoteSnapshot>,
    state: OccurrenceOverrideSyncState?,
  ): OccurrenceOverrideRemoteSnapshot {
    val label = "${identity.scheduleId}@${identity.occurrenceDate}"
    val selected = values.maxBy { it.version }
    ensureNoConflict(label, selected, values.filter { it.version == selected.version })
    ensureNoRegression(label, selected.version, state?.remoteSnapshot?.version)
    ensureNoConflictWithExisting(label, selected, state?.remoteSnapshot)
    return selected
  }

  private fun <T> ensureNoConflict(label: String, selected: T, sameVersion: List<T>) {
    abortUnless(
      sameVersion.all { it == selected },
      ScheduleV2ApplyFailureReason.SAME_VERSION_CONFLICT,
      "$label has different complete payloads at the same highest version",
    )
  }

  private fun ensureNoRegression(label: String, selected: Long, existing: Long?) {
    abortUnless(
      existing == null || selected >= existing,
      ScheduleV2ApplyFailureReason.REMOTE_VERSION_REGRESSION,
      "$label remote version regressed from $existing to $selected",
    )
  }

  private fun <T> ensureNoConflictWithExisting(label: String, selected: T, existing: T?) {
    if (existing is CategoryRemoteSnapshot && selected is CategoryRemoteSnapshot) {
      if (existing.version == selected.version) ensureNoConflict(label, selected, listOf(existing, selected))
    } else if (existing is ScheduleRemoteSnapshot && selected is ScheduleRemoteSnapshot) {
      if (existing.version == selected.version) ensureNoConflict(label, selected, listOf(existing, selected))
    } else if (
      existing is OccurrenceOverrideRemoteSnapshot &&
      selected is OccurrenceOverrideRemoteSnapshot
    ) {
      if (existing.version == selected.version) ensureNoConflict(label, selected, listOf(existing, selected))
    }
  }

  private fun applyCategoryStates(
    states: Map<CategoryIdentity, CategorySyncState>,
    updates: Map<CategoryIdentity, CategoryRemoteSnapshot?>,
    ordinaryClears: Map<CategoryIdentity, UploadedCategoryPending>,
    atomicClears: Map<CategoryIdentity, UploadedCategoryPending>,
  ): List<CategorySyncState> {
    val identities = states.keys + updates.keys
    return identities.mapNotNull { identity ->
      val old = states[identity]
      val remote = if (updates.containsKey(identity)) updates[identity] else old?.remoteSnapshot
      val uploaded = ordinaryClears[identity] ?: atomicClears[identity]
      val pending = pendingAfterRemoteUpdate(
        old?.pending,
        remoteDeleted = updates.containsKey(identity) && remote == null,
        uploadedMatches = old.matches(uploaded),
      )
      if (remote == null && pending == null) null else CategorySyncState(identity, remote, pending)
    }
  }

  private fun applyScheduleStates(
    states: Map<ScheduleIdentity, ScheduleSyncState>,
    updates: Map<ScheduleIdentity, ScheduleRemoteSnapshot?>,
    ordinaryClears: Map<ScheduleIdentity, UploadedSchedulePending>,
    atomicClears: Map<ScheduleIdentity, UploadedSchedulePending>,
  ): List<ScheduleSyncState> {
    val identities = states.keys + updates.keys
    return identities.mapNotNull { identity ->
      val old = states[identity]
      val remote = if (updates.containsKey(identity)) updates[identity] else old?.remoteSnapshot
      val uploaded = ordinaryClears[identity] ?: atomicClears[identity]
      val pending = pendingAfterRemoteUpdate(
        old?.pending,
        remoteDeleted = updates.containsKey(identity) && remote == null,
        uploadedMatches = old.matches(uploaded),
      )
      if (remote == null && pending == null) null else ScheduleSyncState(identity, remote, pending)
    }
  }

  private fun applyOverrideStates(
    states: Map<OccurrenceOverrideIdentity, OccurrenceOverrideSyncState>,
    updates: Map<OccurrenceOverrideIdentity, OccurrenceOverrideRemoteSnapshot?>,
    ordinaryClears: Map<OccurrenceOverrideIdentity, UploadedOccurrenceOverridePending>,
    atomicClears: Map<OccurrenceOverrideIdentity, UploadedOccurrenceOverridePending>,
  ): List<OccurrenceOverrideSyncState> {
    val identities = states.keys + updates.keys
    return identities.mapNotNull { identity ->
      val old = states[identity]
      val remote = if (updates.containsKey(identity)) updates[identity] else old?.remoteSnapshot
      val uploaded = ordinaryClears[identity] ?: atomicClears[identity]
      val pending = pendingAfterRemoteUpdate(
        old?.pending,
        remoteDeleted = updates.containsKey(identity) && remote == null,
        uploadedMatches = old.matches(uploaded),
      )
      if (remote == null && pending == null) null else OccurrenceOverrideSyncState(identity, remote, pending)
    }
  }

  private fun uniqueCategoryStates(values: List<CategorySyncState>): Map<CategoryIdentity, CategorySyncState> =
    uniqueStates(values.map { it.identity to it }, "Category")

  private fun uniqueScheduleStates(values: List<ScheduleSyncState>): Map<ScheduleIdentity, ScheduleSyncState> =
    uniqueStates(values.map { it.identity to it }, "Schedule")

  private fun uniqueOverrideStates(
    values: List<OccurrenceOverrideSyncState>,
  ): Map<OccurrenceOverrideIdentity, OccurrenceOverrideSyncState> =
    uniqueStates(values.map { it.identity to it }, "OccurrenceOverride")

  private fun <K, V> uniqueStates(values: List<Pair<K, V>>, type: String): Map<K, V> {
    val map = linkedMapOf<K, V>()
    values.forEach { (key, value) ->
      abortUnless(
        map.put(key, value) == null,
        ScheduleV2ApplyFailureReason.INVALID_LOCAL_STATE,
        "$type states contain duplicate identities",
      )
    }
    return map
  }

  private fun requireAtomicIdentity(value: Boolean) = abortUnless(
    value,
    ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION,
    "atomic member result identity mismatch",
  )
}

/**
 * 根据本轮 remote 事实与 compare-and-clear 结果决定是否保留当前 pending。
 *
 * [remoteDeleted] 只会由 tombstone/RESOURCE_DELETED 产生，表示该 identity 不允许复活，因此无论
 * pending 是 DELETE、正版本 PATCH 还是 version=0 CREATE 都必须清除。正常 CREATE R→U 成功返回的是
 * Current，remoteDeleted 为 false，仍按 uploadedRevision compare-and-clear 并保留请求期间形成的新 U。
 */
private fun <I : ResourceIdentity, R : SyncResource<I>> pendingAfterRemoteUpdate(
  pending: PendingChange<I, R>?,
  remoteDeleted: Boolean,
  uploadedMatches: Boolean,
): PendingChange<I, R>? = when {
  remoteDeleted -> null
  uploadedMatches -> null
  else -> pending
}

private class RemoteCandidates {
  val categoryLive = linkedMapOf<CategoryIdentity, MutableList<CategoryRemoteSnapshot>>()
  val categoryDeleted = linkedSetOf<CategoryIdentity>()
  val scheduleLive = linkedMapOf<ScheduleIdentity, MutableList<ScheduleRemoteSnapshot>>()
  val scheduleDeleted = linkedSetOf<ScheduleIdentity>()
  val overrideLive =
    linkedMapOf<OccurrenceOverrideIdentity, MutableList<OccurrenceOverrideRemoteSnapshot>>()
  val overrideDeleted = linkedSetOf<OccurrenceOverrideIdentity>()

  fun add(value: CategoryRemoteSnapshot) {
    categoryLive.getOrPut(value.identity) { mutableListOf() } += value
  }

  fun add(value: ScheduleRemoteSnapshot) {
    scheduleLive.getOrPut(value.identity) { mutableListOf() } += value
  }

  fun add(value: OccurrenceOverrideRemoteSnapshot) {
    overrideLive.getOrPut(value.identity) { mutableListOf() } += value
  }

  fun delete(identity: CategoryIdentity) {
    categoryDeleted += identity
  }

  fun delete(identity: ScheduleIdentity) {
    scheduleDeleted += identity
  }

  fun delete(identity: OccurrenceOverrideIdentity) {
    overrideDeleted += identity
  }
}

private data class OrdinaryClears(
  val category: Map<CategoryIdentity, UploadedCategoryPending>,
  val schedule: Map<ScheduleIdentity, UploadedSchedulePending>,
  val override: Map<OccurrenceOverrideIdentity, UploadedOccurrenceOverridePending>,
)

private data class AtomicClears(
  val category: Map<CategoryIdentity, UploadedCategoryPending>,
  val schedule: Map<ScheduleIdentity, UploadedSchedulePending>,
  val override: Map<OccurrenceOverrideIdentity, UploadedOccurrenceOverridePending>,
)

/** 为 HTTP 400 构造只用于本地 compare-and-clear 的对齐结果；不会伪造任何 canonical remote 数据。 */
private fun rejectedResponse(request: SyncRequest): SyncResponse = SyncResponse(
  syncRequestId = request.syncRequestId,
  categories = CategorySyncResponse(
    upserts = emptyList(),
    deletes = emptyList(),
    upsertResults = request.categories.upserts.map {
      CategoryUpsertResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
    },
    deleteResults = request.categories.deletes.map {
      CategoryDeleteResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
    },
  ),
  schedules = ScheduleSyncResponse(
    upserts = emptyList(),
    deletes = emptyList(),
    upsertResults = request.schedules.upserts.map {
      ScheduleUpsertResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
    },
    deleteResults = request.schedules.deletes.map {
      ScheduleDeleteResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
    },
  ),
  occurrenceOverrides = OccurrenceOverrideSyncResponse(
    upserts = emptyList(),
    deletes = emptyList(),
    upsertResults = request.occurrenceOverrides.upserts.map {
      OccurrenceOverrideUpsertResult(
        it.scheduleId,
        it.occurrenceDate,
        MutationResultCode.REJECTED,
        ResultReason.INVALID_REQUEST,
      )
    },
    deleteResults = request.occurrenceOverrides.deletes.map {
      OccurrenceOverrideDeleteResult(
        it.scheduleId,
        it.occurrenceDate,
        MutationResultCode.REJECTED,
        ResultReason.INVALID_REQUEST,
      )
    },
  ),
  atomicBatchResults = request.atomicBatches.map { batch ->
    AtomicBatchResult(
      batchId = batch.batchId,
      code = AtomicBatchResultCode.REJECTED,
      reason = ResultReason.INVALID_REQUEST,
      categories = CategoryAtomicResultBlock(
        upsertResults = batch.categories.upserts.map {
          CategoryAtomicUpsertResult(it.id, AtomicBatchResultCode.REJECTED, ResultReason.INVALID_REQUEST)
        },
        deleteResults = batch.categories.deletes.map {
          CategoryAtomicDeleteResult(it.id, AtomicBatchResultCode.REJECTED, ResultReason.INVALID_REQUEST)
        },
        relatedUpserts = emptyList(),
        relatedDeletes = emptyList(),
      ),
      schedules = ScheduleAtomicResultBlock(
        upsertResults = batch.schedules.upserts.map {
          ScheduleAtomicUpsertResult(it.id, AtomicBatchResultCode.REJECTED, ResultReason.INVALID_REQUEST)
        },
        deleteResults = batch.schedules.deletes.map {
          ScheduleAtomicDeleteResult(it.id, AtomicBatchResultCode.REJECTED, ResultReason.INVALID_REQUEST)
        },
        relatedUpserts = emptyList(),
        relatedDeletes = emptyList(),
      ),
      occurrenceOverrides = OccurrenceOverrideAtomicResultBlock(
        upsertResults = batch.occurrenceOverrides.upserts.map {
          OccurrenceOverrideAtomicUpsertResult(
            it.scheduleId,
            it.occurrenceDate,
            AtomicBatchResultCode.REJECTED,
            ResultReason.INVALID_REQUEST,
          )
        },
        deleteResults = batch.occurrenceOverrides.deletes.map {
          OccurrenceOverrideAtomicDeleteResult(
            it.scheduleId,
            it.occurrenceDate,
            AtomicBatchResultCode.REJECTED,
            ResultReason.INVALID_REQUEST,
          )
        },
        relatedUpserts = emptyList(),
        relatedDeletes = emptyList(),
      ),
    )
  },
)

private fun MutationResultCode.canClearPending(): Boolean = when (this) {
  MutationResultCode.CREATED,
  MutationResultCode.DELETED,
  MutationResultCode.ALREADY_DELETED,
  MutationResultCode.APPLIED,
  MutationResultCode.ALREADY_SATISFIED,
  MutationResultCode.SERVER_WON,
  MutationResultCode.RESOURCE_DELETED,
  MutationResultCode.REJECTED,
  MutationResultCode.ALREADY_EXISTS,
  -> true
}

private fun CategorySyncState?.matches(uploaded: UploadedCategoryPending?): Boolean {
  val current = this?.pending ?: return false
  uploaded ?: return false
  return current.localRevision == uploaded.localRevision &&
    current.localBatchId == uploaded.batchId &&
    ((uploaded.kind == UploadedPendingKind.UPSERT && current is PendingUpsert) ||
      (uploaded.kind == UploadedPendingKind.DELETE && current is PendingDelete))
}

private fun ScheduleSyncState?.matches(uploaded: UploadedSchedulePending?): Boolean {
  val current = this?.pending ?: return false
  uploaded ?: return false
  return current.localRevision == uploaded.localRevision &&
    current.localBatchId == uploaded.batchId &&
    ((uploaded.kind == UploadedPendingKind.UPSERT && current is PendingUpsert) ||
      (uploaded.kind == UploadedPendingKind.DELETE && current is PendingDelete))
}

private fun OccurrenceOverrideSyncState?.matches(
  uploaded: UploadedOccurrenceOverridePending?,
): Boolean {
  val current = this?.pending ?: return false
  uploaded ?: return false
  return current.localRevision == uploaded.localRevision &&
    current.localBatchId == uploaded.batchId &&
    ((uploaded.kind == UploadedPendingKind.UPSERT && current is PendingUpsert) ||
      (uploaded.kind == UploadedPendingKind.DELETE && current is PendingDelete))
}

private class ApplyAbort(
  val reason: ScheduleV2ApplyFailureReason,
  message: String,
) : IllegalStateException(message)

private fun abortUnless(
  condition: Boolean,
  reason: ScheduleV2ApplyFailureReason,
  message: String,
) {
  if (!condition) throw ApplyAbort(reason, message)
}
