package com.cyxbs.pages.schedule.ui.todo.main

import com.cyxbs.components.config.time.MinuteTimeDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 清单页新增事项默认日期与时间段的边界测试。 */
class ScheduleTodoCreationDefaultsTest {

  /** 普通时刻使用系统时区的当天日期，并从当前分钟开始持续一小时。 */
  @Test
  fun defaultTimingUsesTodayAndFollowingHour() {
    val timing = defaultTodoCreationTiming(
      now = Instant.parse("2026-09-06T05:15:00Z"),
      timeZone = TimeZone.of("Asia/Shanghai"),
    )

    assertEquals(MinuteTimeDate(2026, 9, 6, 13, 15), timing.start)
    assertEquals(60, timing.durationMinutes)
    assertEquals("Asia/Shanghai", timing.timeZoneId)
  }

  /** 临近午夜时不跨到次日，仍以当天 23:59 为截止并保留最短 10 分钟。 */
  @Test
  fun defaultTimingStopsAtLastMinuteOfDay() {
    val timing = defaultTodoCreationTiming(
      now = Instant.parse("2026-09-06T15:55:00Z"),
      timeZone = TimeZone.of("Asia/Shanghai"),
    )

    assertEquals(MinuteTimeDate(2026, 9, 6, 23, 49), timing.start)
    assertEquals(10, timing.durationMinutes)
  }
}
