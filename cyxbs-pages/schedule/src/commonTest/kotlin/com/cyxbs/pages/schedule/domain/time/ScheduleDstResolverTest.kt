package com.cyxbs.pages.schedule.domain.time

import com.cyxbs.components.config.time.MinuteTimeDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 跨端 DST resolver 的冻结 contract vectors。 */
class ScheduleDstResolverTest {
  @Test
  fun newYorkSpringGapShiftsForwardByGap() {
    val result = ScheduleDstResolver.resolve(MinuteTimeDate(2026, 3, 8, 2, 30), "America/New_York")

    assertIs<LocalDateTimeResolution.GapShifted>(result)
    assertEquals(LocalDateTime(2026, 3, 8, 3, 30), result.effectiveLocal)
    assertEquals(60 * 60, result.adjustmentSeconds)
    assertEquals(result.effectiveLocal, result.instant.toLocalDateTime(TimeZone.of("America/New_York")))
  }

  @Test
  fun newYorkFallOverlapChoosesEarlierInstant() {
    val result = ScheduleDstResolver.resolve(MinuteTimeDate(2026, 11, 1, 1, 30), "America/New_York")

    assertIs<LocalDateTimeResolution.OverlapResolved>(result)
    assertEquals(LocalDateTime(2026, 11, 1, 1, 30), result.effectiveLocal)
    assertEquals(-4 * 60 * 60, result.offset.totalSeconds)
    assertEquals("2026-11-01T05:30:00Z", result.instant.toString())
  }

  @Test
  fun shanghaiLocalTimeIsExact() {
    val result = ScheduleDstResolver.resolve(MinuteTimeDate(2026, 7, 14, 9, 45), "Asia/Shanghai")

    assertIs<LocalDateTimeResolution.Exact>(result)
    assertEquals(LocalDateTime(2026, 7, 14, 9, 45), result.effectiveLocal)
    assertEquals(8 * 60 * 60, result.offset.totalSeconds)
    assertEquals("2026-07-14T01:45:00Z", result.instant.toString())
  }

  @Test
  fun historicalSecondOffsetExactIsRepresentedByInstant() {
    val result = ScheduleDstResolver.resolve(MinuteTimeDate(1880, 1, 1, 12, 0), "Europe/Paris")

    assertIs<LocalDateTimeResolution.Exact>(result)
    assertEquals(561, result.offset.totalSeconds)
    assertEquals(LocalDateTime(1880, 1, 1, 12, 0), result.effectiveLocal)
    assertEquals(result.effectiveLocal, result.instant.toLocalDateTime(TimeZone.of("Europe/Paris")))
  }

  @Test
  fun historicalSecondGapIsRejectedAsNonMinuteAdjustment() {
    val result = ScheduleDstResolver.resolve(MinuteTimeDate(1908, 5, 1, 0, 1), "Africa/Addis_Ababa")

    assertIs<LocalDateTimeResolution.GapAdjustmentNotMinuteAligned>(result)
    assertEquals(164, result.adjustmentSeconds)
    assertEquals(8_836, result.offsetBefore.totalSeconds)
    assertEquals(9_000, result.offsetAfter.totalSeconds)
  }

  @Test
  fun invalidZoneIsMachineReadable() {
    assertEquals(
      LocalDateTimeResolution.InvalidTimeZone("Mars/Olympus_Mons"),
      ScheduleDstResolver.resolve(MinuteTimeDate(2026, 7, 14, 9, 45), "Mars/Olympus_Mons"),
    )
  }

  @Test
  fun lordHoweThirtyMinuteGapShiftsByThirtyMinutes() {
    val result = ScheduleDstResolver.resolve(MinuteTimeDate(2026, 10, 4, 2, 15), "Australia/Lord_Howe")

    assertIs<LocalDateTimeResolution.GapShifted>(result)
    assertEquals(LocalDateTime(2026, 10, 4, 2, 45), result.effectiveLocal)
    assertEquals(30 * 60, result.adjustmentSeconds)
    assertEquals(11 * 60 * 60, result.offset.totalSeconds)
  }
}
