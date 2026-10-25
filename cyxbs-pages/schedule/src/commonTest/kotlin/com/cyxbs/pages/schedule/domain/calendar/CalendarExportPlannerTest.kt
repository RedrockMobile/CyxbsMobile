package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarExportPlannerTest {
  private val scope = CalendarExportScope("planner_test_scope_01")
  private val scheduleId1 = ScheduleId("018f0f7c-6000-7000-8000-000000000001")
  private val scheduleId2 = ScheduleId("018f0f7c-6000-7000-8000-000000000002")
  private val now = Instant.parse("2026-07-12T12:00:00Z")

  @Test
  fun emptySourceAndEmptyManagedProducesEmptyPlan() {
    val plan = CalendarExportPlanner.plan(
      ScheduleCalendarProjectionResult(emptyList(), emptyList()),
      emptyList(),
      scope,
    )
    assertTrue(plan.actions.isEmpty())
  }

  @Test
  fun newProjectionProducesCreate() {
    val projection = projection(scheduleId1, "fp1")
    val result = ScheduleCalendarProjectionResult(listOf(projection), emptyList())
    val plan = CalendarExportPlanner.plan(result, emptyList(), scope)
    assertEquals(1, plan.actions.size)
    val action = plan.actions.single()
    assertTrue(action is CalendarExportAction.Create)
    assertEquals(projection, action.projection)
  }

  @Test
  fun matchingFingerprintProducesNoOp() {
    val projection = projection(scheduleId1, "fp-stable")
    val managed = managedEvent(projection.id, "fp-stable", 1001L)
    val result = ScheduleCalendarProjectionResult(listOf(projection), emptyList())
    val plan = CalendarExportPlanner.plan(result, listOf(managed), scope)
    assertEquals(1, plan.actions.size)
    val action = plan.actions.single()
    assertTrue(action is CalendarExportAction.NoOp)
    assertEquals(managed, action.event)
  }

  @Test
  fun fingerprintMismatchProducesUpdate() {
    val projection = projection(scheduleId1, "fp-new")
    val managed = managedEvent(projection.id, "fp-old", 2001L)
    val result = ScheduleCalendarProjectionResult(listOf(projection), emptyList())
    val plan = CalendarExportPlanner.plan(result, listOf(managed), scope)
    assertEquals(1, plan.actions.size)
    val action = plan.actions.single()
    assertTrue(action is CalendarExportAction.Update)
    assertEquals(projection, action.projection)
    assertEquals(PlatformCalendarEventRef("2001"), action.existingEventRef)
  }

  @Test
  fun managedEventNotInTargetProducesDelete() {
    val managed = managedEvent(
      CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.SINGLE),
      "fp-orphan",
      3001L,
    )
    val result = ScheduleCalendarProjectionResult(emptyList(), emptyList())
    val plan = CalendarExportPlanner.plan(result, listOf(managed), scope)
    assertEquals(1, plan.actions.size)
    val action = plan.actions.single()
    assertTrue(action is CalendarExportAction.Delete)
    assertEquals(managed, action.event)
  }

  /** 验证 legacy 全批预检会在 Create、Update 之前拒绝含 Delete 的混合计划。 */
  @Test
  fun legacyProviderPreflightRejectsCreateUpdateDeletePlan() {
    val create = CalendarExportAction.Create(projection(scheduleId1, "fp-create"))
    val updateProjection = projection(scheduleId2, "fp-update")
    val update = CalendarExportAction.Update(updateProjection, PlatformCalendarEventRef("3901"))
    val delete = CalendarExportAction.Delete(
      managedEvent(
        CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.SINGLE),
        "fp-delete",
        3902L,
      ),
    )
    val plan = CalendarExportPlan(scope, listOf(create, update, delete))

    assertFailsWith<IllegalStateException> {
      CalendarExportPlanner.assertLegacyProviderPlanDeleteFree(plan)
    }
  }

  /** 验证 legacy 全批预检不会因前置 Update 而放行后续 Delete。 */
  @Test
  fun legacyProviderPreflightRejectsUpdateDeletePlan() {
    val updateProjection = projection(scheduleId1, "fp-update")
    val update = CalendarExportAction.Update(updateProjection, PlatformCalendarEventRef("3951"))
    val delete = CalendarExportAction.Delete(
      managedEvent(
        CalendarProjectionId(scope, scheduleId2, CalendarProjectionKind.SINGLE),
        "fp-delete",
        3952L,
      ),
    )
    val plan = CalendarExportPlan(scope, listOf(update, delete))

    assertFailsWith<IllegalStateException> {
      CalendarExportPlanner.assertLegacyProviderPlanDeleteFree(plan)
    }
  }

  /** 验证仅含 Delete 的计划同样被 legacy 全批预检拒绝。 */
  @Test
  fun legacyProviderPreflightRejectsDeleteOnlyPlan() {
    val delete = CalendarExportAction.Delete(
      managedEvent(
        CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.SINGLE),
        "fp-delete",
        3961L,
      ),
    )
    val plan = CalendarExportPlan(scope, listOf(delete))

    assertFailsWith<IllegalStateException> {
      CalendarExportPlanner.assertLegacyProviderPlanDeleteFree(plan)
    }
  }

  /** 验证无 Delete 的 Create/Update/NoOp/Unsupported 计划保持可执行且不被预检改写。 */
  @Test
  fun legacyProviderPreflightPreservesDeleteFreePlan() {
    val create = CalendarExportAction.Create(projection(scheduleId1, "fp-create"))
    val updateProjection = projection(scheduleId2, "fp-update")
    val update = CalendarExportAction.Update(updateProjection, PlatformCalendarEventRef("3971"))
    val noOpEvent = managedEvent(
      CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.SERIES_MASTER),
      "fp-noop",
      3972L,
    )
    val noOp = CalendarExportAction.NoOp(noOpEvent)
    val unsupported = CalendarExportAction.Unsupported(
      UnsupportedCalendarProjection(
        scheduleId2,
        UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
      ),
    )
    val actions = listOf(create, update, noOp, unsupported)
    val plan = CalendarExportPlan(scope, actions)

    CalendarExportPlanner.assertLegacyProviderPlanDeleteFree(plan)

    assertEquals(actions, plan.actions)
  }

  @Test
  fun duplicateManagedWithMatchingFingerprintKeepsOneAndDeletesOthers() {
    val projection = projection(scheduleId1, "fp-correct")
    val matched = managedEvent(projection.id, "fp-correct", 4001L)
    val duplicate1 = managedEvent(projection.id, "fp-correct", 4002L)
    val duplicate2 = managedEvent(projection.id, "fp-correct", 4003L)
    val result = ScheduleCalendarProjectionResult(listOf(projection), emptyList())
    val plan = CalendarExportPlanner.plan(result, listOf(matched, duplicate1, duplicate2), scope)
    assertEquals(3, plan.actions.size)

    val noOps = plan.actions.filterIsInstance<CalendarExportAction.NoOp>()
    val deletes = plan.actions.filterIsInstance<CalendarExportAction.Delete>()

    assertEquals(1, noOps.size)
    assertEquals(2, deletes.size)

    val keptEventRef = noOps.single().event.platformEventRef
    val deletedRefs = deletes.map { it.event.platformEventRef }.toSet()

    assertTrue(keptEventRef in setOf(PlatformCalendarEventRef("4001"), PlatformCalendarEventRef("4002"), PlatformCalendarEventRef("4003")))
    assertEquals(2, deletedRefs.size)
    assertTrue(deletedRefs.all { it in setOf(PlatformCalendarEventRef("4001"), PlatformCalendarEventRef("4002"), PlatformCalendarEventRef("4003")) })
    assertFalse(keptEventRef in deletedRefs)
  }

  @Test
  fun duplicateManagedWithoutMatchUpdatesFirstAndDeletesRest() {
    val projection = projection(scheduleId1, "fp-latest")
    val old1 = managedEvent(projection.id, "fp-stale-1", 5001L)
    val old2 = managedEvent(projection.id, "fp-stale-2", 5002L)
    val result = ScheduleCalendarProjectionResult(listOf(projection), emptyList())
    val plan = CalendarExportPlanner.plan(result, listOf(old1, old2), scope)
    assertEquals(2, plan.actions.size)
    val update = plan.actions[0]
    val delete = plan.actions[1]
    assertTrue(update is CalendarExportAction.Update)
    assertEquals(PlatformCalendarEventRef("5001"), update.existingEventRef)
    assertTrue(delete is CalendarExportAction.Delete)
    assertEquals(PlatformCalendarEventRef("5002"), delete.event.platformEventRef)
  }

  @Test
  fun unsupportedProjectionAppearsAsUnsupportedAction() {
    val unsupported = UnsupportedCalendarProjection(
      scheduleId1,
      UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
    )
    val result = ScheduleCalendarProjectionResult(emptyList(), listOf(unsupported))
    val plan = CalendarExportPlanner.plan(result, emptyList(), scope)
    assertEquals(1, plan.actions.size)
    val action = plan.actions.single()
    assertTrue(action is CalendarExportAction.Unsupported)
    assertEquals(unsupported, action.item)
  }

  @Test
  fun unsupportedOccurrenceExceptionPreservesExistingSeriesMaster() {
    val unsupported = UnsupportedCalendarProjection(
      scheduleId1,
      UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
    )
    val master = managedEvent(
      CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.SERIES_MASTER),
      "previous-master",
      6101L,
    )
    val plan = CalendarExportPlanner.plan(
      ScheduleCalendarProjectionResult(emptyList(), listOf(unsupported)),
      listOf(master),
      scope,
    )
    assertEquals(1, plan.actions.size)
    assertTrue(plan.actions.single() is CalendarExportAction.Unsupported)
    assertTrue(plan.actions.none { it is CalendarExportAction.Delete })
  }

  @Test
  fun nativeOccurrenceAggregateStillPlansOneMasterBeforeInternalExceptions() {
    val masterId = CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.SERIES_MASTER)
    val occurrenceId = CalendarProjectionId(
      scope,
      scheduleId1,
      CalendarProjectionKind.OCCURRENCE_EXCEPTION,
      com.cyxbs.pages.schedule.domain.model.RecurrenceId(
        MinuteTimeDate(2026, 7, 13, 9, 0), "Asia/Shanghai", false,
      ),
    )
    val occurrenceUri = CalendarProjectionUriCodec.encode(occurrenceId)
    val occurrence = CalendarOccurrenceExceptionProjection(
      id = occurrenceId,
      externalUri = occurrenceUri,
      title = "patched",
      description = "description",
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Asia/Shanghai"),
      deviceReminderMinutes = emptyList(),
      operation = CalendarOccurrenceExceptionOperation.UPSERT,
      fingerprint = CalendarProjectionFingerprint.computeOccurrenceException(
        occurrenceUri,
        "patched",
        "description",
        CalendarTiming.Timed(MinuteTimeDate(2026, 7, 13, 10, 0), 60, "Asia/Shanghai"),
        emptyList(),
        CalendarOccurrenceExceptionOperation.UPSERT,
      ),
    )
    val masterUri = CalendarProjectionUriCodec.encode(masterId)
    val masterTiming = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 0), 60, "Asia/Shanghai")
    val master = CalendarEventProjection(
      id = masterId,
      externalUri = masterUri,
      title = "master",
      description = "description",
      timing = masterTiming,
      recurrenceRule = "FREQ=DAILY",
      deviceReminderMinutes = emptyList(),
      fingerprint = CalendarProjectionFingerprint.compute(
        masterUri, "master", "description", masterTiming, "FREQ=DAILY", emptyList(), listOf(occurrence),
      ),
      nativeOccurrenceExceptions = listOf(occurrence),
    )

    val plan = CalendarExportPlanner.plan(
      ScheduleCalendarProjectionResult(listOf(master), emptyList()),
      emptyList(),
      scope,
    )

    val create = plan.actions.single() as CalendarExportAction.Create
    assertEquals(masterId, create.projection.id)
    assertEquals(listOf(occurrence), create.projection.nativeOccurrenceExceptions)
  }

  /** Deadline exception 已由 projection 标记 Unsupported；planner 对手工注入的半合法子计划仍保持防御性拒绝。 */
  @Test
  fun plannerRejectsNativeExceptionAttachedToDeadlineIdentity() {
    val recurrenceId = com.cyxbs.pages.schedule.domain.model.RecurrenceId(
      MinuteTimeDate(2026, 7, 13, 23, 0), "Asia/Shanghai", false,
    )
    val occurrenceId = CalendarProjectionId(
      scope, scheduleId1, CalendarProjectionKind.OCCURRENCE_EXCEPTION, recurrenceId,
    )
    val occurrenceUri = CalendarProjectionUriCodec.encode(occurrenceId)
    val occurrenceTiming = CalendarTiming.Deadline(
      MinuteTimeDate(2026, 7, 13, 23, 0), "Asia/Shanghai",
    )
    val occurrence = CalendarOccurrenceExceptionProjection(
      id = occurrenceId,
      externalUri = occurrenceUri,
      title = "deadline exception",
      description = "description",
      timing = occurrenceTiming,
      deviceReminderMinutes = emptyList(),
      operation = CalendarOccurrenceExceptionOperation.CANCEL,
      fingerprint = CalendarProjectionFingerprint.computeOccurrenceException(
        occurrenceUri,
        "deadline exception",
        "description",
        occurrenceTiming,
        emptyList(),
        CalendarOccurrenceExceptionOperation.CANCEL,
      ),
    )
    val deadlineId = CalendarProjectionId(scope, scheduleId1, CalendarProjectionKind.DEADLINE)
    val deadlineUri = CalendarProjectionUriCodec.encode(deadlineId)
    val deadlineTiming = CalendarTiming.Deadline(
      MinuteTimeDate(2026, 7, 12, 23, 0), "Asia/Shanghai",
    )
    val deadline = CalendarEventProjection(
      id = deadlineId,
      externalUri = deadlineUri,
      title = "deadline",
      description = "description",
      timing = deadlineTiming,
      recurrenceRule = "FREQ=DAILY",
      deviceReminderMinutes = emptyList(),
      fingerprint = CalendarProjectionFingerprint.compute(
        deadlineUri,
        "deadline",
        "description",
        deadlineTiming,
        "FREQ=DAILY",
        emptyList(),
        listOf(occurrence),
      ),
      nativeOccurrenceExceptions = listOf(occurrence),
    )

    assertFailsWith<IllegalArgumentException> {
      CalendarExportPlanner.plan(
        ScheduleCalendarProjectionResult(listOf(deadline), emptyList()),
        emptyList(),
        scope,
      )
    }
  }

  @Test
  fun mixedScenarioProducesStableSortedPlan() {
    val proj1 = projection(scheduleId1, "fp-1")
    val proj2 = projection(scheduleId2, "fp-2")
    val managed1 = managedEvent(proj1.id, "fp-1", 6001L)
    val orphan = managedEvent(
      CalendarProjectionId(scope, ScheduleId("018f0f7c-6000-7000-8000-000000000099"), CalendarProjectionKind.SINGLE),
      "fp-orphan",
      6099L,
    )
    val result = ScheduleCalendarProjectionResult(listOf(proj1, proj2), emptyList())
    val plan = CalendarExportPlanner.plan(result, listOf(managed1, orphan), scope)
    assertEquals(3, plan.actions.size)
    assertTrue(plan.actions[0] is CalendarExportAction.Create) // proj2 new
    assertTrue(plan.actions[1] is CalendarExportAction.Delete) // orphan
    assertTrue(plan.actions[2] is CalendarExportAction.NoOp)   // proj1 match
  }

  @Test
  fun plannerRejectsCrossScopeProjectionOrManaged() {
    val otherScope = CalendarExportScope("other_scope_abcdef01")
    val crossProjection = projection(scheduleId1, "fp", otherScope)
    assertFailsWith<IllegalArgumentException> {
      CalendarExportPlanner.plan(
        ScheduleCalendarProjectionResult(listOf(crossProjection), emptyList()),
        emptyList(),
        scope,
      )
    }
    val crossManaged = managedEvent(
      CalendarProjectionId(otherScope, scheduleId1, CalendarProjectionKind.SINGLE),
      "fp",
      7001L,
    )
    assertFailsWith<IllegalArgumentException> {
      CalendarExportPlanner.plan(
        ScheduleCalendarProjectionResult(emptyList(), emptyList()),
        listOf(crossManaged),
        scope,
      )
    }
  }

  @Test
  fun actionOrderIsStableAcrossInvocations() {
    val proj = projection(scheduleId1, "fp-stable")
    val result = ScheduleCalendarProjectionResult(listOf(proj), emptyList())
    val managed = managedEvent(proj.id, "fp-stable", 8001L)
    val plan1 = CalendarExportPlanner.plan(result, listOf(managed), scope)
    val plan2 = CalendarExportPlanner.plan(result, listOf(managed), scope)
    assertEquals(plan1.actions, plan2.actions)
  }

  private fun projection(
    id: ScheduleId,
    fingerprint: String,
    targetScope: CalendarExportScope = scope,
  ): CalendarEventProjection {
    val projId = CalendarProjectionId(targetScope, id, CalendarProjectionKind.SINGLE)
    return CalendarEventProjection(
      id = projId,
      externalUri = CalendarProjectionUriCodec.encode(projId),
      title = "Test Event",
      description = "Test description",
      timing = CalendarTiming.Timed(MinuteTimeDate(2026, 7, 12, 9, 0), 60, "Asia/Shanghai"),
      recurrenceRule = null,
      deviceReminderMinutes = emptyList(),
      fingerprint = fingerprint,
    )
  }

  private fun managedEvent(
    id: CalendarProjectionId,
    fingerprint: String,
    eventId: Long,
  ) = ManagedCalendarEvent(id, fingerprint, PlatformCalendarEventRef(eventId.toString()))
}
