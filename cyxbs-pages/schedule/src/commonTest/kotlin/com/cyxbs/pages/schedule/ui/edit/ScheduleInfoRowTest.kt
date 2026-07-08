package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 信息行纯文案格式化（[weekOfTerm]/日期/星期/时间段/提醒/重复缩写）的单测。
 */
class ScheduleInfoRowTest {

  private val firstMonday = Date(2024, 1, 1) // 2024-01-01 是周一，当开学第一天

  /** 未知开学日 → 无法判断周数。 */
  @Test
  fun week_null_when_no_first_monday() {
    assertNull(weekOfTerm(null, Date(2024, 3, 1)))
  }

  /** 开学第一天当天 = 第1周。 */
  @Test
  fun week_first_day_is_week1() {
    assertEquals(1, weekOfTerm(firstMonday, firstMonday))
  }

  /** 第13周：开学 + 12 周。 */
  @Test
  fun week_thirteen() {
    assertEquals(13, weekOfTerm(firstMonday, firstMonday.plusWeeks(12)))
  }

  /** 开学前 → null（不在学期内）。 */
  @Test
  fun week_before_term_is_null() {
    assertNull(weekOfTerm(firstMonday, firstMonday.minusDays(1)))
  }

  /** 超过最大周数(默认25) → null（学期外，只显示日期）。 */
  @Test
  fun week_beyond_max_is_null() {
    assertNull(weekOfTerm(firstMonday, firstMonday.plusWeeks(25)))
  }

  /** 日期：今年不显示年份，非今年显示年份后两位。 */
  @Test
  fun info_date_format() {
    val today = Date(2026, 7, 8)
    assertEquals("7月4日", formatInfoDate(Date(2026, 7, 4), today))
    assertEquals("25年7月4日", formatInfoDate(Date(2025, 7, 4), today))
  }

  /** 星期中文（2024-01-01 为周一）。 */
  @Test
  fun weekday_format() {
    assertEquals("周一", formatWeekday(Date(2024, 1, 1)))
  }

  /** 时刻补零。 */
  @Test
  fun clock_format() {
    assertEquals("10:00", formatClock(600))
    assertEquals("01:30", formatClock(90))
  }

  /** 时间段三种形态：区间 / 截止 / 仅开始 / 未排期。 */
  @Test
  fun time_range_variants() {
    assertEquals("10:00-11:30", formatTimeRange(600, 690))
    assertEquals("截止11:30", formatTimeRange(null, 690))
    assertEquals("10:00", formatTimeRange(600, null))
    assertNull(formatTimeRange(null, null))
  }

  /** 提前提醒文案：不提醒/准时/分钟/小时。 */
  @Test
  fun remind_ahead_format() {
    assertNull(formatRemindAhead(-1))
    assertEquals("准时", formatRemindAhead(0))
    assertEquals("提前10分", formatRemindAhead(10))
    assertEquals("提前1小时", formatRemindAhead(60))
    assertEquals("提前2小时", formatRemindAhead(120))
  }

  /** 重复缩写：每周单日用紧凑「每周一」；不重复为 null。 */
  @Test
  fun recurrence_row_label() {
    assertEquals("每周一", recurrenceRowLabel(Recurrence(rrule = RRule(freq = Freq.WEEKLY, byDay = listOf(1)))))
    assertEquals("每2天", recurrenceRowLabel(Recurrence(rrule = RRule(freq = Freq.DAILY, interval = 2))))
    assertNull(recurrenceRowLabel(null))
  }

  /** 提醒选项菜单文案含「不提醒」。 */
  @Test
  fun remind_option_label() {
    assertEquals("不提醒", remindOptionLabel(-1))
    assertEquals("准时", remindOptionLabel(0))
    assertEquals("提前30分", remindOptionLabel(30))
  }
}
