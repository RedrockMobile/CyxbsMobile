package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatch
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicBlock
import com.cyxbs.pages.schedule.data.remote.v3.CategoryDelete
import com.cyxbs.pages.schedule.data.remote.v3.CategoryInput
import com.cyxbs.pages.schedule.data.remote.v3.CategorySyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.ConfirmedCategory
import com.cyxbs.pages.schedule.data.remote.v3.ConfirmedOccurrenceOverride
import com.cyxbs.pages.schedule.data.remote.v3.ConfirmedSchedule
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicBlock
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideDelete
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideInput
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideSyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicBlock
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDelete
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleSyncRequest
import com.cyxbs.pages.schedule.data.remote.v3.SyncRequest
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState

/** capture 中记录的 pending 分支，仅用于把响应关联回发出时的本地 revision。 */
enum class UploadedPendingKind {
  UPSERT,
  DELETE,
}

/** Category pending 的请求关联信息。 */
data class UploadedCategoryPending(
  val identity: CategoryIdentity,
  val localRevision: Long,
  val kind: UploadedPendingKind,
  val batchId: String?,
)

/** Schedule pending 的请求关联信息。 */
data class UploadedSchedulePending(
  val identity: ScheduleIdentity,
  val localRevision: Long,
  val kind: UploadedPendingKind,
  val batchId: String?,
)

/** OccurrenceOverride pending 的请求关联信息。 */
data class UploadedOccurrenceOverridePending(
  val identity: OccurrenceOverrideIdentity,
  val localRevision: Long,
  val kind: UploadedPendingKind,
  val batchId: String?,
)

/** 一个原子批次在请求发出时的最小关联信息，不持久化执行状态或服务端回执。 */
data class AtomicBatchCapture(
  val batchId: String,
  val categories: List<UploadedCategoryPending>,
  val schedules: List<UploadedSchedulePending>,
  val occurrenceOverrides: List<UploadedOccurrenceOverridePending>,
)

/**
 * 一次不可变同步请求及其最小 compare-and-clear 上下文。
 *
 * capture 不保存 payload hash、mutationId、receipt 或历史；[request] 已经包含本次实际上传的完整快照。
 */
data class ScheduleV2SyncCapture(
  val request: SyncRequest,
  val ordinaryCategories: List<UploadedCategoryPending>,
  val ordinarySchedules: List<UploadedSchedulePending>,
  val ordinaryOccurrenceOverrides: List<UploadedOccurrenceOverridePending>,
  val atomicBatches: List<AtomicBatchCapture>,
)

/**
 * 从当前三类双快照状态捕获一次 Schedule v2 请求。
 *
 * remote live 一律进入 confirmed，即使同 identity 同时存在 pending；无 batchId 的 pending 进入普通块，
 * 相同 batchId 的 pending 聚合为一个 typed AtomicBatch。
 */
class ScheduleV2RequestPlanner {
  /**
   * 捕获调用时可见的 remote/pending 并生成不可变请求。
   *
   * [syncRequestId] 由调用者生成；返回值同时保存响应应用所需的 uploaded localRevision。
   */
  fun capture(
    syncRequestId: String,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2SyncCapture {
    require(syncRequestId.isNotBlank()) { "syncRequestId must not be blank" }
    requireUniqueIdentities(categories.map { it.identity }, "Category")
    requireUniqueIdentities(schedules.map { it.identity }, "Schedule")
    requireUniqueIdentities(occurrenceOverrides.map { it.identity }, "OccurrenceOverride")

    val categoryUpserts = mutableListOf<CategoryInput>()
    val categoryDeletes = mutableListOf<CategoryDelete>()
    val scheduleUpserts = mutableListOf<ScheduleInput>()
    val scheduleDeletes = mutableListOf<ScheduleDelete>()
    val overrideUpserts = mutableListOf<OccurrenceOverrideInput>()
    val overrideDeletes = mutableListOf<OccurrenceOverrideDelete>()
    val ordinaryCategories = mutableListOf<UploadedCategoryPending>()
    val ordinarySchedules = mutableListOf<UploadedSchedulePending>()
    val ordinaryOverrides = mutableListOf<UploadedOccurrenceOverridePending>()
    val batchBuilders = linkedMapOf<String, AtomicBatchBuilder>()

    categories.forEach { state ->
      when (val pending = state.pending) {
        is PendingUpsert -> {
          val wire = state.projectUpsert(pending)
          val capture = UploadedCategoryPending(
            state.identity,
            pending.localRevision,
            UploadedPendingKind.UPSERT,
            pending.localBatchId,
          )
          if (pending.localBatchId == null) {
            categoryUpserts += wire
            ordinaryCategories += capture
          } else {
            batchBuilders.getOrPut(pending.localBatchId) { AtomicBatchBuilder(pending.localBatchId) }
              .addCategoryUpsert(wire, capture)
          }
        }
        is PendingDelete -> {
          val capture = UploadedCategoryPending(
            state.identity,
            pending.localRevision,
            UploadedPendingKind.DELETE,
            pending.localBatchId,
          )
          val wire = CategoryDelete(state.identity.id, pending.localModifiedAt)
          if (pending.localBatchId == null) {
            categoryDeletes += wire
            ordinaryCategories += capture
          } else {
            batchBuilders.getOrPut(pending.localBatchId) { AtomicBatchBuilder(pending.localBatchId) }
              .addCategoryDelete(wire, capture)
          }
        }
        null -> Unit
      }
    }

    schedules.forEach { state ->
      when (val pending = state.pending) {
        is PendingUpsert -> {
          val wire = state.projectUpsert(pending)
          val capture = UploadedSchedulePending(
            state.identity,
            pending.localRevision,
            UploadedPendingKind.UPSERT,
            pending.localBatchId,
          )
          if (pending.localBatchId == null) {
            scheduleUpserts += wire
            ordinarySchedules += capture
          } else {
            batchBuilders.getOrPut(pending.localBatchId) { AtomicBatchBuilder(pending.localBatchId) }
              .addScheduleUpsert(wire, capture)
          }
        }
        is PendingDelete -> {
          val capture = UploadedSchedulePending(
            state.identity,
            pending.localRevision,
            UploadedPendingKind.DELETE,
            pending.localBatchId,
          )
          val wire = ScheduleDelete(state.identity.id, pending.localModifiedAt)
          if (pending.localBatchId == null) {
            scheduleDeletes += wire
            ordinarySchedules += capture
          } else {
            batchBuilders.getOrPut(pending.localBatchId) { AtomicBatchBuilder(pending.localBatchId) }
              .addScheduleDelete(wire, capture)
          }
        }
        null -> Unit
      }
    }

    occurrenceOverrides.forEach { state ->
      when (val pending = state.pending) {
        is PendingUpsert -> {
          val wire = state.projectUpsert(pending)
          val capture = UploadedOccurrenceOverridePending(
            state.identity,
            pending.localRevision,
            UploadedPendingKind.UPSERT,
            pending.localBatchId,
          )
          if (pending.localBatchId == null) {
            overrideUpserts += wire
            ordinaryOverrides += capture
          } else {
            batchBuilders.getOrPut(pending.localBatchId) { AtomicBatchBuilder(pending.localBatchId) }
              .addOverrideUpsert(wire, capture)
          }
        }
        is PendingDelete -> {
          val capture = UploadedOccurrenceOverridePending(
            state.identity,
            pending.localRevision,
            UploadedPendingKind.DELETE,
            pending.localBatchId,
          )
          val wire = OccurrenceOverrideDelete(
            state.identity.scheduleId,
            state.identity.occurrenceDate,
            pending.localModifiedAt,
          )
          if (pending.localBatchId == null) {
            overrideDeletes += wire
            ordinaryOverrides += capture
          } else {
            batchBuilders.getOrPut(pending.localBatchId) { AtomicBatchBuilder(pending.localBatchId) }
              .addOverrideDelete(wire, capture)
          }
        }
        null -> Unit
      }
    }

    val batches = batchBuilders.values.map { it.capture() }
    val request = SyncRequest(
      syncRequestId = syncRequestId,
      categories = CategorySyncRequest(
        confirmed = categories.mapNotNull { state ->
          state.remoteSnapshot?.let { ConfirmedCategory(state.identity.id, it.version.toULong()) }
        },
        upserts = categoryUpserts.toList(),
        deletes = categoryDeletes.toList(),
      ),
      schedules = ScheduleSyncRequest(
        confirmed = schedules.mapNotNull { state ->
          state.remoteSnapshot?.let { ConfirmedSchedule(state.identity.id, it.version.toULong()) }
        },
        upserts = scheduleUpserts.toList(),
        deletes = scheduleDeletes.toList(),
      ),
      occurrenceOverrides = OccurrenceOverrideSyncRequest(
        confirmed = occurrenceOverrides.mapNotNull { state ->
          state.remoteSnapshot?.let {
            ConfirmedOccurrenceOverride(
              state.identity.scheduleId,
              state.identity.occurrenceDate,
              it.version.toULong(),
            )
          }
        },
        upserts = overrideUpserts.toList(),
        deletes = overrideDeletes.toList(),
      ),
      atomicBatches = batchBuilders.values.map { it.wire() },
    )
    return ScheduleV2SyncCapture(
      request = request,
      ordinaryCategories = ordinaryCategories.toList(),
      ordinarySchedules = ordinarySchedules.toList(),
      ordinaryOccurrenceOverrides = ordinaryOverrides.toList(),
      atomicBatches = batches,
    )
  }

  private fun <T> requireUniqueIdentities(identities: List<T>, type: String) {
    require(identities.size == identities.toSet().size) { "$type states contain duplicate identities" }
  }

  /**
   * 请求投影始终使用当前 remote version；CREATE R→U 场景下不改写 version=0 的本地 U。
   */
  private fun CategorySyncState.projectUpsert(
    pending: PendingUpsert<CategoryIdentity, CategoryResource>,
  ): CategoryInput = pending.resource.toWire().let { wire ->
    remoteSnapshot?.let { wire.copy(version = it.version.toULong()) } ?: wire
  }

  /** Schedule 的请求版本取当前 remote；完整业务字段、时间戳和 localRevision 均保持 pending 原值。 */
  private fun ScheduleSyncState.projectUpsert(
    pending: PendingUpsert<ScheduleIdentity, ScheduleResource>,
  ): ScheduleInput = pending.resource.toWire().let { wire ->
    remoteSnapshot?.let { wire.copy(version = it.version.toULong()) } ?: wire
  }

  /** OccurrenceOverride 同样只在 wire 投影 version，不在本地状态上执行 rebase。 */
  private fun OccurrenceOverrideSyncState.projectUpsert(
    pending: PendingUpsert<OccurrenceOverrideIdentity, OccurrenceOverrideResource>,
  ): OccurrenceOverrideInput = pending.resource.toWire().let { wire ->
    remoteSnapshot?.let { wire.copy(version = it.version.toULong()) } ?: wire
  }
}

private class AtomicBatchBuilder(private val batchId: String) {
  private val categoryUpserts = mutableListOf<CategoryInput>()
  private val categoryDeletes = mutableListOf<CategoryDelete>()
  private val scheduleUpserts = mutableListOf<ScheduleInput>()
  private val scheduleDeletes = mutableListOf<ScheduleDelete>()
  private val overrideUpserts = mutableListOf<OccurrenceOverrideInput>()
  private val overrideDeletes = mutableListOf<OccurrenceOverrideDelete>()
  private val categories = mutableListOf<UploadedCategoryPending>()
  private val schedules = mutableListOf<UploadedSchedulePending>()
  private val overrides = mutableListOf<UploadedOccurrenceOverridePending>()

  fun addCategoryUpsert(value: CategoryInput, capture: UploadedCategoryPending) {
    categoryUpserts += value
    categories += capture
  }

  fun addCategoryDelete(value: CategoryDelete, capture: UploadedCategoryPending) {
    categoryDeletes += value
    categories += capture
  }

  fun addScheduleUpsert(value: ScheduleInput, capture: UploadedSchedulePending) {
    scheduleUpserts += value
    schedules += capture
  }

  fun addScheduleDelete(value: ScheduleDelete, capture: UploadedSchedulePending) {
    scheduleDeletes += value
    schedules += capture
  }

  fun addOverrideUpsert(value: OccurrenceOverrideInput, capture: UploadedOccurrenceOverridePending) {
    overrideUpserts += value
    overrides += capture
  }

  fun addOverrideDelete(value: OccurrenceOverrideDelete, capture: UploadedOccurrenceOverridePending) {
    overrideDeletes += value
    overrides += capture
  }

  fun wire(): AtomicBatch = AtomicBatch(
    batchId = batchId,
    categories = CategoryAtomicBlock(categoryUpserts.toList(), categoryDeletes.toList()),
    schedules = ScheduleAtomicBlock(scheduleUpserts.toList(), scheduleDeletes.toList()),
    occurrenceOverrides = OccurrenceOverrideAtomicBlock(
      overrideUpserts.toList(),
      overrideDeletes.toList(),
    ),
  )

  fun capture(): AtomicBatchCapture = AtomicBatchCapture(
    batchId = batchId,
    categories = categories.toList(),
    schedules = schedules.toList(),
    occurrenceOverrides = overrides.toList(),
  )
}
