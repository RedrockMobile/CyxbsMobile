package com.cyxbs.pages.schedule.data.model

import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LegacyRecurrenceMigrationTest {

  private fun entity(remindMode: ScheduleRemindMode, recurrence: Recurrence? = null) = ScheduleEntity(
    todoId = 1L, title = "t", lastModifyTime = 0L, remindMode = remindMode, recurrence = recurrence,
  )

  // 验证 NONE（不重复）：迁移后 recurrence 仍为 null（单次）
  @Test
  fun none_stays_single() {
    val r = LegacyRecurrenceMigration.migrate(entity(ScheduleRemindMode(repeatMode = ScheduleRemindMode.NONE)))
    assertNull(r.recurrence)
  }

  // 验证 DAY → FREQ=DAILY
  @Test
  fun day_to_daily() {
    val r = LegacyRecurrenceMigration.migrate(entity(ScheduleRemindMode(repeatMode = ScheduleRemindMode.DAY)))
    assertEquals(RRule(Freq.DAILY), r.recurrence!!.rrule)
  }

  // 验证 WEEK + week[1,3] → FREQ=WEEKLY;BYDAY(周一、周三)
  @Test
  fun week_to_weekly_byday() {
    val r = LegacyRecurrenceMigration.migrate(
      entity(ScheduleRemindMode(repeatMode = ScheduleRemindMode.WEEK, week = listOf(1, 3)))
    )
    assertEquals(RRule(Freq.WEEKLY, byDay = listOf(1, 3)), r.recurrence!!.rrule)
  }

  // 验证 MONTH + day[1,15] → FREQ=MONTHLY;BYMONTHDAY=1,15
  @Test
  fun month_to_monthly_bymonthday() {
    val r = LegacyRecurrenceMigration.migrate(
      entity(ScheduleRemindMode(repeatMode = ScheduleRemindMode.MONTH, day = listOf(1, 15)))
    )
    assertEquals(RRule(Freq.MONTHLY, byMonthDay = listOf(1, 15)), r.recurrence!!.rrule)
  }

  // 验证 YEAR + date["3.15"] → FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=15
  @Test
  fun year_to_yearly_bymonth_and_day() {
    val r = LegacyRecurrenceMigration.migrate(
      entity(ScheduleRemindMode(repeatMode = ScheduleRemindMode.YEAR, date = listOf("3.15")))
    )
    assertEquals(RRule(Freq.YEARLY, byMonth = listOf(3), byMonthDay = listOf(15)), r.recurrence!!.rrule)
  }

  // 验证已有 recurrence 时不再迁移（原样返回，避免覆盖新结构）
  @Test
  fun existing_recurrence_is_kept() {
    val existing = entity(
      ScheduleRemindMode(repeatMode = ScheduleRemindMode.DAY),
      recurrence = Recurrence(RRule(Freq.WEEKLY)),
    )
    val r = LegacyRecurrenceMigration.migrate(existing)
    assertSame(existing, r) // 已有 recurrence 时原样返回
  }
}
