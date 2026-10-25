package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.CalendarDateCompose
import com.cyxbs.components.view.calendar.WeekTextCompose
import com.cyxbs.components.view.calendar.month.CalendarMonthCompose
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.calendar.rememberScheduleCalendarMarkedDates
import com.cyxbs.pages.schedule.ui.edit.EditScheduleModelState
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime

/**
 * 编辑日期
 *
 * @author 985892345
 * @date 2026/7/5
 */
@Composable
internal fun EditScheduleCalendarArea(
  state: EditScheduleModelState,
  snapshot: ScheduleSnapshot,
) {
  val calendarState = rememberCalendarState(
    initialClickDate = state.anchorDate,
    endDate = TodayNoEffect.plusYears(8).lastDate,
  )
  val markedDates = rememberScheduleCalendarMarkedDates(snapshot, calendarState)
  CalendarCompose(
    modifier = Modifier.fillMaxWidth(),
    state = calendarState,
    // 只留星期行 + 月份网格：去掉默认的左侧年月列(MonthTextCompose)，
    // 年月/第N周 已由上面的信息栏展示，无需在日历里重复。
    // 弹窗空间小，用更小的字号与格子高度，保证整月放得下。
    calendar = {
      calendarState.WeekTextCompose(fontSize = 8.sp)
      calendarState.CalendarMonthCompose { date, show ->
        calendarState.CalendarDateCompose(
          date = date, show = show,
          dayFontSize = 14.sp, lunarFontSize = 9.sp, maxCellHeight = 38.dp,
          hasIndicator = date in markedDates,
        )
      }
    },
  )
  LaunchedEffect(Unit) { calendarState.expand() } // 默认展开整月
  LaunchedEffect(Unit) {
    // clickDate 是日历的展示状态，初次收集会直接给出今天；这里只消费真实点击，避免打开日历就
    // 把迁移得到的无日期清单悄悄改成今天。事件流也允许用户再次点击当前选中日。
    calendarState.clickEventFlow.collect { event ->
      state.applyExplicitDateSelection(event.new)
    }
  }
}

/**
 * 提交用户在日历中的真实选日动作。
 *
 * 未排期日程首次选日后成为“有日期、无具体时刻”的全天日程；已有时间的日程只替换日期并保留时分。
 */
internal fun EditScheduleModelState.applyExplicitDateSelection(date: Date) {
  if (effectiveTiming == com.cyxbs.pages.schedule.domain.model.ScheduleTiming.Unscheduled) {
    val dateText = formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0)
    isAllDay = true
    isInterval = false
    startTime = dateText
    endTime = dateText
  } else {
    startTime = reanchorTimeString(startTime, date)
    endTime = reanchorTimeString(endTime, date)
  }
}

/** 把中文时间串的「日期」改写为 [date]，保留「时分」；空串/无法解析原样返回。 */
private fun reanchorTimeString(time: String, date: Date): String {
  if (time.isBlank()) return time
  val parsed = com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime(time) ?: return time
  val min = parsed.minuteOfDay ?: 0
  return formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, min / 60, min % 60)
}
