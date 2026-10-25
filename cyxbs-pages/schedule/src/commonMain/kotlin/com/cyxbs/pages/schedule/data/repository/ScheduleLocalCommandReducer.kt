package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.toDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.FieldPatch as UiFieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus as UiOccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.OccurrenceTime as UiOccurrenceTime
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency as UiRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleKind as UiScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.TodoState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentSyncState
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeInput
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeKind
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.domain.sync.Weekday
import com.cyxbs.pages.schedule.domain.uuid.UuidV7Generator
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val UTC_DAY_MILLIS = 86_400_000L

/** 本地命令无法安全投影到当前后端协议时的最小稳定原因。 */
enum class ScheduleLocalCommandRejectionReason {
  /** 当前后端合同明确不支持该业务语义。 */
  UNSUPPORTED,

  /** 命令引用的本地有效资源不存在。 */
  NOT_FOUND,

  /** 当前集合、时间或 localRevision 不满足双快照不变量。 */
  INVALID_STATE,
}

/** reducer 的纯结果；不会执行持久化、网络或并发操作。 */
sealed interface ScheduleLocalCommandResult {
  /** 命令已转换为新的完整三类状态集合。 */
  data class Applied(
    val categories: List<CategorySyncState>,
    val schedules: List<ScheduleSyncState>,
    val occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ) : ScheduleLocalCommandResult

  /** 命令不改变 typed 状态，例如 RequestSync 或字段值完全相同的 Update。 */
  data object NoOp : ScheduleLocalCommandResult

  /** 命令被稳定拒绝；调用方必须继续使用原三类集合。 */
  data class Rejected(
    val reason: ScheduleLocalCommandRejectionReason,
  ) : ScheduleLocalCommandResult
}

/**
 * 将现有 UI [ScheduleCommand] 投影为 Schedule typed pending。
 *
 * 调用方必须传入已由 Room 分配的 [localRevision] 与 [nowMillis]。新单次调整的本地 ID 由注入的 UUIDv7
 * 生成器提供；reducer 不读取 Room。旧领域对象的 revision 不属于新协议。
 */
class ScheduleLocalCommandReducer(
  private val uuidGenerator: UuidV7Generator = UuidV7Generator(),
) {
  /**
   * 纯函数式应用一条命令。
   *
   * [localRevision] 必须严格大于目标 identity 当前 pending revision。Unsupported 命令不会产生临时状态、
   * 补偿命令或隐藏批次；所有异常输入都转换为 [ScheduleLocalCommandResult.Rejected]。
   */
  suspend fun reduce(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
    command: ScheduleCommand,
    nowMillis: Long,
    localRevision: Long,
  ): ScheduleLocalCommandResult = try {
    require(nowMillis >= 0) { "nowMillis must not be negative" }
    require(localRevision > 0) { "localRevision must be positive" }
    requireUnique(categories.map { it.identity }, "Category")
    requireUnique(schedules.map { it.identity }, "Schedule")
    requireUnique(occurrenceAdjustments.map { it.identity }, "OccurrenceAdjustment")

    when (command) {
      is ScheduleCommand.Create -> createSchedule(
        categories,
        schedules,
        occurrenceAdjustments,
        command.schedule,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.Update -> updateSchedule(
        categories,
        schedules,
        occurrenceAdjustments,
        command.schedule,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.Delete -> deleteSchedule(
        categories,
        schedules,
        occurrenceAdjustments,
        ScheduleIdentity(command.scheduleId.value),
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.CompleteNonRepeating -> completeNonRepeating(
        categories,
        schedules,
        occurrenceAdjustments,
        ScheduleIdentity(command.scheduleId.value),
        command.completed,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.CreateCategory -> createCategory(
        categories,
        schedules,
        occurrenceAdjustments,
        command.category,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.UpdateCategory -> updateCategory(
        categories,
        schedules,
        occurrenceAdjustments,
        command.category,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.ReorderCategories -> reorderCategories(
        categories,
        schedules,
        occurrenceAdjustments,
        command.categories,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.SaveScheduleWithNewCategory -> saveScheduleWithNewCategory(
        categories,
        schedules,
        occurrenceAdjustments,
        command.category,
        command.schedule,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.SaveOccurrenceWithNewCategory -> saveOccurrenceWithNewCategory(
        categories,
        schedules,
        occurrenceAdjustments,
        command.category,
        command.adjustment,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.DeleteCategory -> deleteCategory(
        categories,
        schedules,
        occurrenceAdjustments,
        CategoryIdentity(command.categoryId.value),
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.UpsertOccurrenceAdjustment -> upsertOccurrence(
        categories,
        schedules,
        occurrenceAdjustments,
        command.adjustment,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.DeleteOccurrenceAdjustment -> deleteOccurrence(
        categories,
        schedules,
        occurrenceAdjustments,
        command.scheduleId.value,
        command.recurrenceId,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.SplitSeries -> splitSeries(
        categories,
        schedules,
        occurrenceAdjustments,
        command,
        nowMillis,
        localRevision,
      )

      is ScheduleCommand.DeleteThisAndFollowing -> deleteThisAndFollowing(
        categories,
        schedules,
        occurrenceAdjustments,
        command,
        nowMillis,
        localRevision,
      )

      ScheduleCommand.RequestSync -> ScheduleLocalCommandResult.NoOp
    }
  } catch (rejected: ReducerRejected) {
    ScheduleLocalCommandResult.Rejected(rejected.reason)
  } catch (_: IllegalArgumentException) {
    ScheduleLocalCommandResult.Rejected(ScheduleLocalCommandRejectionReason.INVALID_STATE)
  }

  private fun createSchedule(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    schedule: Schedule,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val identity = ScheduleIdentity(schedule.id.value)
    if (schedules.any { it.identity == identity }) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val resource = schedule.toResource(version = 0, old = null, now = now)
    val state = ScheduleSyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(resource, revision),
    )
    return applied(categories, schedules + state, adjustments)
  }

  private fun updateSchedule(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    schedule: Schedule,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val identity = ScheduleIdentity(schedule.id.value)
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val resource = schedule.toResource(effective.version, effective, now)
    if (resource == effective) return ScheduleLocalCommandResult.NoOp
    val updated = state.replacePending(PendingUpsert(resource, revision))
    val updatedAdjustments = if (effective.recurrence.data != null && resource.recurrence.data == null) {
      // 关闭重复意味着原系列生命周期结束；远端子资源显式删除，本地未上传子资源直接丢弃。以后重新开启是新系列。
      adjustments.mapNotNull { child ->
        if (child.identity.scheduleId != identity.id) {
          child
        } else if (child.remoteSnapshot != null) {
          child.replacePending(PendingDelete(child.identity, now, revision))
        } else {
          null
        }
      }
    } else {
      // 仅改变 RRULE 时保留未命中的单次调整；投影层负责休眠，规则改回来后可自动恢复。
      adjustments
    }
    return applied(categories, schedules.replace(identity) { updated }, updatedAdjustments)
  }

  private fun deleteSchedule(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    identity: ScheduleIdentity,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val childAdjustments = adjustments.filter { it.identity.scheduleId == identity.id }
    // 只有服务端已确认的单次调整需要生成 DELETE；本地尚未上传的调整直接移除。
    val updated = state.replacePending(
      PendingDelete(
        identity,
        localModifiedAt = now,
        localRevision = revision,
      ),
    )
    val updatedAdjustments = if (childAdjustments.isEmpty()) {
      adjustments
    } else {
      adjustments.mapNotNull { child ->
        if (child !in childAdjustments) {
          child
        } else if (child.remoteSnapshot != null) {
          child.replacePending(
            PendingDelete(
              identity = child.identity,
              localModifiedAt = now,
              localRevision = revision,
            ),
          )
        } else {
          null
        }
      }
    }
    return applied(categories, schedules.replace(identity) { updated }, updatedAdjustments)
  }

  private fun completeNonRepeating(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    identity: ScheduleIdentity,
    completed: Boolean,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    if (effective.recurrence.data != null) {
      reject(ScheduleLocalCommandRejectionReason.UNSUPPORTED)
    }
    if (effective.todoState.data == null) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val todoState = if (completed) TodoState.COMPLETED else TodoState.OPEN
    if (effective.todoState.data == todoState) return ScheduleLocalCommandResult.NoOp
    val resource = effective.copy(todoState = AtomicField(todoState, now))
    val updated = state.replacePending(PendingUpsert(resource, revision))
    return applied(categories, schedules.replace(identity) { updated }, adjustments)
  }

  private fun createCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    category: ScheduleCategory,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val identity = CategoryIdentity(category.id.value)
    val normalizedName = category.name.trim()
    if (categories.any { it.identity == identity }) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    if (normalizedName.isEmpty() || hasDuplicateCategoryName(categories, normalizedName)) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    // UI、迁移和测试入口最终都经过 reducer；在这里统一保存 canonical 名称，避免绕过 UI 后
    // Room 暂存首尾空白、再由服务端修正而造成一次无意义的状态跳变。
    val resource = category.copy(name = normalizedName).toResource(version = 0, old = null, now = now)
    val state = CategorySyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(resource, revision),
    )
    return applied(categories + state, schedules, adjustments)
  }

  private fun updateCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    category: ScheduleCategory,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val identity = CategoryIdentity(category.id.value)
    val normalizedName = category.name.trim()
    val state = categories.firstOrNull { it.identity == identity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    if (normalizedName.isEmpty() || hasDuplicateCategoryName(categories, normalizedName, excluding = identity)) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val resource = category.copy(name = normalizedName).toResource(effective.version, effective, now)
    if (resource == effective) return ScheduleLocalCommandResult.NoOp
    val updated = state.replacePending(PendingUpsert(resource, revision))
    return applied(categories.replace(identity) { updated }, schedules, adjustments)
  }

  /**
   * 将一次拖拽后的完整顺序转换为同 revision 的多个 Category pending。
   *
   * 已存在分类只改 sortOrder；尚未落库的固定默认候选按调用方提供的稳定 identity 创建。相同 revision
   * 让 daily bridge 自动把全部变化收敛进同一次普通请求，避免逐条请求暴露中间顺序。
   */
  private fun reorderCategories(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    ordered: List<ScheduleCategory>,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    if (ordered.isEmpty()) return ScheduleLocalCommandResult.NoOp
    requireUnique(ordered.map { it.id }, "Reordered Category")
    var currentCategories = categories
    var changed = false
    ordered.forEachIndexed { index, category ->
      val identity = CategoryIdentity(category.id.value)
      val result = if (currentCategories.any { it.identity == identity }) {
        updateCategory(
          currentCategories,
          schedules,
          adjustments,
          category.copy(sortOrder = index),
          now,
          revision,
        )
      } else {
        createCategory(
          currentCategories,
          schedules,
          adjustments,
          category.copy(revision = 0, sortOrder = index),
          now,
          revision,
        )
      }
      if (result is ScheduleLocalCommandResult.Applied) {
        currentCategories = result.categories
        changed = true
      }
    }
    return if (changed) applied(currentCategories, schedules, adjustments)
    else ScheduleLocalCommandResult.NoOp
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
   * 惰性创建固定默认分类并保存引用它的日程；两个 pending 使用同一 revision，由一次请求一起上传。
   *
   * 分类必须尚不存在，日程可为创建或更新。该能力只服务于默认分类首次使用，通用分类管理不走此入口。
   */
  private fun saveScheduleWithNewCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    category: ScheduleCategory,
    schedule: Schedule,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    if (schedule.categoryId != category.id) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val withCategory = createCategory(categories, schedules, adjustments, category, now, revision)
    if (withCategory !is ScheduleLocalCommandResult.Applied) return withCategory

    val scheduleIdentity = ScheduleIdentity(schedule.id.value)
    val saved = if (schedules.any { it.identity == scheduleIdentity }) {
      updateSchedule(
        withCategory.categories,
        withCategory.schedules,
        withCategory.occurrenceAdjustments,
        schedule,
        now,
        revision,
      )
    } else {
      createSchedule(
        withCategory.categories,
        withCategory.schedules,
        withCategory.occurrenceAdjustments,
        schedule,
        now,
        revision,
      )
    }
    if (saved !is ScheduleLocalCommandResult.Applied) return saved

    return saved
  }

  /**
   * 惰性创建固定默认分类并保存引用它的单次调整；两个 pending 使用同一 revision 一起上传。
   *
   * 只有明确 REPLACE 到该分类的单次调整才能走此入口，避免创建与本次调整无关的孤立分类。
   */
  private suspend fun saveOccurrenceWithNewCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    category: ScheduleCategory,
    adjustment: ScheduleOccurrenceAdjustment,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    if (adjustment.patch?.categoryId != UiFieldPatch.Replace(category.id)) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val withCategory = createCategory(categories, schedules, adjustments, category, now, revision)
    if (withCategory !is ScheduleLocalCommandResult.Applied) return withCategory

    return upsertOccurrence(
      withCategory.categories,
      withCategory.schedules,
      withCategory.occurrenceAdjustments,
      adjustment,
      now,
      revision,
    )
  }

  private fun deleteCategory(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    identity: CategoryIdentity,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val state = categories.firstOrNull { it.identity == identity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val categoryInUse = schedules.any {
      it.remoteSnapshot?.resource?.categoryId?.data == identity.id ||
          it.effectiveResource()?.categoryId?.data == identity.id
    } || adjustments.any {
      // remote 引用即使被本地 pending 隐藏，在服务端确认前仍会阻止分类删除。
      it.remoteSnapshot?.resource?.categoryId?.data == FieldPatch.Replace(identity.id) ||
          it.effectiveResource()?.categoryId?.data == FieldPatch.Replace(identity.id)
    }
    if (categoryInUse) {
      // remote 即使被本地 pending DELETE 隐藏，确认前仍会让服务端拒绝分类删除，因此本地先拒绝。
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val updated = state.replacePending(
      PendingDelete(identity, localModifiedAt = now, localRevision = revision),
    )
    return applied(categories.replace(identity) { updated }, schedules, adjustments)
  }

  private suspend fun upsertOccurrence(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    adjustment: ScheduleOccurrenceAdjustment,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val originalOccurrenceDate = adjustment.recurrenceId.originalDateTime.date.toUtcDaySlot()
    val existing = adjustments.firstOrNull {
      it.identity.scheduleId == adjustment.scheduleId.value &&
          it.identity.originalOccurrenceDate == originalOccurrenceDate
    }
    val identity = existing?.identity ?: newOccurrenceIdentity(
      scheduleId = adjustment.scheduleId.value,
      originalOccurrenceDate = originalOccurrenceDate,
    )
    val effective = existing?.effectiveResource()
    val resource = adjustment.toResource(identity, effective?.version ?: 0, effective, now)
    if (resource == effective) return ScheduleLocalCommandResult.NoOp
    val updated = if (existing == null) {
      OccurrenceAdjustmentSyncState(
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
      if (existing == null) adjustments + updated else adjustments.replace(identity) { updated },
    )
  }

  private fun deleteOccurrence(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    scheduleId: String,
    recurrenceId: RecurrenceId,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val originalOccurrenceDate = recurrenceId.originalDateTime.date.toUtcDaySlot()
    val state = adjustments.firstOrNull {
      it.identity.scheduleId == scheduleId &&
          it.identity.originalOccurrenceDate == originalOccurrenceDate
    }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    if (state.remoteSnapshot == null) {
      // 从未上传成功的本地单次调整无需产生服务端 DELETE，直接移除这条临时状态。
      return applied(categories, schedules, adjustments.filterNot { it.identity == state.identity })
    }
    val updated = state.replacePending(
      PendingDelete(state.identity, localModifiedAt = now, localRevision = revision),
    )
    return applied(categories, schedules, adjustments.replace(state.identity) { updated })
  }

  /**
   * 在同一次本地事务中拆分重复系列：更新旧系列、创建新系列，并迁移边界后的单次调整。
   *
   * 边界 occurrence 的有效内容已经被提升为新系列字段，因此只删除旧 identity、不复制该调整；更晚的有效调整
   * 保留 originalOccurrenceDate 并改挂新 scheduleId。仅存在本地 CREATE 的调整可直接移除，已有远端快照的调整
   * 必须显式 DELETE。所有成员共享 [revision]，客户端将其拆成普通修改、新增和删除独立提交。
   */
  private suspend fun splitSeries(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    command: ScheduleCommand.SplitSeries,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val previousIdentity = ScheduleIdentity(command.previousSchedule.id.value)
    val followingIdentity = ScheduleIdentity(command.followingSchedule.id.value)
    if (previousIdentity == followingIdentity || command.previousSchedule.recurrence == null ||
      command.followingSchedule.recurrence == null
    ) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val previousState = schedules.firstOrNull { it.identity == previousIdentity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val previousEffective = previousState.effectiveResource()
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    if (schedules.any { it.identity == followingIdentity }) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val boundaryDate = command.recurrenceId.originalDateTime.date.toUtcDaySlot()
    var nextCategories = categories
    command.newCategory?.let { category ->
      if (command.followingSchedule.categoryId != category.id) {
        reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
      }
      val categoryResult = createCategory(
        nextCategories, schedules, adjustments, category, now, revision,
      ) as? ScheduleLocalCommandResult.Applied
        ?: reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
      nextCategories = categoryResult.categories
    }

    val previousResource = command.previousSchedule.toResource(
      version = previousEffective.version,
      old = previousEffective,
      now = now,
    )
    val followingResource = command.followingSchedule.toResource(version = 0, old = null, now = now)
    val nextSchedules = schedules.replace(previousIdentity) { state ->
      state.replacePending(PendingUpsert(previousResource, revision))
    } + ScheduleSyncState(
      identity = followingIdentity,
      remoteSnapshot = null,
      pending = PendingUpsert(followingResource, revision),
    )

    val nextAdjustments = migrateFollowingAdjustments(
      adjustments = adjustments,
      previousScheduleId = previousIdentity.id,
      followingScheduleId = followingIdentity.id,
      boundaryDate = boundaryDate,
      now = now,
      revision = revision,
    )
    return applied(nextCategories, nextSchedules, nextAdjustments)
  }

  /**
   * 删除当前及后续 occurrence：更新截断后的父系列，并同步删除边界及更晚的单次调整。
   *
   * 本地尚未上传的调整直接从双快照集合移除；已有 remote 的调整生成无版本 DELETE。系列更新与所有 DELETE
   * 使用同一 revision 进入一次请求，但服务端会按资源独立返回成功或拒绝。
   */
  private fun deleteThisAndFollowing(
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    adjustments: List<OccurrenceAdjustmentSyncState>,
    command: ScheduleCommand.DeleteThisAndFollowing,
    now: Long,
    revision: Long,
  ): ScheduleLocalCommandResult {
    val identity = ScheduleIdentity(command.previousSchedule.id.value)
    val state = schedules.firstOrNull { it.identity == identity }
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    val effective = state.effectiveResource()
      ?: reject(ScheduleLocalCommandRejectionReason.NOT_FOUND)
    if (command.previousSchedule.recurrence == null) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    val boundaryDate = command.recurrenceId.originalDateTime.date.toUtcDaySlot()
    val previousResource = command.previousSchedule.toResource(effective.version, effective, now)
    val nextSchedules = schedules.replace(identity) { current ->
      current.replacePending(PendingUpsert(previousResource, revision))
    }
    val nextAdjustments = adjustments.mapNotNull { adjustment ->
      if (adjustment.identity.scheduleId != identity.id || adjustment.identity.originalOccurrenceDate < boundaryDate) {
        adjustment
      } else if (adjustment.remoteSnapshot == null) {
        null
      } else {
        adjustment.replacePending(
          PendingDelete(
            identity = adjustment.identity,
            localModifiedAt = now,
            localRevision = revision,
          )
        )
      }
    }
    return applied(categories, nextSchedules, nextAdjustments)
  }

  /**
   * 把拆分边界后的单次调整从旧 schedule identity 迁移到新系列。
   *
   * 边界调整不复制；它的有效字段已成为新系列字段。更晚调整只有在当前 effective 仍 live 时才创建新资源，
   * pending DELETE 不会复活。旧 identity 若已存在远端资源则保留为同批 DELETE，否则直接移除本地临时行。
   */
  private suspend fun migrateFollowingAdjustments(
    adjustments: List<OccurrenceAdjustmentSyncState>,
    previousScheduleId: String,
    followingScheduleId: String,
    boundaryDate: Long,
    now: Long,
    revision: Long,
  ): List<OccurrenceAdjustmentSyncState> {
    val result = mutableListOf<OccurrenceAdjustmentSyncState>()
    for (state in adjustments) {
      if (state.identity.scheduleId != previousScheduleId || state.identity.originalOccurrenceDate < boundaryDate) {
        result += state
        continue
      }
      val effective = state.effectiveResource()
      if (state.remoteSnapshot != null) {
        result +=
          state.replacePending(
            PendingDelete(
              identity = state.identity,
              localModifiedAt = now,
              localRevision = revision,
            )
          )
      }
      if (state.identity.originalOccurrenceDate > boundaryDate && effective != null) {
        val followingAdjustmentIdentity = newOccurrenceIdentity(
          scheduleId = followingScheduleId,
          originalOccurrenceDate = state.identity.originalOccurrenceDate,
        )
        result +=
          OccurrenceAdjustmentSyncState(
            identity = followingAdjustmentIdentity,
            remoteSnapshot = null,
            pending = PendingUpsert(
              resource = effective.copy(
                identity = followingAdjustmentIdentity,
                remoteId = null,
                version = 0,
              ),
              localRevision = revision,
            ),
          )
      }
    }
    return result
  }

  private fun Schedule.toResource(
    version: Long,
    old: ScheduleResource?,
    now: Long,
  ): ScheduleResource {
    // null 是“未分组”的正式协议值，不能在本地 reducer 中把它误判为不支持。
    val category = categoryId?.value
    val timingValue = timing.toWireTiming()
    val recurrenceValue = recurrence?.toWireRecurrence(
      timing = timing,
      // 活跃系列继续使用旧 anchor；规则已清除时重新启用属于新序列，必须按当前 timing 建立日期轴。
      stableAnchorDate = old?.recurrence?.data?.anchorDate,
    )
    val reminderValue = reminder?.toWireReminder()
    if (old != null && old.kind != kind.toWire()) {
      reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    }
    return ScheduleResource(
      identity = ScheduleIdentity(id.value),
      version = version,
      kind = kind.toWire(),
      title = atomic(title, old?.title, now),
      description = atomic(description, old?.description, now),
      categoryId = atomic(category, old?.categoryId, now),
      timing = atomic(timingValue, old?.timing, now),
      recurrence = atomic(recurrenceValue, old?.recurrence, now),
      reminder = atomic(reminderValue, old?.reminder, now),
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
    remoteId = old?.remoteId,
    version = version,
    name = atomic(name, old?.name, now),
    // `null` 是“未设置自定义颜色”的协议事实，不能归一化为空串。
    color = atomic(color, old?.color, now),
    sortOrder = atomic(sortOrder.toLong(), old?.sortOrder, now),
  )

  private fun ScheduleOccurrenceAdjustment.toResource(
    identity: OccurrenceAdjustmentIdentity,
    version: Long,
    old: OccurrenceAdjustmentResource?,
    now: Long,
  ): OccurrenceAdjustmentResource {
    val patchValue = patch ?: OccurrencePatch()
    val dateValue = patchValue.date.toWireDatePatch()
    val timeValue = patchValue.time.toWireTimePatch()
    val titleValue = patchValue.title.toWireTitlePatch()
    val descriptionValue = patchValue.description.toWireStringPatch()
    val categoryValue = patchValue.categoryId.toWireCategoryPatch()
    val reminderValue = patchValue.reminder.toWireReminderPatch()
    return OccurrenceAdjustmentResource(
      identity = identity,
      remoteId = old?.remoteId,
      version = version,
      status = atomic(status.toWire(), old?.status, now),
      date = atomic(dateValue, old?.date, now),
      time = atomic(timeValue, old?.time, now),
      title = atomic(titleValue, old?.title, now),
      description = atomic(descriptionValue, old?.description, now),
      categoryId = atomic(categoryValue, old?.categoryId, now),
      reminder = atomic(reminderValue, old?.reminder, now),
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
      TimingInput(
        kind = TimingKind.ALL_DAY,
        date = date.toUtcDaySlot(),
      )
    }

    ScheduleTiming.Unscheduled -> TimingInput(kind = TimingKind.UNSCHEDULED)
  }

  private fun RecurrenceRule.toWireRecurrence(
    timing: ScheduleTiming,
    stableAnchorDate: Long?,
  ): RecurrenceInput {
    // 先在端内边界拒绝会被当前协议忽略的 selector，避免静默丢字段后仍把操作标记为成功。
    val hasUnsupportedSelectors = when (frequency) {
      UiRecurrenceFrequency.DAILY ->
        byWeekDays.isNotEmpty() || byMonthDays.isNotEmpty() || byMonths.isNotEmpty()

      UiRecurrenceFrequency.WEEKLY ->
        byMonthDays.isNotEmpty() || byMonths.isNotEmpty()

      UiRecurrenceFrequency.MONTHLY ->
        byWeekDays.isNotEmpty() || byMonths.isNotEmpty()

      UiRecurrenceFrequency.YEARLY ->
        byWeekDays.isNotEmpty() || byMonthDays.isEmpty() != byMonths.isEmpty()
    }
    if (hasUnsupportedSelectors) {
      reject(ScheduleLocalCommandRejectionReason.UNSUPPORTED)
    }
    val frequency = when (frequency) {
      UiRecurrenceFrequency.DAILY -> RecurrenceFrequency.DAILY
      UiRecurrenceFrequency.WEEKLY -> RecurrenceFrequency.WEEKLY
      UiRecurrenceFrequency.MONTHLY -> RecurrenceFrequency.MONTHLY
      UiRecurrenceFrequency.YEARLY -> RecurrenceFrequency.YEARLY
    }
    val timingAnchor = when (timing) {
      is ScheduleTiming.Timed -> timing.start.date
      is ScheduleTiming.Deadline -> timing.due.date
      is ScheduleTiming.AllDay -> timing.date
      ScheduleTiming.Unscheduled -> reject(ScheduleLocalCommandRejectionReason.UNSUPPORTED)
    }
    // 同一 Schedule identity 的 recurrence 日期轴首次启用后保持稳定；当前 timing 可以相对它产生正负偏移。
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
        RecurrenceFrequency.DAILY,
        RecurrenceFrequency.MONTHLY,
        RecurrenceFrequency.YEARLY,
          -> emptySet()

        RecurrenceFrequency.WEEKLY -> {
          val anchorWeekday = requireNotNull(IsoWeekDay.fromIsoNumber(anchor.dayOfWeekNumber))
          byWeekDays.ifEmpty { setOf(anchorWeekday) }.map { it.toWire() }.toSet()
        }
      },
      monthDays = when (frequency) {
        RecurrenceFrequency.MONTHLY,
        RecurrenceFrequency.YEARLY,
          -> byMonthDays.ifEmpty { setOf(anchor.dayOfMonth) }

        else -> emptySet()
      },
      months = if (frequency == RecurrenceFrequency.YEARLY) {
        byMonths.ifEmpty { setOf(anchor.monthNumber) }
      } else {
        emptySet()
      },
    )
  }

  /** 协议只保存一个提醒及其提前分钟数。 */
  private fun ScheduleReminder.toWireReminder(): ReminderInput =
    ReminderInput(minutesBefore = offsetMinutes)

  private fun UiFieldPatch<String>.toWireTitlePatch(): FieldPatch<String> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> reject(ScheduleLocalCommandRejectionReason.UNSUPPORTED)
    is UiFieldPatch.Replace -> FieldPatch.Replace(value)
  }

  /** 单次日期不可清空；REPLACE 始终上传 UTC 午夜日期槽。 */
  private fun UiFieldPatch<Date>.toWireDatePatch(): FieldPatch<Long> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
    is UiFieldPatch.Replace -> FieldPatch.Replace(value.toUtcDaySlot())
  }

  /** 单次时间不可清空且不携带日期；时区只在客户端领域值中保留。 */
  private fun UiFieldPatch<UiOccurrenceTime>.toWireTimePatch(): FieldPatch<OccurrenceTimeInput> =
    when (this) {
      UiFieldPatch.Inherit -> FieldPatch.Inherit
      UiFieldPatch.Clear -> reject(ScheduleLocalCommandRejectionReason.INVALID_STATE)
      is UiFieldPatch.Replace -> FieldPatch.Replace(value.toWireOccurrenceTime())
    }

  /** 将客户端单次时间形态转换为不含日期的同步值。 */
  private fun UiOccurrenceTime.toWireOccurrenceTime(): OccurrenceTimeInput = when (this) {
    is UiOccurrenceTime.TimeRange -> OccurrenceTimeInput(
      kind = OccurrenceTimeKind.TIME_RANGE,
      startMinuteOfDay = startMinuteOfDay,
      durationMinutes = durationMinutes,
    )
    is UiOccurrenceTime.TimePoint -> OccurrenceTimeInput(
      kind = OccurrenceTimeKind.TIME_POINT,
      minuteOfDay = minuteOfDay,
    )
    UiOccurrenceTime.AllDay -> OccurrenceTimeInput(kind = OccurrenceTimeKind.ALL_DAY)
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

  private fun UiFieldPatch<ScheduleReminder>.toWireReminderPatch():
      FieldPatch<ReminderInput> = when (this) {
    UiFieldPatch.Inherit -> FieldPatch.Inherit
    UiFieldPatch.Clear -> FieldPatch.Clear
    is UiFieldPatch.Replace -> FieldPatch.Replace(value.toWireReminder())
  }

  /**
   * Occurrence identity 只取 recurrenceId 原始墙上时间的日期并转 UTC 日期槽；
   * timeZoneId、allDay 与后续 timing 移动都不能改变该 identity。
   */
  private suspend fun newOccurrenceIdentity(
    scheduleId: String,
    originalOccurrenceDate: Long,
  ): OccurrenceAdjustmentIdentity = OccurrenceAdjustmentIdentity(
    localId = uuidGenerator.nextString(),
    scheduleId = scheduleId,
    originalOccurrenceDate = originalOccurrenceDate,
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
    adjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleLocalCommandResult.Applied =
    ScheduleLocalCommandResult.Applied(
      categories = categories,
      schedules = schedules,
      occurrenceAdjustments = adjustments,
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

  private fun List<OccurrenceAdjustmentSyncState>.replace(
    identity: OccurrenceAdjustmentIdentity,
    transform: (OccurrenceAdjustmentSyncState) -> OccurrenceAdjustmentSyncState,
  ): List<OccurrenceAdjustmentSyncState> = map { if (it.identity == identity) transform(it) else it }
}

private class ReducerRejected(
  val reason: ScheduleLocalCommandRejectionReason,
) : IllegalStateException()

private fun reject(reason: ScheduleLocalCommandRejectionReason): Nothing =
  throw ReducerRejected(reason)
