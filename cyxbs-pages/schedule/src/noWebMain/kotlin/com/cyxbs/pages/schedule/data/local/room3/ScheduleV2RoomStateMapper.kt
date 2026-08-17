package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.repository.v3.toDomain
import com.cyxbs.pages.schedule.data.repository.v3.toWire
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.PendingChange
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState

/**
 * 一个账号的 common 双快照集合。
 *
 * 仅聚合三类 identity state；账号级 cursor、outbox、回执或网络状态不属于该结构。
 */
internal data class ScheduleV2CommonAccountState(
  val accountId: String,
  val categories: List<CategorySyncState>,
  val schedules: List<ScheduleSyncState>,
  val occurrenceOverrides: List<OccurrenceOverrideSyncState>,
)

/**
 * Room typed entity 与 common 双快照状态的无损映射。
 *
 * wire 与 domain 资源字段转换全部复用共享 mapper；本文件只处理 Room 行的 identity、pending 形态及纯本地
 * localRevision；它绝不编码进 wire payload。localBatchId 会由 common planner 映射为 wire
 * AtomicBatch.batchId，但不代表 receipt 或服务端进度。
 */

/** 将 Category Room 行恢复为 common 双快照状态；数据库非法 pending 组合立即失败。 */
internal fun ScheduleV2CategoryStateEntity.toCommonSyncState(): CategorySyncState {
  val identity = CategoryIdentity(categoryId)
  return CategorySyncState(
    identity = identity,
    remoteSnapshot = remoteSnapshot?.toDomain(),
    pending = toCategoryPending(identity),
  )
}

/** 将 common Category 双快照状态保存为一行 typed Room entity。 */
internal fun CategorySyncState.toRoomEntity(accountId: String): ScheduleV2CategoryStateEntity {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  val pending = pending
  return ScheduleV2CategoryStateEntity(
    accountId = accountId,
    categoryId = identity.id,
    remoteSnapshot = remoteSnapshot?.toWire(),
    pendingOperation = when (pending) {
      null -> null
      is PendingUpsert -> ScheduleV2PendingOperation.UPSERT
      is PendingDelete -> ScheduleV2PendingOperation.DELETE
    },
    pendingSnapshot = when (pending) {
      is PendingUpsert -> pending.resource.toWire()
      else -> null
    },
    pendingLocalModifiedAt = when (pending) {
      is PendingDelete -> pending.localModifiedAt
      else -> null
    },
    localRevision = pending?.localRevision,
    localBatchId = pending?.localBatchId,
  )
}

/** 将 Schedule Room 行恢复为 common 双快照状态；数据库非法 pending 组合立即失败。 */
internal fun ScheduleV2ScheduleStateEntity.toCommonSyncState(): ScheduleSyncState {
  val identity = ScheduleIdentity(scheduleId)
  return ScheduleSyncState(
    identity = identity,
    remoteSnapshot = remoteSnapshot?.toDomain(),
    pending = toSchedulePending(identity),
  )
}

/** 将 common Schedule 双快照状态保存为一行 typed Room entity。 */
internal fun ScheduleSyncState.toRoomEntity(accountId: String): ScheduleV2ScheduleStateEntity {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  val pending = pending
  return ScheduleV2ScheduleStateEntity(
    accountId = accountId,
    scheduleId = identity.id,
    remoteSnapshot = remoteSnapshot?.toWire(),
    pendingOperation = when (pending) {
      null -> null
      is PendingUpsert -> ScheduleV2PendingOperation.UPSERT
      is PendingDelete -> ScheduleV2PendingOperation.DELETE
    },
    pendingSnapshot = when (pending) {
      is PendingUpsert -> pending.resource.toWire()
      else -> null
    },
    pendingLocalModifiedAt = when (pending) {
      is PendingDelete -> pending.localModifiedAt
      else -> null
    },
    localRevision = pending?.localRevision,
    localBatchId = pending?.localBatchId,
  )
}

/** 将 OccurrenceOverride Room 行恢复为 common 双快照状态；数据库非法 pending 组合立即失败。 */
internal fun ScheduleV2OccurrenceOverrideStateEntity.toCommonSyncState(): OccurrenceOverrideSyncState {
  val identity = OccurrenceOverrideIdentity(scheduleId, occurrenceDate)
  return OccurrenceOverrideSyncState(
    identity = identity,
    remoteSnapshot = remoteSnapshot?.toDomain(),
    pending = toOccurrenceOverridePending(identity),
  )
}

/** 将 common OccurrenceOverride 双快照状态保存为一行 typed Room entity。 */
internal fun OccurrenceOverrideSyncState.toRoomEntity(accountId: String): ScheduleV2OccurrenceOverrideStateEntity {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  val pending = pending
  return ScheduleV2OccurrenceOverrideStateEntity(
    accountId = accountId,
    scheduleId = identity.scheduleId,
    occurrenceDate = identity.occurrenceDate,
    remoteSnapshot = remoteSnapshot?.toWire(),
    pendingOperation = when (pending) {
      null -> null
      is PendingUpsert -> ScheduleV2PendingOperation.UPSERT
      is PendingDelete -> ScheduleV2PendingOperation.DELETE
    },
    pendingSnapshot = when (pending) {
      is PendingUpsert -> pending.resource.toWire()
      else -> null
    },
    pendingLocalModifiedAt = when (pending) {
      is PendingDelete -> pending.localModifiedAt
      else -> null
    },
    localRevision = pending?.localRevision,
    localBatchId = pending?.localBatchId,
  )
}

/**
 * 把 Room 的三类账号状态聚合为 common 双快照集合。
 *
 * [accountId] 必须和每一行相同，避免错误账号分区被静默带入同步 applier。
 */
internal fun ScheduleV2RoomAccountState.toCommonAccountState(accountId: String): ScheduleV2CommonAccountState {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  require(categories.all { it.accountId == accountId }) { "category accountId mismatch" }
  require(schedules.all { it.accountId == accountId }) { "schedule accountId mismatch" }
  require(occurrenceOverrides.all { it.accountId == accountId }) { "occurrence override accountId mismatch" }
  return ScheduleV2CommonAccountState(
    accountId = accountId,
    categories = categories.map { it.toCommonSyncState() },
    schedules = schedules.map { it.toCommonSyncState() },
    occurrenceOverrides = occurrenceOverrides.map { it.toCommonSyncState() },
  )
}

/** 将 common 三类完整状态集合还原为可供 Room 全量替换的 typed entity 集合。 */
internal fun ScheduleV2CommonAccountState.toRoomAccountState(): ScheduleV2RoomAccountState {
  require(accountId.isNotBlank()) { "accountId must not be blank" }
  return ScheduleV2RoomAccountState(
    categories = categories.map { it.toRoomEntity(accountId) },
    schedules = schedules.map { it.toRoomEntity(accountId) },
    occurrenceOverrides = occurrenceOverrides.map { it.toRoomEntity(accountId) },
  )
}

/** 按 Category 行的 pending 列构建 common pending，并拒绝损坏或不完整数据。 */
private fun ScheduleV2CategoryStateEntity.toCategoryPending(
  identity: CategoryIdentity,
): PendingChange<CategoryIdentity, CategoryResource>? = when (pendingOperation) {
  null -> {
    require(pendingSnapshot == null && pendingLocalModifiedAt == null && localRevision == null && localBatchId == null) {
      "category has pending fields without pending operation"
    }
    null
  }
  ScheduleV2PendingOperation.UPSERT -> {
    require(pendingSnapshot != null && pendingLocalModifiedAt == null && localRevision != null) {
      "category UPSERT pending shape is invalid"
    }
    PendingUpsert(pendingSnapshot.toDomain(), localRevision, localBatchId)
  }
  ScheduleV2PendingOperation.DELETE -> {
    require(pendingSnapshot == null && pendingLocalModifiedAt != null && localRevision != null) {
      "category DELETE pending shape is invalid"
    }
    PendingDelete(identity, pendingLocalModifiedAt, localRevision, localBatchId)
  }
  else -> error("unsupported category pending operation=$pendingOperation")
}

/** 按 Schedule 行的 pending 列构建 common pending，并拒绝损坏或不完整数据。 */
private fun ScheduleV2ScheduleStateEntity.toSchedulePending(
  identity: ScheduleIdentity,
): PendingChange<ScheduleIdentity, ScheduleResource>? = when (pendingOperation) {
  null -> {
    require(pendingSnapshot == null && pendingLocalModifiedAt == null && localRevision == null && localBatchId == null) {
      "schedule has pending fields without pending operation"
    }
    null
  }
  ScheduleV2PendingOperation.UPSERT -> {
    require(pendingSnapshot != null && pendingLocalModifiedAt == null && localRevision != null) {
      "schedule UPSERT pending shape is invalid"
    }
    PendingUpsert(pendingSnapshot.toDomain(), localRevision, localBatchId)
  }
  ScheduleV2PendingOperation.DELETE -> {
    require(pendingSnapshot == null && pendingLocalModifiedAt != null && localRevision != null) {
      "schedule DELETE pending shape is invalid"
    }
    PendingDelete(identity, pendingLocalModifiedAt, localRevision, localBatchId)
  }
  else -> error("unsupported schedule pending operation=$pendingOperation")
}

/** 按 OccurrenceOverride 行的 pending 列构建 common pending，并拒绝损坏或不完整数据。 */
private fun ScheduleV2OccurrenceOverrideStateEntity.toOccurrenceOverridePending(
  identity: OccurrenceOverrideIdentity,
): PendingChange<OccurrenceOverrideIdentity, OccurrenceOverrideResource>? = when (pendingOperation) {
  null -> {
    require(pendingSnapshot == null && pendingLocalModifiedAt == null && localRevision == null && localBatchId == null) {
      "occurrence override has pending fields without pending operation"
    }
    null
  }
  ScheduleV2PendingOperation.UPSERT -> {
    require(pendingSnapshot != null && pendingLocalModifiedAt == null && localRevision != null) {
      "occurrence override UPSERT pending shape is invalid"
    }
    PendingUpsert(pendingSnapshot.toDomain(), localRevision, localBatchId)
  }
  ScheduleV2PendingOperation.DELETE -> {
    require(pendingSnapshot == null && pendingLocalModifiedAt != null && localRevision != null) {
      "occurrence override DELETE pending shape is invalid"
    }
    PendingDelete(identity, pendingLocalModifiedAt, localRevision, localBatchId)
  }
  else -> error("unsupported occurrence override pending operation=$pendingOperation")
}
