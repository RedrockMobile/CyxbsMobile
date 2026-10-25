package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证单向导出保留的 Provider canonical 字段与稳定身份 codec。 */
class CalendarCanonicalBaselineTest {
  /** 字段快照必须复制提醒集合，并保留 timing、RRULE 与文本的精确值。 */
  @Test
  fun calendarFieldsKeepCanonicalValuesAndDetachMutableReminderInput() {
    val reminders = mutableListOf(0, 15)
    val fields = CanonicalCalendarFields(
      title = "标题",
      description = "描述",
      timing = CalendarTiming.Timed(
        start = MinuteTimeDate(2026, 8, 17, 9, 30),
        durationMinutes = 45,
        timeZoneId = "Asia/Shanghai",
      ),
      recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,FR",
      deviceReminderMinutes = reminders,
    )

    reminders += 30

    assertEquals("标题", fields.title)
    assertEquals("FREQ=WEEKLY;BYDAY=MO,FR", fields.recurrenceRule)
    assertEquals(listOf(0, 15), fields.deviceReminderMinutes)
  }

  /** 单向 gateway 用于重发现事件的 canonical URI 必须严格往返同一投影身份。 */
  @Test
  fun retainedProjectionIdentityCodecRoundTrips() {
    val id = CalendarProjectionId(
      scope = CalendarExportScope("20260001"),
      scheduleId = ScheduleId("018f0f7c-6000-7000-8000-000000000001"),
      kind = CalendarProjectionKind.SINGLE,
    )

    val encoded = CalendarProjectionUriCodec.encode(id)

    assertEquals(id, CalendarProjectionUriCodec.decodeOrNull(encoded))
    assertEquals(encoded, CalendarProjectionUriCodec.encode(requireNotNull(CalendarProjectionUriCodec.decodeOrNull(encoded))))
  }

  /** 非规范提醒集合在 Provider 比较边界直接拒绝，不能进入 planner 形成近似写入。 */
  @Test
  fun calendarFieldsRejectNonCanonicalReminderOrder() {
    assertFailsWith<IllegalArgumentException> {
      CanonicalCalendarFields(
        title = "标题",
        description = "描述",
        timing = CalendarTiming.Deadline(
          due = MinuteTimeDate(2026, 8, 17, 9, 30),
          timeZoneId = "Asia/Shanghai",
        ),
        recurrenceRule = null,
        deviceReminderMinutes = listOf(15, 5),
      )
    }
  }
}
