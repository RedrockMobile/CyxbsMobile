package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** EventKit foundation 的纯 iOS 测试；不构造 EKEventStore、不请求权限也不访问设备日历。 */
class IosEventKitCalendarAdapterFoundationTest {
  /**
   * production bridge 实际使用的阶段状态机必须严格区分 pre-commit、commit-entered 与 post-commit readback。
   *
   * 测试只驱动纯状态机，不构造 `EKEventStore`：pre-commit 继续遵守当前权限，进入 commit 后即使撤权也必须保持
   * `ATOMIC_COMMIT_OUTCOME_UNKNOWN`；reset 资格只描述本地队列清理，不能改变上述提交 provenance。
   */
  @Test
  fun atomicCreateFailureStateClassifiesCommitBoundaryAndAccessWithoutEventStore() {
    val state = IosEventKitAtomicCreateFailureState()
    assertEquals(
      IosEventKitStoreFailure.AMBIGUOUS,
      state.failureFor(IosEventKitFullAccessStatus.FULL_ACCESS),
      "PRE_COMMIT + full access",
    )
    IosEventKitFullAccessStatus.entries
      .filterNot { it == IosEventKitFullAccessStatus.FULL_ACCESS }
      .forEach { status ->
        assertEquals(
          IosEventKitStoreFailure.ACCESS_LOST,
          state.failureFor(status),
          "PRE_COMMIT + $status",
        )
      }
    assertTrue(state.shouldResetAfterException())

    state.enterCommit()
    IosEventKitFullAccessStatus.entries.forEach { status ->
      assertEquals(
        IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN,
        state.failureFor(status),
        "COMMIT_ENTERED + $status",
      )
    }
    assertTrue(state.shouldResetAfterException())

    state.enterPostCommitReadback()
    IosEventKitFullAccessStatus.entries.forEach { status ->
      assertEquals(
        IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN,
        state.failureFor(status),
        "POST_COMMIT_READBACK + $status",
      )
    }
    assertEquals(false, state.shouldResetAfterException())
  }

  @Test
  fun writePayloadKeepsCanonicalIdentityMinuteTimingDeadlineAndDeviceAlarms() {
    val timed = projection(
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 90, ZONE),
      reminders = listOf(0, 15),
    )
    val deadline = projection(
      kind = CalendarProjectionKind.DEADLINE,
      timing = CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 18, 0), ZONE),
    )

    val timedPayload = IosEventKitCalendarAdapterFoundation.toWritePayload(timed).mapped()
    val timedTiming = assertIs<IosEventKitWriteTiming.Timed>(timedPayload.timing)
    assertEquals(timed.externalUri, timedPayload.externalUri)
    assertEquals(rawMoment(MinuteTimeDate(2026, 7, 12, 9, 30)), timedTiming.start)
    assertEquals(rawMoment(MinuteTimeDate(2026, 7, 12, 11, 0)), timedTiming.endExclusive)
    assertEquals(
      listOf(IosEventKitRelativeAlarm(0), IosEventKitRelativeAlarm(-15 * 60L)),
      timedPayload.alarms,
    )

    val deadlinePayload = IosEventKitCalendarAdapterFoundation.toWritePayload(deadline).mapped()
    val deadlineTiming = assertIs<IosEventKitWriteTiming.Timed>(deadlinePayload.timing)
    assertEquals(rawMoment(MinuteTimeDate(2026, 7, 12, 18, 0)), deadlineTiming.start)
    assertEquals(rawMoment(MinuteTimeDate(2026, 7, 12, 18, 1)), deadlineTiming.endExclusive)
  }

  /**
   * 验证回读以 canonical URI 作为业务身份，并将 EventKit identifier 仅保留为可失效缓存。
   *
   * 带 RRULE 的 fixture 必须是 SERIES_MASTER；SINGLE 与 recurrence 的组合是 foundation 明确拒绝的非可逆形状。
   */
  @Test
  fun rawManagedEventUsesUriAsIdentityAndIdentifierOnlyAsOpaqueCache() {
    val target = projection(
      kind = CalendarProjectionKind.SERIES_MASTER,
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 90, ZONE),
      recurrence = "FREQ=WEEKLY;BYDAY=MO,WE;COUNT=3",
      reminders = listOf(15),
    )
    val raw = IosEventKitRawEvent(
      eventIdentifier = "eventkit-cache-id",
      externalUri = target.externalUri,
      title = target.title,
      notes = target.description,
      start = rawMoment(MinuteTimeDate(2026, 7, 12, 9, 30)),
      endExclusive = rawMoment(MinuteTimeDate(2026, 7, 12, 11, 0)),
      timeZoneId = ZONE,
      allDay = false,
      recurrenceRules = listOf("COUNT=3;BYDAY=WE,MO;FREQ=WEEKLY"),
      alarms = listOf(IosEventKitRawAlarm(-15 * 60.0)),
      hasOccurrenceException = false,
    )

    val actual = IosEventKitCalendarAdapterFoundation.toManagedEvent(raw, SCOPE).mapped()

    assertEquals(target.id, actual.managedEvent.id)
    assertEquals("eventkit-cache-id", actual.managedEvent.platformEventRef.value)
    assertEquals(target.fingerprint, actual.managedEvent.fingerprint)
    assertEquals(
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 90, ZONE),
      actual.canonicalFields.timing
    )
    assertEquals("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=3", actual.canonicalFields.recurrenceRule)
    assertEquals(listOf(15), actual.canonicalFields.deviceReminderMinutes)
  }

  @Test
  fun allDayUsesDateHalfOpenIntervalWithoutDeviceTimeZone() {
    val target = projection(timing = CalendarTiming.AllDay(Date(2026, 7, 12), 2))
    val raw = IosEventKitRawEvent(
      eventIdentifier = "all-day-cache",
      externalUri = target.externalUri,
      title = target.title,
      notes = null,
      start = rawUtcMoment(2026, 7, 12),
      endExclusive = rawUtcMoment(2026, 7, 14),
      timeZoneId = null,
      allDay = true,
      recurrenceRules = emptyList(),
      alarms = emptyList(),
      hasOccurrenceException = false,
    )

    val actual = IosEventKitCalendarAdapterFoundation.toManagedEvent(raw, SCOPE).mapped()
    val timing = assertIs<CalendarTiming.AllDay>(actual.canonicalFields.timing)

    assertEquals(Date(2026, 7, 12), timing.startDate)
    assertEquals(2, timing.durationDays)
    assertEquals("", actual.canonicalFields.description)
  }

  /** 验证非分钟事件与原始小数 alarm 均不会被 mapper 静默归一。 */
  @Test
  fun nonMinuteUnsupportedRecurrenceOccurrenceExceptionAndFractionalAlarmFailClosed() {
    val target =
      projection(timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 60, ZONE))
    val raw = rawFor(target)

    assertEquals(
      IosEventKitMappingError.NON_MINUTE_PRECISION,
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(start = raw.start.copy(nanoseconds = 1)),
        SCOPE,
      ).unsupportedError(),
    )
    // EventKit 的 -60.5s 必须以原始 Double 失败，不能截断成 -1min。
    assertEquals(
      IosEventKitMappingError.UNSUPPORTED_ALARM,
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(alarms = listOf(IosEventKitRawAlarm(-60.5))),
        SCOPE,
      ).unsupportedError(),
    )
    assertEquals(
      IosEventKitMappingError.UNSUPPORTED_RECURRENCE,
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(recurrenceRules = listOf("FREQ=HOURLY")),
        SCOPE,
      ).unsupportedError(),
    )
    assertEquals(
      IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION,
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(hasOccurrenceException = true),
        SCOPE,
      ).unsupportedError(),
    )
  }

  /**
   * 验证 alarm offset 在转为分钟整数前严格校验 IEEE-754 原值。
   *
   * EventKit 正向 offset 不受支持；负向仅精确的整分钟可映射为提醒分钟数，任何相邻浮点值都必须
   * fail-closed，防止经过截断或舍入后被误接受。
   */
  @Test
  fun rawAlarmRejectsEveryNonMinuteDoubleBeforeIntegerConversion() {
    val target =
      projection(timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 60, ZONE))
    val raw = rawFor(target)
    fun mappedReminder(offset: Double): List<Int> =
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(alarms = listOf(IosEventKitRawAlarm(offset))),
        SCOPE,
      ).mapped().canonicalFields.deviceReminderMinutes

    fun unsupportedAlarm(offset: Double?): IosEventKitMappingError =
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(alarms = listOf(IosEventKitRawAlarm(offset))),
        SCOPE,
      ).unsupportedError()

    assertEquals(listOf(0), mappedReminder(-0.0))
    assertEquals(listOf(0), mappedReminder(0.0))

    // 精确 +60 秒与其两个 IEEE-754 邻居都不可映射为设备提醒。
    val positiveMinute = 60.0
    val positiveNextUp = Double.fromBits(positiveMinute.toBits() + 1L)
    val positiveNextDown = Double.fromBits(positiveMinute.toBits() - 1L)
    assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(positiveMinute))
    assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(positiveNextUp))
    assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(positiveNextDown))

    // 精确 -60 秒是唯一被接受的 1 分钟提醒；两个相邻值不得被截断或舍入接受。
    val negativeMinute = -60.0
    val negativeNextUp = Double.fromBits(negativeMinute.toBits() - 1L)
    val negativeNextDown = Double.fromBits(negativeMinute.toBits() + 1L)
    assertEquals(listOf(1), mappedReminder(negativeMinute))
    assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(negativeNextUp))
    assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(negativeNextDown))

    assertEquals(listOf(2), mappedReminder(-120.0))
    assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(null))

    val maximum = -(Int.MAX_VALUE.toDouble() * 60.0)
    assertEquals(listOf(Int.MAX_VALUE), mappedReminder(maximum))
    val maximumNextUp = Double.fromBits(maximum.toBits() - 1L)
    val maximumNextDown = Double.fromBits(maximum.toBits() + 1L)
    assertNotEquals(maximum, maximumNextUp)
    assertNotEquals(maximum, maximumNextDown)

    listOf(
      -60.5,
      -60.0000000005,
      60.5,
      60.0000000005,
      maximumNextUp,
      maximumNextDown,
      -61.0,
      maximum - 60.0,
      Double.NaN,
      Double.NEGATIVE_INFINITY,
      Double.POSITIVE_INFINITY,
    ).forEach { offset ->
      assertEquals(IosEventKitMappingError.UNSUPPORTED_ALARM, unsupportedAlarm(offset))
    }
  }


  /**
   * IANA 历史秒级 offset 产生的 instant 不能借 local 时分外观绕过分钟合同。
   *
   * `Asia/Shanghai` 在该历史日期的 offset 含秒；fixture 保证 raw instant 的 epoch 秒不是 60 的倍数，
   * 并锁定 mapper 在调用 `toLocalDateTime` 前返回稳定的精度错误。
   */
  @Test
  fun historicalSecondOffsetTimedReadFailsAsNonMinutePrecision() {
    val startLocal = MinuteTimeDate(1900, 1, 1, 9, 30)
    val endLocal = MinuteTimeDate(1900, 1, 1, 10, 30)
    val target = projection(
      timing = CalendarTiming.Timed(startLocal, 60, HISTORICAL_SECOND_OFFSET_ZONE),
    )
    val start = rawMoment(startLocal, HISTORICAL_SECOND_OFFSET_ZONE)
    val endExclusive = rawMoment(endLocal, HISTORICAL_SECOND_OFFSET_ZONE)
    assertNotEquals(0L, start.epochSeconds % 60L)
    assertNotEquals(0L, endExclusive.epochSeconds % 60L)

    assertEquals(
      IosEventKitMappingError.NON_MINUTE_PRECISION,
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        rawFor(target).copy(
          start = start,
          endExclusive = endExclusive,
          timeZoneId = HISTORICAL_SECOND_OFFSET_ZONE,
        ),
        SCOPE,
      ).unsupportedError(),
    )
  }


  /** 验证 Spring gap 不能被静默改写，Fall overlap 按 common 选定 instant 完整往返。 */
  @Test
  fun dstGapWriteFailsClosedAndOverlapRoundTripsCanonicalTiming() {
    val gap = projection(
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 3, 8, 2, 30), 60, NEW_YORK_ZONE),
    )
    assertEquals(
      IosEventKitMappingError.IRREVERSIBLE_WALL_TIME,
      IosEventKitCalendarAdapterFoundation.toWritePayload(gap).unsupportedError(),
    )

    val overlap = projection(
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 11, 1, 1, 30), 60, NEW_YORK_ZONE),
    )
    val payload = IosEventKitCalendarAdapterFoundation.toWritePayload(overlap).mapped()
    val timed = assertIs<IosEventKitWriteTiming.Timed>(payload.timing)
    val restored = IosEventKitCalendarAdapterFoundation.toManagedEvent(
      IosEventKitRawEvent(
        eventIdentifier = "new-york-overlap-cache",
        externalUri = overlap.externalUri,
        title = overlap.title,
        notes = overlap.description,
        start = timed.start,
        endExclusive = timed.endExclusive,
        timeZoneId = NEW_YORK_ZONE,
        allDay = false,
        recurrenceRules = emptyList(),
        alarms = emptyList(),
        hasOccurrenceException = false,
      ),
      SCOPE,
    ).mapped()

    assertEquals(overlap.timing, restored.canonicalFields.timing)
  }

  /** 写路径必须拒绝 URI kind 与 timing/RRULE 的不兼容组合。 */
  @Test
  fun writeRejectsEveryIncompatibleProjectionShape() {
    val minute = MinuteTimeDate(2026, 7, 12, 9, 30)
    val invalidProjections = listOf(
      projection(CalendarProjectionKind.DEADLINE, CalendarTiming.AllDay(Date(2026, 7, 12), 1)),
      projection(CalendarProjectionKind.DEADLINE, CalendarTiming.Timed(minute, 1, ZONE)),
      projection(
        CalendarProjectionKind.DEADLINE,
        CalendarTiming.Deadline(minute, ZONE),
        recurrence = "FREQ=DAILY",
      ),
      projection(CalendarProjectionKind.SERIES_MASTER, CalendarTiming.Timed(minute, 60, ZONE)),
      projection(
        CalendarProjectionKind.SERIES_MASTER,
        CalendarTiming.Deadline(minute, ZONE),
        recurrence = "FREQ=DAILY",
      ),
      projection(
        CalendarProjectionKind.SINGLE,
        CalendarTiming.Timed(minute, 60, ZONE),
        recurrence = "FREQ=DAILY",
      ),
      projection(CalendarProjectionKind.SINGLE, CalendarTiming.Deadline(minute, ZONE)),
    )

    invalidProjections.forEach { projection ->
      assertEquals(
        IosEventKitMappingError.INVALID_PROJECTION_SHAPE,
        IosEventKitCalendarAdapterFoundation.toWritePayload(projection).unsupportedError(),
      )
    }
  }

  /** 读取路径也必须依 URI kind 复用同一形状校验，不能仅相信 EventKit 的通用字段。 */
  @Test
  fun readRejectsIncompatibleUriKindTimingAndRruleShapes() {
    val deadline = projection(
      kind = CalendarProjectionKind.DEADLINE,
      timing = CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 18, 0), ZONE),
    )
    val deadlineRaw = rawFor(deadline).copy(
      start = rawMoment(MinuteTimeDate(2026, 7, 12, 18, 0)),
      endExclusive = rawMoment(MinuteTimeDate(2026, 7, 12, 18, 1)),
    )
    val series = projection(
      kind = CalendarProjectionKind.SERIES_MASTER,
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 60, ZONE),
      recurrence = "FREQ=DAILY",
    )
    val single = projection(
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 60, ZONE),
    )
    val incompatibleRawEvents = listOf(
      deadlineRaw.copy(
        start = rawUtcMoment(2026, 7, 12),
        endExclusive = rawUtcMoment(2026, 7, 13),
        timeZoneId = null,
        allDay = true,
      ),
      deadlineRaw.copy(recurrenceRules = listOf("FREQ=DAILY")),
      rawFor(series),
      rawFor(single).copy(recurrenceRules = listOf("FREQ=DAILY")),
    )

    incompatibleRawEvents.forEach { raw ->
      assertEquals(
        IosEventKitMappingError.INVALID_PROJECTION_SHAPE,
        IosEventKitCalendarAdapterFoundation.toManagedEvent(raw, SCOPE).unsupportedError(),
      )
    }
  }

  /**
   * 读取只接受 canonical v2 URI；缺失、畸形、非 canonical 文本与其他 scope 都不能产出托管事件。
   *
   * raw EventKit 字段即使其余部分完整，也不能替代 URI 身份或绕过当前导出空间隔离。
   */
  @Test
  fun toManagedEventRejectsInvalidCanonicalUriAndScopeMismatchWithoutManagedEvent() {
    val target = projection(
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 30), 60, ZONE),
    )
    val raw = rawFor(target)
    val nonCanonicalV2Uri =
      "cyxbs://schedule?scope=${SCOPE.value}&v=2&scheduleId=${SCHEDULE_ID.value}&kind=single"

    listOf(
      null,
      "not-a-uri",
      nonCanonicalV2Uri,
      target.externalUri.replace("v=2", "v=1"),
      target.externalUri.replace("&kind=single", "&kind=single&unknown=value"),
    ).forEach { externalUri ->
      assertEquals(
        IosEventKitMappingError.INVALID_CANONICAL_URI,
        IosEventKitCalendarAdapterFoundation.toManagedEvent(
          raw.copy(externalUri = externalUri),
          SCOPE,
        ).unsupportedError(),
      )
    }

    val otherScopeUri = target.externalUri.replace(
      "scope=${SCOPE.value}",
      "scope=other_scope_1",
    )
    assertEquals(
      IosEventKitMappingError.SCOPE_MISMATCH,
      IosEventKitCalendarAdapterFoundation.toManagedEvent(
        raw.copy(externalUri = otherScopeUri),
        SCOPE,
      ).unsupportedError(),
    )
  }


  private fun projection(
    kind: CalendarProjectionKind = CalendarProjectionKind.SINGLE,
    timing: CalendarTiming,
    recurrence: String? = null,
    reminders: List<Int> = emptyList(),
  ): CalendarEventProjection {
    val id = CalendarProjectionId(SCOPE, SCHEDULE_ID, kind)
    val uri = CalendarProjectionUriCodec.encode(id)
    return CalendarEventProjection(
      id = id,
      externalUri = uri,
      title = "EventKit foundation",
      description = "纯映射",
      timing = timing,
      recurrenceRule = recurrence,
      deviceReminderMinutes = reminders,
      fingerprint = CalendarProjectionFingerprint.compute(
        externalUri = uri,
        title = "EventKit foundation",
        description = "纯映射",
        timing = timing,
        recurrenceRule = recurrence,
        reminderMinutes = reminders,
      ),
    )
  }

  private fun rawFor(target: CalendarEventProjection): IosEventKitRawEvent = IosEventKitRawEvent(
    eventIdentifier = "eventkit-cache-id",
    externalUri = target.externalUri,
    title = target.title,
    notes = target.description,
    start = rawMoment(MinuteTimeDate(2026, 7, 12, 9, 30)),
    endExclusive = rawMoment(MinuteTimeDate(2026, 7, 12, 10, 30)),
    timeZoneId = ZONE,
    allDay = false,
    recurrenceRules = emptyList(),
    alarms = emptyList(),
    hasOccurrenceException = false,
  )

  /**
   * 用指定 IANA 时区创建 raw instant。
   *
   * 默认时区用于普通 fixture；历史秒级 offset fixture 显式传入时区，以锁定 mapper 在 local 转换前拒绝
   * 非整分钟 epoch 秒的 fail-closed 边界。
   */
  private fun rawMoment(
    value: MinuteTimeDate,
    timeZoneId: String = ZONE,
  ): IosEventKitRawMoment {
    val instant = value.toLocalDateTime().toInstant(TimeZone.of(timeZoneId))
    return IosEventKitRawMoment(instant.epochSeconds, instant.nanosecondsOfSecond)
  }

  /** 全天测试只用 UTC 午夜，以验证日期而非固定 24 小时的外部合同。 */
  private fun rawUtcMoment(year: Int, month: Int, day: Int): IosEventKitRawMoment {
    val instant = kotlinx.datetime.LocalDateTime(year, month, day, 0, 0).toInstant(TimeZone.UTC)
    return IosEventKitRawMoment(instant.epochSeconds, instant.nanosecondsOfSecond)
  }

  private fun <T> IosEventKitMappingResult<T>.mapped(): T =
    assertIs<IosEventKitMappingResult.Mapped<T>>(this).value

  private fun IosEventKitMappingResult<*>.unsupportedError(): IosEventKitMappingError =
    assertIs<IosEventKitMappingResult.Unsupported>(this).error

  private companion object {
    val SCOPE = CalendarExportScope("ios_eventkit_foundation")
    val SCHEDULE_ID = ScheduleId("018f7d5a-1234-7abc-8def-1234567890ab")
    const val ZONE = "Asia/Shanghai"
    const val NEW_YORK_ZONE = "America/New_York"
    const val HISTORICAL_SECOND_OFFSET_ZONE = "Asia/Shanghai"
  }
}
