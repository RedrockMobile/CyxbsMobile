package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.OccurrenceTime
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduleCalendarProjectionTest {
  private val scope = CalendarExportScope("scope_0123456789abcdef")
  private val scheduleId = ScheduleId("018f0f7c-6000-7000-8000-000000000001")
  private val now = Instant.parse("2026-07-12T00:00:00Z")

  @Test
  fun uriRoundTripPreservesCanonicalIdentity() {
    val id = CalendarProjectionId(scope, scheduleId, CalendarProjectionKind.SERIES_MASTER)
    val uri = CalendarProjectionUriCodec.encode(id)
    assertEquals(
      "cyxbs://schedule?v=2&scope=scope_0123456789abcdef&scheduleId=$scheduleId&kind=series",
      uri,
    )
    assertEquals(id, CalendarProjectionUriCodec.decodeOrNull(uri))
  }

  @Test
  fun occurrenceUriPreservesOriginalIdentityAndEscapesTimeZone() {
    val recurrenceId = RecurrenceId(
      MinuteTimeDate(2026, 3, 29, 9, 30),
      "America/New_York",
      false,
    )
    val id = CalendarProjectionId(
      scope, scheduleId, CalendarProjectionKind.OCCURRENCE_EXCEPTION, recurrenceId,
    )
    val uri = CalendarProjectionUriCodec.encode(id)
    assertTrue("recurrenceLocal=2026-03-29%2009%3A30" in uri)
    assertTrue("timeZoneId=America%2FNew_York" in uri)
    assertEquals(id, CalendarProjectionUriCodec.decodeOrNull(uri))
  }

  @Test
  fun uriDecoderRejectsUntrustedVariants() {
    val valid = CalendarProjectionUriCodec.encode(
      CalendarProjectionId(scope, scheduleId, CalendarProjectionKind.SINGLE),
    )
    assertNull(CalendarProjectionUriCodec.decodeOrNull(valid.replace("v=2", "v=1")))
    assertNull(CalendarProjectionUriCodec.decodeOrNull(valid.replace(scheduleId.value, scheduleId.value.uppercase())))
    assertNull(CalendarProjectionUriCodec.decodeOrNull("$valid&scope=scope_0123456789abcdef"))
    assertNull(CalendarProjectionUriCodec.decodeOrNull("$valid&unknown=value"))
    assertNull(CalendarProjectionUriCodec.decodeOrNull(
      "cyxbs://schedule?scope=scope_0123456789abcdef&v=2&scheduleId=$scheduleId&kind=single",
    ))
    assertNull(CalendarProjectionUriCodec.decodeOrNull(valid.replace("kind=single", "kind=occurrence")))
    assertNull(CalendarProjectionUriCodec.decodeOrNull(valid.replace("scope=", "scope=%ZZ")))
    val occurrence = CalendarProjectionUriCodec.encode(CalendarProjectionId(
      scope,
      scheduleId,
      CalendarProjectionKind.OCCURRENCE_EXCEPTION,
      RecurrenceId(MinuteTimeDate(2026, 7, 12, 9, 0), "Asia/Shanghai", false),
    ))
    assertNull(CalendarProjectionUriCodec.decodeOrNull(occurrence.replace("Asia%2FShanghai", "Not%2FAZone")))
    assertFailsWith<IllegalArgumentException> { CalendarExportScope("12345") }
  }

  @Test
  fun projectionKeepsTimingAndDeviceReminder() {
    val schedule = schedule(
      timing = ScheduleTiming.Timed(
        MinuteTimeDate(2026, 11, 1, 9, 0), 90, "America/New_York",
      ),
      reminder = reminder(10),
    )
    val event = project(schedule).events.single()
    assertEquals(
      CalendarTiming.Timed(MinuteTimeDate(2026, 11, 1, 9, 0), 90, "America/New_York"),
      event.timing,
    )
    assertEquals(listOf(10), event.deviceReminderMinutes)
    assertNull(event.recurrenceRule)
  }

  @Test
  fun allDayProjectionKeepsSingleDateAcrossDst() {
    val event = project(schedule(timing = ScheduleTiming.AllDay(Date(2026, 3, 8)))).events.single()
    assertEquals(CalendarTiming.AllDay(Date(2026, 3, 8), 1), event.timing)
  }

  @Test
  fun deadlineProjectionKeepsDeadlineSemantics() {
    val event = project(
      schedule(timing = ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 12, 23, 0), "Asia/Shanghai")),
    ).events.single()
    assertEquals(
      CalendarTiming.Deadline(MinuteTimeDate(2026, 7, 12, 23, 0), "Asia/Shanghai"),
      event.timing,
    )
  }

  @Test
  fun completedAndUnscheduledNonRepeatingSchedulesAreOmitted() {
    val completed = schedule(todoState = ScheduleTodoState.COMPLETED)
    val unscheduled = schedule(
      id = ScheduleId("018f0f7c-6000-7000-8000-000000000002"),
      timing = ScheduleTiming.Unscheduled,
    )
    val result = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(completed, unscheduled), emptyList()), scope,
    )
    assertTrue(result.events.isEmpty())
    assertTrue(result.unsupported.isEmpty())
  }

  @Test
  fun recurringMasterUsesCanonicalRRule() {
    val rule = RecurrenceRule(
      frequency = RecurrenceFrequency.MONTHLY,
      interval = 2,
      byMonthDays = linkedSetOf(20, -1, 1),
      end = RecurrenceEnd.Count(8),
    )
    val event = project(schedule(recurrence = rule)).events.single()
    assertEquals(CalendarProjectionKind.SERIES_MASTER, event.id.kind)
    assertEquals(
      "FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=-1,1,20;COUNT=8",
      event.recurrenceRule,
    )
  }

  @Test
  fun recurrenceUntilUsesScheduleTimezoneAndUtcBasicFormat() {
    val event = project(schedule(recurrence = RecurrenceRule(
      RecurrenceFrequency.DAILY,
      end = RecurrenceEnd.Until(Date(2026, 12, 31)),
    ))).events.single()
    assertEquals("FREQ=DAILY;UNTIL=20261231T155959Z", event.recurrenceRule)
  }

  @Test
  fun allDayRecurrenceUntilUsesDateValueType() {
    val event = project(schedule(
      timing = ScheduleTiming.AllDay(Date(2026, 7, 12)),
      recurrence = RecurrenceRule(
        RecurrenceFrequency.DAILY,
        end = RecurrenceEnd.Until(Date(2026, 12, 31)),
      ),
    )).events.single()
    assertEquals("FREQ=DAILY;UNTIL=20261231", event.recurrenceRule)
  }

  @Test
  fun providerRRuleCanonicalizationIgnoresOrderAndRejectsUnsupportedValues() {
    assertEquals(
      "FREQ=MONTHLY;INTERVAL=2;BYDAY=MO,FR;BYMONTHDAY=-1,1,20;BYMONTH=2,12;COUNT=8",
      CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
        "count=8;bymonth=12,2;byday=FR,MO;freq=monthly;bymonthday=20,-1,1;interval=2",
        allDay = false,
      ),
    )
    assertEquals(
      "FREQ=DAILY;UNTIL=20261231",
      CalendarRecurrenceCanonicalizer.canonicalizeOrNull("UNTIL=20261231;FREQ=DAILY", allDay = true),
    )
    assertNull(CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
      "FREQ=DAILY;UNTIL=20261231T000000Z", allDay = true,
    ))
    assertNull(CalendarRecurrenceCanonicalizer.canonicalizeOrNull("FREQ=DAILY;BYSETPOS=1", allDay = false))
    assertNull(CalendarRecurrenceCanonicalizer.canonicalizeOrNull("FREQ=DAILY;FREQ=WEEKLY", allDay = false))
  }

  @Test
  fun activePatchAndMoveStayInsideOneSeriesMasterPlan() {
    val recurring = schedule(recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))
    val recurrenceId = RecurrenceId(MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false)
    val exception = occurrenceException(
      recurring,
      recurrenceId,
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(
        time = FieldPatch.Replace(
          OccurrenceTime.TimeRange(13 * 60, 60, "Asia/Shanghai"),
        ),
        title = FieldPatch.Replace("移动后的标题"),
      ),
    )

    val result = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(recurring), listOf(exception)),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    )
    val master = result.events.single()
    val native = master.nativeOccurrenceExceptions.single()

    assertTrue(result.unsupported.isEmpty())
    assertEquals(CalendarProjectionKind.SERIES_MASTER, master.id.kind)
    assertEquals(CalendarOccurrenceExceptionOperation.UPSERT, native.operation)
    assertEquals(recurrenceId, native.id.recurrenceId)
    assertEquals(
      CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 13, 0), 60, "Asia/Shanghai"),
      native.timing,
    )
    assertEquals("移动后的标题", native.title)
  }

  /**
   * 未升级的 finalized Android outbound 与 iOS mapper 都走默认 capability；仅 exception 变化时必须保留旧 master，
   * 不能生成它们无法消费或重算的聚合 fingerprint。
   */
  @Test
  fun defaultConsumerCapabilitiesFailClosedOnExceptionOnlyChange() {
    val recurring = schedule(recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))
    val previousMaster = project(recurring).events.single()
    val exception = occurrenceException(
      recurring,
      RecurrenceId(MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(title = FieldPatch.Replace("仅修改单次实例")),
    )

    val result = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(recurring), listOf(exception)),
      scope,
    )
    val plan = CalendarExportPlanner.plan(
      result,
      listOf(ManagedCalendarEvent(previousMaster.id, previousMaster.fingerprint, PlatformCalendarEventRef("208"))),
      scope,
    )

    assertTrue(result.events.isEmpty())
    assertEquals(
      listOf(UnsupportedCalendarProjection(
        scheduleId,
        UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
      )),
      result.unsupported,
    )
    assertTrue(plan.actions.single() is CalendarExportAction.Unsupported)
    assertTrue(plan.actions.none { it is CalendarExportAction.Update || it is CalendarExportAction.Delete })
  }

  /** 重复 Deadline 与其他重复时间形态一样使用 series master，从而允许 Provider 绑定单次例外。 */
  @Test
  fun recurringDeadlineOccurrenceExceptionUsesSeriesMaster() {
    val deadline = schedule(
      timing = ScheduleTiming.Deadline(
        MinuteTimeDate(2026, 7, 12, 23, 0),
        "Asia/Shanghai",
      ),
      recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
    )
    val completed = occurrenceException(
      deadline,
      RecurrenceId(MinuteTimeDate(2026, 7, 13, 23, 0), "Asia/Shanghai", false),
      OccurrenceStatus.COMPLETED,
    )

    val result = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(deadline), listOf(completed)),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    )

    assertTrue(result.unsupported.isEmpty())
    val master = result.events.single()
    assertEquals(CalendarProjectionKind.SERIES_MASTER, master.id.kind)
    assertTrue(master.timing is CalendarTiming.Deadline)
    assertEquals(CalendarOccurrenceExceptionOperation.CANCEL, master.nativeOccurrenceExceptions.single().operation)
  }

  /** 分类只影响应用内列表，不应让系统日历把整个重复系列判为不支持。 */
  @Test
  fun categoryOnlyAdjustmentDoesNotCreateNativeException() {
    val recurring = schedule(recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))
    val categoryOnly = occurrenceException(
      recurring,
      RecurrenceId(MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.ACTIVE,
      OccurrencePatch(categoryId = FieldPatch.Replace(CategoryId("018f0f7c-6000-7000-8000-000000000003"))),
    )

    val result = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(recurring), listOf(categoryOnly)),
      scope,
    )

    assertTrue(result.unsupported.isEmpty())
    assertTrue(result.events.single().nativeOccurrenceExceptions.isEmpty())
  }

  /** 事务关联清单后的完成态不应从系统日历消失；若同时改了标题，只写标题例外。 */
  @Test
  fun completedAffairOccurrenceKeepsCalendarProjection() {
    val affair = schedule(
      recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
      kind = ScheduleKind.AFFAIR,
    )
    val recurrenceId = RecurrenceId(MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false)
    val completionOnly = occurrenceException(affair, recurrenceId, OccurrenceStatus.COMPLETED)

    val unchanged = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(affair), listOf(completionOnly)),
      scope,
    )
    assertTrue(unchanged.unsupported.isEmpty())
    assertTrue(unchanged.events.single().nativeOccurrenceExceptions.isEmpty())

    val changed = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(
        listOf(affair),
        listOf(completionOnly.copy(patch = OccurrencePatch(title = FieldPatch.Replace("已完成但仍展示")))),
      ),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    ).events.single().nativeOccurrenceExceptions.single()
    assertEquals(CalendarOccurrenceExceptionOperation.UPSERT, changed.operation)
    assertEquals("已完成但仍展示", changed.title)
  }

  @Test
  fun completionAndCancellationAreExplicitNativeCancelOperations() {
    val recurring = schedule(recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))
    val completed = occurrenceException(
      recurring,
      RecurrenceId(MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.COMPLETED,
    )
    val cancelled = occurrenceException(
      recurring,
      RecurrenceId(MinuteTimeDate(2026, 7, 14, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.CANCELLED,
    )

    val master = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(recurring), listOf(cancelled, completed)),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    ).events.single()

    assertEquals(2, master.nativeOccurrenceExceptions.size)
    assertTrue(master.nativeOccurrenceExceptions.all {
      it.operation == CalendarOccurrenceExceptionOperation.CANCEL
    })
    assertEquals(
      master.nativeOccurrenceExceptions.sortedBy { it.externalUri },
      master.nativeOccurrenceExceptions,
    )
  }

  @Test
  fun allDayAndTimedDstExceptionsKeepOriginalIdentity() {
    val timed = schedule(
      recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
      timing = ScheduleTiming.Timed(MinuteTimeDate(2026, 11, 1, 1, 30), 60, "America/New_York"),
    )
    val timedIdentity = RecurrenceId(
      MinuteTimeDate(2026, 11, 1, 1, 30), "America/New_York", false,
    )
    val timedNative = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(
        listOf(timed),
        listOf(occurrenceException(timed, timedIdentity, OccurrenceStatus.COMPLETED)),
      ),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    ).events.single().nativeOccurrenceExceptions.single()
    assertEquals(timedIdentity, timedNative.id.recurrenceId)

    val allDay = schedule(
      recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
      timing = ScheduleTiming.AllDay(Date(2026, 3, 8)),
    )
    val allDayIdentity = RecurrenceId(MinuteTimeDate(2026, 3, 9, 0, 0), null, true)
    val allDayNative = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(
        listOf(allDay),
        listOf(occurrenceException(allDay, allDayIdentity, OccurrenceStatus.CANCELLED)),
      ),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    ).events.single().nativeOccurrenceExceptions.single()
    assertEquals(allDayIdentity, allDayNative.id.recurrenceId)
    assertEquals(CalendarTiming.AllDay(Date(2026, 3, 9), 1), allDayNative.timing)
  }

  @Test
  fun activeAdjustmentWithoutCalendarChangeIsIgnored() {
    val recurring = schedule(recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY))
    val noPatch = occurrenceException(
      recurring,
      RecurrenceId(MinuteTimeDate(2026, 7, 19, 9, 0), "Asia/Shanghai", false),
      OccurrenceStatus.ACTIVE,
    )
    val result = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(recurring), listOf(noPatch)),
      scope,
      setOf(ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS),
    )
    assertTrue(result.unsupported.isEmpty())
    assertTrue(result.events.single().nativeOccurrenceExceptions.isEmpty())
  }

  @Test
  fun sourceRejectsDuplicateSchedulesOrOrphanExceptions() {
    val value = schedule()
    assertFailsWith<IllegalArgumentException> {
      ScheduleCalendarProjectionFactory.project(
        ScheduleCalendarSource(listOf(value, value), emptyList()), scope,
      )
    }
    val orphan = ScheduleOccurrenceAdjustment(
      ScheduleId("018f0f7c-6000-7000-8000-000000000099"),
      RecurrenceId(MinuteTimeDate(2026, 7, 12, 9, 0), "Asia/Shanghai", false),
      0, OccurrenceStatus.CANCELLED, null, now, now,
    )
    assertFailsWith<IllegalArgumentException> {
      ScheduleCalendarProjectionFactory.project(
        ScheduleCalendarSource(listOf(value), listOf(orphan)), scope,
      )
    }
  }

  @Test
  fun sourceRejectsInvalidSchedule() {
    assertFailsWith<IllegalArgumentException> {
      project(schedule(timing = ScheduleTiming.Timed(
        MinuteTimeDate(2026, 7, 12, 9, 0), 0, "Asia/Shanghai",
      )))
    }
  }

  @Test
  fun projectionOrderAndFingerprintIgnoreInputCollectionOrder() {
    val first = schedule(
      id = ScheduleId("018f0f7c-6000-7000-8000-000000000003"),
      recurrence = RecurrenceRule(
        RecurrenceFrequency.WEEKLY,
        byWeekDays = linkedSetOf(IsoWeekDay.FRIDAY, IsoWeekDay.MONDAY),
      ),
      reminder = reminder(5),
    )
    val reordered = first.copy(
      recurrence = first.recurrence?.copy(
        byWeekDays = linkedSetOf(IsoWeekDay.MONDAY, IsoWeekDay.FRIDAY),
      ),
    )
    val second = schedule(id = ScheduleId("018f0f7c-6000-7000-8000-000000000002"))
    val left = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(first, second), emptyList()), scope,
    )
    val right = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(second, reordered), emptyList()), scope,
    )
    assertEquals(left, right)
    assertEquals(left.events.sortedBy { it.externalUri }, left.events)
  }

  @Test
  fun sameScheduleInDifferentScopeHasDifferentIdentityAndFingerprint() {
    val schedule = schedule()
    val first = project(schedule).events.single()
    val second = ScheduleCalendarProjectionFactory.project(
      ScheduleCalendarSource(listOf(schedule), emptyList()),
      CalendarExportScope("scope_fedcba9876543210"),
    ).events.single()
    assertNotEquals(first.id, second.id)
    assertNotEquals(first.externalUri, second.externalUri)
    assertNotEquals(first.fingerprint, second.fingerprint)
  }

  private fun project(schedule: Schedule) = ScheduleCalendarProjectionFactory.project(
    ScheduleCalendarSource(listOf(schedule), emptyList()), scope,
  )

  private fun schedule(
    id: ScheduleId = scheduleId,
    timing: ScheduleTiming = ScheduleTiming.Timed(
      MinuteTimeDate(2026, 7, 12, 9, 0), 60, "Asia/Shanghai",
    ),
    recurrence: RecurrenceRule? = null,
    reminder: ScheduleReminder? = null,
    todoState: ScheduleTodoState? = ScheduleTodoState.PENDING,
    kind: ScheduleKind = ScheduleKind.TODO,
  ) = Schedule(
    id = id,
    revision = 0,
    title = "日程|标题",
    description = "说明:包含分隔符",
    categoryId = null,
    timing = timing,
    recurrence = recurrence,
    reminder = reminder,
    todoState = todoState,
    kind = kind,
    linkedToCourse = kind == ScheduleKind.AFFAIR,
    createdAt = now,
    updatedAt = now,
  )

  /** 构造只依赖 common 值对象的 occurrence exception，不触达 Provider 或平台时钟。 */
  private fun occurrenceException(
    schedule: Schedule,
    recurrenceId: RecurrenceId,
    status: OccurrenceStatus,
    patch: OccurrencePatch? = null,
  ) = ScheduleOccurrenceAdjustment(
    scheduleId = schedule.id,
    recurrenceId = recurrenceId,
    revision = 0,
    status = status,
    patch = patch,
    createdAt = now,
    updatedAt = now,
  )

  private fun reminder(minutes: Int) = ScheduleReminder(minutes)
}
