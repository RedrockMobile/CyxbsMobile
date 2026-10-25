package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.repository.toDomain
import com.cyxbs.pages.schedule.data.repository.toLocalDomain
import com.cyxbs.pages.schedule.data.repository.toWire
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
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

/** 一个账号的三类本地双快照；不包含 outbox、回执或网络状态。 */
internal data class ScheduleCommonAccountState(
  val accountId: String,
  val categories: List<CategorySyncState>,
  val schedules: List<ScheduleSyncState>,
  val occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
)

/** 将分类状态行恢复为领域双快照。 */
internal fun ScheduleCategoryStateEntity.toCommonSyncState(): CategorySyncState {
  val identity = CategoryIdentity(categoryId)
  return CategorySyncState(
    identity = identity,
    remoteSnapshot = remoteSnapshot?.toDomain(identity)?.let(::CategoryRemoteSnapshot),
    pending = toCategoryPending(identity),
  )
}

/** 将分类领域状态保存为一行 typed Room entity。 */
internal fun CategorySyncState.toRoomEntity(accountId: String): ScheduleCategoryStateEntity {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  return ScheduleCategoryStateEntity(
    accountId = accountId,
    categoryId = identity.id,
    remoteSnapshot = remoteSnapshot?.resource?.toWire(),
    pendingOperation = pending.operationName(),
    pendingSnapshot = (pending as? PendingUpsert)?.resource?.toWire(),
    pendingLocalModifiedAt = (pending as? PendingDelete)?.localModifiedAt,
    localRevision = pending?.localRevision,
  )
}

/** 将日程状态行恢复为领域双快照，数字分类 ID 必须可解析为本地 UUID。 */
internal fun ScheduleStateEntity.toCommonSyncState(
  categoryByRemoteId: (Long) -> String?,
): ScheduleSyncState {
  val identity = ScheduleIdentity(scheduleId)
  return ScheduleSyncState(
    identity = identity,
    remoteSnapshot = remoteSnapshot?.toDomain(categoryByRemoteId)?.let(::ScheduleRemoteSnapshot),
    pending = toSchedulePending(identity, categoryByRemoteId),
  )
}

/** 将日程领域状态保存为一行 typed Room entity。 */
internal fun ScheduleSyncState.toRoomEntity(
  accountId: String,
  categoryByLocalId: (String) -> CategoryResource?,
): ScheduleStateEntity {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  return ScheduleStateEntity(
    accountId = accountId,
    scheduleId = identity.id,
    remoteSnapshot = remoteSnapshot?.resource?.toWire(categoryByLocalId),
    pendingOperation = pending.operationName(),
    pendingSnapshot = (pending as? PendingUpsert)?.resource?.toWire(categoryByLocalId),
    pendingLocalModifiedAt = (pending as? PendingDelete)?.localModifiedAt,
    localRevision = pending?.localRevision,
  )
}

/** 将单次调整状态行恢复为领域双快照，并核对 Room 行保存的不可变逻辑槽。 */
internal fun ScheduleOccurrenceAdjustmentStateEntity.toCommonSyncState(
  categoryByRemoteId: (Long) -> String?,
): OccurrenceAdjustmentSyncState {
  val identity = OccurrenceAdjustmentIdentity(localId, scheduleId, originalOccurrenceDate)
  remoteSnapshot?.requireLogicalSlot(identity)
  pendingSnapshot?.requireLogicalSlot(identity)
  return OccurrenceAdjustmentSyncState(
    identity = identity,
    remoteSnapshot = remoteSnapshot?.toDomain(identity, categoryByRemoteId)
      ?.let(::OccurrenceAdjustmentRemoteSnapshot),
    pending = toOccurrenceAdjustmentPending(identity, categoryByRemoteId),
  )
}

/** 将单次调整领域状态保存为一行 typed Room entity。 */
internal fun OccurrenceAdjustmentSyncState.toRoomEntity(
  accountId: String,
  categoryByLocalId: (String) -> CategoryResource?,
): ScheduleOccurrenceAdjustmentStateEntity {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  return ScheduleOccurrenceAdjustmentStateEntity(
    accountId = accountId,
    localId = identity.localId,
    scheduleId = identity.scheduleId,
    originalOccurrenceDate = identity.originalOccurrenceDate,
    remoteSnapshot = remoteSnapshot?.resource?.toWire(categoryByLocalId),
    pendingOperation = pending.operationName(),
    pendingSnapshot = (pending as? PendingUpsert)?.resource?.toWire(categoryByLocalId),
    pendingLocalModifiedAt = (pending as? PendingDelete)?.localModifiedAt,
    localRevision = pending?.localRevision,
  )
}

/**
 * 把 Room 的三类账号状态聚合为领域状态。
 *
 * 分类必须先恢复，后续日程和单次调整才能把远端数字分类 ID 解析回客户端 UUID。
 */
internal fun ScheduleRoomAccountState.toCommonAccountState(accountId: String): ScheduleCommonAccountState {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  require(categories.all { it.accountId == accountId }) { "category accountId mismatch" }
  require(schedules.all { it.accountId == accountId }) { "schedule accountId mismatch" }
  require(occurrenceAdjustments.all { it.accountId == accountId }) { "occurrence adjustment accountId mismatch" }

  val categoryStates = categories.map { it.toCommonSyncState() }
  val categoryByRemoteId = categoryStates
    .flatMap { state -> listOfNotNull(state.remoteSnapshot?.resource, state.effectiveResource()) }
    .mapNotNull { category -> category.remoteId?.let { it to category.identity.id } }
    .toMap()
  return ScheduleCommonAccountState(
    accountId = accountId,
    categories = categoryStates,
    schedules = schedules.map { it.toCommonSyncState(categoryByRemoteId::get) },
    occurrenceAdjustments = occurrenceAdjustments.map { it.toCommonSyncState(categoryByRemoteId::get) },
  )
}

/** 将领域三类完整状态转换为可供 Room 原子替换的实体集合。 */
internal fun ScheduleCommonAccountState.toRoomAccountState(): ScheduleRoomAccountState {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  val categoryByLocalId = categories
    .flatMap { state -> listOfNotNull(state.remoteSnapshot?.resource, state.effectiveResource()) }
    .associateBy { it.identity.id }
  return ScheduleRoomAccountState(
    categories = categories.map { it.toRoomEntity(accountId) },
    schedules = schedules.map { it.toRoomEntity(accountId, categoryByLocalId::get) },
    occurrenceAdjustments = occurrenceAdjustments.map {
      it.toRoomEntity(accountId, categoryByLocalId::get)
    },
  )
}

private fun ScheduleCategoryStateEntity.toCategoryPending(
  identity: CategoryIdentity,
): PendingChange<CategoryIdentity, CategoryResource>? = when (pendingOperation) {
  null -> noPending()
  SchedulePendingOperation.UPSERT -> {
    require(pendingSnapshot != null && pendingLocalModifiedAt == null && localRevision != null)
    PendingUpsert(pendingSnapshot.toLocalDomain(identity), localRevision)
  }
  SchedulePendingOperation.DELETE -> pendingDelete(identity)
  else -> error("unsupported category pending operation=$pendingOperation")
}

private fun ScheduleStateEntity.toSchedulePending(
  identity: ScheduleIdentity,
  categoryByRemoteId: (Long) -> String?,
): PendingChange<ScheduleIdentity, ScheduleResource>? = when (pendingOperation) {
  null -> noPending()
  SchedulePendingOperation.UPSERT -> {
    require(pendingSnapshot != null && pendingLocalModifiedAt == null && localRevision != null)
    PendingUpsert(pendingSnapshot.toLocalDomain(categoryByRemoteId), localRevision)
  }
  SchedulePendingOperation.DELETE -> pendingDelete(identity)
  else -> error("unsupported schedule pending operation=$pendingOperation")
}

private fun ScheduleOccurrenceAdjustmentStateEntity.toOccurrenceAdjustmentPending(
  identity: OccurrenceAdjustmentIdentity,
  categoryByRemoteId: (Long) -> String?,
): PendingChange<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>? = when (pendingOperation) {
  null -> noPending()
  SchedulePendingOperation.UPSERT -> {
    require(pendingSnapshot != null && pendingLocalModifiedAt == null && localRevision != null)
    PendingUpsert(pendingSnapshot.toLocalDomain(identity, categoryByRemoteId), localRevision)
  }
  SchedulePendingOperation.DELETE -> pendingDelete(identity)
  else -> error("unsupported occurrence adjustment pending operation=$pendingOperation")
}

/** null operation 不能残留任意 pending 字段。 */
private fun ScheduleCategoryStateEntity.noPending(): PendingChange<CategoryIdentity, CategoryResource>? {
  require(pendingSnapshot == null && pendingLocalModifiedAt == null && localRevision == null)
  return null
}

private fun ScheduleStateEntity.noPending(): PendingChange<ScheduleIdentity, ScheduleResource>? {
  require(pendingSnapshot == null && pendingLocalModifiedAt == null && localRevision == null)
  return null
}

private fun ScheduleOccurrenceAdjustmentStateEntity.noPending(): PendingChange<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>? {
  require(pendingSnapshot == null && pendingLocalModifiedAt == null && localRevision == null)
  return null
}

private fun ScheduleCategoryStateEntity.pendingDelete(
  identity: CategoryIdentity,
): PendingDelete<CategoryIdentity, CategoryResource> {
  require(pendingSnapshot == null && pendingLocalModifiedAt != null && localRevision != null)
  return PendingDelete(identity, pendingLocalModifiedAt, localRevision)
}

private fun ScheduleStateEntity.pendingDelete(
  identity: ScheduleIdentity,
): PendingDelete<ScheduleIdentity, ScheduleResource> {
  require(pendingSnapshot == null && pendingLocalModifiedAt != null && localRevision != null)
  return PendingDelete(identity, pendingLocalModifiedAt, localRevision)
}

private fun ScheduleOccurrenceAdjustmentStateEntity.pendingDelete(
  identity: OccurrenceAdjustmentIdentity,
): PendingDelete<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource> {
  require(pendingSnapshot == null && pendingLocalModifiedAt != null && localRevision != null)
  return PendingDelete(identity, pendingLocalModifiedAt, localRevision)
}

private fun PendingChange<*, *>?.operationName(): String? = when (this) {
  null -> null
  is PendingUpsert -> SchedulePendingOperation.UPSERT
  is PendingDelete -> SchedulePendingOperation.DELETE
}

private fun com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput.requireLogicalSlot(
  identity: OccurrenceAdjustmentIdentity,
) {
  require(scheduleId == identity.scheduleId && originalOccurrenceDate == identity.originalOccurrenceDate) {
    "occurrence adjustment logical slot mismatch"
  }
}
