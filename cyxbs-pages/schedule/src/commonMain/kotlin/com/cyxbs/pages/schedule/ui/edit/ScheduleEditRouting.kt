package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.data.repository.ScheduleIdGenerators
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.recurrence.SeriesSplitter
import com.cyxbs.pages.schedule.domain.repository.*
import com.cyxbs.pages.schedule.ui.model.toNewDomain
import com.cyxbs.pages.schedule.ui.model.toUpdatedDomain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 按 [scope] 将编辑状态路由为新建、整系列更新、单实例例外或“本次及以后”拆分命令。
 *
 * @param recurrenceId 重复实例的原始本地时间锚点；[EditScope.THIS_ONLY] 与
 * [EditScope.THIS_AND_FOLLOWING] 必须非空，否则立即失败，避免误改整个系列。
 * @param idGenerators 新建时生成正式 ID；编辑状态中的 draft ID 只是校验占位符。
 * @param clock 为命令及例外提供统一时间戳。
 * @param newCategory 所选固定默认分类尚不存在时提供完整分类；仅在整系列 CREATE/PATCH 时与日程同批提交。
 *
 * 此函数会挂起并写入仓库；无实际字段变化时不发命令。Compose 层不会接触 record/DTO。
 */
suspend fun ScheduleRepository.applyScheduleEdit(
  state: EditScheduleModelState,
  scope: EditScope,
  recurrenceId: RecurrenceId?,
  idGenerators: ScheduleIdGenerators,
  clock: Clock,
  newCategory: ScheduleCategory? = null,
) {
  val now = clock.now()
  val origin = state.origin

  if (origin == null) {
    val draft = state.toDraft().copy(id = idGenerators.scheduleId())
    val created = draft.toNewDomain(now)
    execute(
      newCategory?.let { ScheduleCommand.SaveScheduleWithNewCategory(it, created) }
        ?: ScheduleCommand.Create(created),
    )
    return
  }
  val editedDraft = state.toDraft()
  // 未排期与提醒在领域上不可共存；timing 的显式用户修改需原子清理 payload，但不得把提醒控件标记为 dirty。
  val effectiveDraft = if (state.isOccurrenceTimingChanged && editedDraft.timing == ScheduleTiming.Unscheduled) {
    editedDraft.copy(reminders = emptyList())
  } else {
    editedDraft
  }
  val edited = effectiveDraft.toUpdatedDomain(origin, now)
  require(!(state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled && edited.recurrence != null)) {
    "recurring schedule cannot become unscheduled without removing recurrence"
  }
  val seriesEdited = if (state.initialOccurrence == null) edited else origin.copy(
    title = if (state.isOccurrenceTitleChanged) edited.title else origin.title,
    description = if (state.isOccurrenceDescriptionChanged) edited.description else origin.description,
    categoryId = if (state.isOccurrenceCategoryChanged) edited.categoryId else origin.categoryId,
    timing = if (state.isOccurrenceTimingChanged) {
      rebaseSeriesTiming(origin.timing, state.initialOccurrence.timing, edited.timing)
    } else {
      origin.timing
    },
    recurrence = if (state.isSeriesRecurrenceChanged) edited.recurrence else origin.recurrence,
    reminders = if (state.isOccurrenceRemindersChanged ||
      (state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled)
    ) edited.reminders else origin.reminders,
    todoState = if (state.isSeriesRelationChanged) edited.todoState else origin.todoState,
    linkedToCourse = if (state.isSeriesRelationChanged) edited.linkedToCourse else origin.linkedToCourse,
    updatedAt = now,
  )
  val relationEdited = origin.copy(
    todoState = edited.todoState,
    linkedToCourse = edited.linkedToCourse,
    updatedAt = now,
  )
  // 从 occurrence 打开的表单分别追踪实例字段和 RRULE；系列 payload 只合并实际 dirty 字段，禁止提升其他单例覆盖。
  val hasSeriesContentChanges = if (state.initialOccurrence != null) {
    state.isOccurrenceFieldsChanged || state.isSeriesRecurrenceChanged
  } else {
    edited.copy(todoState = origin.todoState, linkedToCourse = origin.linkedToCourse) != origin
  }
  val hasSeriesScopeChanges = hasSeriesContentChanges || state.isSeriesRelationChanged
  when (scope) {
    EditScope.ALL -> {
      if (hasSeriesScopeChanges) {
        execute(
          newCategory?.let { ScheduleCommand.SaveScheduleWithNewCategory(it, seriesEdited) }
            ?: ScheduleCommand.Update(seriesEdited),
        )
      }
    }
    EditScope.THIS_ONLY -> {
      // 关联关系属于整个系列；“仅本次”只约束标题、时间等 occurrence 字段，不能生成单次关联例外。
      if (state.isSeriesRelationChanged) execute(ScheduleCommand.Update(relationEdited))
      // RRULE 只属于系列；仅修改 recurrence 后选择 THIS_ONLY 按 no-op 处理，不创建空 occurrence exception。
      if (!state.isOccurrenceFieldsChanged) return
      require(!(state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled)) {
        "THIS_ONLY occurrence timing cannot become unscheduled"
      }
      requireNotNull(recurrenceId).let { id ->
        val existing = snapshot.value.exceptions.firstOrNull {
          it.scheduleId == origin.id && it.recurrenceId == id
        }
        val occurrence = state.toOccurrencePatch(origin, id, now, existing?.patch)
        if (occurrence.patch == OccurrencePatch()) {
          if (existing?.status == OccurrenceStatus.ACTIVE) {
            execute(ScheduleCommand.DeleteOccurrenceException(origin.id, id))
          } else if (existing != null) {
            // 清除全部字段覆盖时仍保留完成/取消状态；状态与 patch 是相互正交的业务事实。
            execute(ScheduleCommand.UpsertOccurrenceException(existing.copy(patch = null, updatedAt = now)))
          }
          return@let
        }
        // 再次编辑单实例视为恢复为 ACTIVE；沿用既有例外的 revision/createdAt，避免把同步资源误当成新记录。
        execute(ScheduleCommand.UpsertOccurrenceException(
          occurrence.copy(
            revision = existing?.revision ?: 0,
            createdAt = existing?.createdAt ?: now,
          )
        ))
      }
    }
    EditScope.THIS_AND_FOLLOWING -> {
      val boundary = requireNotNull(recurrenceId)
      // 首次 occurrence 的“此次及以后”等价于整个系列；即使旧版调用方仍传入该范围也不能产生空旧系列。
      if (!SeriesSplitter.canSplitAt(origin, boundary)) {
        if (hasSeriesScopeChanges) {
          execute(
            newCategory?.let { ScheduleCommand.SaveScheduleWithNewCategory(it, seriesEdited) }
              ?: ScheduleCommand.Update(seriesEdited),
          )
        }
        return
      }
      // 只修改系列级关联关系时无需拆分；关系对原系列所有 occurrence 一次生效。
      if (!hasSeriesContentChanges) {
        if (state.isSeriesRelationChanged) execute(ScheduleCommand.Update(relationEdited))
        return
      }
      val split = SeriesSplitter.split(
        schedule = origin,
        exceptions = snapshot.value.exceptions.filter { it.scheduleId == origin.id },
        boundary = boundary,
        followingId = idGenerators.scheduleId(),
      )
      val previous = split.previousSchedule.copy(
        todoState = edited.todoState?.let { ScheduleTodoState.PENDING },
        linkedToCourse = edited.linkedToCourse,
        updatedAt = now,
      )
      val following = split.followingSchedule.copy(
        // 新系列把当前 occurrence 的有效内容提升为系列值；边界自身的旧 exception 随后会被移除。
        title = edited.title,
        description = edited.description,
        categoryId = edited.categoryId,
        timing = edited.timing,
        recurrence = if (state.isSeriesRecurrenceChanged) edited.recurrence
        else split.followingSchedule.recurrence,
        reminders = edited.reminders,
        todoState = edited.todoState?.let { ScheduleTodoState.PENDING },
        linkedToCourse = edited.linkedToCourse,
        createdAt = now,
        updatedAt = now,
      )
      execute(ScheduleCommand.SplitSeries(previous, following, boundary, newCategory))
    }
  }
}

/**
 * 切换某次日程的完成态。
 *
 * 非重复日程直接更新系列完成态；重复日程使用稳定 [recurrenceId] 写 occurrence 例外，并保留已有例外中的
 * 内容覆盖、revision 与创建时间。该操作会立即写入本地优先仓库，不经过编辑表单的范围选择。
 */
suspend fun ScheduleRepository.applyScheduleCompletion(
  scheduleId: ScheduleId,
  recurrenceId: RecurrenceId?,
  completed: Boolean,
  clock: Clock,
) {
  if (recurrenceId == null) {
    execute(ScheduleCommand.CompleteNonRepeating(scheduleId, completed))
    return
  }
  val now = clock.now()
  val existing = snapshot.value.exceptions.firstOrNull {
    it.scheduleId == scheduleId && it.recurrenceId == recurrenceId
  }
  execute(
    ScheduleCommand.UpsertOccurrenceException(
      existing?.copy(
        status = if (completed) OccurrenceStatus.COMPLETED else OccurrenceStatus.ACTIVE,
        updatedAt = now,
      ) ?: ScheduleOccurrenceException(
        scheduleId = scheduleId,
        recurrenceId = recurrenceId,
        revision = 0,
        status = if (completed) OccurrenceStatus.COMPLETED else OccurrenceStatus.ACTIVE,
        patch = null,
        createdAt = now,
        updatedAt = now,
      ),
    ),
  )
}

/**
 * 按范围删除日程：单实例写入 CANCELLED 例外，“本次及以后”按原始实例锚点截断，全部则删除系列。
 *
 * @param recurrenceId 单实例和后续范围的稳定锚点；对应范围下为空会立即失败。
 * @param clock 创建或更新单实例墓碑时使用，确保删除能参与后续同步。
 *
 * 该函数会挂起并写仓库；单实例删除不会物理删除系列，也不会丢失已有例外的 revision。
 */
suspend fun ScheduleRepository.applyScheduleDelete(
  scheduleId: ScheduleId,
  scope: EditScope,
  recurrenceId: RecurrenceId?,
  clock: Clock,
) {
  when (scope) {
    EditScope.ALL -> execute(ScheduleCommand.Delete(scheduleId))
    EditScope.THIS_ONLY -> {
      val id = requireNotNull(recurrenceId)
      val now = clock.now()
      val existing = snapshot.value.exceptions.firstOrNull {
        it.scheduleId == scheduleId && it.recurrenceId == id
      }
      execute(ScheduleCommand.UpsertOccurrenceException(
        existing?.copy(status = OccurrenceStatus.CANCELLED, updatedAt = now)
          ?: ScheduleOccurrenceException(
            scheduleId, id, 0, OccurrenceStatus.CANCELLED, null, now, now,
          )
      ))
    }
    EditScope.THIS_AND_FOLLOWING -> {
      val boundary = requireNotNull(recurrenceId)
      val schedule = snapshot.value.schedules.firstOrNull { it.id == scheduleId }
        ?: return
      if (!SeriesSplitter.canSplitAt(schedule, boundary)) {
        execute(ScheduleCommand.Delete(scheduleId))
      } else {
        execute(ScheduleCommand.DeleteThisAndFollowing(
          previousSchedule = SeriesSplitter.truncateBefore(schedule, boundary),
          recurrenceId = boundary,
        ))
      }
    }
  }
}

/**
 * 把中间 occurrence 的实际移动量应用到父系列锚点，同时保留编辑后的 timing 类型、时长与时区。
 *
 * 直接把所选 occurrence 的绝对日期写回父系列会让 DTSTART 跳到系列中段并丢失早期实例；这里仅迁移用户相对
 * 当前 occurrence 输入的墙上时间偏移。UTC 仅作为无时区分钟坐标使用，避免 DST 改变用户输入的日期/时分差。
 */
private fun rebaseSeriesTiming(
  parent: ScheduleTiming,
  initialOccurrence: ScheduleTiming,
  editedOccurrence: ScheduleTiming,
): ScheduleTiming {
  val parentStart = parent.effectiveStart()
  val initialStart = initialOccurrence.effectiveStart()
  val editedStart = editedOccurrence.effectiveStart()
  val deltaMinutes = (
    editedStart.toLocalDateTime().toInstant(TimeZone.UTC) -
      initialStart.toLocalDateTime().toInstant(TimeZone.UTC)
    ).inWholeMinutes.toInt()
  val rebasedStart = parentStart.plusMinutes(deltaMinutes)
  return when (editedOccurrence) {
    is ScheduleTiming.Timed -> editedOccurrence.copy(start = rebasedStart)
    is ScheduleTiming.Deadline -> editedOccurrence.copy(due = rebasedStart)
    is ScheduleTiming.AllDay -> editedOccurrence.copy(startDate = rebasedStart.date)
    ScheduleTiming.Unscheduled -> error("recurring schedule cannot become unscheduled")
  }
}

/** 返回 timing 的墙上开始；未排期值没有可供重复系列迁移的锚点。 */
private fun ScheduleTiming.effectiveStart() = when (this) {
  is ScheduleTiming.Timed -> start
  is ScheduleTiming.Deadline -> due
  is ScheduleTiming.AllDay -> com.cyxbs.components.config.time.MinuteTimeDate(startDate, 0, 0)
  ScheduleTiming.Unscheduled -> error("unscheduled timing has no occurrence start")
}

/**
 * 把实例编辑结果压缩成 sparse patch，并保留 [recurrenceId] 作为不可变的 occurrence identity。
 *
 * 未触碰字段保留 existing patch；实际改动字段保存本次 occurrence 的显式结果。timing 使用完整联合原子替换，
 * 实例标识仍指向原始展开锚点，从而保证深链、完成状态和后续同步仍能定位同一次 occurrence。
 * 当前 UI 没有“恢复继承”动作，因此改成与 parent 相同的非空值也保存 Replace；可清空字段的空值保存 Clear。
 */
private fun EditScheduleModelState.toOccurrencePatch(
  origin: Schedule,
  recurrenceId: RecurrenceId,
  now: Instant,
  existingPatch: OccurrencePatch?,
): ScheduleOccurrenceException {
  val edited = toDraft()
  /**
   * 未实际触碰的字段原样保留 [existingPatch]，即使其当前投影恰与 parent 相等；否则编辑另一字段会意外
   * 把既有 Clear/Replace 降成 Inherit。字段一旦真实变化，当前 UI 没有“恢复继承”动作，因此按用户提交的
   * occurrence 值编码：等于 parent 也保存 Replace（可清空值则空值保存 Clear），避免未来 parent 修改改变结果。
   */
  fun <T : Any> preserveOrBuild(
    changed: Boolean,
    existing: FieldPatch<T>,
    build: () -> FieldPatch<T>,
  ): FieldPatch<T> = if (changed) build() else existing

  val existing = existingPatch ?: OccurrencePatch()
  val patch = OccurrencePatch(
    timing = preserveOrBuild(isOccurrenceTimingChanged, existing.timing) {
      FieldPatch.Replace(edited.timing)
    },
    title = preserveOrBuild(isOccurrenceTitleChanged, existing.title) {
      FieldPatch.Replace(edited.title)
    },
    description = preserveOrBuild(isOccurrenceDescriptionChanged, existing.description) {
      if (edited.description.isEmpty()) FieldPatch.Clear else FieldPatch.Replace(edited.description)
    },
    categoryId = preserveOrBuild(isOccurrenceCategoryChanged, existing.categoryId) {
      if (edited.categoryId == null) FieldPatch.Clear else FieldPatch.Replace(edited.categoryId)
    },
    reminders = preserveOrBuild(
      isOccurrenceRemindersChanged || (isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled),
      existing.reminders,
    ) {
      if (edited.reminders.isEmpty()) FieldPatch.Clear else FieldPatch.Replace(edited.reminders)
    },
  )
  return ScheduleOccurrenceException(origin.id, recurrenceId, 0, OccurrenceStatus.ACTIVE, patch, now, now)
}
