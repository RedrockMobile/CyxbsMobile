package com.cyxbs.pages.schedule.ui.todo

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCompletion
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
 * 页面按系列最多展示一项：非重复日程直接展示自身；重复系列展示当前窗口中最早的未完成实例，
 * 没有未完成实例时才展示最近完成实例。这样清单不会因为每日重复规则无限铺开，同时完成操作仍保留
 * 精确 recurrence identity。
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

  val items = snapshot.schedules.mapNotNull { schedule ->
    val occurrence = schedule.todoOccurrence(snapshot, windowStart, windowEnd, viewerTimeZone)
      ?: return@mapNotNull null
    occurrence.toTodoItem(schedule, now, viewerTimeZone)
  }

  val pending = items
    .filter { it.occurrence.status == OccurrenceStatus.ACTIVE }
    .sortedWith(
      compareBy<ScheduleTodoItemUi> { it.sortInstant(viewerTimeZone) == null }
        .thenBy { it.sortInstant(viewerTimeZone) }
        .thenBy { it.schedule.id.value },
    )
  val completed = items
    .filter { item ->
      item.occurrence.status == OccurrenceStatus.COMPLETED &&
        item.completedAt(snapshot) >= now - 7.days
    }
    .sortedWith(
      compareByDescending<ScheduleTodoItemUi> { it.sortInstant(viewerTimeZone) }
        .thenBy { it.schedule.id.value },
    )
  return ScheduleTodoProjection(pending = pending, completed = completed)
}

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
 * 非重复事项完成时会推进日程本身的 [Schedule.updatedAt]；重复实例则由 occurrence exception 记录
 * 完成状态，因此优先使用匹配 exception 的更新时间。找不到匹配行时回退到系列更新时间并保持可展示。
 */
private fun ScheduleTodoItemUi.completedAt(snapshot: ScheduleSnapshot): Instant {
  val recurrenceId = occurrence.recurrenceId ?: return schedule.updatedAt
  return snapshot.exceptions.firstOrNull { exception ->
    exception.scheduleId == schedule.id &&
      exception.recurrenceId == recurrenceId &&
      exception.status == OccurrenceStatus.COMPLETED
  }?.updatedAt ?: schedule.updatedAt
}

/** 为一个系列选择清单中唯一可见的实例，避免重复规则生成大量卡片。 */
private fun Schedule.todoOccurrence(
  snapshot: ScheduleSnapshot,
  startInclusive: MinuteTimeDate,
  endExclusive: MinuteTimeDate,
  viewerTimeZone: TimeZone,
): ScheduleUiOccurrence? {
  if (recurrence == null) {
    return ScheduleOccurrence(
      scheduleId = id,
      recurrenceId = null,
      timing = timing,
      title = title,
      description = description,
      categoryId = categoryId,
      reminders = reminders,
      status = if (completion == ScheduleCompletion.COMPLETED) {
        OccurrenceStatus.COMPLETED
      } else {
        OccurrenceStatus.ACTIVE
      },
      isOverridden = false,
    ).toUiModel()
  }

  val occurrences = runCatching {
    RecurrenceEngine.expandInRange(
      schedule = this,
      exceptions = snapshot.exceptions.filter { it.scheduleId == id },
      rangeStartInclusive = startInclusive,
      rangeEndExclusive = endExclusive,
    ).map(ScheduleOccurrence::toUiModel)
  }.getOrElse { return null }

  return occurrences
    .filter { it.status == OccurrenceStatus.ACTIVE }
    .minWithOrNull(
      compareBy<ScheduleUiOccurrence> { it.sortInstant(viewerTimeZone) == null }
        .thenBy { it.sortInstant(viewerTimeZone) },
    )
    ?: occurrences
      .filter { it.status == OccurrenceStatus.COMPLETED }
      .maxByOrNull { it.sortInstant(viewerTimeZone) ?: Instant.DISTANT_PAST }
}

/** 把实例的四态 timing 转成卡片文案，并计算临期状态。 */
private fun ScheduleUiOccurrence.toTodoItem(
  schedule: Schedule,
  now: Instant,
  viewerTimeZone: TimeZone,
): ScheduleTodoItemUi {
  val boundary = boundaryInstant(viewerTimeZone)
  return ScheduleTodoItemUi(
    schedule = schedule,
    occurrence = this,
    timeText = timing.todoTimeText(),
    isOverdue = isExpired(now, viewerTimeZone),
    // “临期”只覆盖未来 24 小时；超期由独立状态表达，不能与临期重叠。
    isDueSoon = status == OccurrenceStatus.ACTIVE && boundary != null &&
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
  is ScheduleTiming.AllDay -> value.startDate.plusDays(value.durationDays).toLocalDate()
    .atStartOfDayIn(viewerTimeZone)
  ScheduleTiming.Unscheduled -> null
}

/** 卡片排序使用清单展示的截止边界；旧 Timed 数据也按结束时刻排序。 */
private fun ScheduleTodoItemUi.sortInstant(viewerTimeZone: TimeZone): Instant? =
  occurrence.boundaryInstant(viewerTimeZone)

/** 与卡片相同的时间排序规则，供重复实例选择和最终列表排序复用。 */
private fun ScheduleUiOccurrence.sortInstant(viewerTimeZone: TimeZone): Instant? = when (val value = timing) {
  is ScheduleTiming.Timed -> {
    val zone = TimeZone.of(value.timeZoneId)
    value.start.toLocalDateTime().toInstant(zone) + value.durationMinutes.minutes
  }
  is ScheduleTiming.Deadline -> value.due.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId))
  is ScheduleTiming.AllDay -> value.startDate.toLocalDate().atStartOfDayIn(viewerTimeZone)
  ScheduleTiming.Unscheduled -> null
}

/**
 * 将日程时间转换为清单卡片文案。
 *
 * Deadline 展示单个时间点；Timed 必须同时展示开始和结束，避免已经由课表或其他入口创建的
 * 时间段在清单中丢失持续时间。跨日区间会分别展示两端日期，同日区间只重复一次日期。
 */
private fun ScheduleTiming.todoTimeText(): String = when (this) {
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
  is ScheduleTiming.AllDay -> {
    if (durationDays == 1) "${startDate.shortText()} · 全天"
    else {
      val endDate = startDate.plusDays(durationDays - 1)
      "${startDate.shortText()}–${endDate.shortText()} · 全天"
    }
  }
  ScheduleTiming.Unscheduled -> "未设置时间"
}

private fun Date.shortText(): String = "${monthNumber}月${dayOfMonth}日"

private fun MinuteTimeDate.minuteText(): String =
  "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** recurrence identity 的稳定 key；不能用移动后的显示时间代替原始身份。 */
private fun RecurrenceId.stableKey(): String =
  "${originalDateTime}|${timeZoneId.orEmpty()}|$allDay"
