package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDelete
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState

/**
 * 单条日常 Schedule mutation 的不可变捕获结果。
 *
 * [Failure] 只表示该 state 不能走非 atomic 日常接口；原子批次必须留给完整 Sync，不在 bridge 内拆批。
 */
sealed interface ScheduleV2DailyMutationCapture {
  /** POST/PUT 共用完整 ScheduleInput；[create] 仅由 planner 投影后的 version 是否为 0 决定。 */
  data class Upsert(
    val input: ScheduleInput,
    val create: Boolean,
    val capture: ScheduleV2SyncCapture,
  ) : ScheduleV2DailyMutationCapture

  /** DELETE 不携带 version，只保留 identity 与本地修改时间。 */
  data class Delete(
    val input: ScheduleDelete,
    val capture: ScheduleV2SyncCapture,
  ) : ScheduleV2DailyMutationCapture

  /** 无 pending 或 atomic pending 等不适用于日常接口的稳定拒绝。 */
  data class Failure(val message: String) : ScheduleV2DailyMutationCapture
}

/**
 * 把单个非 atomic Schedule pending 接到日常 POST/PUT/DELETE 的纯 common 适配层。
 *
 * bridge 不复制版本合并或 compare-and-clear 状态机：capture 复用 [ScheduleV2RequestPlanner]，日常 typed
 * result 只被包装成最小 synthetic [SyncResponse]，再交给 [ScheduleV2ResponseApplier]。本层不解释 HTTP
 * envelope、不重试、不持久化，也不会把 atomic batch 拆成普通请求。
 */
class ScheduleV2DailyMutationBridge(
  private val planner: ScheduleV2RequestPlanner = ScheduleV2RequestPlanner(),
  private val applier: ScheduleV2ResponseApplier = ScheduleV2ResponseApplier(),
) {
  /**
   * 捕获一条 Schedule pending，并生成日常接口所需 input。
   *
   * synthetic syncRequestId 仅用于本次内存中的 response 关联，由 identity 与 localRevision 确定性构造，
   * 不作为 mutationId、回执或持久状态。
   */
  fun capture(state: ScheduleSyncState): ScheduleV2DailyMutationCapture {
    val pending = state.pending
      ?: return ScheduleV2DailyMutationCapture.Failure("Schedule has no pending mutation")
    if (pending.localBatchId != null) {
      return ScheduleV2DailyMutationCapture.Failure("atomic Schedule pending must use Sync")
    }
    val captured = planner.capture(
      syncRequestId = "daily-schedule-${state.identity.id}-${pending.localRevision}",
      categories = emptyList(),
      schedules = listOf(state),
      occurrenceOverrides = emptyList(),
    )
    return when (pending) {
      is PendingUpsert -> {
        val input = captured.request.schedules.upserts.singleOrNull()
          ?: return ScheduleV2DailyMutationCapture.Failure("planner did not produce one Schedule upsert")
        ScheduleV2DailyMutationCapture.Upsert(
          input = input,
          create = input.version == 0uL,
          capture = captured,
        )
      }
      is PendingDelete -> {
        val input = captured.request.schedules.deletes.singleOrNull()
          ?: return ScheduleV2DailyMutationCapture.Failure("planner did not produce one Schedule delete")
        ScheduleV2DailyMutationCapture.Delete(input, captured)
      }
    }
  }

  /**
   * 将日常 POST/PUT 的 typed result 交给统一 applier。
   *
   * [categories]、[schedules]、[occurrenceOverrides] 必须是响应到达时的当前完整状态，以保留 R→U。
   */
  fun applyUpsert(
    captured: ScheduleV2DailyMutationCapture.Upsert,
    result: ScheduleUpsertResult,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2ApplyResult = applier.apply(
    capture = captured.capture,
    response = syntheticResponse(captured.capture, upsertResult = result),
    categories = categories,
    schedules = schedules,
    occurrenceOverrides = occurrenceOverrides,
  )

  /** 将日常 DELETE typed result 包装后交给统一 applier，复用 tombstone 与 compare-and-clear 语义。 */
  fun applyDelete(
    captured: ScheduleV2DailyMutationCapture.Delete,
    result: ScheduleDeleteResult,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2ApplyResult = applier.apply(
    capture = captured.capture,
    response = syntheticResponse(captured.capture, deleteResult = result),
    categories = categories,
    schedules = schedules,
    occurrenceOverrides = occurrenceOverrides,
  )

  /** 构造仅含单条 Schedule result 的完整 typed SyncResponse，其他 block/list 必须显式为空。 */
  private fun syntheticResponse(
    capture: ScheduleV2SyncCapture,
    upsertResult: ScheduleUpsertResult? = null,
    deleteResult: ScheduleDeleteResult? = null,
  ): SyncResponse = SyncResponse(
    syncRequestId = capture.request.syncRequestId,
    categories = CategorySyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
    schedules = ScheduleSyncResponse(
      upserts = emptyList(),
      deletes = emptyList(),
      upsertResults = listOfNotNull(upsertResult),
      deleteResults = listOfNotNull(deleteResult),
    ),
    occurrenceOverrides = OccurrenceOverrideSyncResponse(
      emptyList(),
      emptyList(),
      emptyList(),
      emptyList(),
    ),
    atomicBatchResults = emptyList(),
  )
}
