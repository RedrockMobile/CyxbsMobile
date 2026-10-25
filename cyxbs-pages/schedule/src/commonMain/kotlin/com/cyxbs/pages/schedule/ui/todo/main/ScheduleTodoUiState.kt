package com.cyxbs.pages.schedule.ui.todo.main

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence
import com.cyxbs.pages.schedule.ui.model.isExpired
import com.cyxbs.pages.schedule.ui.model.toUiModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 邮子清单对共享 Schedule 快照的一次只读投影。
 *
 * 非重复日程直接展示自身；重复系列在未完成区只展示下一次实例，已完成区则展示最近七天内真实完成的
 * 实例。这样不会把未来规则无限铺开，同时用户仍能找到并取消某一次完成。
 */
internal data class ScheduleTodoProjection(
  val pending: List<ScheduleTodoItemUi>,
  val completed: List<ScheduleTodoItemUi>,
) {
  /** 即将到期或已经超期的未完成事项数量，用于顶部提醒条。 */
  val urgentCount: Int get() = pending.count { it.isOverdue || it.isDueSoon }
}

/**
 * 单个清单卡片的稳定 UI 模型。
 *
 * [schedule] 保存系列事实供编辑和批量删除，[occurrence] 保存本次发生的有效字段与实例 identity。
 */
internal data class ScheduleTodoItemUi(
  val schedule: Schedule,
  val occurrence: ScheduleUiOccurrence,
  val timeText: String,
  val isOverdue: Boolean,
  val isDueSoon: Boolean,
) {
  val key: String
    get() = buildString {
      append(schedule.id.value)
      occurrence.recurrenceId?.let { append('|').append(it.stableKey()) }
    }
}

/**
 * 将权威快照投影为清单视图。
 *
 * @param now 当前时刻，用于过期和临期判断；调用方可在测试中注入固定值。
 * @param viewerTimeZone 全天事项和当前日期采用的查看者时区。
 */
internal fun projectScheduleTodo(
  snapshot: ScheduleSnapshot,
  now: Instant,
  viewerTimeZone: TimeZone,
): ScheduleTodoProjection {
  val today = now.toLocalDateTime(viewerTimeZone).toMinuteTimeDate().date
  val windowStart = MinuteTimeDate(today.plusDays(-30), 0, 0)
  val windowEnd = MinuteTimeDate(today.plusYears(1).plusDays(1), 0, 0)

  val pending = mutableListOf<ScheduleTodoItemUi>()
  val completed = mutableListOf<ScheduleTodoItemUi>()
  snapshot.schedules.forEach { schedule ->
    // 原生事务只有在 todoState 非空、即显式关联清单后才进入邮子清单。
    if (schedule.todoState == null) return@forEach
    val occurrences = schedule.todoOccurrences(snapshot, windowStart, windowEnd)
    val nextActive = occurrences
      .asSequence()
      .filter { it.status == OccurrenceStatus.ACTIVE }
      .map(ScheduleOccurrence::toUiModel)
      .minWithOrNull(
        compareBy<ScheduleUiOccurrence> { it.sortInstant(viewerTimeZone) == null }
          .thenBy { it.sortInstant(viewerTimeZone) },
      )
    if (nextActive != null) pending += nextActive.toTodoItem(schedule, now, viewerTimeZone)

    occurrences
      .asSequence()
      .filter { it.status == OccurrenceStatus.COMPLETED }
      .map(ScheduleOccurrence::toUiModel)
      .map { it.toTodoItem(schedule, now, viewerTimeZone) }
      .filter { it.completedAt(snapshot) >= now - 7.days }
      .forEach(completed::add)
  }

  val sortedPending = sortScheduleTodoByTime(pending, viewerTimeZone)
  val sortedCompleted = sortScheduleTodoCompleted(completed, viewerTimeZone)
  return ScheduleTodoProjection(pending = sortedPending, completed = sortedCompleted)
}

/** 按发生时间升序排列，未设置时间的清单放在最后；同一时刻使用 ScheduleId 保持顺序稳定。 */
internal fun sortScheduleTodoByTime(
  items: List<ScheduleTodoItemUi>,
  viewerTimeZone: TimeZone,
): List<ScheduleTodoItemUi> = items.sortedWith(
  compareBy<ScheduleTodoItemUi> { it.sortInstant(viewerTimeZone) == null }
    .thenBy { it.sortInstant(viewerTimeZone) }
    .thenBy { it.schedule.id.value },
)

/** 按发生时间倒序排列已完成项，与清单主页的历史分区保持一致。 */
internal fun sortScheduleTodoCompleted(
  items: List<ScheduleTodoItemUi>,
  viewerTimeZone: TimeZone,
): List<ScheduleTodoItemUi> = items.sortedWith(
  compareByDescending<ScheduleTodoItemUi> { it.sortInstant(viewerTimeZone) }
    .thenBy { it.schedule.id.value },
)

/**
 * 按清单产品优先级排列未完成事项。
 *
 * 顺序固定为超期、置顶、24 小时内临期、普通有时间、无时间；超期始终压过端上置顶。置顶组内部遵循
 * [pinnedIds] 在 Settings 中保存的顺序，其余同组保持领域投影原有的截止时间顺序。
 */
internal fun sortScheduleTodoPending(
  items: List<ScheduleTodoItemUi>,
  pinnedIds: List<ScheduleId>,
): List<ScheduleTodoItemUi> {
  val pinnedOrder = pinnedIds.withIndex().associate { (index, id) -> id to index }
  fun ScheduleTodoItemUi.priority(): Int = when {
    isOverdue -> 0
    schedule.id in pinnedOrder -> 1
    isDueSoon -> 2
    occurrence.timing == ScheduleTiming.Unscheduled -> 4
    else -> 3
  }
  return items.sortedWith(
    compareBy<ScheduleTodoItemUi> { it.priority() }
      .thenBy { item ->
        if (item.priority() == 1) pinnedOrder[item.schedule.id] ?: Int.MAX_VALUE else Int.MAX_VALUE
      },
  )
}

/**
 * 返回完成状态实际落库的时间，用于限制已完成列表的展示窗口。
 *
 * 非重复事项完成时会推进日程本身的 [Schedule.updatedAt]；重复实例则由单次调整记录完成状态，
 * 因此优先使用匹配调整的更新时间。找不到匹配行时回退到系列更新时间并保持可展示。
 */
private fun ScheduleTodoItemUi.completedAt(snapshot: ScheduleSnapshot): Instant {
  val recurrenceId = occurrence.recurrenceId ?: return schedule.updatedAt
  return snapshot.occurrenceAdjustments.firstOrNull { adjustment ->
    adjustment.scheduleId == schedule.id &&
      adjustment.recurrenceId == recurrenceId &&
      adjustment.status == OccurrenceStatus.COMPLETED
  }?.updatedAt ?: schedule.updatedAt
}

/** 展开一个系列的有界实例；调用方只选下一项未完成和七天内已完成，避免未来规则无限进入列表。 */
private fun Schedule.todoOccurrences(
  snapshot: ScheduleSnapshot,
  startInclusive: MinuteTimeDate,
  endExclusive: MinuteTimeDate,
): List<ScheduleOccurrence> {
  if (recurrence == null) {
    return listOf(ScheduleOccurrence(
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
    ))
  }

  return runCatching {
    RecurrenceEngine.expandInRange(
      schedule = this,
      occurrenceAdjustments = snapshot.occurrenceAdjustments.filter { it.scheduleId == id },
      rangeStartInclusive = startInclusive,
      rangeEndExclusive = endExclusive,
    )
  }.getOrElse { emptyList() }
}

/** 把实例的四态 timing 转成卡片文案，并计算临期状态。 */
internal fun ScheduleUiOccurrence.toTodoItem(
  schedule: Schedule,
  now: Instant,
  viewerTimeZone: TimeZone,
  allowDueSoonWithoutTodo: Boolean = false,
): ScheduleTodoItemUi {
  val boundary = boundaryInstant(viewerTimeZone)
  return ScheduleTodoItemUi(
    schedule = schedule,
    occurrence = this,
    timeText = timing.todoTimeText(),
    // 事务没有“逾期”语义；该公共投影也会被分组全量页用于展示纯事务。
    isOverdue = schedule.todoState != null && isExpired(now, viewerTimeZone),
    // “临期”只覆盖未来 24 小时；超期由独立状态表达，不能与临期重叠。
    isDueSoon = (schedule.todoState != null || allowDueSoonWithoutTodo) &&
      status == OccurrenceStatus.ACTIVE && boundary != null &&
      boundary >= now && boundary <= now + 24.hours,
  )
}

/** 返回可比较的事项结束边界；未排期没有远端或 UI 可解释的边界。 */
private fun ScheduleUiOccurrence.boundaryInstant(viewerTimeZone: TimeZone): Instant? = when (val value = timing) {
  is ScheduleTiming.Timed -> {
    val zone = TimeZone.of(value.timeZoneId)
    value.start.toLocalDateTime().toInstant(zone) + value.durationMinutes.minutes
  }
  is ScheduleTiming.Deadline -> value.due.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId))
  is ScheduleTiming.AllDay -> value.date.plusDays(1).toLocalDate()
    .atStartOfDayIn(viewerTimeZone)
  ScheduleTiming.Unscheduled -> null
}

/** 卡片排序使用清单展示的截止边界；旧 Timed 数据也按结束时刻排序。 */
internal fun ScheduleTodoItemUi.sortInstant(viewerTimeZone: TimeZone): Instant? =
  occurrence.boundaryInstant(viewerTimeZone)

/** 与卡片相同的时间排序规则，供重复实例选择和最终列表排序复用。 */
private fun ScheduleUiOccurrence.sortInstant(viewerTimeZone: TimeZone): Instant? = when (val value = timing) {
  is ScheduleTiming.Timed -> {
    val zone = TimeZone.of(value.timeZoneId)
    value.start.toLocalDateTime().toInstant(zone) + value.durationMinutes.minutes
  }
  is ScheduleTiming.Deadline -> value.due.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId))
  is ScheduleTiming.AllDay -> value.date.toLocalDate().atStartOfDayIn(viewerTimeZone)
  ScheduleTiming.Unscheduled -> null
}

/**
 * 将日程时间转换为清单卡片文案。
 *
 * Deadline 展示单个时间点；Timed 必须同时展示开始和结束，避免已经由课表或其他入口创建的
 * 时间段在清单中丢失持续时间。跨日区间会分别展示两端日期，同日区间只重复一次日期。
 */
internal fun ScheduleTiming.todoTimeText(): String = when (this) {
  is ScheduleTiming.Deadline -> "${due.date.shortText()} ${due.minuteText()}"
  is ScheduleTiming.Timed -> {
    val zone = TimeZone.of(timeZoneId)
    val end = (start.toLocalDateTime().toInstant(zone) + durationMinutes.minutes)
      .toLocalDateTime(zone).toMinuteTimeDate()
    if (start.date == end.date) {
      "${start.date.shortText()} ${start.minuteText()}–${end.minuteText()}"
    } else {
      "${start.date.shortText()} ${start.minuteText()}–${end.date.shortText()} ${end.minuteText()}"
    }
  }
  is ScheduleTiming.AllDay -> "${date.shortText()} · 全天"
  ScheduleTiming.Unscheduled -> "未设置时间"
}

private fun Date.shortText(): String = "${monthNumber}月${dayOfMonth}日"

private fun MinuteTimeDate.minuteText(): String =
  "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** recurrence identity 的稳定 key；不能用移动后的显示时间代替原始身份。 */
private fun RecurrenceId.stableKey(): String =
  "${originalDateTime}|${timeZoneId.orEmpty()}|$allDay"
