package com.cyxbs.pages.schedule.ui.category

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toDate
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.model.toUiModel
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoItemUi
import com.cyxbs.pages.schedule.ui.todo.main.projectScheduleTodo
import com.cyxbs.pages.schedule.ui.todo.main.sortScheduleTodoByTime
import com.cyxbs.pages.schedule.ui.todo.main.sortScheduleTodoCompleted
import com.cyxbs.pages.schedule.ui.todo.main.sortInstant
import com.cyxbs.pages.schedule.ui.todo.main.sortScheduleTodoPending
import com.cyxbs.pages.schedule.ui.todo.main.toTodoItem
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 单个分组的完整日程投影。
 *
 * 与清单主页不同，这里不裁掉七天前完成的事项，也不排除纯事务；重复系列仍只占一行，避免把无限
 * occurrence 展开成无法管理的列表。
 */
internal data class ScheduleCategoryItems(
  val todos: ScheduleCategoryItemGroup,
  val schedules: ScheduleCategoryScheduleGroup,
)

/** 具有完成态的待办按状态分区；普通日程不使用该结构。 */
internal data class ScheduleCategoryItemGroup(
  val active: List<ScheduleTodoItemUi>,
  val completed: List<ScheduleTodoItemUi>,
)

/** 普通日程按系列是否仍存在今天及未来 occurrence 分区。 */
internal data class ScheduleCategoryScheduleGroup(
  val upcoming: List<ScheduleTodoItemUi>,
  val expired: List<ScheduleTodoItemUi>,
)

/** 投影过程附带系列是否过期，避免把该状态伪装成待办的完成或超期状态。 */
private data class ScheduleCategoryItemCandidate(
  val item: ScheduleTodoItemUi,
  val scheduleExpired: Boolean,
)

/**
 * 从完整仓库快照中列出全部日程，或引用 [categoryId] 的所有日程。
 *
 * 系列字段直接引用分组时优先沿用清单主页选择的下一次有效实例；仅有单次调整引用分组时展示距离最近的
 * 对应实例。两种情况最终都按 ScheduleId 去重，因此批量操作始终以整条日程为单位。
 */
internal fun projectScheduleCategoryItems(
  snapshot: ScheduleSnapshot,
  categoryId: CategoryId?,
  now: Instant,
  viewerTimeZone: TimeZone,
  pinnedIds: List<ScheduleId>,
): ScheduleCategoryItems {
  val regularProjection = projectScheduleTodo(snapshot, now, viewerTimeZone)
  val regularByScheduleId = buildMap {
    regularProjection.pending.forEach { put(it.schedule.id, it) }
    regularProjection.completed.forEach { item ->
      if (item.schedule.id !in this) put(item.schedule.id, item)
    }
  }
  val adjustmentsByScheduleId = snapshot.occurrenceAdjustments.groupBy { it.scheduleId }
  val today = now.toLocalDateTime(viewerTimeZone).date.toDate()

  val allItems = snapshot.schedules.mapNotNull { schedule ->
    val categoryInheritedBySeries = categoryId == null || schedule.categoryId == categoryId
    val scheduleAdjustments = adjustmentsByScheduleId[schedule.id].orEmpty()
    val matchingAdjustments = if (categoryId == null) emptyList() else {
      scheduleAdjustments.filter { adjustment ->
        (adjustment.patch?.categoryId as? FieldPatch.Replace)?.value == categoryId
      }
    }
    if (!categoryInheritedBySeries && matchingAdjustments.isEmpty()) return@mapNotNull null

    if (categoryInheritedBySeries && schedule.todoState == null) {
      return@mapNotNull schedule.toOrdinaryScheduleCandidate(
        occurrenceAdjustments = scheduleAdjustments,
        today = today,
        now = now,
        viewerTimeZone = viewerTimeZone,
      )
    }

    val item = if (categoryInheritedBySeries) {
      regularByScheduleId[schedule.id]
    } else {
      matchingAdjustments
        .asSequence()
        .mapNotNull { adjustment ->
          runCatching {
            RecurrenceEngine.resolveOccurrenceByIdentity(
              schedule = schedule,
              occurrenceAdjustments = scheduleAdjustments,
              recurrenceId = adjustment.recurrenceId,
            )
          }.getOrNull()
        }
        .map(ScheduleOccurrence::toUiModel)
        .map {
          it.toTodoItem(
            schedule,
            now,
            viewerTimeZone,
            allowDueSoonWithoutTodo = schedule.todoState == null,
          )
        }
        .minWithOrNull(
          compareBy<ScheduleTodoItemUi> { it.occurrence.status != OccurrenceStatus.ACTIVE }
            .thenBy { it.sortInstant(viewerTimeZone) == null }
            .thenBy { it.sortInstant(viewerTimeZone) },
        )
    }
    val resolved = item ?: schedule.toSeriesListItem(now, viewerTimeZone)
    ScheduleCategoryItemCandidate(
      item = resolved,
      scheduleExpired = schedule.todoState == null &&
        resolved.occurrence.timing.startDateOrNull()?.let { it < today } == true,
    )
  }

  val (todos, schedules) = allItems.partition { it.item.schedule.todoState != null }
  val (expiredSchedules, upcomingSchedules) = schedules.partition(ScheduleCategoryItemCandidate::scheduleExpired)
  return ScheduleCategoryItems(
    todos = todos.map(ScheduleCategoryItemCandidate::item)
      .toCategoryItemGroup(pinnedIds, viewerTimeZone),
    schedules = ScheduleCategoryScheduleGroup(
      upcoming = sortScheduleTodoByTime(
        upcomingSchedules.map(ScheduleCategoryItemCandidate::item),
        viewerTimeZone,
      ),
      expired = sortScheduleTodoCompleted(
        expiredSchedules.map(ScheduleCategoryItemCandidate::item),
        viewerTimeZone,
      ),
    ),
  )
}

/**
 * 为普通日程选择列表代表实例。
 *
 * 重复系列优先选择今天起的第一次有效 occurrence；不存在未来实例时选择最后一次历史 occurrence。
 * 搜索窗口覆盖当前规则的五个间隔，足以处理产品开放的日、周、月、年重复及闰日跳跃，又不会无限展开。
 */
private fun Schedule.toOrdinaryScheduleCandidate(
  occurrenceAdjustments: List<ScheduleOccurrenceAdjustment>,
  today: Date,
  now: Instant,
  viewerTimeZone: TimeZone,
): ScheduleCategoryItemCandidate {
  if (recurrence == null || timing == ScheduleTiming.Unscheduled) {
    val item = toSeriesListItem(now, viewerTimeZone)
    return ScheduleCategoryItemCandidate(
      item = item,
      scheduleExpired = timing.startDateOrNull()?.let { it < today } == true,
    )
  }

  val todayStart = MinuteTimeDate(today, 0, 0)
  val futureEnd = MinuteTimeDate(today.recurrenceSearchEnd(recurrence), 0, 0)
  val upcoming = runCatching {
    RecurrenceEngine.expandInRange(this, occurrenceAdjustments, todayStart, futureEnd)
  }.getOrElse { emptyList() }
    .asSequence()
    .filter { it.timing.startDateOrNull()?.let { date -> date >= today } == true }
    .map(ScheduleOccurrence::toUiModel)
    .map { it.toTodoItem(this, now, viewerTimeZone, allowDueSoonWithoutTodo = true) }
    .minWithOrNull(
      compareBy<ScheduleTodoItemUi> { it.sortInstant(viewerTimeZone) == null }
        .thenBy { it.sortInstant(viewerTimeZone) },
    )
  if (upcoming != null) return ScheduleCategoryItemCandidate(upcoming, scheduleExpired = false)

  val firstDate = minOf(recurrenceAnchorDate ?: timing.startDateOrNull() ?: today, today)
  val previous = runCatching {
    RecurrenceEngine.expandInRange(
      this,
      occurrenceAdjustments,
      MinuteTimeDate(firstDate, 0, 0),
      todayStart,
    )
  }.getOrElse { emptyList() }
    .asSequence()
    .filter { it.timing.startDateOrNull()?.let { date -> date < today } == true }
    .map(ScheduleOccurrence::toUiModel)
    .map { it.toTodoItem(this, now, viewerTimeZone, allowDueSoonWithoutTodo = true) }
    .maxWithOrNull(
      compareBy<ScheduleTodoItemUi> { it.sortInstant(viewerTimeZone) }
        .thenBy { it.schedule.id.value },
    )
  return ScheduleCategoryItemCandidate(
    item = previous ?: toSeriesListItem(now, viewerTimeZone),
    scheduleExpired = true,
  )
}

/** 返回足以命中下一周期的有界搜索末端；额外乘五用于覆盖月末和闰日缺失。 */
private fun Date.recurrenceSearchEnd(rule: RecurrenceRule): Date {
  val span = rule.interval.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE / 5) * 5
  val end = when (rule.frequency) {
    RecurrenceFrequency.DAILY -> plusDays(span)
    RecurrenceFrequency.WEEKLY -> plusWeeks(span)
    RecurrenceFrequency.MONTHLY -> plusMonths(span)
    RecurrenceFrequency.YEARLY -> plusYears(span)
  }
  return end.plusDays(1)
}

/** 普通日程区域按开始日期判定归属；结束时间仅用于区域内的稳定排序。 */
private fun ScheduleTiming.startDateOrNull(): Date? = when (this) {
  is ScheduleTiming.Timed -> start.date
  is ScheduleTiming.Deadline -> due.date
  is ScheduleTiming.AllDay -> date
  ScheduleTiming.Unscheduled -> null
}

/**
 * 沿用清单主页的排序规则，并区分未完成和已完成。
 */
private fun List<ScheduleTodoItemUi>.toCategoryItemGroup(
  pinnedIds: List<ScheduleId>,
  viewerTimeZone: TimeZone,
): ScheduleCategoryItemGroup {
  val (completed, active) = partition { item ->
    item.schedule.todoState != null && item.occurrence.status == OccurrenceStatus.COMPLETED
  }
  // 产品优先级排序同级保持输入顺序，因此先复用清单主页的基础时间顺序。
  val activeByTime = sortScheduleTodoByTime(active, viewerTimeZone)
  return ScheduleCategoryItemGroup(
    active = sortScheduleTodoPending(activeByTime, pinnedIds),
    completed = sortScheduleTodoCompleted(completed, viewerTimeZone),
  )
}

/** 构造系列级兜底卡片；用于普通日程、较早完成项和当前展开窗口外的重复系列。 */
private fun Schedule.toSeriesListItem(
  now: Instant,
  viewerTimeZone: TimeZone,
): ScheduleTodoItemUi = ScheduleOccurrence(
  scheduleId = id,
  recurrenceId = null,
  timing = timing,
  title = title,
  description = description,
  categoryId = categoryId,
  reminder = reminder,
  status = if (todoState == ScheduleTodoState.COMPLETED) {
    OccurrenceStatus.COMPLETED
  } else {
    OccurrenceStatus.ACTIVE
  },
  isAdjusted = false,
).toUiModel().toTodoItem(
  schedule = this,
  now = now,
  viewerTimeZone = viewerTimeZone,
  allowDueSoonWithoutTodo = todoState == null,
)
