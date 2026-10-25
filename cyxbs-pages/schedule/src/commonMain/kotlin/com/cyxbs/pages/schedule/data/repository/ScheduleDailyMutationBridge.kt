package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.data.remote.MutationRequest
import com.cyxbs.pages.schedule.data.remote.MutationResponse
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentSyncState
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState

/** 日常请求使用的 HTTP 方法；具体资源仍由 version 和操作分支区分增删改。 */
enum class ScheduleDailyMutationMethod {
  CREATE,
  UPDATE,
  DELETE,
}

/** 一次本地命令对应的不可变日常请求 capture。 */
sealed interface ScheduleDailyMutationCapture {
  /** 已捕获同一 localRevision 下的全部资源变更，可以立即发起请求。 */
  data class Ready(
    val method: ScheduleDailyMutationMethod,
    val request: MutationRequest,
    val capture: ScheduleMutationCapture,
  ) : ScheduleDailyMutationCapture

  /** reducer 没有产生 pending，无法形成非空请求。 */
  data class Failure(val message: String) : ScheduleDailyMutationCapture
}

/**
 * 把一次本地命令产生的 pending 收敛为一个日常请求。
 *
 * 同一命令创建或修改的 Category、Schedule、OccurrenceAdjustment 共用 localRevision，因此会一起上传；服务端对
 * 每个资源独立返回成功或拒绝，失败项继续保留在本地，成功项按 localRevision compare-and-clear。
 */
class ScheduleDailyMutationBridge(
  private val planner: ScheduleRequestPlanner = ScheduleRequestPlanner(),
  private val applier: ScheduleResponseApplier = ScheduleResponseApplier(),
) {

  /**
   * 捕获本次 localRevision，并补入它实际引用但尚未获得远端 ID 的分类创建。
   *
   * 分类创建失败后，用户再次编辑同一日程会产生更高 revision；若仍只按 revision 过滤，新请求会只上传日程并被
   * `CATEGORY_NOT_FOUND` 再次拒绝。这里只补依赖分类，不携带其他旧失败，仍保持一次日程操作的最小请求范围。
   */
  fun capture(
    localRevision: Long,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleDailyMutationCapture {
    val selectedSchedules = schedules.filter { it.pending?.localRevision == localRevision }
    val selectedAdjustments = occurrenceAdjustments.filter { it.pending?.localRevision == localRevision }
    val requiredLocalCategoryIds = buildSet {
      selectedSchedules.mapNotNullTo(this) { it.effectiveResource()?.categoryId?.data }
      selectedAdjustments.mapNotNullTo(this) { state ->
        when (val patch = state.effectiveResource()?.categoryId?.data) {
          is FieldPatch.Replace -> patch.value
          else -> null
        }
      }
    }
    val selectedCategories = categories.filter { state ->
      state.pending?.localRevision == localRevision ||
        (state.identity.id in requiredLocalCategoryIds &&
          state.remoteSnapshot == null && state.pending is PendingUpsert<*, *>)
    }
    if (selectedCategories.isEmpty() && selectedSchedules.isEmpty() && selectedAdjustments.isEmpty()) {
      return ScheduleDailyMutationCapture.Failure("local command produced no pending mutation")
    }
    val capture = planner.captureMutation(
      categories = selectedCategories,
      schedules = selectedSchedules,
      occurrenceAdjustments = selectedAdjustments,
      categoryReferences = categories,
    )
    return ScheduleDailyMutationCapture.Ready(
      method = selectMethod(selectedCategories, selectedSchedules, selectedAdjustments),
      request = capture.request,
      capture = capture,
    )
  }

  /** 应用日常逐资源结果，并保留请求期间形成的较新本地修改。 */
  suspend fun apply(
    captured: ScheduleDailyMutationCapture.Ready,
    result: MutationResponse,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleApplyResult = applier.applyMutation(
    capture = captured.capture,
    response = result,
    categories = categories,
    schedules = schedules,
    occurrenceAdjustments = occurrenceAdjustments,
  )

  /** 优先由本次主 Schedule 的 pending 类型选择路由，没有 Schedule 时再检查其余资源。 */
  private fun selectMethod(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleDailyMutationMethod {
    if (schedules.any { it.pending is PendingDelete }) return ScheduleDailyMutationMethod.DELETE
    if (schedules.isNotEmpty()) {
      val createsSchedule = schedules.any { state ->
        state.remoteSnapshot == null &&
          (state.pending as? PendingUpsert<*, *>)?.resource?.version == 0L
      }
      return if (createsSchedule) ScheduleDailyMutationMethod.CREATE
      else ScheduleDailyMutationMethod.UPDATE
    }
    val pending = categories.mapNotNull { it.pending } + occurrenceAdjustments.mapNotNull { it.pending }
    if (pending.all { it is PendingDelete }) return ScheduleDailyMutationMethod.DELETE
    if (pending.any { it is PendingUpsert<*, *> && it.resource.version == 0L }) {
      return ScheduleDailyMutationMethod.CREATE
    }
    return ScheduleDailyMutationMethod.UPDATE
  }
}
