package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.TodoState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeInput
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeKind
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.domain.sync.Weekday
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 领域模型与 wire canonical 模型的双向无损映射测试。 */
class ScheduleDomainWireMapperTest {

  @Test
  fun categoryResourceAndCurrentRoundTrip() {
    val resource = CategoryResource(
      identity = CategoryIdentity("category-1"),
      remoteId = 21L,
      version = 3,
      name = AtomicField("课程", 11),
      color = AtomicField(null, 12),
      sortOrder = AtomicField(7, 13),
    )
    assertEquals(resource, resource.toWire().toDomain(resource.identity))
  }

  @Test
  fun scheduleResourceRoundTripPreservesRecurrenceAndZeroMinuteReminder() {
    val category = CategoryResource(
      identity = CategoryIdentity("category-1"),
      remoteId = 41L,
      version = 1,
      name = AtomicField("课程", 1),
      color = AtomicField(null, 1),
      sortOrder = AtomicField(0, 1),
    )
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
      // 0 是准时提醒的显式 wire 值，资源级双向映射不能用默认值或空值把它吞掉。
      reminder = AtomicField(ReminderInput(0), 36),
      todoState = AtomicField(TodoState.OPEN, 37),
      linkedToCourse = AtomicField(true, 38),
    )
    val wire = resource.toWire { localId -> category.takeIf { it.identity.id == localId } }
    val restored = wire.toDomain { remoteId -> category.identity.id.takeIf { remoteId == category.remoteId } }

    assertEquals(resource, restored)
    assertEquals(41L, wire.categoryId.data)
    assertEquals(0, restored.reminder.data?.minutesBefore)
  }

  @Test
  fun occurrenceAdjustmentResourceRoundTrip() {
    val category = CategoryResource(
      identity = CategoryIdentity("category-2"),
      remoteId = 42L,
      version = 1,
      name = AtomicField("临时分类", 1),
      color = AtomicField(null, 1),
      sortOrder = AtomicField(0, 1),
    )
    val resource = OccurrenceAdjustmentResource(
      identity = OccurrenceAdjustmentIdentity(
        localId = "local-override-1",
        scheduleId = "schedule-1",
        originalOccurrenceDate = 172_800_000,
      ),
      remoteId = 61L,
      version = 5,
      status = AtomicField(OccurrenceStatus.COMPLETED, 51),
      date = AtomicField(FieldPatch.Inherit, 51),
      time = AtomicField(
        FieldPatch.Replace(OccurrenceTimeInput(OccurrenceTimeKind.TIME_RANGE, 9 * 60, 60)),
        52,
      ),
      title = AtomicField(FieldPatch.Replace("临时标题"), 52),
      description = AtomicField(FieldPatch.Clear, 53),
      categoryId = AtomicField(FieldPatch.Replace("category-2"), 53),
      reminder = AtomicField(
        FieldPatch.Replace(ReminderInput(5)),
        54,
      ),
    )
    val wire = resource.toWire { localId -> category.takeIf { it.identity.id == localId } }
    assertEquals(
      resource,
      wire.toDomain(resource.identity) { remoteId -> category.identity.id.takeIf { remoteId == 42L } },
    )

    val updateJson = defaultJson.encodeToString(wire)
    val createJson = defaultJson.encodeToString(
      resource.copy(remoteId = null, version = 0).toWire { localId ->
        category.takeIf { it.identity.id == localId }
      },
    )
    assertFalse(resource.identity.localId in updateJson)
    assertContains(updateJson, "\"id\":61")
    assertFalse("\"id\"" in createJson)
  }
}
