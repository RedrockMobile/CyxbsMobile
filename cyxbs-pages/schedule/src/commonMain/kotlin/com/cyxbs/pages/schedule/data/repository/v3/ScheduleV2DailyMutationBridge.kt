package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatch
import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResult
import com.cyxbs.pages.schedule.data.remote.v3.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState

/** 日常聚合请求使用的 HTTP 方法；服务端仍由 typed payload 推断每个成员的 CREATE/PATCH/DELETE。 */
enum class ScheduleV2DailyMutationMethod {
  CREATE,
  UPDATE,
  DELETE,
}

/** 一次本地命令对应的不可变日常 Schedule 聚合 capture。 */
sealed interface ScheduleV2DailyMutationCapture {
  /** [batch] 同时携带本次资源和与目标 Schedule 相关、仍待提交的 Category/Override。 */
  data class Batch(
    val method: ScheduleV2DailyMutationMethod,
    val batch: AtomicBatch,
    val capture: ScheduleV2SyncCapture,
  ) : ScheduleV2DailyMutationCapture

  /** reducer 没有产生 pending，或本地状态无法形成非空批次。 */
  data class Failure(val message: String) : ScheduleV2DailyMutationCapture
}

/**
 * 把一次本地命令产生的 pending 收敛为一个 Schedule 日常聚合请求。
 *
 * capture 以本次 `localRevision` 为起点；若操作涉及某个 Schedule，还会带上该日程引用的待提交 Category、
 * 同 parent 的待提交 OccurrenceOverride，以及这些成员已有的原子批次闭包。服务端因此能在一个 owner 事务内
 * 校验最终图。bridge 不持久化、不重试，也不创建另一套 related DTO。
 */
class ScheduleV2DailyMutationBridge(
  private val planner: ScheduleV2RequestPlanner = ScheduleV2RequestPlanner(),
  private val applier: ScheduleV2ResponseApplier = ScheduleV2ResponseApplier(),
) {
  /**
   * 捕获本次命令及其 Schedule 关系闭包。
   *
   * 只选择仍有 pending 的 state；旧的无关失败不会被本次请求顺带绑定，避免一个业务拒绝回滚其他独立编辑。
   */
  fun capture(
    syncRequestId: String,
    localRevision: Long,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2DailyMutationCapture {
    val categoryIds = categories.filter { it.pending?.localRevision == localRevision }
      .mapTo(linkedSetOf()) { it.identity }
    val scheduleIds = schedules.filter { it.pending?.localRevision == localRevision }
      .mapTo(linkedSetOf()) { it.identity }
    val overrideIds = occurrenceOverrides.filter { it.pending?.localRevision == localRevision }
      .mapTo(linkedSetOf()) { it.identity }
    if (categoryIds.isEmpty() && scheduleIds.isEmpty() && overrideIds.isEmpty()) {
      return ScheduleV2DailyMutationCapture.Failure("local command produced no pending mutation")
    }

    // Override 命令属于其 parent Schedule；Schedule 命令则携带同 parent 的其他待提交 override。
    overrideIds.mapTo(scheduleIds) { ScheduleIdentity(it.scheduleId) }
    scheduleIds.toList().forEach { scheduleId ->
      val schedule = schedules.firstOrNull { it.identity == scheduleId }
      val categoryId = schedule?.effectiveResource()?.categoryId?.data
        ?: schedule?.remoteSnapshot?.resource?.categoryId?.data
      if (categoryId != null && categories.any { it.identity.id == categoryId && it.pending != null }) {
        categoryIds += CategoryIdentity(categoryId)
      }
      occurrenceOverrides.filter { it.identity.scheduleId == scheduleId.id && it.pending != null }
        .mapTo(overrideIds) { it.identity }
    }

    // 已持久化的 parent/delete 闭包不能被日常请求拆开；把命中的 localBatchId 成员完整纳入。
    var expanded: Boolean
    do {
      val batchIds = buildSet {
        categories.filter { it.identity in categoryIds }.mapNotNullTo(this) { it.pending?.localBatchId }
        schedules.filter { it.identity in scheduleIds }.mapNotNullTo(this) { it.pending?.localBatchId }
        occurrenceOverrides.filter { it.identity in overrideIds }.mapNotNullTo(this) { it.pending?.localBatchId }
      }
      val beforeSize = categoryIds.size + scheduleIds.size + overrideIds.size
      categories.filter { it.pending?.localBatchId in batchIds }.mapTo(categoryIds) { it.identity }
      schedules.filter { it.pending?.localBatchId in batchIds }.mapTo(scheduleIds) { it.identity }
      occurrenceOverrides.filter { it.pending?.localBatchId in batchIds }.mapTo(overrideIds) { it.identity }
      expanded = beforeSize != categoryIds.size + scheduleIds.size + overrideIds.size
    } while (expanded)

    val selectedCategories = categories.filter { it.identity in categoryIds && it.pending != null }
    val selectedSchedules = schedules.filter { it.identity in scheduleIds && it.pending != null }
    val selectedOverrides = occurrenceOverrides.filter { it.identity in overrideIds && it.pending != null }
    val capture = planner.captureAtomic(
      syncRequestId = syncRequestId,
      batchId = "$syncRequestId-batch",
      categories = selectedCategories,
      schedules = selectedSchedules,
      occurrenceOverrides = selectedOverrides,
    )
    val method = selectMethod(selectedCategories, selectedSchedules, selectedOverrides)
    return ScheduleV2DailyMutationCapture.Batch(method, capture.request.atomicBatches.single(), capture)
  }

  /** 把日常 AtomicBatchResult 包装为最小 SyncResponse，统一复用 canonical 合并与 R→U 清理。 */
  fun apply(
    captured: ScheduleV2DailyMutationCapture.Batch,
    result: AtomicBatchResult,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2ApplyResult = applier.apply(
    capture = captured.capture,
    response = SyncResponse(
      syncRequestId = captured.capture.request.syncRequestId,
      categories = CategorySyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
      schedules = ScheduleSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
      occurrenceOverrides = OccurrenceOverrideSyncResponse(emptyList(), emptyList(), emptyList(), emptyList()),
      atomicBatchResults = listOf(result),
    ),
    categories = categories,
    schedules = schedules,
    occurrenceOverrides = occurrenceOverrides,
  )

  /**
   * 选择聚合请求的 HTTP 方法。
   *
   * 聚合批次只要包含 Schedule，就必须由主 Schedule 的 CREATE/PATCH/DELETE 决定路由；例如“新 Category +
   * 既有 Schedule PATCH”仍应调用 PUT。没有 Schedule 的兼容调用才退回按其余成员版本选择。
   */
  private fun selectMethod(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2DailyMutationMethod {
    if (schedules.any { it.pending is PendingDelete }) return ScheduleV2DailyMutationMethod.DELETE
    if (schedules.isNotEmpty()) {
      val createsSchedule = schedules.any { state ->
        state.remoteSnapshot == null &&
          (state.pending as? PendingUpsert<*, *>)?.resource?.version == 0L
      }
      return if (createsSchedule) ScheduleV2DailyMutationMethod.CREATE
      else ScheduleV2DailyMutationMethod.UPDATE
    }
    val pending = categories.mapNotNull { it.pending } +
      schedules.mapNotNull { it.pending } + occurrenceOverrides.mapNotNull { it.pending }
    if (pending.all { it is PendingDelete }) return ScheduleV2DailyMutationMethod.DELETE
    if (pending.any { it is PendingUpsert<*, *> && it.resource.version == 0L }) {
      return ScheduleV2DailyMutationMethod.CREATE
    }
    return ScheduleV2DailyMutationMethod.UPDATE
  }
}
