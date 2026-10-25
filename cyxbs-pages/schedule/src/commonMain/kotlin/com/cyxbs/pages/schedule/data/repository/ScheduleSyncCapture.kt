package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.CategoryMutationRequest
import com.cyxbs.pages.schedule.data.remote.CategorySyncRequest
import com.cyxbs.pages.schedule.data.remote.ConfirmedResource
import com.cyxbs.pages.schedule.data.remote.DeleteResource
import com.cyxbs.pages.schedule.data.remote.MutationRequest
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentMutationRequest
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentSyncRequest
import com.cyxbs.pages.schedule.data.remote.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.ScheduleMutationRequest
import com.cyxbs.pages.schedule.data.remote.ScheduleSyncRequest
import com.cyxbs.pages.schedule.data.remote.SyncRequest
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentSyncState
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState

/** capture 中记录的 pending 分支，仅用于按 localRevision 比较并清理已确认变更。 */
enum class UploadedPendingKind { UPSERT, DELETE }

/** 分类 pending 的位置关联信息。 */
data class UploadedCategoryPending(
  val identity: CategoryIdentity,
  val localRevision: Long,
  val kind: UploadedPendingKind,
)

/** 日程 pending 的位置关联信息；日程 UUID 同时是本地与远端主键。 */
data class UploadedSchedulePending(
  val identity: ScheduleIdentity,
  val localRevision: Long,
  val kind: UploadedPendingKind,
)

/** 单次调整 pending 的位置关联信息。 */
data class UploadedOccurrenceAdjustmentPending(
  val identity: OccurrenceAdjustmentIdentity,
  /** 首次创建为 null；更新和删除时保存请求使用的远端自增 ID。 */
  val remoteId: Long?,
  val localRevision: Long,
  val kind: UploadedPendingKind,
)

/** 一次不可变同步请求及其 compare-and-clear 上下文。 */
data class ScheduleSyncCapture(
  val request: SyncRequest,
  val categories: List<UploadedCategoryPending>,
  val schedules: List<UploadedSchedulePending>,
  val occurrenceAdjustments: List<UploadedOccurrenceAdjustmentPending>,
)

/** 一次不可变日常请求及其 compare-and-clear 上下文。 */
data class ScheduleMutationCapture(
  val request: MutationRequest,
  val categories: List<UploadedCategoryPending>,
  val schedules: List<UploadedSchedulePending>,
  val occurrenceAdjustments: List<UploadedOccurrenceAdjustmentPending>,
)

/**
 * 从本地双快照状态生成同步与日常请求。
 *
 * 请求体不携带 requestId。upsert/delete 结果严格按请求数组位置关联，inventory 则通过远端 ID 关联。
 * 分类和单次调整首次创建会临时上传 localId；日程直接上传客户端生成的稳定 UUID。
 */
class ScheduleRequestPlanner {

  /** 捕获完整远端 inventory 与当前全部 pending。 */
  fun capture(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleSyncCapture {
    requireUniqueIdentities(categories.map { it.identity }, "Category")
    requireUniqueIdentities(schedules.map { it.identity }, "Schedule")
    requireUniqueIdentities(occurrenceAdjustments.map { it.identity }, "OccurrenceAdjustment")
    val pending = capturePending(categories, schedules, occurrenceAdjustments)
    return ScheduleSyncCapture(
      request = SyncRequest(
        categories = CategorySyncRequest(
          confirmed = categories.mapNotNull { state ->
            state.remoteSnapshot?.let {
              ConfirmedResource(requireNotNull(it.resource.remoteId), it.version.toULong())
            }
          },
          upserts = pending.categoryUpserts,
          deletes = pending.categoryDeletes,
        ),
        schedules = ScheduleSyncRequest(
          confirmed = schedules.mapNotNull { state ->
            state.remoteSnapshot?.let { ConfirmedResource(state.identity.id, it.version.toULong()) }
          },
          upserts = pending.scheduleUpserts,
          deletes = pending.scheduleDeletes,
        ),
        occurrenceAdjustments = OccurrenceAdjustmentSyncRequest(
          confirmed = occurrenceAdjustments.mapNotNull { state ->
            state.remoteSnapshot?.let {
              ConfirmedResource(requireNotNull(it.resource.remoteId), it.version.toULong())
            }
          },
          upserts = pending.adjustmentUpserts,
          deletes = pending.adjustmentDeletes,
        ),
      ),
      categories = pending.categories,
      schedules = pending.schedules,
      occurrenceAdjustments = pending.adjustments,
    )
  }

  /**
   * 捕获一次本地命令产生的非空 pending 集合。
   *
   * [categories] 只包含本次需要上传的分类；[categoryReferences] 是当前账户的完整分类快照，仅用于把日程中的
   * 客户端分类 ID 解析成远端 ID，不会因此把无 pending 的分类加入请求。
   */
  fun captureMutation(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
    categoryReferences: List<CategorySyncState> = categories,
  ): ScheduleMutationCapture {
    require(categories.isNotEmpty() || schedules.isNotEmpty() || occurrenceAdjustments.isNotEmpty()) {
      "daily mutation capture requires pending resources"
    }
    require(
      categories.all { it.pending != null } &&
        schedules.all { it.pending != null } &&
        occurrenceAdjustments.all { it.pending != null },
    ) { "daily mutation capture only accepts states with pending" }
    val pending = capturePending(
      categories = categories,
      schedules = schedules,
      occurrenceAdjustments = occurrenceAdjustments,
      categoryReferences = categoryReferences,
    )
    return ScheduleMutationCapture(
      request = MutationRequest(
        categories = CategoryMutationRequest(pending.categoryUpserts, pending.categoryDeletes),
        schedules = ScheduleMutationRequest(pending.scheduleUpserts, pending.scheduleDeletes),
        occurrenceAdjustments = OccurrenceAdjustmentMutationRequest(
          pending.adjustmentUpserts,
          pending.adjustmentDeletes,
        ),
      ),
      categories = pending.categories,
      schedules = pending.schedules,
      occurrenceAdjustments = pending.adjustments,
    )
  }

  /** 将三类 pending 依次投影为 wire 列表，并保持 capture 与请求分支顺序一致。 */
  private fun capturePending(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
    categoryReferences: List<CategorySyncState> = categories,
  ): PendingProjection {
    val projection = PendingProjection()
    // 日常请求只上传本次命令涉及的 categories，但日程可以引用任意已落库分类，解析引用必须使用完整分类快照。
    val categoryByLocalId = categoryReferences.mapNotNull { state ->
      state.effectiveResource()?.let { state.identity.id to it }
    }.toMap()

    categories.forEach { state ->
      when (val pending = state.pending) {
        is PendingUpsert -> {
          projection.categoryUpserts += state.projectUpsert(pending)
          projection.categories += UploadedCategoryPending(
            state.identity, pending.localRevision, UploadedPendingKind.UPSERT,
          )
        }
        is PendingDelete -> {
          val remoteId = state.remoteSnapshot?.resource?.remoteId
          // 从未获得远端 ID 的分类删除应由 reducer 直接抵消，不产生无法定位的远端请求。
          if (remoteId != null) {
            projection.categoryDeletes += DeleteResource(remoteId)
            projection.categories += UploadedCategoryPending(
              state.identity, pending.localRevision, UploadedPendingKind.DELETE,
            )
          }
        }
        null -> Unit
      }
    }
    schedules.forEach { state ->
      when (val pending = state.pending) {
        is PendingUpsert -> {
          projection.scheduleUpserts += state.projectUpsert(pending, categoryByLocalId::get)
          projection.schedules += UploadedSchedulePending(
            state.identity, pending.localRevision, UploadedPendingKind.UPSERT,
          )
        }
        is PendingDelete -> {
          projection.scheduleDeletes += DeleteResource(state.identity.id)
          projection.schedules += UploadedSchedulePending(
            state.identity, pending.localRevision, UploadedPendingKind.DELETE,
          )
        }
        null -> Unit
      }
    }
    occurrenceAdjustments.forEach { state ->
      when (val pending = state.pending) {
        is PendingUpsert -> {
          projection.adjustmentUpserts += state.projectUpsert(pending, categoryByLocalId::get)
          projection.adjustments += UploadedOccurrenceAdjustmentPending(
            state.identity, state.remoteSnapshot?.resource?.remoteId,
            pending.localRevision, UploadedPendingKind.UPSERT,
          )
        }
        is PendingDelete -> {
          val remoteId = state.remoteSnapshot?.resource?.remoteId
          // 单次调整没有远端 ID 时，还原操作只需在本地删除该临时调整。
          if (remoteId != null) {
            projection.adjustmentDeletes += DeleteResource(remoteId)
            projection.adjustments += UploadedOccurrenceAdjustmentPending(
              state.identity, remoteId, pending.localRevision, UploadedPendingKind.DELETE,
            )
          }
        }
        null -> Unit
      }
    }
    return projection
  }

  private fun <T> requireUniqueIdentities(identities: List<T>, type: String) {
    require(identities.size == identities.toSet().size) { "$type states contain duplicate identities" }
  }

  /** 创建响应丢失后再次上传时，已有 remote 快照的 ID/version 优先用于更新。 */
  private fun CategorySyncState.projectUpsert(
    pending: PendingUpsert<CategoryIdentity, CategoryResource>,
  ): CategoryInput = pending.resource.toWire().let { wire ->
    remoteSnapshot?.resource?.let {
      wire.copy(localId = null, id = requireNotNull(it.remoteId), version = it.version.toULong())
    } ?: wire
  }

  /** 日程 ID 始终不变，仅以最新远端版本作为本轮合并基线。 */
  private fun ScheduleSyncState.projectUpsert(
    pending: PendingUpsert<ScheduleIdentity, ScheduleResource>,
    categoryByLocalId: (String) -> CategoryResource?,
  ): ScheduleInput = pending.resource.toWire(categoryByLocalId).let { wire ->
    remoteSnapshot?.let { wire.copy(version = it.version.toULong()) } ?: wire
  }

  /** 单次调整首次创建上传 localId；已有远端快照时改用其自增 ID/version。 */
  private fun OccurrenceAdjustmentSyncState.projectUpsert(
    pending: PendingUpsert<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>,
    categoryByLocalId: (String) -> CategoryResource?,
  ): OccurrenceAdjustmentInput = pending.resource.toWire(categoryByLocalId).let { wire ->
    remoteSnapshot?.resource?.let {
      wire.copy(localId = null, id = requireNotNull(it.remoteId), version = it.version.toULong())
    } ?: wire
  }
}

/** capturePending 的可变构建结果，只在一次规划过程中存在。 */
private class PendingProjection {
  val categoryUpserts = mutableListOf<CategoryInput>()
  val categoryDeletes = mutableListOf<DeleteResource<Long>>()
  val scheduleUpserts = mutableListOf<ScheduleInput>()
  val scheduleDeletes = mutableListOf<DeleteResource<String>>()
  val adjustmentUpserts = mutableListOf<OccurrenceAdjustmentInput>()
  val adjustmentDeletes = mutableListOf<DeleteResource<Long>>()
  val categories = mutableListOf<UploadedCategoryPending>()
  val schedules = mutableListOf<UploadedSchedulePending>()
  val adjustments = mutableListOf<UploadedOccurrenceAdjustmentPending>()
}
