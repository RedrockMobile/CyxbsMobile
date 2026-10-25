package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 双快照状态投影为 UI 日程快照的关键边界测试。 */
class ScheduleSnapshotProjectorTest {
  private val projector = ScheduleSnapshotProjector()

  /** pending upsert 优先于远端快照，pending delete 则从 UI 隐藏资源。 */
  @Test
  fun pendingStateControlsVisibleSchedule() {
    val remote = testScheduleResource(version = 3, title = "远端标题")
    val updated = remote.copy(title = AtomicField("本地标题", 20))
    val visible = testScheduleState(remote, PendingUpsert(updated, 4))
    val deleted = testScheduleState(
      remote.copy(identity = com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity(
        "019d0000-0000-7000-8000-000000000002",
      )),
      PendingDelete(
        com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity(
          "019d0000-0000-7000-8000-000000000002",
        ),
        localModifiedAt = 20,
        localRevision = 4,
      ),
    )

    val snapshot = assertIs<ScheduleSnapshotProjection.Success>(
      projector.project("2020214988", TimeZone.UTC, emptyList(), listOf(visible, deleted), emptyList()),
    ).snapshot

    assertEquals(listOf("本地标题"), snapshot.schedules.map { it.title })
    assertEquals(ScheduleRepositoryStatus.Ready(2, true), snapshot.status)
  }

  /** 未被当前 RRULE 命中的单次调整会休眠；规则恢复命中后，同一条调整会重新进入 UI。 */
  @Test
  fun recurrenceMembershipControlsVisibleAdjustments() {
    val parent = recurringParent()
    val active = testAdjustmentResource(remoteId = 71L, version = 2)
    val dormant = active.copy(
      identity = active.identity.copy(
        localId = "019d0000-0000-7000-8000-000000000021",
        originalOccurrenceDate = TEST_OCCURRENCE_DATE + 86_400_000L,
      ),
    )
    val weeklyParent = parent.copy(
      recurrence = AtomicField(
        RecurrenceInput(
          frequency = RecurrenceFrequency.WEEKLY,
          interval = 1,
          anchorDate = TEST_OCCURRENCE_DATE,
          weekdays = setOf(com.cyxbs.pages.schedule.domain.sync.Weekday.FR),
        ),
        10,
      ),
    )

    val snapshot = assertIs<ScheduleSnapshotProjection.Success>(
      projector.project(
        "2020214988",
        TimeZone.UTC,
        emptyList(),
        listOf(testScheduleState(weeklyParent)),
        listOf(testAdjustmentState(active), testAdjustmentState(dormant)),
      ),
    ).snapshot

    assertEquals(1, snapshot.occurrenceAdjustments.size)
    assertEquals(TEST_SCHEDULE_ID, snapshot.occurrenceAdjustments.single().scheduleId.value)

    val restoredSnapshot = assertIs<ScheduleSnapshotProjection.Success>(
      projector.project(
        "2020214988",
        TimeZone.UTC,
        emptyList(),
        listOf(testScheduleState(parent)),
        listOf(testAdjustmentState(active), testAdjustmentState(dormant)),
      ),
    ).snapshot

    assertEquals(2, restoredSnapshot.occurrenceAdjustments.size)
    assertEquals(
      setOf("2026-05-01", "2026-05-02"),
      restoredSnapshot.occurrenceAdjustments
        .map { it.recurrenceId.originalDateTime.date.toString() }
        .toSet(),
    )
  }

  /** 坏的联合时间数据不能部分发布，整次投影必须失败。 */
  @Test
  fun malformedTimingFailsClosed() {
    val invalid = testScheduleResource(version = 1).copy(
      timing = AtomicField(
        TimingInput(TimingKind.TIMED, startAt = 1_000, endAt = 1_000),
        10,
      ),
    )

    assertIs<ScheduleSnapshotProjection.Failure>(
      projector.project(
        "2020214988",
        TimeZone.UTC,
        emptyList(),
        listOf(testScheduleState(invalid)),
        emptyList(),
      ),
    )
  }

  /** 旧清单迁移产生的无日期日程仍能读取，但保持不可重复、不可提醒、不可投射课表。 */
  @Test
  fun legacyUnscheduledScheduleCanBeProjected() {
    val schedule = testScheduleResource(version = 1)

    val snapshot = assertIs<ScheduleSnapshotProjection.Success>(
      projector.project(
        "2020214988",
        TimeZone.UTC,
        emptyList(),
        listOf(testScheduleState(schedule)),
        emptyList(),
      ),
    ).snapshot

    assertTrue(snapshot.schedules.single().timing is com.cyxbs.pages.schedule.domain.model.ScheduleTiming.Unscheduled)
  }

  /** 构造锚点为 2026-05-01 的按日重复全天日程。 */
  private fun recurringParent() = testScheduleResource(version = 3).copy(
    timing = AtomicField(TimingInput(TimingKind.ALL_DAY, date = TEST_OCCURRENCE_DATE), 10),
    recurrence = AtomicField(
      RecurrenceInput(RecurrenceFrequency.DAILY, 1, TEST_OCCURRENCE_DATE),
      10,
    ),
  )
}
