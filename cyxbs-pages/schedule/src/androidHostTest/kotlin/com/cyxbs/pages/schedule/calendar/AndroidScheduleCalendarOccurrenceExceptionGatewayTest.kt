package com.cyxbs.pages.schedule.calendar

import android.provider.CalendarContract
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionOperation
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Android 原生 occurrence 写入预检的 JVM host 合同；只检查普通值计划，不构造 Provider 或设备。 */
class AndroidScheduleCalendarOccurrenceExceptionGatewayTest {
  @Test
  fun movedTimedExceptionKeepsOriginalDstIdentityAndCanonicalUri() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 11, 1, 1, 30), "America/New_York", false,
    )
    val exception = occurrence(
      recurrenceId = recurrenceId,
      timing = CalendarTiming.Timed(
        MinuteTimeDate(2026, 11, 1, 3, 30), 60, "America/New_York",
      ),
      operation = CalendarOccurrenceExceptionOperation.UPSERT,
    )

    val prepared = AndroidOccurrenceExceptionWritePlanner.prepare(master(
      exceptions = listOf(exception),
      timing = CalendarTiming.Timed(
        MinuteTimeDate(2026, 11, 1, 1, 30), 60, "America/New_York",
      ),
    )).single()

    assertEquals(recurrenceId, prepared.projection.id.recurrenceId)
    assertEquals(CalendarProjectionUriCodec.encode(exception.id), prepared.projection.externalUri)
    assertEquals(1_793_511_000_000L, prepared.originalInstanceTimeMillis)
    assertEquals(0, prepared.originalAllDay)
    assertNull(prepared.rDate)
  }

  @Test
  fun allDayCompletionAndCancellationUseExplicitCanceledRows() {
    val completed = occurrence(
      RecurrenceId(MinuteTimeDate(2026, 3, 8, 0, 0), null, true),
      CalendarTiming.AllDay(Date(2026, 3, 9), 1),
      CalendarOccurrenceExceptionOperation.CANCEL,
    )
    val cancelled = occurrence(
      RecurrenceId(MinuteTimeDate(2026, 3, 9, 0, 0), null, true),
      CalendarTiming.AllDay(Date(2026, 3, 9), 1),
      CalendarOccurrenceExceptionOperation.CANCEL,
    )

    val prepared = AndroidOccurrenceExceptionWritePlanner.prepare(
      master(
        exceptions = listOf(completed, cancelled).sortedBy { it.externalUri },
        timing = CalendarTiming.AllDay(Date(2026, 3, 8), 1),
      ),
    )

    assertEquals(listOf(1, 1), prepared.map { it.originalAllDay })
    assertEquals(listOf(android.provider.CalendarContract.Events.STATUS_CANCELED, android.provider.CalendarContract.Events.STATUS_CANCELED), prepared.map { it.providerStatus })
    assertEquals(prepared.sortedBy { it.projection.externalUri }, prepared)
    assertEquals(listOf(null, null), prepared.map { it.rDate })
  }

  /** production 写入直接消费该计划；锁定原始槽位、原全天标志与 exception 自身提醒。 */
  @Test
  fun productionProviderWritePlanKeepsOriginalIdentityAndUnsetRecurrenceFields() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false,
    )
    val exception = occurrence(
      recurrenceId,
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Asia/Shanghai"),
      CalendarOccurrenceExceptionOperation.UPSERT,
      reminderMinutes = listOf(5, 10),
    )
    val prepared = AndroidOccurrenceExceptionWritePlanner.prepare(master(listOf(exception))).single()

    assertEquals(
      AndroidOccurrenceExceptionWritePlanner.originalInstanceTimeMillis(recurrenceId),
      prepared.originalInstanceTimeMillis,
    )
    assertEquals(0, prepared.originalAllDay)
    assertEquals(listOf(5, 10), prepared.projection.deviceReminderMinutes)
    assertNull(prepared.recurrenceRule)
    assertNull(prepared.rDate)
  }

  /** 重复 Deadline 使用 series master，零时长例外可沿用与 Timed 相同的原生关系。 */
  @Test
  fun recurringDeadlineExceptionPreflightProducesNativeWrite() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 7, 13, 23, 0), "Asia/Shanghai", false,
    )
    val deadlineException = occurrence(
      recurrenceId,
      CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 13, 23, 0), "Asia/Shanghai"),
      CalendarOccurrenceExceptionOperation.CANCEL,
    )
    val deadlineMaster = master(
      exceptions = listOf(deadlineException),
      timing = CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 23, 0), "Asia/Shanghai"),
      kind = CalendarProjectionKind.SERIES_MASTER,
    )
    val fake = CountingWriteDependencies()

    AndroidOccurrenceExceptionWritePlanner.withPreparedWrite(deadlineMaster, fake::write)

    assertEquals(1, fake.registryLookups)
    assertEquals(1, fake.calendarWrites)
    assertEquals(2, fake.eventWrites)
    assertEquals(0, fake.reminderWrites)
  }

  /** 原始槽位身份与当前展示形态彼此独立，单次调整可切换时间类型和当前时区。 */
  @Test
  fun exceptionTimingKindAndTimezoneMayDifferFromOriginalSlot() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false,
    )
    val deadline = occurrence(
      recurrenceId,
      CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 13, 10, 0), "UTC"),
      CalendarOccurrenceExceptionOperation.UPSERT,
    )
    val allDay = occurrence(
      recurrenceId.copy(originalDateTime = MinuteTimeDate(2026, 7, 14, 9, 0)),
      CalendarTiming.AllDay(Date(2026, 7, 15), 1),
      CalendarOccurrenceExceptionOperation.UPSERT,
    )

    val prepared = AndroidOccurrenceExceptionWritePlanner.prepare(
      master(listOf(deadline, allDay).sortedBy { it.externalUri }),
    )

    assertEquals(listOf(0, 0), prepared.map { it.originalAllDay })
    assertTrue(prepared.any { it.projection.timing is CalendarTiming.Deadline })
    assertTrue(prepared.any { it.projection.timing is CalendarTiming.AllDay })
  }

  /**
   * master/exception 的 URI、聚合 fingerprint、有效时间、时区关系和 reminder 都必须在 registry/Provider 前失败。
   */
  @Test
  fun invalidPreflightProducesZeroRegistryCalendarEventAndReminderWrites() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false,
    )
    val valid = occurrence(
      recurrenceId,
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Asia/Shanghai"),
      CalendarOccurrenceExceptionOperation.UPSERT,
    )
    val malformedUri = valid.copy(externalUri = valid.externalUri + "&alias=1")
    val invalidTimeZone = occurrence(
      recurrenceId,
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Not/AZone"),
      CalendarOccurrenceExceptionOperation.UPSERT,
    )
    val invalidDuration = occurrence(
      recurrenceId,
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 0, "Asia/Shanghai"),
      CalendarOccurrenceExceptionOperation.UPSERT,
    )
    val invalidReminders = occurrence(
      recurrenceId,
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Asia/Shanghai"),
      CalendarOccurrenceExceptionOperation.UPSERT,
      reminderMinutes = listOf(10, 10),
    )
    val canonicalMaster = master(listOf(valid))
    val invalidMasters = listOf(
      master(listOf(malformedUri)),
      master(listOf(invalidTimeZone)),
      master(listOf(invalidDuration)),
      master(listOf(invalidReminders)),
      canonicalMaster.copy(externalUri = canonicalMaster.externalUri + "&alias=1"),
      canonicalMaster.copy(fingerprint = canonicalMaster.fingerprint + "-stale"),
    )
    val fake = CountingWriteDependencies()

    invalidMasters.forEach { invalid ->
      assertFailsWith<IllegalArgumentException> {
        AndroidOccurrenceExceptionWritePlanner.withPreparedWrite(invalid, fake::write)
      }
    }

    assertEquals(0, fake.registryLookups)
    assertEquals(0, fake.calendarWrites)
    assertEquals(0, fake.eventWrites)
    assertEquals(0, fake.reminderWrites)
  }

  /**
   * fresh preflight 后只要 Provider 改写 ORIGINAL_INSTANCE_TIME 或 ORIGINAL_ALL_DAY，expected-count 就必须失败；
   * host transaction 先删除 reminder 再失败，用回滚结果证明不会留下半替换状态。
   */
  @Test
  fun replacementDeleteIdentityDriftFailsExpectedCountAndRollsBackWholeBatch() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false,
    )
    val exception = occurrence(
      recurrenceId,
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Asia/Shanghai"),
      CalendarOccurrenceExceptionOperation.UPSERT,
    )
    val originalMillis = AndroidOccurrenceExceptionWritePlanner.originalInstanceTimeMillis(recurrenceId)
    val snapshot = AndroidManagedCalendarSnapshotOccurrenceException(
      projection = exception,
      platformEventRef = AndroidCalendarEventRefCodec.encode(42L),
      masterEventRef = AndroidCalendarEventRefCodec.encode(41L),
      originalInstanceTimeMillis = originalMillis,
    )
    val selection = AndroidOccurrenceExceptionWritePlanner.replacementDeleteSelection(
      existing = snapshot,
      calendarId = 77L,
      masterEventId = 41L,
      packageName = "com.cyxbs.test",
    )
    assertTrue(CalendarContract.Events.ORIGINAL_INSTANCE_TIME in selection.selection)
    assertTrue(CalendarContract.Events.ORIGINAL_ALL_DAY in selection.selection)
    val originalRow = FakeOccurrenceRow(
      eventId = 42L,
      calendarId = 77L,
      masterEventId = 41L,
      originalInstanceTimeMillis = originalMillis,
      originalAllDay = 0,
      packageName = "com.cyxbs.test",
      externalUri = exception.externalUri,
    )

    listOf(
      originalRow.copy(originalInstanceTimeMillis = originalMillis + 60_000L),
      originalRow.copy(originalAllDay = 1),
    ).forEach { drifted ->
      val fake = AtomicReplacementBatchFake(drifted, reminderMinutes = listOf(5))

      assertFailsWith<IllegalStateException> { fake.apply(selection) }

      assertEquals(drifted, fake.event)
      assertEquals(listOf(5), fake.reminderMinutes)
      assertEquals(0, fake.replacementInsertCount)
    }
  }

  /** 构造包含一个 Android 原生子计划的唯一 series master。 */
  private fun master(
    exceptions: List<CalendarOccurrenceExceptionProjection>,
    timing: CalendarTiming = CalendarTiming.Timed(
      MinuteTimeDate(2026, 7, 12, 9, 0), 60, "Asia/Shanghai",
    ),
    kind: CalendarProjectionKind = CalendarProjectionKind.SERIES_MASTER,
  ): CalendarEventProjection {
    val id = CalendarProjectionId(SCOPE, SCHEDULE_ID, kind)
    val uri = CalendarProjectionUriCodec.encode(id)
    return CalendarEventProjection(
      id = id,
      externalUri = uri,
      title = "master",
      description = "description",
      timing = timing,
      recurrenceRule = "FREQ=DAILY",
      deviceReminderMinutes = emptyList(),
      fingerprint = CalendarProjectionFingerprint.compute(
        uri, "master", "description", timing, "FREQ=DAILY", emptyList(), exceptions,
      ),
      nativeOccurrenceExceptions = exceptions,
    )
  }

  /** 构造 canonical occurrence 目标，原身份与移动后的内容时间保持分离。 */
  private fun occurrence(
    recurrenceId: RecurrenceId,
    timing: CalendarTiming,
    operation: CalendarOccurrenceExceptionOperation,
    reminderMinutes: List<Int> = emptyList(),
  ): CalendarOccurrenceExceptionProjection {
    val id = CalendarProjectionId(
      SCOPE, SCHEDULE_ID, CalendarProjectionKind.OCCURRENCE_EXCEPTION, recurrenceId,
    )
    val uri = CalendarProjectionUriCodec.encode(id)
    return CalendarOccurrenceExceptionProjection(
      id = id,
      externalUri = uri,
      title = "exception",
      description = "description",
      timing = timing,
      deviceReminderMinutes = reminderMinutes,
      operation = operation,
      fingerprint = CalendarProjectionFingerprint.computeOccurrenceException(
        uri, "exception", "description", timing, reminderMinutes, operation,
      ),
    )
  }

  /** 模拟 createEvent 在 production preflight 之后才会访问的 registry 与三类 Provider 资源。 */
  private class CountingWriteDependencies {
    var registryLookups = 0
    var calendarWrites = 0
    var eventWrites = 0
    var reminderWrites = 0

    fun write(prepared: List<AndroidPreparedOccurrenceExceptionWrite>) {
      registryLookups++
      calendarWrites++
      eventWrites += 1 + prepared.size
      reminderWrites += prepared.sumOf { it.projection.deviceReminderMinutes.size }
    }
  }

  /** host fake 中参与完整 compare-and-delete 的 Provider occurrence 行。 */
  private data class FakeOccurrenceRow(
    val eventId: Long,
    val calendarId: Long,
    val masterEventId: Long,
    val originalInstanceTimeMillis: Long,
    val originalAllDay: Int,
    val packageName: String,
    val externalUri: String,
  )

  /**
   * 模拟 Calendar Provider 的原子 batch：先删 reminder，event expected-count 不为 1 时恢复事务前快照。
   */
  private class AtomicReplacementBatchFake(
    event: FakeOccurrenceRow,
    reminderMinutes: List<Int>,
  ) {
    var event: FakeOccurrenceRow? = event
      private set
    var reminderMinutes: List<Int> = reminderMinutes
      private set
    var replacementInsertCount: Int = 0
      private set

    fun apply(selection: AndroidOccurrenceExceptionDeleteSelection) {
      val originalEvent = event
      val originalReminders = reminderMinutes
      val originalInsertCount = replacementInsertCount
      try {
        reminderMinutes = emptyList()
        val matched = event?.let { row -> if (selection.matches(row)) 1 else 0 } ?: 0
        check(matched == selection.expectedCount) { "Provider expected-count mismatch" }
        event = null
        replacementInsertCount++
      } catch (failure: Throwable) {
        event = originalEvent
        reminderMinutes = originalReminders
        replacementInsertCount = originalInsertCount
        throw failure
      }
    }

    /** selectionArgs 顺序与 production ContentProviderOperation 共用，确保 host seam 覆盖完整原身份。 */
    private fun AndroidOccurrenceExceptionDeleteSelection.matches(row: FakeOccurrenceRow): Boolean =
      selectionArgs == listOf(
        row.eventId.toString(),
        row.calendarId.toString(),
        row.masterEventId.toString(),
        row.originalInstanceTimeMillis.toString(),
        row.originalAllDay.toString(),
        row.packageName,
        row.externalUri,
      )
  }

  private companion object {
    val SCOPE = CalendarExportScope("android_occurrence_host")
    val SCHEDULE_ID = ScheduleId("018f0f7c-6000-7000-8000-000000000208")
  }
}
