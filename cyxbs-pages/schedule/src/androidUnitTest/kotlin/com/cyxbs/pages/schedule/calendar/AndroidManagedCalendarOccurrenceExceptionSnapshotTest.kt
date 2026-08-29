package com.cyxbs.pages.schedule.calendar

import android.provider.CalendarContract
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.calendar.AndroidManagedCalendarIdentifierCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarOccurrenceExceptionOperation
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/** occurrence relationship 聚合的 Android JVM host 合同；全部 Provider 行来自内存 fake。 */
class AndroidManagedCalendarOccurrenceExceptionSnapshotTest {
  @Test
  fun aggregatesCanonicalExceptionIntoOneSeriesMasterSnapshot() {
    val master = masterRow(41L)
    val occurrence = occurrenceRow(master.eventId, 42L)
    val fake = SnapshotReadHostFake(
      calendarId = 77L,
      events = listOf(occurrence, master),
    )

    val snapshot = AndroidManagedCalendarSnapshotAcquirer(fake, ACCOUNT).acquire(SCOPE)
    val present = assertIs<AndroidManagedCalendarSnapshot.Present>(snapshot)
    val event = present.events.single()
    val native = event.occurrenceExceptions.single()

    assertEquals(AndroidManagedCalendarIdentifierCodec.encode(77L, HOST_INCARNATION), present.calendarIdentifier)
    assertEquals(MASTER_ID, event.projectionId)
    assertEquals(EXCEPTION_ID, native.projection.id)
    assertEquals(PlatformRef(41L), native.masterEventRef)
    assertEquals(CalendarOccurrenceExceptionOperation.UPSERT, native.projection.operation)
    assertNotEquals(masterOnlyFingerprint(), event.providerFingerprint)
  }

  /** 参数重排与非必要转义仍属于当前 app/scope/Schedule 的 v2 候选；full 与增量读取都必须整轮失败。 */
  @Test
  fun fullAndIncrementalSnapshotsRejectReorderedOrEscapedManagedUris() {
    val reordered =
      "cyxbs://schedule?scope=${SCOPE.value}&v=2&scheduleId=${SCHEDULE_ID.value}&kind=series"
    val escaped = CalendarProjectionUriCodec.encode(MASTER_ID)
      .replace("scope=${SCOPE.value}", "scope=%6F${SCOPE.value.drop(1)}")
      .replace("scheduleId=${SCHEDULE_ID.value}", "scheduleId=%30${SCHEDULE_ID.value.drop(1)}")

    listOf(reordered, escaped).forEach { noncanonicalUri ->
      listOf<Set<ScheduleId>?>(null, setOf(SCHEDULE_ID)).forEach { requestedIds ->
        val fake = SnapshotReadHostFake(
          events = listOf(masterRow(41L).copy(customAppUri = noncanonicalUri)),
        )

        assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
          AndroidManagedCalendarSnapshotAcquirer(fake, ACCOUNT).acquire(SCOPE, requestedIds)
        }
        assertEquals(
          listOf("permission", "calendar", "events:${requestedIds?.size ?: "all"}"),
          fake.operations,
        )
      }
    }
  }

  @Test
  fun missingOrAmbiguousMasterRejectsWholeSnapshot() {
    val occurrence = occurrenceRow(41L, 42L)
    assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
      AndroidManagedCalendarSnapshotAcquirer(
        SnapshotReadHostFake(events = listOf(occurrence)), ACCOUNT,
      ).acquire(SCOPE)
    }
    assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
      AndroidManagedCalendarSnapshotAcquirer(
        SnapshotReadHostFake(events = listOf(masterRow(41L), masterRow(43L), occurrence)), ACCOUNT,
      ).acquire(SCOPE)
    }
  }

  /** Deadline+RRULE 顶层仍是 kind=deadline；snapshot 不得把它暗中提升为原生 exception master。 */
  @Test
  fun recurringDeadlineOccurrenceRowsFailClosedAsUnsupportedMasterIdentity() {
    assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
      AndroidManagedCalendarSnapshotAcquirer(
        SnapshotReadHostFake(
          events = listOf(deadlineMasterRow(41L), occurrenceRow(41L, 42L)),
        ),
        ACCOUNT,
      ).acquire(SCOPE)
    }
  }

  @Test
  fun malformedOrProviderDriftRelationshipAndRdateFailClosed() {
    val invalidRows = listOf(
      occurrenceRow(999L, 42L),
      occurrenceRow(41L, 42L).copy(originalInstanceTime = 1L),
      occurrenceRow(41L, 42L).copy(originalAllDay = 1),
      occurrenceRow(41L, 42L).copy(customAppUri = CalendarProjectionUriCodec.encode(EXCEPTION_ID) + "&alias=1"),
      occurrenceRow(41L, 42L).copy(rDate = "20260713T010000Z"),
    )
    invalidRows.forEach { invalid ->
      assertFailsWith<AndroidScheduleCalendarGateway.CalendarProviderReadException> {
        AndroidManagedCalendarSnapshotAcquirer(
          SnapshotReadHostFake(events = listOf(masterRow(41L), invalid)), ACCOUNT,
        ).acquire(SCOPE)
      }
    }
  }

  /** Provider series master 使用 DURATION，且 RDATE 必须保持未设置。 */
  private fun masterRow(eventId: Long) = hostEventRow(MASTER_ID, eventId).copy(
    dtEnd = null,
    duration = "PT60M",
    recurrenceRule = "FREQ=DAILY",
    rDate = null,
  )

  /** Deadline 重复 master 维持 kind=deadline 与零分钟 DURATION，用于证明例外链不会擅自接受该身份。 */
  private fun deadlineMasterRow(eventId: Long) = hostEventRow(DEADLINE_MASTER_ID, eventId).copy(
    dtEnd = null,
    duration = "PT0M",
    recurrenceRule = "FREQ=DAILY",
    rDate = null,
  )

  /** 构造拥有 ORIGINAL_ID/original-instance/status 的普通内存 exception row。 */
  private fun occurrenceRow(masterEventId: Long, eventId: Long): AndroidManagedCalendarSnapshotEventRow {
    val originalMillis = AndroidOccurrenceExceptionWritePlanner.originalInstanceTimeMillis(RECURRENCE_ID)
    return hostEventRow(EXCEPTION_ID, eventId).copy(
      dtStart = originalMillis + 60 * 60_000L,
      dtEnd = originalMillis + 2 * 60 * 60_000L,
      originalId = masterEventId,
      originalInstanceTime = originalMillis,
      originalAllDay = 0,
      status = CalendarContract.Events.STATUS_CONFIRMED,
      rDate = null,
    )
  }

  /** 不含子计划时的 master-only fingerprint，用于证明聚合 fingerprint 纳入 exception。 */
  private fun masterOnlyFingerprint(): String = com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint.compute(
    externalUri = CalendarProjectionUriCodec.encode(MASTER_ID),
    title = "host title",
    description = "host description",
    timing = com.cyxbs.pages.schedule.domain.calendar.CalendarTiming.Timed(
      MinuteTimeDate(2025, 1, 1, 0, 0), 60, "UTC",
    ),
    recurrenceRule = "FREQ=DAILY",
    reminderMinutes = emptyList(),
  )

  private fun PlatformRef(eventId: Long) = AndroidCalendarEventRefCodec.encode(eventId)

  private companion object {
    const val ACCOUNT = "occurrence-host-account"
    val SCOPE = CalendarExportScope("occurrence_snapshot_host")
    val SCHEDULE_ID = ScheduleId("018f0f7c-6000-7000-8000-000000000208")
    val RECURRENCE_ID = RecurrenceId(
      MinuteTimeDate(2025, 1, 2, 0, 0), "UTC", false,
    )
    val MASTER_ID = CalendarProjectionId(SCOPE, SCHEDULE_ID, CalendarProjectionKind.SERIES_MASTER)
    val DEADLINE_MASTER_ID = CalendarProjectionId(SCOPE, SCHEDULE_ID, CalendarProjectionKind.DEADLINE)
    val EXCEPTION_ID = CalendarProjectionId(
      SCOPE, SCHEDULE_ID, CalendarProjectionKind.OCCURRENCE_EXCEPTION, RECURRENCE_ID,
    )
  }
}
