package com.cyxbs.pages.schedule.ui.timeline

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence

/**
 * 编辑框日期时间文本的解析结果，仅作为 UI 输入过渡格式；Schedule v2 持久化始终使用四态 [ScheduleTiming]，
 * 不会保存该字符串或 minuteOfDay 表示。
 */
data class ScheduleDateTime(val date: Date, val minuteOfDay: Int?)

fun parseScheduleDateTime(raw: String?): ScheduleDateTime? {
  if (raw.isNullOrBlank()) return null
  val match = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日(?:\s*(\d{1,2})[:：](\d{1,2}))?""")
    .matchEntire(raw.trim()) ?: return null
  val values = match.groupValues
  val date = runCatching { Date(values[1].toInt(), values[2].toInt(), values[3].toInt()) }.getOrNull() ?: return null
  val minute = values[4].takeIf(String::isNotEmpty)?.toIntOrNull()?.let { hour ->
    val min = values[5].toIntOrNull() ?: return null
    if (hour !in 0..23 || min !in 0..59) return null
    hour * 60 + min
  }
  return ScheduleDateTime(date, minute)
}

/**
 * 单个 occurrence 在某一天时间轴上的裁剪结果。
 * [startMin]/[endMin] 均限制在当天 0..1440；跨日区间会在相交的每一天生成一个片段，
 * 但 [occurrence] 的系列/实例 identity 始终保持不变，点击仍定位原实例。
 */
data class DayTimedSchedule(
  val occurrence: ScheduleUiOccurrence,
  val isInterval: Boolean,
  val startMin: Int,
  val endMin: Int,
)

internal const val FULL_DAY_MINUTES = 24 * 60
internal fun DayTimedSchedule.isFullDay(): Boolean = isInterval && startMin <= 0 && endMin >= FULL_DAY_MINUTES

/**
 * 将已由业务引擎展开的实例投影到单个可见日的时间轴。
 *
 * 时间段会按墙上时间占用区间与 `[date 00:00, nextDate 00:00)` 相交后裁剪到当天 0..24 点，
 * 因此跨日或超过一天的实例会在每个相交日分别占据正确片段；截止项只落在 due 所在日，全天项覆盖
 * durationDays 中的每一天，未排期项仅在 [includeUnscheduled] 为 true 时进入整日栏。该布局层绝不自行
 * 展开重复规则，避免与业务引擎产生窗口/例外语义分叉。
 */
internal fun timelineSchedulesForDate(
  occurrences: List<ScheduleUiOccurrence>,
  date: Date,
  includeUnscheduled: Boolean = false,
): List<DayTimedSchedule> {
  val dayStart = MinuteTimeDate(date, 0, 0)
  val dayEndExclusive = MinuteTimeDate(date.plusDays(1), 0, 0)
  return occurrences.mapNotNull { occurrence ->
    when (val timing = occurrence.timing) {
      is ScheduleTiming.Timed -> {
        val start = timing.start
        val endExclusive = start.plusMinutes(timing.durationMinutes)
        if (start >= dayEndExclusive || endExclusive <= dayStart) null else DayTimedSchedule(
          occurrence = occurrence,
          isInterval = true,
          startMin = if (start <= dayStart) 0 else start.minuteOfDay,
          endMin = if (endExclusive >= dayEndExclusive) FULL_DAY_MINUTES else endExclusive.minuteOfDay,
        )
      }
      is ScheduleTiming.Deadline -> if (timing.due.date == date) {
        val minute = timing.due.minuteOfDay
        DayTimedSchedule(occurrence, false, minute, minute)
      } else null
      is ScheduleTiming.AllDay -> if (date >= timing.startDate && date < timing.startDate.plusDays(timing.durationDays)) {
        DayTimedSchedule(occurrence, true, 0, FULL_DAY_MINUTES)
      } else null
      ScheduleTiming.Unscheduled -> if (includeUnscheduled) DayTimedSchedule(occurrence, true, 0, FULL_DAY_MINUTES) else null
    }
  }.sortedBy(DayTimedSchedule::startMin)
}

internal fun formatScheduleDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): String =
  "${year}年${month}月${day}日 ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
