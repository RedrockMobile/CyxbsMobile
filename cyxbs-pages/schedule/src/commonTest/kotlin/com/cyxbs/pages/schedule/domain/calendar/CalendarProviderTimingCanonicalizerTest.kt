package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Provider 时间精度的纯契约测试；不依赖真实 Android Calendar Provider。 */
class CalendarProviderTimingCanonicalizerTest {
  @Test
  fun canonicalTimedAndDeadlineValuesRoundTripWithoutPrecisionLoss() {
    val start = epochMillis(MinuteTimeDate(2026, 7, 12, 9, 30), "Asia/Shanghai")

    assertEquals(
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 90, "Asia/Shanghai"),
      reconstruct(start, dtEnd = start + 90 * MILLIS_PER_MINUTE),
    )
    assertEquals(
      CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 9, 30), "Asia/Shanghai"),
      reconstruct(
        start,
        duration = "PT0M",
        recurring = true,
        kind = CalendarProjectionKind.DEADLINE,
      ),
    )
  }

  @Test
  fun timedRejectsSecondAndMillisecondPrecision() {
    val start = epochMillis(MinuteTimeDate(2026, 7, 12, 9, 30), "Asia/Shanghai")

    assertNull(reconstruct(start + 1, dtEnd = start + 60 * MILLIS_PER_MINUTE))
    assertNull(reconstruct(start + 30_000, dtEnd = start + 60 * MILLIS_PER_MINUTE))
    assertNull(reconstruct(start, dtEnd = start + 60 * MILLIS_PER_MINUTE + 1))
    assertNull(reconstruct(start, dtEnd = start + 90_000))
    assertNull(reconstruct(start, duration = "PT90S", recurring = true))
    assertNull(reconstruct(start, duration = "P", recurring = true, kind = CalendarProjectionKind.DEADLINE))
    assertNull(reconstruct(start, duration = "P1DT", recurring = true))
    assertNull(reconstruct(start, duration = "P999999999999999999999999DT1M", recurring = true))
    assertNull(reconstruct(start, duration = "PT999999999999999999999999H1M", recurring = true))
    assertNull(reconstruct(start, duration = "PT999999999999999999999999M", recurring = true))
  }

  @Test
  fun allDayRequiresUtcMidnightAndPositiveWholeDays() {
    val start = Date(2026, 7, 12).toUtcStartMillis()

    assertEquals(
      CalendarTiming.AllDay(Date(2026, 7, 12), 2),
      reconstruct(
        start,
        dtEnd = start + 2 * MILLIS_PER_DAY,
        allDay = true,
        timeZoneId = TimeZone.UTC.id,
      ),
    )
    assertNull(reconstruct(
      start + 1,
      dtEnd = start + 2 * MILLIS_PER_DAY,
      allDay = true,
      timeZoneId = TimeZone.UTC.id,
    ))
    assertNull(reconstruct(
      start,
      dtEnd = start + MILLIS_PER_DAY + 30_000,
      allDay = true,
      timeZoneId = TimeZone.UTC.id,
    ))
    assertNull(reconstruct(
      start,
      duration = null,
      recurring = true,
      allDay = true,
      timeZoneId = TimeZone.UTC.id,
    ))
  }

  @Test
  fun deadlineAcceptsOnlyZeroDuration() {
    val start = epochMillis(MinuteTimeDate(2026, 7, 12, 9, 30), "Asia/Shanghai")

    assertEquals(
      CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 9, 30), "Asia/Shanghai"),
      reconstruct(start, dtEnd = start, kind = CalendarProjectionKind.DEADLINE),
    )
    assertNull(reconstruct(
      start,
      dtEnd = start + MILLIS_PER_MINUTE,
      kind = CalendarProjectionKind.DEADLINE,
    ))
    assertNull(reconstruct(
      start,
      duration = "PT1M",
      recurring = true,
      kind = CalendarProjectionKind.DEADLINE,
    ))
    // 相同边界只属于 Deadline，不能被普通时间段误读成零分钟 Timed。
    assertNull(reconstruct(start, dtEnd = start))
    assertNull(reconstruct(start, duration = "PT0M", recurring = true))
  }

  @Test
  fun fieldShapeMustMatchSingleOrRecurringProviderContract() {
    val start = epochMillis(MinuteTimeDate(2026, 7, 12, 9, 30), "Asia/Shanghai")

    assertNull(reconstruct(start, duration = "PT30M"))
    assertNull(reconstruct(start, dtEnd = start + 30 * MILLIS_PER_MINUTE, recurring = true))
    assertNull(reconstruct(
      start,
      dtEnd = start + 30 * MILLIS_PER_MINUTE,
      duration = "PT30M",
    ))
  }

  private fun reconstruct(
    dtStart: Long,
    dtEnd: Long? = null,
    duration: String? = null,
    timeZoneId: String? = "Asia/Shanghai",
    allDay: Boolean = false,
    recurring: Boolean = false,
    kind: CalendarProjectionKind = CalendarProjectionKind.SINGLE,
  ): CalendarTiming? = CalendarProviderTimingCanonicalizer.reconstructOrNull(
    dtStart,
    dtEnd,
    duration,
    timeZoneId,
    allDay,
    recurring,
    kind,
  )

  private fun epochMillis(value: MinuteTimeDate, timeZoneId: String): Long =
    value.toLocalDateTime().toInstant(TimeZone.of(timeZoneId)).toEpochMilliseconds()

  private fun Date.toUtcStartMillis(): Long = kotlinx.datetime.LocalDate(
    year,
    monthNumber,
    dayOfMonth,
  ).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

  private companion object {
    const val MILLIS_PER_MINUTE = 60_000L
    const val MILLIS_PER_DAY = 24 * 60 * MILLIS_PER_MINUTE
  }
}
