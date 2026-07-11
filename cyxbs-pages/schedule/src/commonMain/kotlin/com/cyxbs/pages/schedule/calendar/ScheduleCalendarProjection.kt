package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.recurrence.OccurrenceStatus
import com.cyxbs.pages.schedule.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.recurrence.RecurrenceParser
import com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime

/**
 * 可写入系统日历的一条平台无关投影。
 *
 * [key] 在同一 todo 内稳定，是本地 eventId 映射与幂等对账的业务主键：单次/纯系列使用固定后缀，
 * 展开的重复实例使用原始 recurrenceId，而不是改期后的日期，避免改期后丢失旧系统事件。
 * [start] 为空且 [end] 有值表示截止型；二者均无时刻且 [allDay] 为 true 表示全天事件。
 */
data class ScheduleCalendarProjection(
  val key: String,
  val todoId: Long,
  val title: String,
  val description: String,
  val date: Date,
  val start: MinuteTime?,
  val end: MinuteTime?,
  val allDay: Boolean,
  val remindMinutes: Int,
  val rrule: String? = null,
)

/** Schedule 数据到系统日历事件的纯映射。 */
object ScheduleCalendarProjectionFactory {

  /**
   * 生成系统日历的目标投影。
   *
   * @param todo Schedule 的唯一事实源实体。
   * @param rangeStart 带例外的重复系列展开窗口起点（含）。
   * @param rangeEnd 带例外的重复系列展开窗口终点（含）。
   * @return 应存在于系统日历中的事件；无提醒、无日期或整体已完成时为空。
   *
   * 纯 RRULE 系列保留成一条规则事件，避免无限展开；只要出现 RDATE、EXDATE 或 override，Android
   * Calendar Provider 就无法可靠表达所有例外，因此先由 [RecurrenceEngine] 展开为有界的单次事件。
   */
  fun create(
    todo: ScheduleEntity,
    rangeStart: Date,
    rangeEnd: Date,
  ): List<ScheduleCalendarProjection> {
    if (todo.remindMinutes < 0 || todo.isDone == 1) return emptyList()
    val startDateTime = parseScheduleDateTime(todo.startTime)
    val endDateTime = parseScheduleDateTime(todo.endTime)
    val anchorDate = startDateTime?.date ?: endDateTime?.date ?: return emptyList()
    val start = startDateTime?.minuteOfDay?.let(::minuteTimeOf)
    val end = endDateTime?.minuteOfDay?.let(::minuteTimeOf)
    val allDay = startDateTime?.minuteOfDay == null && endDateTime?.minuteOfDay == null
    val recurrence = todo.recurrence

    if (recurrence == null || recurrence.rrule == null && recurrence.rdate.isEmpty()) {
      return listOf(todo.toProjection("${todo.todoId}:single", anchorDate, start, end, allDay))
    }

    // 只有无例外的 RRULE 才直接交给系统 Provider；例外系列必须由我们自己物化并过滤状态。
    val hasExceptions = recurrence.rdate.isNotEmpty() || recurrence.exdate.isNotEmpty() ||
      recurrence.overrides.isNotEmpty()
    if (!hasExceptions && recurrence.rrule != null) {
      return listOf(
        todo.toProjection(
          key = "${todo.todoId}:series",
          date = anchorDate,
          start = start,
          end = end,
          allDay = allDay,
          rrule = RecurrenceParser.serialize(recurrence.rrule),
        )
      )
    }

    val anchorEnd = end ?: start ?: MinuteTime(0, 0)
    // completed/cancelled 不映射为 CalendarContract.STATUS：它是会议响应状态，不等价于待办完成。
    return RecurrenceEngine.expandInRange(
      recurrence = recurrence,
      anchorDate = anchorDate,
      anchorStart = start,
      anchorEnd = anchorEnd,
      rangeStart = rangeStart,
      rangeEnd = rangeEnd,
    ).mapNotNull { occurrence ->
      if (occurrence.status == OccurrenceStatus.COMPLETED ||
        occurrence.status == OccurrenceStatus.CANCELLED) return@mapNotNull null
      val override = recurrence.overrides.firstOrNull { it.recurrenceId == occurrence.recurrenceId }
      todo.copy(
        title = override?.title ?: todo.title,
        detail = override?.detail ?: todo.detail,
      ).toProjection(
        key = "${todo.todoId}:${RecurrenceParser.fmtDate(occurrence.recurrenceId)}",
        date = occurrence.date,
        start = occurrence.start,
        end = occurrence.end,
        allDay = allDay,
      )
    }
  }

  private fun ScheduleEntity.toProjection(
    key: String,
    date: Date,
    start: MinuteTime?,
    end: MinuteTime?,
    allDay: Boolean,
    rrule: String? = null,
  ) = ScheduleCalendarProjection(
    key = key,
    todoId = todoId,
    title = title,
    description = detail,
    date = date,
    start = start,
    end = end,
    allDay = allDay,
    remindMinutes = remindMinutes,
    rrule = rrule,
  )

  private fun minuteTimeOf(minuteOfDay: Int): MinuteTime =
    MinuteTime(minuteOfDay / 60, minuteOfDay % 60)
}
