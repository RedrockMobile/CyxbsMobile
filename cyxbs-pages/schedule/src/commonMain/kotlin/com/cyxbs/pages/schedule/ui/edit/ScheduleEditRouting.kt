package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.data.repository.ScheduleIdGenerators
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
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
 *
 * @return 新建时返回已经写入仓库的日程，供创建弹窗原位切换到详情；编辑已有日程时返回 null。
 */
suspend fun ScheduleRepository.applyScheduleEdit(
  state: EditScheduleModelState,
  scope: EditScope,
  recurrenceId: RecurrenceId?,
  idGenerators: ScheduleIdGenerators,
  clock: Clock,
  newCategory: ScheduleCategory? = null,
) : Schedule? {
  val created = applyScheduleFieldEdit(state, scope, recurrenceId, idGenerators, clock, newCategory)
  val scheduleId = state.origin?.id ?: return created
  // 单次调整属于同一编辑会话，但不与 scope 混为一个 occurrence patch；表单保存成功后再逐条恢复继承。
  state.occurrenceRestoreIds.forEach { id ->
    restoreOccurrenceAdjustment(scheduleId, id)
  }
  return null
}

/** 执行标题、时间、重复规则和关联关系等原有表单字段保存，不处理暂存的单次调整还原。 */
private suspend fun ScheduleRepository.applyScheduleFieldEdit(
  state: EditScheduleModelState,
  scope: EditScope,
  recurrenceId: RecurrenceId?,
  idGenerators: ScheduleIdGenerators,
  clock: Clock,
  newCategory: ScheduleCategory? = null,
) : Schedule? {
  val now = clock.now()
  val origin = state.origin

  if (origin == null) {
    val draft = state.toDraft().copy(id = idGenerators.scheduleId())
    val created = draft.toNewDomain(now)
    execute(
      newCategory?.let { ScheduleCommand.SaveScheduleWithNewCategory(it, created) }
        ?: ScheduleCommand.Create(created),
    )
    return created
  }
  val editedDraft = state.toDraft()
  // 未排期与提醒在领域上不可共存；timing 的显式用户修改需原子清理 payload，但不得把提醒控件标记为 dirty。
  val effectiveDraft = if (state.isOccurrenceTimingChanged && editedDraft.timing == ScheduleTiming.Unscheduled) {
    editedDraft.copy(reminder = null)
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
      when {
        // 无时间没有 occurrence 起点，不能走时间偏移重算；它是整个系列的明确最终 timing。
        edited.timing == ScheduleTiming.Unscheduled -> ScheduleTiming.Unscheduled
        // 非重复日程从 occurrence 入口打开时也会携带 initialOccurrence。旧清单可能没有日期，首次
        // 补充时间应直接采用表单结果，不能拿 Unscheduled 计算一个不存在的系列相对位移。
        origin.recurrence == null -> edited.timing
        else -> rebaseSeriesTiming(origin.timing, state.initialOccurrence.timing, edited.timing)
      }
    } else {
      origin.timing
    },
    recurrence = if (state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled) {
      null
    } else if (state.isSeriesRecurrenceChanged) {
      edited.recurrence
    } else {
      origin.recurrence
    },
    reminder = if (state.isOccurrenceReminderChanged ||
      (state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled)
    ) edited.reminder else origin.reminder,
    todoState = if (state.isSeriesRelationChanged) edited.todoState else origin.todoState,
    linkedToCourse = if (state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled) {
      false
    } else if (state.isSeriesRelationChanged) {
      edited.linkedToCourse
    } else {
      origin.linkedToCourse
    },
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
      // 关联关系属于整个系列；“仅本次”只约束标题、时间等 occurrence 字段，不能生成单次关联调整。
      if (state.isSeriesRelationChanged) execute(ScheduleCommand.Update(relationEdited))
      // RRULE 只属于系列；仅修改 recurrence 后选择 THIS_ONLY 按 no-op 处理，不创建空的单次调整。
      if (!state.isOccurrenceFieldsChanged) return null
      require(!(state.isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled)) {
        "THIS_ONLY occurrence timing cannot become unscheduled"
      }
      requireNotNull(recurrenceId).let { id ->
        val existing = snapshot.value.occurrenceAdjustments.firstOrNull {
          it.scheduleId == origin.id && it.recurrenceId == id
        }
        val occurrence = state.toOccurrencePatch(
          origin = origin,
          recurrenceId = id,
          now = now,
          existingPatch = existing?.patch,
          status = existing?.status ?: state.initialOccurrence?.status ?: OccurrenceStatus.ACTIVE,
        )
        if (occurrence.patch == OccurrencePatch()) {
          // 空覆盖仍通过 UPSERT 保存状态；恢复调整不再等价为资源 DELETE。
          existing?.let {
            execute(ScheduleCommand.UpsertOccurrenceAdjustment(it.copy(patch = null, updatedAt = now)))
          }
          return@let
        }
        // 内容调整与完成态相互正交；沿用既有单次调整的状态、revision/createdAt，避免编辑标题或时间时
        // 意外取消完成，也避免把同步资源误当成新记录。
        val savedOccurrence = occurrence.copy(
          revision = existing?.revision ?: 0,
          createdAt = existing?.createdAt ?: now,
        )
        execute(
          newCategory?.let { ScheduleCommand.SaveOccurrenceWithNewCategory(it, savedOccurrence) }
            ?: ScheduleCommand.UpsertOccurrenceAdjustment(savedOccurrence),
        )
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
        return null
      }
      // 只修改系列级关联关系时无需拆分；关系对原系列所有 occurrence 一次生效。
      if (!hasSeriesContentChanges) {
        if (state.isSeriesRelationChanged) execute(ScheduleCommand.Update(relationEdited))
        return null
      }
      val split = SeriesSplitter.split(
        schedule = origin,
        occurrenceAdjustments = snapshot.value.occurrenceAdjustments.filter { it.scheduleId == origin.id },
        boundary = boundary,
        followingId = idGenerators.scheduleId(),
      )
      val previous = split.previousSchedule.copy(
        todoState = edited.todoState?.let { ScheduleTodoState.PENDING },
        linkedToCourse = edited.linkedToCourse,
        updatedAt = now,
      )
      val following = split.followingSchedule.copy(
        // 新系列把当前 occurrence 的有效内容提升为系列值；边界自身的旧单次调整随后会被移除。
        title = edited.title,
        description = edited.description,
        categoryId = edited.categoryId,
        timing = edited.timing,
        recurrence = if (edited.timing == ScheduleTiming.Unscheduled) {
          null
        } else if (state.isSeriesRecurrenceChanged) {
          edited.recurrence
        } else {
          split.followingSchedule.recurrence
        },
        reminder = edited.reminder,
        todoState = edited.todoState?.let { ScheduleTodoState.PENDING },
        linkedToCourse = edited.linkedToCourse,
        createdAt = now,
        updatedAt = now,
      )
      execute(ScheduleCommand.SplitSeries(previous, following, boundary, newCategory))
    }
  }
  return null
}

/**
 * 切换某次日程的完成态。
 *
 * 非重复日程直接更新系列完成态；重复日程使用稳定 [recurrenceId] 写单次调整，并保留已有调整中的
 * 内容字段、revision 与创建时间。该操作会立即写入本地优先仓库，不经过编辑表单的范围选择。
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
  val existing = snapshot.value.occurrenceAdjustments.firstOrNull {
    it.scheduleId == scheduleId && it.recurrenceId == recurrenceId
  }
  if (!completed) {
    if (existing == null) return
    if (existing.patch == null) {
      // ACTIVE 且没有内容覆盖时整条 adjustment 已无业务含义，物理删除还能避免旧 Patch 将来复活。
      execute(ScheduleCommand.DeleteOccurrenceAdjustment(scheduleId, recurrenceId))
      return
    }
  }
  execute(
    ScheduleCommand.UpsertOccurrenceAdjustment(
      existing?.copy(
        status = if (completed) OccurrenceStatus.COMPLETED else OccurrenceStatus.ACTIVE,
        updatedAt = now,
      ) ?: ScheduleOccurrenceAdjustment(
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
 * 按范围删除日程：单实例写入 CANCELLED 单次调整，“本次及以后”按原始实例锚点截断，全部则删除系列。
 *
 * @param recurrenceId 单实例和后续范围的稳定锚点；对应范围下为空会立即失败。
 * @param clock 创建或更新单次调整时使用，确保删除状态能参与后续同步。
 *
 * 该函数会挂起并写仓库；普通单实例删除不会物理删除系列，也不会丢失已有调整的 revision。若有限系列
 * 只剩当前实例，则删除当前实例等价于删除父系列，避免留下没有任何可见 occurrence 的空资源。
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
      val currentSnapshot = snapshot.value
      val schedule = currentSnapshot.schedules.firstOrNull { it.id == scheduleId }
        ?: return
      val scheduleAdjustments = currentSnapshot.occurrenceAdjustments.filter { it.scheduleId == scheduleId }
      if (RecurrenceEngine.isOnlyRemainingOccurrence(schedule, scheduleAdjustments, id)) {
        execute(ScheduleCommand.Delete(scheduleId))
        return
      }
      val now = clock.now()
      val existing = scheduleAdjustments.firstOrNull {
        it.scheduleId == scheduleId && it.recurrenceId == id
      }
      execute(ScheduleCommand.UpsertOccurrenceAdjustment(
        existing?.copy(status = OccurrenceStatus.CANCELLED, patch = null, updatedAt = now)
          ?: ScheduleOccurrenceAdjustment(
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
 * 还原某次发生的单次调整，使其重新继承重复系列。
 *
 * 还原只清除内容 Patch。ACTIVE/CANCELLED 在还原后均回到父系列的默认 ACTIVE，因此物理删除整条资源；
 * COMPLETED 必须继续保留完成态，只把 Patch 改为空。没有对应调整时不会产生命令。
 */
suspend fun ScheduleRepository.restoreOccurrenceAdjustment(
  scheduleId: ScheduleId,
  recurrenceId: RecurrenceId,
) {
  val existing = snapshot.value.occurrenceAdjustments.firstOrNull {
    it.scheduleId == scheduleId && it.recurrenceId == recurrenceId
  } ?: return
  if (existing.status == OccurrenceStatus.COMPLETED) {
    execute(ScheduleCommand.UpsertOccurrenceAdjustment(existing.copy(patch = null)))
  } else {
    execute(ScheduleCommand.DeleteOccurrenceAdjustment(existing.scheduleId, existing.recurrenceId))
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
    is ScheduleTiming.AllDay -> editedOccurrence.copy(date = rebasedStart.date)
    ScheduleTiming.Unscheduled -> error("recurring schedule cannot become unscheduled")
  }
}

/** 返回 timing 的墙上开始；未排期值没有可供重复系列迁移的锚点。 */
private fun ScheduleTiming.effectiveStart() = when (this) {
  is ScheduleTiming.Timed -> start
  is ScheduleTiming.Deadline -> due
  is ScheduleTiming.AllDay -> com.cyxbs.components.config.time.MinuteTimeDate(date, 0, 0)
  ScheduleTiming.Unscheduled -> error("unscheduled timing has no occurrence start")
}

/**
 * 把实例编辑结果压缩成 sparse patch，并保留 [recurrenceId] 作为不可变的 occurrence identity。
 *
 * 未触碰字段保留 existing patch；实际改动字段保存本次 occurrence 的显式结果。日期与时间形态分别写入，
 * 实例标识仍指向原始展开锚点，从而保证整系列改日期或时间时不会覆盖另一维的单次调整。
 * 当前 UI 没有“恢复继承”动作，因此改成与 parent 相同的非空值也保存 Replace；可清空字段的空值保存 Clear。
 */
private fun EditScheduleModelState.toOccurrencePatch(
  origin: Schedule,
  recurrenceId: RecurrenceId,
  now: Instant,
  existingPatch: OccurrencePatch?,
  status: OccurrenceStatus,
): ScheduleOccurrenceAdjustment {
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
  val initialTiming = requireNotNull(initialOccurrence).timing
  val patch = OccurrencePatch(
    date = preserveOrBuild(
      isOccurrenceTimingChanged && initialTiming.occurrenceDate() != edited.timing.occurrenceDate(),
      existing.date,
    ) {
      FieldPatch.Replace(edited.timing.occurrenceDate())
    },
    time = preserveOrBuild(
      isOccurrenceTimingChanged && initialTiming.occurrenceTime() != edited.timing.occurrenceTime(),
      existing.time,
    ) {
      FieldPatch.Replace(edited.timing.occurrenceTime())
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
    reminder = preserveOrBuild(
      isOccurrenceReminderChanged || (isOccurrenceTimingChanged && edited.timing == ScheduleTiming.Unscheduled),
      existing.reminder,
    ) {
      edited.reminder?.let { FieldPatch.Replace(it) } ?: FieldPatch.Clear
    },
  )
  return ScheduleOccurrenceAdjustment(origin.id, recurrenceId, 0, status, patch, now, now)
}

/** 重复 occurrence 的四种有效 timing 都必须有一个明确日期。 */
private fun ScheduleTiming.occurrenceDate() = when (this) {
  is ScheduleTiming.Timed -> start.date
  is ScheduleTiming.Deadline -> due.date
  is ScheduleTiming.AllDay -> date
  ScheduleTiming.Unscheduled -> error("recurring occurrence cannot be unscheduled")
}

/** 将完整 timing 拆成不携带日期的单次时间形态，供 date/time 两个原子分别比较与提交。 */
private fun ScheduleTiming.occurrenceTime(): OccurrenceTime = when (this) {
  is ScheduleTiming.Timed -> OccurrenceTime.TimeRange(start.minuteOfDay, durationMinutes, timeZoneId)
  is ScheduleTiming.Deadline -> OccurrenceTime.TimePoint(due.minuteOfDay, timeZoneId)
  is ScheduleTiming.AllDay -> OccurrenceTime.AllDay
  ScheduleTiming.Unscheduled -> error("recurring occurrence cannot be unscheduled")
}
