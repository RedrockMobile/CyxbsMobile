package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.toDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.FieldPatch as UiFieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus as UiOccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency as UiRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleKind as UiScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.TodoState
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.PendingChange
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.v2.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.v2.ResourceIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind
import com.cyxbs.pages.schedule.domain.sync.v2.SyncResource
import com.cyxbs.pages.schedule.domain.sync.v2.Weekday
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val UTC_DAY_MILLIS = 86_400_000L

/** 为组合本地命令附加原子批次键；不改变 payload、revision 或删除时间。 */
private fun <
  I : ResourceIdentity,
  R : SyncResource<I>,
> PendingChange<I, R>.withLocalBatchId(
  batchId: String,
): PendingChange<I, R> = when (this) {
  is PendingUpsert -> copy(localBatchId = batchId)
  is PendingDelete -> copy(localBatchId = batchId)
}

/** 本地命令无法安全投影到当前后端协议时的最小稳定原因。 */
enum class ScheduleV2LocalCommandRejectionReason {
  /** 当前后端合同明确不支持该业务语义。 */
  UNSUPPORTED,

  /** 命令引用的本地有效资源不存在。 */
  NOT_FOUND,

  /** 当前集合、时间或 localRevision 不满足双快照不变量。 */
  INVALID_STATE,
}

/** reducer 的纯结果；不会执行持久化、网络或并发操作。 */
sealed interface ScheduleV2LocalCommandResult {
  /** 命令已转换为新的完整三类状态集合。 */
  data class Applied(
    val categories: List<CategorySyncState>,
    val schedules: List<ScheduleSyncState>,
    val occurrenceOverrides: List<OccurrenceOverrideSyncState>,
  ) : ScheduleV2LocalCommandResult

  /** 命令不改变 typed 状态，例如 RequestSync 或字段值完全相同的 Update。 */
  data object NoOp : ScheduleV2LocalCommandResult

  /** 命令被稳定拒绝；调用方必须继续使用原三类集合。 */
  data class Rejected(
    val reason: ScheduleV2LocalCommandRejectionReason,
  ) : ScheduleV2LocalCommandResult
}

/**
 * 将现有 UI [ScheduleCommand] 投影为 Schedule v2 typed pending。
 *
 * 调用方必须传入已由 Room 分配的 [localRevision] 与 [nowMillis]；reducer 不生成 ID、时间或 revision，
 * 也不读取 Room。旧领域对象的 revision 不属于新协议，始终不会被当作 resource version 或 localRevision。
 */
class ScheduleV2LocalCommandReducer {
  /**
   * 纯函数式应用一条命令。
   *
   * [localRevision] 必须严格大于目标 identity 当前 pending revision。Unsupported 命令不会产生临时状态、
   * 补偿命令或隐藏批次；所有异常输入都转换为 [ScheduleV2LocalCommandResult.Rejected]。
   */
  fun reduce(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceOverrides: List<OccurrenceOverrideSyncState>,
    command: ScheduleCommand,
    nowMillis: Long,
    localRevision: Long,
  ): ScheduleV2LocalCommandResult = try {
    require(nowMillis >= 0) { "nowMillis must not be negative" }
    require(localRevision > 0) { "localRevision must be positive" }
    requireUnique(categories.map { it.identity }, "Category")
    requireUnique(schedules.map { it.identity }, "Schedule")
    requireUnique(occurrenceOverrides.map { it.identity }, "OccurrenceOverride")

    when (command) {
      is ScheduleCommand.Create -> createSchedule(
        categories,
        schedules,
        occurrenceOverrides,
        command.schedule,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.Update -> updateSchedule(
        categories,
        schedules,
        occurrenceOverrides,
        command.schedule,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.Delete -> deleteSchedule(
        categories,
        schedules,
        occurrenceOverrides,
        ScheduleIdentity(command.scheduleId.value),
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.CompleteNonRepeating -> completeNonRepeating(
        categories,
        schedules,
        occurrenceOverrides,
        ScheduleIdentity(command.scheduleId.value),
        command.completed,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.CreateCategory -> createCategory(
        categories,
        schedules,
        occurrenceOverrides,
        command.category,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.UpdateCategory -> updateCategory(
        categories,
        schedules,
        occurrenceOverrides,
        command.category,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.ReorderCategories -> reorderCategories(
        categories,
        schedules,
        occurrenceOverrides,
        command.categories,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.SaveScheduleWithNewCategory -> saveScheduleWithNewCategory(
        categories,
        schedules,
        occurrenceOverrides,
        command.category,
        command.schedule,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.DeleteCategory -> deleteCategory(
        categories,
        schedules,
        occurrenceOverrides,
        CategoryIdentity(command.categoryId.value),
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.UpsertOccurrenceException -> upsertOccurrence(
        categories,
        schedules,
        occurrenceOverrides,
        command.exception,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.DeleteOccurrenceException -> deleteOccurrence(
        categories,
        schedules,
        occurrenceOverrides,
        occurrenceIdentity(command.scheduleId.value, command.recurrenceId),
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.SplitSeries -> splitSeries(
        categories,
        schedules,
        occurrenceOverrides,
        command,
        nowMillis,
        localRevision,
      )
      is ScheduleCommand.DeleteThisAndFollowing -> deleteThisAndFollowing(
        categories,
        schedules,
        occurrenceOverrides,
        command,
        nowMillis,
        localRevision,
      )
      ScheduleCommand.RequestSync -> ScheduleV2LocalCommandResult.NoOp
    }
  } catch (rejected: ReducerRejected) {
    ScheduleV2LocalCommandResult.Rejected(rejected.reason)
  } catch (_: IllegalArgumentException) {
    ScheduleV2LocalCommandResult.Rejected(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
  }

  private fun createSchedule(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    schedule: Schedule,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val identity = ScheduleIdentity(schedule.id.value)
    if (schedules.any { it.identity == identity }) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val resource = schedule.toResource(version = 0, old = null, now = now)
    val state = ScheduleSyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(resource, revision),
    )
    return applied(categories, schedules + state, overrides)
  }

  private fun updateSchedule(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    schedule: Schedule,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val identity = ScheduleIdentity(schedule.id.value)
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val resource = schedule.toResource(effective.version, effective, now)
    if (resource == effective) return ScheduleV2LocalCommandResult.NoOp
    val updated = state.replacePending(PendingUpsert(resource, revision))
    return applied(categories, schedules.replace(identity) { updated }, overrides)
  }

  private fun deleteSchedule(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    identity: ScheduleIdentity,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val liveChildren = overrides.filter {
      it.identity.scheduleId == identity.id &&
        (it.remoteSnapshot != null || it.effectiveResource() != null)
    }
    // 本地 effective 或服务端 remote 仍 live 的 Override 都必须与 parent 同批删除，避免遗漏已 pending DELETE 的子项。
    val batchId = if (liveChildren.isEmpty()) null else "schedule-delete-$revision"
    val updated = state.replacePending(
      PendingDelete(
        identity,
        localModifiedAt = now,
        localRevision = revision,
        localBatchId = batchId,
      ),
    )
    val updatedOverrides = if (batchId == null) {
      overrides
    } else {
      overrides.map { child ->
        if (child !in liveChildren) {
          child
        } else {
          child.replacePending(
            PendingDelete(
              identity = child.identity,
              localModifiedAt = now,
              localRevision = revision,
              localBatchId = batchId,
            ),
          )
        }
      }
    }
    return applied(categories, schedules.replace(identity) { updated }, updatedOverrides)
  }

  private fun completeNonRepeating(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    identity: ScheduleIdentity,
    completed: Boolean,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    if (effective.recurrence.data != null) {
      reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    }
    if (effective.todoState.data == null) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val todoState = if (completed) TodoState.COMPLETED else TodoState.OPEN
    if (effective.todoState.data == todoState) return ScheduleV2LocalCommandResult.NoOp
    val resource = effective.copy(todoState = AtomicField(todoState, now))
    val updated = state.replacePending(PendingUpsert(resource, revision))
    return applied(categories, schedules.replace(identity) { updated }, overrides)
  }

  private fun createCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    category: ScheduleCategory,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val identity = CategoryIdentity(category.id.value)
    if (categories.any { it.identity == identity }) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    if (hasDuplicateCategoryName(categories, category.name)) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val resource = category.toResource(version = 0, old = null, now = now)
    val state = CategorySyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(resource, revision),
    )
    return applied(categories + state, schedules, overrides)
  }

  private fun updateCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    category: ScheduleCategory,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val identity = CategoryIdentity(category.id.value)
    val state = categories.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    if (hasDuplicateCategoryName(categories, category.name, excluding = identity)) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val resource = category.toResource(effective.version, effective, now)
    if (resource == effective) return ScheduleV2LocalCommandResult.NoOp
    val updated = state.replacePending(PendingUpsert(resource, revision))
    return applied(categories.replace(identity) { updated }, schedules, overrides)
  }

  /**
   * 将一次拖拽后的完整顺序转换为同 revision 的多个 Category pending。
   *
   * 已存在分类只改 sortOrder；尚未落库的固定默认候选按调用方提供的稳定 identity 创建。相同 revision
   * 让 daily bridge 自动把全部变化收敛进一个 AtomicBatch，避免逐条请求暴露中间顺序。
   */
  private fun reorderCategories(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    ordered: List<ScheduleCategory>,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    if (ordered.isEmpty()) return ScheduleV2LocalCommandResult.NoOp
    requireUnique(ordered.map { it.id }, "Reordered Category")
    var currentCategories = categories
    var changed = false
    ordered.forEachIndexed { index, category ->
      val identity = CategoryIdentity(category.id.value)
      val result = if (currentCategories.any { it.identity == identity }) {
        updateCategory(
          currentCategories,
          schedules,
          overrides,
          category.copy(sortOrder = index),
          now,
          revision,
        )
      } else {
        createCategory(
          currentCategories,
          schedules,
          overrides,
          category.copy(revision = 0, sortOrder = index),
          now,
          revision,
        )
      }
      if (result is ScheduleV2LocalCommandResult.Applied) {
        currentCategories = result.categories
        changed = true
      }
    }
    return if (changed) applied(currentCategories, schedules, overrides)
    else ScheduleV2LocalCommandResult.NoOp
  }

  /**
   * 分类名按去除首尾空白、忽略大小写比较；pending DELETE 在远端确认前仍占用名称，不能被新分类复用。
   */
  private fun hasDuplicateCategoryName(
    categories: List<CategorySyncState>,
    name: String,
    excluding: CategoryIdentity? = null,
  ): Boolean {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return false
    return categories.any { state ->
      state.identity != excluding &&
        (state.effectiveResource() ?: state.remoteSnapshot?.resource)
          ?.name?.data?.trim()?.equals(normalizedName, ignoreCase = true) == true
    }
  }

  /**
   * 惰性创建固定默认分类并保存引用它的日程；两个 pending 使用同一 revision 和本地批次键。
   *
   * 分类必须尚不存在，日程可为 CREATE 或 PATCH。该能力只服务于默认分类首次使用，通用分类管理不走此入口。
   */
  private fun saveScheduleWithNewCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    category: ScheduleCategory,
    schedule: Schedule,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    if (schedule.categoryId != category.id) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val withCategory = createCategory(categories, schedules, overrides, category, now, revision)
    if (withCategory !is ScheduleV2LocalCommandResult.Applied) return withCategory

    val scheduleIdentity = ScheduleIdentity(schedule.id.value)
    val saved = if (schedules.any { it.identity == scheduleIdentity }) {
      updateSchedule(
        withCategory.categories,
        withCategory.schedules,
        withCategory.occurrenceOverrides,
        schedule,
        now,
        revision,
      )
    } else {
      createSchedule(
        withCategory.categories,
        withCategory.schedules,
        withCategory.occurrenceOverrides,
        schedule,
        now,
        revision,
      )
    }
    if (saved !is ScheduleV2LocalCommandResult.Applied) return saved

    val categoryIdentity = CategoryIdentity(category.id.value)
    val batchId = "category-schedule-$revision"
    return saved.copy(
      categories = saved.categories.map { state ->
        if (state.identity != categoryIdentity) state
        else state.copy(pending = state.pending?.withLocalBatchId(batchId))
      },
      schedules = saved.schedules.map { state ->
        if (state.identity != scheduleIdentity) state
        else state.copy(pending = state.pending?.withLocalBatchId(batchId))
      },
    )
  }

  private fun deleteCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    identity: CategoryIdentity,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val state = categories.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val categoryInUse = schedules.any {
      it.remoteSnapshot?.resource?.categoryId?.data == identity.id ||
        it.effectiveResource()?.categoryId?.data == identity.id
    }
    if (categoryInUse) {
      // remote 即使被本地 pending DELETE 隐藏，确认前仍会让服务端拒绝分类删除，因此本地先拒绝。
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val updated = state.replacePending(
      PendingDelete(identity, localModifiedAt = now, localRevision = revision),
    )
    return applied(categories.replace(identity) { updated }, schedules, overrides)
  }

  private fun upsertOccurrence(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    exception: ScheduleOccurrenceException,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val identity = occurrenceIdentity(exception.scheduleId.value, exception.recurrenceId)
    val existing = overrides.firstOrNull { it.identity == identity }
    val effective = existing?.effectiveResource()
    val version = effective?.version ?: 0
    val resource = exception.toResource(identity, version, effective, now)
    if (resource == effective) return ScheduleV2LocalCommandResult.NoOp
    val updated = if (existing == null) {
      OccurrenceOverrideSyncState(
        identity = identity,
        remoteSnapshot = null,
        pending = PendingUpsert(resource, revision),
      )
    } else {
      existing.replacePending(PendingUpsert(resource, revision))
    }
    return applied(
      categories,
      schedules,
      if (existing == null) overrides + updated else overrides.replace(identity) { updated },
    )
  }

  private fun deleteOccurrence(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    identity: OccurrenceOverrideIdentity,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val state = overrides.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val updated = state.replacePending(
      PendingDelete(identity, localModifiedAt = now, localRevision = revision),
    )
    return applied(categories, schedules, overrides.replace(identity) { updated })
  }

  /**
   * 原子拆分重复系列：PATCH 旧系列、CREATE 新系列，并迁移边界后的 occurrence 例外。
   *
   * 边界 occurrence 的有效内容已经被提升为新系列字段，因此只删除旧 identity、不复制该例外；更晚的有效例外
   * 保留原 occurrenceDate 并改挂新 scheduleId。仅存在本地 CREATE 的旧例外可直接移除，已经存在远端快照的
   * 例外则必须显式 DELETE。所有成员共享 [revision] 与 batchId，日常请求不会暴露半拆分状态。
   */
  private fun splitSeries(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    command: ScheduleCommand.SplitSeries,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val previousIdentity = ScheduleIdentity(command.previousSchedule.id.value)
    val followingIdentity = ScheduleIdentity(command.followingSchedule.id.value)
    if (previousIdentity == followingIdentity || command.previousSchedule.recurrence == null ||
      command.followingSchedule.recurrence == null
    ) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val previousState = schedules.firstOrNull { it.identity == previousIdentity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val previousEffective = previousState.effectiveResource()
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    if (schedules.any { it.identity == followingIdentity }) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val boundaryDate = occurrenceIdentity(previousIdentity.id, command.recurrenceId).occurrenceDate
    val batchId = "series-split-$revision"

    var nextCategories = categories
    command.newCategory?.let { category ->
      if (command.followingSchedule.categoryId != category.id) {
        reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
      }
      val categoryResult = createCategory(
        nextCategories, schedules, overrides, category, now, revision,
      ) as? ScheduleV2LocalCommandResult.Applied
        ?: reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
      nextCategories = categoryResult.categories.map { state ->
        if (state.identity.id == category.id.value) {
          state.copy(pending = state.pending?.withLocalBatchId(batchId))
        } else {
          state
        }
      }
    }

    val previousResource = command.previousSchedule.toResource(
      version = previousEffective.version,
      old = previousEffective,
      now = now,
    )
    val followingResource = command.followingSchedule.toResource(version = 0, old = null, now = now)
    val nextSchedules = schedules.replace(previousIdentity) { state ->
      state.replacePending(PendingUpsert(previousResource, revision, batchId))
    } + ScheduleSyncState(
      identity = followingIdentity,
      remoteSnapshot = null,
      pending = PendingUpsert(followingResource, revision, batchId),
    )

    val nextOverrides = migrateFollowingOverrides(
      overrides = overrides,
      previousScheduleId = previousIdentity.id,
      followingScheduleId = followingIdentity.id,
      boundaryDate = boundaryDate,
      now = now,
      revision = revision,
      batchId = batchId,
    )
    return applied(nextCategories, nextSchedules, nextOverrides)
  }

  /**
   * 删除当前及后续 occurrence：PATCH 截断后的父系列，并同步删除边界及更晚的旧例外。
   *
   * 本地尚未上传的例外直接从双快照集合移除；已有 remote 的例外生成无版本 DELETE。系列 PATCH 与所有 DELETE
   * 共享原子批次，服务端最终图校验时不会看到落在截断范围外的孤立例外。
   */
  private fun deleteThisAndFollowing(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
    command: ScheduleCommand.DeleteThisAndFollowing,
    now: Long,
    revision: Long,
  ): ScheduleV2LocalCommandResult {
    val identity = ScheduleIdentity(command.previousSchedule.id.value)
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleV2LocalCommandRejectionReason.NOT_FOUND)
    if (command.previousSchedule.recurrence == null) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    val boundaryDate = occurrenceIdentity(identity.id, command.recurrenceId).occurrenceDate
    val batchId = "series-truncate-$revision"
    val previousResource = command.previousSchedule.toResource(effective.version, effective, now)
    val nextSchedules = schedules.replace(identity) { current ->
      current.replacePending(PendingUpsert(previousResource, revision, batchId))
    }
    val nextOverrides = overrides.mapNotNull { override ->
      if (override.identity.scheduleId != identity.id || override.identity.occurrenceDate < boundaryDate) {
        override
      } else if (override.remoteSnapshot == null) {
        null
      } else {
        override.replacePending(PendingDelete(
          identity = override.identity,
          localModifiedAt = now,
          localRevision = revision,
          localBatchId = batchId,
        ))
      }
    }
    return applied(categories, nextSchedules, nextOverrides)
  }

  /**
   * 把拆分边界后的例外从旧 schedule identity 迁移到新系列。
   *
   * 边界例外不复制；它的有效字段已成为新系列字段。更晚例外只有在当前 effective 仍 live 时才创建新资源，
   * pending DELETE 不会复活。旧 identity 若已存在远端资源则保留为同批 DELETE，否则直接移除本地临时行。
   */
  private fun migrateFollowingOverrides(
    overrides: List<OccurrenceOverrideSyncState>,
    previousScheduleId: String,
    followingScheduleId: String,
    boundaryDate: Long,
    now: Long,
    revision: Long,
    batchId: String,
  ): List<OccurrenceOverrideSyncState> = buildList {
    overrides.forEach { state ->
      if (state.identity.scheduleId != previousScheduleId || state.identity.occurrenceDate < boundaryDate) {
        add(state)
        return@forEach
      }
      val effective = state.effectiveResource()
      if (state.remoteSnapshot != null) {
        add(state.replacePending(PendingDelete(
          identity = state.identity,
          localModifiedAt = now,
          localRevision = revision,
          localBatchId = batchId,
        )))
      }
      if (state.identity.occurrenceDate > boundaryDate && effective != null) {
        val followingOverrideIdentity = OccurrenceOverrideIdentity(
          scheduleId = followingScheduleId,
          occurrenceDate = state.identity.occurrenceDate,
        )
        add(OccurrenceOverrideSyncState(
          identity = followingOverrideIdentity,
          remoteSnapshot = null,
          pending = PendingUpsert(
            resource = effective.copy(identity = followingOverrideIdentity, version = 0),
            localRevision = revision,
            localBatchId = batchId,
          ),
        ))
      }
    }
  }

  private fun Schedule.toResource(
    version: Long,
    old: ScheduleResource?,
    now: Long,
  ): ScheduleResource {
    val category = categoryId
      ?: reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    val timingValue = timing.toWireTiming()
    val recurrenceValue = recurrence?.toWireRecurrence(
      timing = timing,
      stableAnchorDate = old?.recurrence?.data?.anchorDate ?: recurrenceAnchorDate?.toUtcDaySlot(),
    )
    val reminderValues = reminders.toWireReminders()
    if (old != null && old.kind != kind.toWire()) {
      reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    }
    return ScheduleResource(
      identity = ScheduleIdentity(id.value),
      version = version,
      kind = kind.toWire(),
      title = atomic(title, old?.title, now),
      description = atomic(description, old?.description, now),
      categoryId = atomic(category.value, old?.categoryId, now),
      timing = atomic(timingValue, old?.timing, now),
      recurrence = atomic(recurrenceValue, old?.recurrence, now),
      reminders = atomic(reminderValues, old?.reminders, now),
      todoState = atomic(todoState?.toWire(), old?.todoState, now),
      linkedToCourse = atomic(linkedToCourse, old?.linkedToCourse, now),
    )
  }

  private fun ScheduleCategory.toResource(
    version: Long,
    old: CategoryResource?,
    now: Long,
  ): CategoryResource = CategoryResource(
    identity = CategoryIdentity(id.value),
    version = version,
    name = atomic(name, old?.name, now),
    // `null` 是“未设置自定义颜色”的协议事实，不能归一化为空串。
    color = atomic(color, old?.color, now),
    sortOrder = atomic(sortOrder.toLong(), old?.sortOrder, now),
  )

  private fun ScheduleOccurrenceException.toResource(
    identity: OccurrenceOverrideIdentity,
    version: Long,
    old: OccurrenceOverrideResource?,
    now: Long,
  ): OccurrenceOverrideResource {
    val patchValue = patch ?: OccurrencePatch()
    val timingValue = patchValue.timing.toWireTimingPatch()
    val titleValue = patchValue.title.toWireTitlePatch()
    val descriptionValue = patchValue.description.toWireStringPatch()
    val categoryValue = patchValue.categoryId.toWireCategoryPatch()
    val reminderValue = patchValue.reminders.toWireReminderPatch()
    return OccurrenceOverrideResource(
      identity = identity,
      version = version,
      status = atomic(status.toWire(), old?.status, now),
      timing = atomic(timingValue, old?.timing, now),
      title = atomic(titleValue, old?.title, now),
      description = atomic(descriptionValue, old?.description, now),
      categoryId = atomic(categoryValue, old?.categoryId, now),
      reminders = atomic(reminderValue, old?.reminders, now),
    )
  }

  /**
   * 仅字段业务值变化时写入 now；未变化字段复用整个旧 AtomicField，保留原 modifiedAt。
   */
  private fun <T> atomic(value: T, old: AtomicField<T>?, now: Long): AtomicField<T> =
    if (old != null && old.data == value) old else AtomicField(value, now)

  private fun ScheduleTiming.toWireTiming(): TimingInput = when (this) {
    is ScheduleTiming.Timed -> {
      val startMillis = start.toLocalDateTime()
        .toInstant(TimeZone.of(timeZoneId))
        .toEpochMilliseconds()
      TimingInput(
        kind = TimingKind.TIMED,
        startAt = startMillis,
        endAt = startMillis + durationMinutes.minutes.inWholeMilliseconds,
      )
    }
    is ScheduleTiming.Deadline -> TimingInput(
      kind = TimingKind.DEADLINE,
      dueAt = due.toLocalDateTime()
        .toInstant(TimeZone.of(timeZoneId))
        .toEpochMilliseconds(),
    )
    is ScheduleTiming.AllDay -> {
      val startMillis = startDate.toUtcDaySlot()
      TimingInput(
        kind = TimingKind.ALL_DAY,
        startAt = startMillis,
        endAt = startMillis + durationDays * UTC_DAY_MILLIS,
      )
    }
    ScheduleTiming.Unscheduled -> TimingInput(kind = TimingKind.UNSCHEDULED)
  }

  private fun RecurrenceRule.toWireRecurrence(
    timing: ScheduleTiming,
    stableAnchorDate: Long?,
  ): RecurrenceInput {
    if (byMonthDays.isNotEmpty() || byMonths.isNotEmpty()) {
      reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    }
    val frequency = when (frequency) {
      UiRecurrenceFrequency.DAILY -> RecurrenceFrequency.DAILY
      UiRecurrenceFrequency.WEEKLY -> RecurrenceFrequency.WEEKLY
      UiRecurrenceFrequency.MONTHLY,
      UiRecurrenceFrequency.YEARLY,
      -> reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    }
    val timingAnchor = when (timing) {
      is ScheduleTiming.Timed -> timing.start.date
      is ScheduleTiming.Deadline -> timing.due.date
      is ScheduleTiming.AllDay -> timing.startDate
      ScheduleTiming.Unscheduled -> reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    }
    val anchor = stableAnchorDate?.toUtcDate() ?: timingAnchor
    val count: Int?
    val untilDate: Long?
    when (val recurrenceEnd = end) {
      RecurrenceEnd.Never -> {
        count = null
        untilDate = null
      }
      is RecurrenceEnd.Count -> {
        count = recurrenceEnd.value
        untilDate = null
      }
      is RecurrenceEnd.Until -> {
        count = null
        untilDate = recurrenceEnd.date.toUtcDaySlot()
      }
    }
    return RecurrenceInput(
      frequency = frequency,
      interval = interval,
      anchorDate = anchor.toUtcDaySlot(),
      count = count,
      untilDate = untilDate,
      weekdays = when (frequency) {
        RecurrenceFrequency.DAILY -> byWeekDays.map { it.toWire() }.toSet()
        RecurrenceFrequency.WEEKLY -> {
          val anchorWeekday = requireNotNull(IsoWeekDay.fromIsoNumber(anchor.dayOfWeekNumber))
          val effectiveWeekdays = byWeekDays.ifEmpty { setOf(anchorWeekday) }
          if (anchorWeekday !in effectiveWeekdays) {
            // 后端要求稳定 anchor 本身属于 WEEKLY 集合，不能静默移动系列首个 occurrence。
            reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
          }
          effectiveWeekdays.map { it.toWire() }.toSet()
        }
      },
    )
  }

  private fun List<ScheduleReminder>.toWireReminders(): List<ReminderInput> = map { reminder ->
    if (reminder.channel != ReminderChannel.DEVICE) {
      reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    }
    ReminderInput(
      minutesBefore = reminder.offsetMinutes,
      // ReminderId 只用于客户端列表 identity，协议 message 当前没有对应的用户文案来源。
      message = "",
    )
  }

  private fun UiFieldPatch<String>.toWireTitlePatch(): FieldPatch<String> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> reject(ScheduleV2LocalCommandRejectionReason.UNSUPPORTED)
    is UiFieldPatch.Replace -> FieldPatch.Replace(value)
  }

  /** timing 是不可清空的联合值；单次移动始终以完整 REPLACE 上传。 */
  private fun UiFieldPatch<ScheduleTiming>.toWireTimingPatch(): FieldPatch<TimingInput> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
    is UiFieldPatch.Replace -> {
      if (value == ScheduleTiming.Unscheduled) reject(ScheduleV2LocalCommandRejectionReason.INVALID_STATE)
      FieldPatch.Replace(value.toWireTiming())
    }
  }

  private fun UiFieldPatch<CategoryId>.toWireCategoryPatch(): FieldPatch<String> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> FieldPatch.Clear
    is UiFieldPatch.Replace -> FieldPatch.Replace(value.value)
  }

  private fun UiFieldPatch<String>.toWireStringPatch(): FieldPatch<String> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> FieldPatch.Clear
    is UiFieldPatch.Replace -> FieldPatch.Replace(value)
  }

  private fun UiFieldPatch<List<ScheduleReminder>>.toWireReminderPatch():
    FieldPatch<List<ReminderInput>> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> FieldPatch.Clear
    is UiFieldPatch.Replace -> FieldPatch.Replace(value.toWireReminders())
  }

  /**
   * Occurrence identity 只取 recurrenceId 原始墙上时间的日期并转 UTC 日期槽；
   * timeZoneId、allDay 与后续 timing 移动都不能改变该 identity。
   */
  private fun occurrenceIdentity(
    scheduleId: String,
    recurrenceId: RecurrenceId,
  ): OccurrenceOverrideIdentity = OccurrenceOverrideIdentity(
    scheduleId = scheduleId,
    occurrenceDate = recurrenceId.originalDateTime.date.toUtcDaySlot(),
  )

  private fun Date.toUtcDaySlot(): Long =
    toLocalDate().atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

  private fun Long.toUtcDate(): Date =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toDate()

  private fun ScheduleTodoState.toWire(): TodoState = when (this) {
    ScheduleTodoState.PENDING -> TodoState.OPEN
    ScheduleTodoState.COMPLETED -> TodoState.COMPLETED
  }

  private fun UiScheduleKind.toWire(): ScheduleKind = when (this) {
    UiScheduleKind.TODO -> ScheduleKind.TODO
    UiScheduleKind.AFFAIR -> ScheduleKind.AFFAIR
  }

  private fun UiOccurrenceStatus.toWire(): OccurrenceStatus = when (this) {
    UiOccurrenceStatus.ACTIVE -> OccurrenceStatus.ACTIVE
    UiOccurrenceStatus.COMPLETED -> OccurrenceStatus.COMPLETED
    UiOccurrenceStatus.CANCELLED -> OccurrenceStatus.CANCELLED
  }

  private fun IsoWeekDay.toWire(): Weekday = when (this) {
    IsoWeekDay.MONDAY -> Weekday.MO
    IsoWeekDay.TUESDAY -> Weekday.TU
    IsoWeekDay.WEDNESDAY -> Weekday.WE
    IsoWeekDay.THURSDAY -> Weekday.TH
    IsoWeekDay.FRIDAY -> Weekday.FR
    IsoWeekDay.SATURDAY -> Weekday.SA
    IsoWeekDay.SUNDAY -> Weekday.SU
  }

  private fun applied(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    overrides: List<OccurrenceOverrideSyncState>,
  ): ScheduleV2LocalCommandResult.Applied =
    ScheduleV2LocalCommandResult.Applied(
      categories = categories,
      schedules = schedules,
      occurrenceOverrides = overrides,
    )

  private fun <T> requireUnique(values: List<T>, type: String) {
    require(values.size == values.toSet().size) { "$type states contain duplicate identities" }
  }

  private fun List<CategorySyncState>.replace(
    identity: CategoryIdentity,
    transform: (CategorySyncState) -> CategorySyncState,
  ): List<CategorySyncState> = map { if (it.identity == identity) transform(it) else it }

  private fun List<ScheduleSyncState>.replace(
    identity: ScheduleIdentity,
    transform: (ScheduleSyncState) -> ScheduleSyncState,
  ): List<ScheduleSyncState> = map { if (it.identity == identity) transform(it) else it }

  private fun List<OccurrenceOverrideSyncState>.replace(
    identity: OccurrenceOverrideIdentity,
    transform: (OccurrenceOverrideSyncState) -> OccurrenceOverrideSyncState,
  ): List<OccurrenceOverrideSyncState> = map { if (it.identity == identity) transform(it) else it }
}

private class ReducerRejected(
  val reason: ScheduleV2LocalCommandRejectionReason,
) : IllegalStateException()

private fun reject(reason: ScheduleV2LocalCommandRejectionReason): Nothing =
  throw ReducerRejected(reason)
