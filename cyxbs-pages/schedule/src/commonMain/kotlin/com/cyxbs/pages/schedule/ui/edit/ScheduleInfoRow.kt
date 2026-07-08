package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.Recurrence

/**
 * 查看/编辑弹窗「信息行」（日期·第N周·周几·时间段·重复·提醒 同一行展示）的纯文案格式化函数。
 *
 * 全部为 commonMain 纯函数（周数推导显式传入开学第一天，不在内部读 SchoolCalendar），便于单测。
 * 文案缩写规则见各函数；不适用的段返回 null，由 UI 跳过不显示。
 */

/**
 * 由开学第一天（周一）推导 [date] 的学期周数；不在学期内（开学前 / 超过 [maxWeek] / 未知开学日）返回 null。
 *
 * 与 [com.cyxbs.components.config.time.SchoolCalendar.getWeekOfTerm] 同口径：第1周从开学第一天起。
 */
fun weekOfTerm(firstMonday: Date?, date: Date, maxWeek: Int = 30): Int? {
  if (firstMonday == null) return null
  val diff = firstMonday.daysUntil(date)
  if (diff < 0) return null
  val week = diff / 7 + 1
  return if (week in 1..maxWeek) week else null
}

/** 日期：今年显示 `7月4日`，非今年显示年份后两位，如 `25年7月4日`。 */
fun formatInfoDate(date: Date, today: Date = TodayNoEffect): String = buildString {
  if (date.year != today.year) {
    append((date.year % 100).toString().padStart(2, '0')).append('年')
  }
  append(date.monthNumber).append('月').append(date.dayOfMonth).append('日')
}

/** 第N周：在学期内返回「第13周」，否则 null（不显示）。 */
fun formatWeekOfTerm(firstMonday: Date?, date: Date): String? =
  weekOfTerm(firstMonday, date)?.let { "第${it}周" }

/** 星期：`周日`/`周一`…（复用 [weekNumberToChinese]）。 */
fun formatWeekday(date: Date): String = "周" + weekNumberToChinese(date.dayOfWeekNumber)

/** 单个时刻：分钟数 → `10:00`（时分补零）。 */
fun formatClock(minuteOfDay: Int): String {
  val h = (minuteOfDay / 60).toString().padStart(2, '0')
  val m = (minuteOfDay % 60).toString().padStart(2, '0')
  return "$h:$m"
}

/**
 * 时间段文案：
 * - 时间段型(有开始)：`10:00-11:30`；
 * - 截止型(无开始、有结束)：`截止11:30`；
 * - 都没有(未排期)：null。
 */
fun formatTimeRange(startMin: Int?, endMin: Int?): String? = when {
  startMin != null && endMin != null -> "${formatClock(startMin)}-${formatClock(endMin)}"
  startMin != null -> "截止${formatClock(startMin)}"
  endMin != null -> "截止${formatClock(endMin)}"
  else -> null
}

/**
 * 提前提醒文案：`-1`(不提醒)→null；`0`→「准时」；`60` 的整数倍→「提前N小时」；其余→「提前N分」。
 */
fun formatRemindAhead(remindMinutes: Int): String? = when {
  remindMinutes < 0 -> null
  remindMinutes == 0 -> "准时"
  remindMinutes % 60 == 0 -> "提前${remindMinutes / 60}小时"
  else -> "提前${remindMinutes}分种"
}

/** 提前提醒选项的菜单文案（含「不提醒」）。 */
fun remindOptionLabel(remindMinutes: Int): String =
  if (remindMinutes < 0) "不提醒" else formatRemindAhead(remindMinutes) ?: "不提醒"

/**
 * 信息行里的重复摘要（紧凑版）：
 * - 每周单日、间隔1：`每周一`（比 [buildRecurrenceLabels] 的「每周 周一」更短）；
 * - 其余沿用 [buildRecurrenceLabels] 的首段；不重复返回 null。
 */
fun recurrenceRowLabel(recurrence: Recurrence?): String? {
  val r = recurrence?.rrule ?: return null
  if (r.freq == Freq.WEEKLY && r.interval == 1 && r.byDay.size == 1) {
    return "每周${weekNumberToChinese(r.byDay.first())}"
  }
  return buildRecurrenceLabels(recurrence).firstOrNull()
}
