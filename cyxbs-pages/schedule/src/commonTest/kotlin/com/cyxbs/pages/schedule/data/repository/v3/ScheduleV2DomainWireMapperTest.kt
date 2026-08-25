package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.TodoState
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.v2.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind
import com.cyxbs.pages.schedule.domain.sync.v2.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals

/** v3 领域模型与 wire canonical 模型的双向无损映射测试。 */
class ScheduleV2DomainWireMapperTest {

  @Test
  fun categoryResourceAndCurrentRoundTrip() {
    val resource = CategoryResource(
      identity = CategoryIdentity("category-1"),
      version = 3,
      name = AtomicField("课程", 11),
      color = AtomicField(null, 12),
      sortOrder = AtomicField(7, 13),
    )
    val snapshot = CategoryRemoteSnapshot(resource, ServerResourceMeta(21, 22))

    assertEquals(resource, resource.toWire().toDomain())
    assertEquals(snapshot, snapshot.toWire().toDomain())
  }

  @Test
  fun scheduleResourceAndCurrentRoundTripPreservesMetaAndFirstAnchor() {
    val resource = ScheduleResource(
      identity = ScheduleIdentity("schedule-1"),
      version = 8,
      kind = ScheduleKind.AFFAIR,
      title = AtomicField("高数", 31),
      description = AtomicField("第三章", 32),
      categoryId = AtomicField("category-1", 33),
      timing = AtomicField(TimingInput(TimingKind.TIMED, startAt = 100, endAt = 200), 34),
      recurrence = AtomicField(
        RecurrenceInput(
          frequency = RecurrenceFrequency.WEEKLY,
          interval = 2,
          anchorDate = 86_400_000,
          untilDate = 864_000_000,
          weekdays = setOf(Weekday.MO, Weekday.FR),
        ),
        35,
      ),
      reminders = AtomicField(listOf(ReminderInput(15, "reminder-1")), 36),
      todoState = AtomicField(TodoState.OPEN, 37),
      linkedToCourse = AtomicField(true, 38),
    )
    val snapshot = ScheduleRemoteSnapshot(
      resource = resource,
      meta = ServerResourceMeta(createdAt = 41, remoteModifiedAt = 42),
      firstRecurrenceAnchorDate = 86_400_000,
    )

    assertEquals(resource, resource.toWire().toDomain())
    assertEquals(snapshot, snapshot.toWire().toDomain())
  }

  @Test
  fun occurrenceOverrideResourceAndCurrentRoundTrip() {
    val resource = OccurrenceOverrideResource(
      identity = OccurrenceOverrideIdentity("schedule-1", 172_800_000),
      version = 5,
      status = AtomicField(OccurrenceStatus.COMPLETED, 51),
      title = AtomicField(FieldPatch.Replace("临时标题"), 52),
      description = AtomicField(FieldPatch.Clear, 53),
      reminders = AtomicField(
        FieldPatch.Replace(listOf(ReminderInput(5, "reminder-2"))),
        54,
      ),
    )
    val snapshot = OccurrenceOverrideRemoteSnapshot(resource, ServerResourceMeta(61, 62))

    assertEquals(resource, resource.toWire().toDomain())
    assertEquals(snapshot, snapshot.toWire().toDomain())
  }
}
