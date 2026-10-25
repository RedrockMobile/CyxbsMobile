package com.cyxbs.pages.schedule.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.view.calendar.state.CalendarState
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.edit.RecurrenceDraft
import com.cyxbs.pages.schedule.ui.edit.previewDatesInRange
import com.cyxbs.pages.schedule.ui.model.occurrencesInRange

/**
 * 计算当前日历页及相邻预加载页中需要显示日程圆点的日期。
 *
 * 计算始终限制在有限日期窗口内，避免为了一个月视图无界展开重复日程；相邻页也一并计算，防止用户横向翻页时
 * 圆点到页面切换完成后才突然出现。折叠态覆盖前后各一周，展开态覆盖前后各一月。
 */
@Composable
internal fun rememberScheduleCalendarMarkedDates(
  snapshot: ScheduleSnapshot,
  calendarState: CalendarState,
): Set<Date> {
  val range = rememberCalendarMarkerRange(calendarState)
  return remember(
    snapshot.schedules,
    snapshot.occurrenceAdjustments,
    range.startInclusive,
    range.endExclusive,
  ) {
    scheduleCalendarMarkedDates(
      snapshot = snapshot,
      startInclusive = range.startInclusive,
      endExclusive = range.endExclusive,
    )
  }
}

/**
 * 为重复截止日历预览当前正在编辑的系列日期，而不是混入账号内其他日程。
 *
 * 圆点随草稿规则实时变化，并继续展示当前页内可供选择的后续发生日期；预览只描述系列本身，
 * 尚未保存的草稿不套用历史单次调整。
 */
@Composable
internal fun rememberRecurrenceCalendarMarkedDates(
  draft: RecurrenceDraft,
  anchorDate: Date,
  calendarState: CalendarState,
): Set<Date> {
  val range = rememberCalendarMarkerRange(calendarState)
  return remember(draft, anchorDate, range.startInclusive, range.endExclusive) {
    draft.previewDatesInRange(
      anchor = anchorDate,
      startInclusive = range.startInclusive,
      endExclusive = range.endExclusive,
    )
  }
}

/** 统一计算日历当前页和相邻预加载页的有限日期窗口。 */
@Composable
private fun rememberCalendarMarkerRange(calendarState: CalendarState): CalendarMarkerRange {
  val anchorDate = calendarState.clickDate
  val collapsed = calendarState.currentIsCollapsed
  return remember(anchorDate, collapsed) {
    if (collapsed) {
      val currentWeekStart = anchorDate.minusDays(anchorDate.dayOfWeekOrdinal)
      CalendarMarkerRange(
        startInclusive = currentWeekStart.minusDays(7),
        endExclusive = currentWeekStart.plusDays(14),
      )
    } else {
      val currentMonthStart = anchorDate.copy(dayOfMonth = 1)
      val previousMonthStart = currentMonthStart.minusMonths(1)
      val monthAfterNextStart = currentMonthStart.plusMonths(2)
      CalendarMarkerRange(
        startInclusive = previousMonthStart.minusDays(previousMonthStart.dayOfWeekOrdinal),
        endExclusive = monthAfterNextStart.plusDays(
          (7 - monthAfterNextStart.dayOfWeekOrdinal) % 7,
        ),
      )
    }
  }
}

/**
 * 展开给定半开日期窗口内的日程，并返回实际占用的自然日。
 *
 * 时间段跨过零点时，其覆盖到的每一天都会显示圆点；截止时间与全天日程只标记自身日期，未设置日期的日程不标记。
 * 被取消的单次 occurrence 已由重复引擎过滤，不会留下误导性的圆点。
 */
internal fun scheduleCalendarMarkedDates(
  snapshot: ScheduleSnapshot,
  startInclusive: Date,
  endExclusive: Date,
): Set<Date> = buildSet {
  snapshot.occurrencesInRange(
    startInclusive = MinuteTimeDate(startInclusive, 0, 0),
    endExclusive = MinuteTimeDate(endExclusive, 0, 0),
  ).forEach { occurrence ->
    addOccupiedDates(occurrence.timing, startInclusive, endExclusive)
  }
}

/** 把一次 occurrence 占用的自然日裁剪到当前日历窗口后加入结果。 */
private fun MutableSet<Date>.addOccupiedDates(
  timing: ScheduleTiming,
  startInclusive: Date,
  endExclusive: Date,
) {
  val firstDate: Date
  val lastDate: Date
  when (timing) {
    is ScheduleTiming.Timed -> {
      firstDate = timing.start.date
      lastDate = timing.start.plusMinutes((timing.durationMinutes - 1).coerceAtLeast(0)).date
    }
    is ScheduleTiming.Deadline -> {
      firstDate = timing.due.date
      lastDate = timing.due.date
    }
    is ScheduleTiming.AllDay -> {
      firstDate = timing.date
      lastDate = timing.date
    }
    ScheduleTiming.Unscheduled -> return
  }

  var date = maxOf(firstDate, startInclusive)
  val finalDate = minOf(lastDate, endExclusive.minusDays(1))
  while (date <= finalDate) {
    add(date)
    date = date.plusDays(1)
  }
}

private data class CalendarMarkerRange(
  val startInclusive: Date,
  val endExclusive: Date,
)
