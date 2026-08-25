package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.domain.model.FieldPatch as UiFieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus as UiOccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency as UiRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.TodoState
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.v2.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind
import com.cyxbs.pages.schedule.domain.sync.v2.Weekday
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** typed 双快照到旧 UI ScheduleSnapshot 的纯投影合同测试。 */
class ScheduleV2SnapshotProjectorTest {
  private val projector = ScheduleV2SnapshotProjector()
  private val zone = TimeZone.of("Asia/Shanghai")

  @Test
  fun remoteAndPendingResourcesProjectWithExpectedVisibilityAndTimestamps() {
    val categoryRemote = categoryResource(version = 5, name = "远端分类", color = null, timestamp = 10)
    val categoryState = CategorySyncState(
      categoryRemote.identity,
      CategoryRemoteSnapshot(categoryRemote, ServerResourceMeta(1, 2)),
      PendingUpsert(
        categoryRemote.copy(name = AtomicField("本地分类", 30)),
        localRevision = 3,
      ),
    )
    val deletedCategoryResource = categoryResource("deleted-category", version = 2)
    val deletedCategory = CategorySyncState(
      deletedCategoryResource.identity,
      CategoryRemoteSnapshot(deletedCategoryResource, ServerResourceMeta(1, 2)),
      PendingDelete(deletedCategoryResource.identity, localModifiedAt = 40, localRevision = 4),
    )
    val remote = scheduleResource(
      id = SCHEDULE_ID,
      version = 7,
      timing = timed(2026, 7, 20, 9, 0, durationMinutes = 60),
      title = "R",
      timestamp = 100,
    )
    val scheduleState = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(createdAt = 50, remoteModifiedAt = 150)),
      PendingUpsert(remote.copy(title = AtomicField("U", 200)), localRevision = 8),
    )
    val deletedScheduleResource = scheduleResource(SCHEDULE_ID_2, 3, TimingInput(TimingKind.UNSCHEDULED))
    val deletedSchedule = ScheduleSyncState(
      deletedScheduleResource.identity,
      ScheduleRemoteSnapshot(deletedScheduleResource, ServerResourceMeta(1, 2)),
      PendingDelete(deletedScheduleResource.identity, localModifiedAt = 9, localRevision = 1),
    )

    val snapshot = project(
      categories = listOf(categoryState, deletedCategory),
      schedules = listOf(scheduleState, deletedSchedule),
    ).snapshot

    assertEquals("本地分类", snapshot.categories.single().name)
    assertEquals(5, snapshot.categories.single().revision)
    assertNull(snapshot.categories.single().color)
    val schedule = snapshot.schedules.single()
    assertEquals("U", schedule.title)
    assertEquals(7, schedule.revision)
    assertEquals(Instant.fromEpochMilliseconds(50), schedule.createdAt)
    assertEquals(Instant.fromEpochMilliseconds(200), schedule.updatedAt)
    assertEquals("$SCHEDULE_ID:reminder:0", schedule.reminders.single().id.value)
    assertEquals(ReminderChannel.DEVICE, schedule.reminders.single().channel)
    assertEquals(
      ScheduleRepositoryStatus.Ready(pendingCount = 4, hasPendingDeletes = true),
      snapshot.status,
    )
  }

  @Test
  fun pendingCreateUsesAtomicMinMaxAndVersionZero() {
    val create = scheduleResource(
      id = SCHEDULE_ID,
      version = 0,
      timing = TimingInput(TimingKind.UNSCHEDULED),
      timestamp = 20,
    ).copy(
      title = AtomicField("create", 10),
      todoState = AtomicField(TodoState.OPEN, 70),
    )
    val state = ScheduleSyncState(
      create.identity,
      remoteSnapshot = null,
      pending = PendingUpsert(create, localRevision = 1),
    )

    val schedule = project(schedules = listOf(state)).snapshot.schedules.single()

    assertEquals(0, schedule.revision)
    assertEquals(Instant.fromEpochMilliseconds(10), schedule.createdAt)
    assertEquals(Instant.fromEpochMilliseconds(70), schedule.updatedAt)
  }

  @Test
  fun fourTimingKindsAreRestoredWithoutLosingTheirSemantics() {
    val timedStart = timed(2026, 7, 20, 9, 30, durationMinutes = 90)
    val deadlineDue = MinuteTimeDate(2026, 7, 21, 18, 15)
      .toLocalDateTime().toInstant(zone).toEpochMilliseconds()
    val allDayStart = Date(2026, 7, 22).utcSlot()
    val states = listOf(
      scheduleState(SCHEDULE_ID, timedStart),
      scheduleState(SCHEDULE_ID_2, TimingInput(TimingKind.DEADLINE, dueAt = deadlineDue)),
      scheduleState(
        SCHEDULE_ID_3,
        TimingInput(TimingKind.ALL_DAY, startAt = allDayStart, endAt = allDayStart + 2 * DAY),
      ),
      scheduleState(SCHEDULE_ID_4, TimingInput(TimingKind.UNSCHEDULED)),
    )

    val schedules = project(schedules = states).snapshot.schedules.associateBy { it.id.value }

    assertEquals(
      ScheduleTiming.Timed(MinuteTimeDate(2026, 7, 20, 9, 30), 90, zone.id),
      schedules.getValue(SCHEDULE_ID).timing,
    )
    assertEquals(
      ScheduleTiming.Deadline(MinuteTimeDate(2026, 7, 21, 18, 15), zone.id),
      schedules.getValue(SCHEDULE_ID_2).timing,
    )
    assertEquals(
      ScheduleTiming.AllDay(Date(2026, 7, 22), 2),
      schedules.getValue(SCHEDULE_ID_3).timing,
    )
    assertEquals(ScheduleTiming.Unscheduled, schedules.getValue(SCHEDULE_ID_4).timing)
  }

  @Test
  fun recurrenceProjectsDailyWeeklyAndAllEndKinds() {
    val dailyUntil = RecurrenceInput(
      frequency = RecurrenceFrequency.DAILY,
      interval = 2,
      anchorDate = Date(2026, 7, 20).utcSlot(),
      untilDate = Date(2026, 7, 30).utcSlot(),
    )
    val weeklyCount = RecurrenceInput(
      frequency = RecurrenceFrequency.WEEKLY,
      interval = 1,
      anchorDate = Date(2026, 7, 21).utcSlot(),
      count = 6,
      weekdays = setOf(Weekday.TU, Weekday.TH),
    )
    val dailyNever = RecurrenceInput(
      frequency = RecurrenceFrequency.DAILY,
      interval = 1,
      anchorDate = Date(2026, 7, 22).utcSlot(),
    )
    val schedules = project(schedules = listOf(
      scheduleState(SCHEDULE_ID, timed(2026, 7, 20, 9, 0, 60), dailyUntil),
      scheduleState(SCHEDULE_ID_2, timed(2026, 7, 21, 9, 0, 60), weeklyCount),
      scheduleState(SCHEDULE_ID_3, timed(2026, 7, 22, 9, 0, 60), dailyNever),
    )).snapshot.schedules.associateBy { it.id.value }

    val daily = schedules.getValue(SCHEDULE_ID).recurrence!!
    assertEquals(UiRecurrenceFrequency.DAILY, daily.frequency)
    assertEquals(2, daily.interval)
    assertEquals(RecurrenceEnd.Until(Date(2026, 7, 30)), daily.end)
    val weekly = schedules.getValue(SCHEDULE_ID_2).recurrence!!
    assertEquals(UiRecurrenceFrequency.WEEKLY, weekly.frequency)
    assertEquals(setOf(IsoWeekDay.TUESDAY, IsoWeekDay.THURSDAY), weekly.byWeekDays)
    assertEquals(RecurrenceEnd.Count(6), weekly.end)
    assertEquals(RecurrenceEnd.Never, schedules.getValue(SCHEDULE_ID_3).recurrence!!.end)
  }

  @Test
  fun overridesRestoreIdentityFromParentAndMapOnlyFourAtoms() {
    val occurrenceDate = Date(2026, 7, 23).utcSlot()
    val timedParent = scheduleState(SCHEDULE_ID, timed(2026, 7, 20, 9, 30, 60))
    val deadlineParent = scheduleState(
      SCHEDULE_ID_2,
      TimingInput(
        TimingKind.DEADLINE,
        dueAt = MinuteTimeDate(2026, 7, 20, 18, 0).toLocalDateTime()
          .toInstant(zone).toEpochMilliseconds(),
      ),
    )
    val allDayParent = scheduleState(
      SCHEDULE_ID_3,
      TimingInput(
        TimingKind.ALL_DAY,
        startAt = Date(2026, 7, 20).utcSlot(),
        endAt = Date(2026, 7, 21).utcSlot(),
      ),
    )
    val overrides = listOf(
      overrideState(SCHEDULE_ID, occurrenceDate, version = 4),
      overrideState(SCHEDULE_ID_2, occurrenceDate, version = 5),
      overrideState(SCHEDULE_ID_3, occurrenceDate, version = 6),
    )

    val exceptions = project(
      schedules = listOf(timedParent, deadlineParent, allDayParent),
      overrides = overrides,
    ).snapshot.exceptions.associateBy { it.scheduleId.value }

    val timed = exceptions.getValue(SCHEDULE_ID)
    assertEquals(MinuteTimeDate(2026, 7, 23, 9, 30), timed.recurrenceId.originalDateTime)
    assertEquals(zone.id, timed.recurrenceId.timeZoneId)
    assertEquals(false, timed.recurrenceId.allDay)
    assertEquals(4, timed.revision)
    assertEquals(UiOccurrenceStatus.COMPLETED, timed.status)
    assertEquals(UiFieldPatch.Inherit, timed.patch!!.timing)
    assertEquals(UiFieldPatch.Inherit, timed.patch.categoryId)
    assertEquals(UiFieldPatch.Replace("单次标题"), timed.patch.title)
    assertEquals(UiFieldPatch.Clear, timed.patch.description)
    val reminders = assertIs<UiFieldPatch.Replace<*>>(timed.patch.reminders).value
    assertEquals(
      "$SCHEDULE_ID@$occurrenceDate:reminder:0",
      assertIs<List<*>>(reminders).single().let { assertIs<com.cyxbs.pages.schedule.domain.model.ScheduleReminder>(it).id.value },
    )
    assertEquals(Instant.fromEpochMilliseconds(500), timed.createdAt)
    assertEquals(Instant.fromEpochMilliseconds(650), timed.updatedAt)

    val deadline = exceptions.getValue(SCHEDULE_ID_2).recurrenceId
    assertEquals(MinuteTimeDate(2026, 7, 23, 18, 0), deadline.originalDateTime)
    assertEquals(zone.id, deadline.timeZoneId)
    val allDay = exceptions.getValue(SCHEDULE_ID_3).recurrenceId
    assertEquals(MinuteTimeDate(2026, 7, 23, 0, 0), allDay.originalDateTime)
    assertNull(allDay.timeZoneId)
    assertTrue(allDay.allDay)
  }

  @Test
  fun malformedTimingWeeklyAnchorAndOverrideParentFailClosed() {
    val invalidTimed = scheduleState(
      SCHEDULE_ID,
      TimingInput(TimingKind.TIMED, startAt = 1_001, endAt = 61_001),
    )
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project("account", zone, emptyList(), listOf(invalidTimed), emptyList()),
    )
    val invalidDeadline = scheduleState(
      SCHEDULE_ID,
      TimingInput(TimingKind.DEADLINE, dueAt = 60_001),
    )
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project("account", zone, emptyList(), listOf(invalidDeadline), emptyList()),
    )

    val invalidWeekly = RecurrenceInput(
      frequency = RecurrenceFrequency.WEEKLY,
      interval = 1,
      anchorDate = Date(2026, 7, 21).utcSlot(),
      weekdays = setOf(Weekday.MO),
    )
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project(
        "account",
        zone,
        emptyList(),
        listOf(scheduleState(SCHEDULE_ID, timed(2026, 7, 21, 9, 0, 60), invalidWeekly)),
        emptyList(),
      ),
    )

    val orphan = overrideState(SCHEDULE_ID, Date(2026, 7, 23).utcSlot(), version = 2)
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project("account", zone, emptyList(), emptyList(), listOf(orphan)),
    )
    val unscheduledParent = scheduleState(SCHEDULE_ID, TimingInput(TimingKind.UNSCHEDULED))
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project("account", zone, emptyList(), listOf(unscheduledParent), listOf(orphan)),
    )
  }

  @Test
  fun deletedParentHidesOverrideButMissingParentStillFails() {
    val occurrenceDate = Date(2026, 7, 23).utcSlot()
    val liveParent = scheduleState(SCHEDULE_ID, timed(2026, 7, 20, 9, 0, 60))
    val deletingParent = liveParent.replacePending(
      PendingDelete(liveParent.identity, localModifiedAt = 100, localRevision = 1),
    )
    val child = overrideState(SCHEDULE_ID, occurrenceDate, version = 2)

    val hidden = project(schedules = listOf(deletingParent), overrides = listOf(child)).snapshot

    assertTrue(hidden.schedules.isEmpty())
    assertTrue(hidden.exceptions.isEmpty())
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project("account", zone, emptyList(), emptyList(), listOf(child)),
    )
  }

  @Test
  fun recurrenceMustRoundTripThroughOldUiWithoutChangingMeaning() {
    val anchor = Date(2026, 7, 21).utcSlot()
    val timing = timed(2026, 7, 21, 9, 0, 60)
    val dailyWithWeekday = RecurrenceInput(
      RecurrenceFrequency.DAILY,
      interval = 1,
      anchorDate = anchor,
      weekdays = setOf(Weekday.TU),
    )
    assertProjectionFailure(scheduleState(SCHEDULE_ID, timing, dailyWithWeekday))

    val untilBeforeAnchor = RecurrenceInput(
      RecurrenceFrequency.DAILY,
      interval = 1,
      anchorDate = anchor,
      untilDate = Date(2026, 7, 20).utcSlot(),
    )
    assertProjectionFailure(scheduleState(SCHEDULE_ID, timing, untilBeforeAnchor))

    val mismatchedAnchor = RecurrenceInput(
      RecurrenceFrequency.DAILY,
      interval = 1,
      anchorDate = Date(2026, 7, 22).utcSlot(),
    )
    assertProjectionFailure(scheduleState(SCHEDULE_ID, timing, mismatchedAnchor))

    val unscheduledRecurrence = RecurrenceInput(
      RecurrenceFrequency.DAILY,
      interval = 1,
      anchorDate = anchor,
    )
    assertProjectionFailure(
      scheduleState(SCHEDULE_ID, TimingInput(TimingKind.UNSCHEDULED), unscheduledRecurrence),
    )
  }

  @Test
  fun nonEmptyReminderMessageFailsInsteadOfBeingSilentlyDiscarded() {
    val resource = scheduleResource(
      SCHEDULE_ID,
      version = 3,
      timing = TimingInput(TimingKind.UNSCHEDULED),
    ).copy(
      reminders = AtomicField(listOf(ReminderInput(15, "旧 UI 无字段")), 20),
    )
    val state = ScheduleSyncState(
      resource.identity,
      ScheduleRemoteSnapshot(resource, ServerResourceMeta(1, 2)),
    )

    assertProjectionFailure(state)
  }

  private fun project(
    categories: List<CategorySyncState> = emptyList(),
    schedules: List<ScheduleSyncState> = emptyList(),
    overrides: List<OccurrenceOverrideSyncState> = emptyList(),
  ): ScheduleV2SnapshotProjection.Success = assertIs(
    projector.project("account", zone, categories, schedules, overrides),
  )

  private fun assertProjectionFailure(schedule: ScheduleSyncState) {
    assertIs<ScheduleV2SnapshotProjection.Failure>(
      projector.project("account", zone, emptyList(), listOf(schedule), emptyList()),
    )
  }

  private fun categoryResource(
    id: String = CATEGORY_ID,
    version: Long,
    name: String = "分类",
    color: String? = "#123456",
    timestamp: Long = 10,
  ): CategoryResource = CategoryResource(
    CategoryIdentity(id),
    version,
    AtomicField(name, timestamp),
    AtomicField(color, timestamp),
    AtomicField(1, timestamp),
  )

  private fun scheduleState(
    id: String,
    timing: TimingInput,
    recurrence: RecurrenceInput? = null,
  ): ScheduleSyncState {
    val resource = scheduleResource(id, version = 3, timing = timing, recurrence = recurrence)
    return ScheduleSyncState(
      resource.identity,
      ScheduleRemoteSnapshot(resource, ServerResourceMeta(createdAt = 10, remoteModifiedAt = 20)),
    )
  }

  private fun scheduleResource(
    id: String,
    version: Long,
    timing: TimingInput,
    recurrence: RecurrenceInput? = null,
    title: String = "日程",
    timestamp: Long = 10,
  ): ScheduleResource = ScheduleResource(
    identity = ScheduleIdentity(id),
    version = version,
    kind = ScheduleKind.TODO,
    title = AtomicField(title, timestamp),
    description = AtomicField("描述", timestamp),
    categoryId = AtomicField(CATEGORY_ID, timestamp),
    timing = AtomicField(timing, timestamp),
    recurrence = AtomicField(recurrence, timestamp),
    reminders = AtomicField(listOf(ReminderInput(15, "")), timestamp),
    todoState = AtomicField(TodoState.OPEN, timestamp),
    linkedToCourse = AtomicField(false, timestamp),
  )

  private fun overrideState(
    scheduleId: String,
    occurrenceDate: Long,
    version: Long,
  ): OccurrenceOverrideSyncState {
    val identity = OccurrenceOverrideIdentity(scheduleId, occurrenceDate)
    val resource = OccurrenceOverrideResource(
      identity = identity,
      version = version,
      status = AtomicField(OccurrenceStatus.COMPLETED, 610),
      title = AtomicField(FieldPatch.Replace("单次标题"), 620),
      description = AtomicField(FieldPatch.Clear, 630),
      reminders = AtomicField(FieldPatch.Replace(listOf(ReminderInput(5, ""))), 650),
    )
    return OccurrenceOverrideSyncState(
      identity,
      OccurrenceOverrideRemoteSnapshot(
        resource,
        ServerResourceMeta(createdAt = 500, remoteModifiedAt = 600),
      ),
    )
  }

  private fun timed(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    durationMinutes: Int,
  ): TimingInput {
    val start = MinuteTimeDate(year, month, day, hour, minute)
      .toLocalDateTime().toInstant(zone).toEpochMilliseconds()
    return TimingInput(
      TimingKind.TIMED,
      startAt = start,
      endAt = start + durationMinutes * 60_000L,
    )
  }

  private fun Date.utcSlot(): Long =
    toLocalDate().atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

  private companion object {
    const val DAY = 86_400_000L
    const val CATEGORY_ID = "category-1"
    const val SCHEDULE_ID = "0197f000-0000-7000-8000-000000000001"
    const val SCHEDULE_ID_2 = "0197f000-0000-7000-8000-000000000002"
    const val SCHEDULE_ID_3 = "0197f000-0000-7000-8000-000000000003"
    const val SCHEDULE_ID_4 = "0197f000-0000-7000-8000-000000000004"
  }
}
