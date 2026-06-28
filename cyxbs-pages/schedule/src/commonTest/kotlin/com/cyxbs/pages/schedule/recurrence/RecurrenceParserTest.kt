package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecurrenceParserTest {

  // 验证 serialize：基本字段 + BYDAY 升序归一输出
  @Test
  fun serialize_basic() {
    assertEquals(
      "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE",
      RecurrenceParser.serialize(RRule(Freq.WEEKLY, interval = 2, byDay = listOf(3, 1))),
    )
  }

  // 验证 parse：星期码 MO/WE 解析为 ISO 1/3
  @Test
  fun parse_basic() {
    assertEquals(
      RRule(Freq.WEEKLY, interval = 2, byDay = listOf(1, 3)),
      RecurrenceParser.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"),
    )
  }

  // 验证 parse 容忍 "RRULE:" 前缀，并解析 COUNT
  @Test
  fun parse_tolerates_prefix_and_count() {
    assertEquals(
      RRule(Freq.DAILY, count = 5),
      RecurrenceParser.parse("RRULE:FREQ=DAILY;COUNT=5"),
    )
  }

  // 验证 UNTIL 序列化为 yyyyMMdd 并往返一致
  @Test
  fun until_round_trip() {
    val rule = RRule(Freq.MONTHLY, until = Date(2026, 12, 31))
    val s = RecurrenceParser.serialize(rule)
    assertEquals("FREQ=MONTHLY;UNTIL=20261231", s)
    assertEquals(rule, RecurrenceParser.parse(s))
  }

  // 验证 UNTIL 接受 DATE-TIME 形式（只取日期部分）
  @Test
  fun until_accepts_date_time_form() {
    assertEquals(
      Date(2026, 1, 1),
      RecurrenceParser.parse("FREQ=DAILY;UNTIL=20260101T235959Z").until,
    )
  }

  // 验证负数 BYMONTHDAY 序列化/解析往返
  @Test
  fun negative_month_day_round_trip() {
    val rule = RRule(Freq.MONTHLY, byMonthDay = listOf(-1))
    val s = RecurrenceParser.serialize(rule)
    assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", s)
    assertEquals(rule, RecurrenceParser.parse(s))
  }

  // 验证归一化后的规则 parse∘serialize 恒等
  @Test
  fun round_trip_normalized() {
    // byDay 升序归一后，parse∘serialize 恒等
    val rule = RRule(
      freq = Freq.WEEKLY,
      interval = 3,
      byDay = listOf(1, 5),
      until = Date(2027, 6, 1),
    )
    assertEquals(rule, RecurrenceParser.parse(RecurrenceParser.serialize(rule)))
  }

  // 验证 serializeFull 输出 RRULE/RDATE/EXDATE 多行（供 .ics 导出）
  @Test
  fun serialize_full_lines() {
    val r = Recurrence(
      rrule = RRule(Freq.WEEKLY, byDay = listOf(1)),
      rdate = listOf(Date(2026, 1, 10)),
      exdate = listOf(Date(2026, 1, 17)),
    )
    assertEquals(
      "RRULE:FREQ=WEEKLY;BYDAY=MO\n" +
        "RDATE;VALUE=DATE:20260110\n" +
        "EXDATE;VALUE=DATE:20260117",
      RecurrenceParser.serializeFull(r),
    )
  }

  // 验证缺少 FREQ 时 parse 抛 IllegalArgumentException（requireNotNull）
  @Test
  fun parse_missing_freq_throws() {
    assertFailsWith<IllegalArgumentException> { RecurrenceParser.parse("INTERVAL=2;BYDAY=MO") }
  }

  // 验证 BYMONTH 序列化/解析往返
  @Test
  fun bymonth_round_trip() {
    val rule = RRule(Freq.YEARLY, byMonth = listOf(3, 6))
    val s = RecurrenceParser.serialize(rule)
    assertEquals("FREQ=YEARLY;BYMONTH=3,6", s)
    assertEquals(rule, RecurrenceParser.parse(s))
  }

  // 验证 parse 忽略未知键与空段，不影响有效字段
  @Test
  fun parse_ignores_unknown_and_empty_segments() {
    assertEquals(RRule(Freq.DAILY), RecurrenceParser.parse("FREQ=DAILY;;X=Y;FOO"))
  }
}
