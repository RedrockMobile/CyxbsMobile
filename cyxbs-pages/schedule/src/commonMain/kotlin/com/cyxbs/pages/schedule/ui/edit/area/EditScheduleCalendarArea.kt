package com.cyxbs.pages.schedule.ui.edit.area

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.CalendarDateCompose
import com.cyxbs.components.view.calendar.WeekTextCompose
import com.cyxbs.components.view.calendar.month.CalendarMonthCompose
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.pages.schedule.ui.edit.EditScheduleState
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime

/**
 * 编辑日期
 *
 * @author 985892345
 * @date 2026/7/5
 */
@Composable
internal fun EditScheduleCalendarArea(
  state: EditScheduleState,
) {
  val calendarState = rememberCalendarState(
    initialClickDate = state.anchorDate,
    onClick = { date ->
      state.startTime = reanchorTimeString(state.startTime, date)
      state.endTime = reanchorTimeString(state.endTime, date)
    },
  )
  LaunchedEffect(calendarState) { calendarState.expand() } // 默认展开整月
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
        )
      }
    },
  )
}

/** 把中文时间串的「日期」改写为 [date]，保留「时分」；空串/无法解析原样返回。 */
private fun reanchorTimeString(time: String, date: Date): String {
  if (time.isBlank()) return time
  val parsed = com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime(time) ?: return time
  val min = parsed.minuteOfDay ?: 0
  return formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, min / 60, min % 60)
}
