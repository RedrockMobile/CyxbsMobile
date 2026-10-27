package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus as UiOccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.TodoState
import com.cyxbs.pages.schedule.ui.model.occurrencesInRange
import com.cyxbs.pages.schedule.ui.model.occurrenceByIdentity
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 双快照状态投影为 UI 日程快照的关键边界测试。 */
class ScheduleSnapshotProjectorTest {
  private val projector = ScheduleSnapshotProjector()

  /** 后端的秒、毫秒只在展示中省略，不因客户端使用分钟模型而拒绝读取。 */
  @Test
  fun subMinuteTimedAndDeadlineAreDisplayedAtMinutePrecision() {
    val start = TEST_OCCURRENCE_DATE + 9 * 3_600_000L + 1_234L
    val timings = listOf(
      TimingInput(TimingKind.TIMED, startAt = start, endAt = start + 3_600_000L),
      TimingInput(TimingKind.DEADLINE, dueAt = start),
    )
    timings.forEach { timing ->
      val resource = testScheduleResource(version = 3).copy(timing = AtomicField(timing, 10))
      val schedule = assertIs<ScheduleSnapshotProjection.Success>(projector.project(
        "2020214988", TimeZone.UTC, emptyList(), listOf(testScheduleState(resource)), emptyList(),
      )).snapshot.schedules.single()
      val expected = if (timing.kind == TimingKind.TIMED) {
        com.cyxbs.pages.schedule.domain.model.ScheduleTiming.Timed(MinuteTimeDate(2026, 5, 1, 9, 0), 60, TimeZone.UTC.id)
      } else {
        com.cyxbs.pages.schedule.domain.model.ScheduleTiming.Deadline(MinuteTimeDate(2026, 5, 1, 9, 0), TimeZone.UTC.id)
      }
      assertEquals(expected, schedule.timing)
      assertEquals(timing, resource.timing.data)
    }
  }

  /** 正时长小于一分钟时仍需构造有效的分钟区间，不能舍入为零后在读取阶段失败。 */
  @Test
  fun positiveSubMinuteDurationIsDisplayedAsOneMinute() {
    val start = TEST_OCCURRENCE_DATE + 1_234L
    val resource = testScheduleResource(version = 3).copy(
      timing = AtomicField(TimingInput(TimingKind.TIMED, startAt = start, endAt = start + 1), 10),
    )
    val schedule = assertIs<ScheduleSnapshotProjection.Success>(projector.project(
      "2020214988", TimeZone.UTC, emptyList(), listOf(testScheduleState(resource)), emptyList(),
    )).snapshot.schedules.single()
    assertEquals(1, assertIs<com.cyxbs.pages.schedule.domain.model.ScheduleTiming.Timed>(schedule.timing).durationMinutes)
  }

  /** 服务端合并出取消态加单次标题覆盖时，保留原始字段供同步，但列表和详情均不展示该实例。 */
  @Test
  fun cancelledOccurrenceWithMergedContentRemainsHidden() {
    val cancelled = testAdjustmentResource(remoteId = 71L, version = 3).copy(
      status = AtomicField(OccurrenceStatus.CANCELLED, 40),
      title = AtomicField(FieldPatch.Replace("另一端修改的标题"), 30),
    )
    val snapshot = assertIs<ScheduleSnapshotProjection.Success>(projector.project(
      "2020214988", TimeZone.UTC, emptyList(), listOf(testScheduleState(recurringParent())),
      listOf(testAdjustmentState(cancelled)),
    )).snapshot
    val raw = snapshot.occurrenceAdjustments.single()
    assertEquals(UiOccurrenceStatus.CANCELLED, raw.status)
    assertEquals(
      com.cyxbs.pages.schedule.domain.model.FieldPatch.Replace("另一端修改的标题"), raw.patch?.title,
    )
    assertTrue(snapshot.occurrencesInRange(
      MinuteTimeDate(2026, 5, 1, 0, 0), MinuteTimeDate(2026, 5, 2, 0, 0),
    ).isEmpty())
    assertEquals(null, snapshot.occurrenceByIdentity(raw.scheduleId, raw.recurrenceId))
  }

  /** 空备注的 REPLACE 与 CLEAR 都是显式覆盖，不能等到界面展开时才按编码偏好拒绝。 */
  @Test
  fun blankDescriptionReplacementIsReadableWithoutLosingRawPatch() {
    listOf("", " \n ").forEach { description ->
      val adjustment = testAdjustmentResource(remoteId = 71L).copy(
        description = AtomicField(FieldPatch.Replace(description), 20),
      )
      val snapshot = assertIs<ScheduleSnapshotProjection.Success>(projector.project(
        "2020214988", TimeZone.UTC, emptyList(), listOf(testScheduleState(recurringParent())),
        listOf(testAdjustmentState(adjustment)),
      )).snapshot
      assertEquals(description, snapshot.occurrencesInRange(
        MinuteTimeDate(2026, 5, 1, 0, 0), MinuteTimeDate(2026, 5, 2, 0, 0),
      ).single().description)
      assertEquals(
        com.cyxbs.pages.schedule.domain.model.FieldPatch.Replace(description),
        snapshot.occurrenceAdjustments.single().patch?.description,
      )
    }
  }

  /** 标题等真正非法的 patch 在预投影时拒绝，不能进入持久化后的实例展开。 */
  @Test
  fun invalidOccurrenceTitleFailsBeforeSnapshotIsPublished() {
    val adjustment = testAdjustmentResource(remoteId = 71L).copy(
      title = AtomicField(FieldPatch.Replace(" "), 20),
    )
    val failure = assertIs<ScheduleSnapshotProjection.Failure>(projector.project(
      "2020214988", TimeZone.UTC, emptyList(), listOf(testScheduleState(recurringParent())),
      listOf(testAdjustmentState(adjustment)),
    ))
    assertTrue(failure.message.contains("patch.title"))
  }

  /** 同步保留未关联事务的完成记录，展示层忽略完成态；重新关联后无需改写子记录即可恢复。 */
  @Test
  fun unlinkedAffairKeepsRawCompletionWhileProjectingActiveOccurrence() {
    val start = TEST_OCCURRENCE_DATE + 9 * 60 * 60 * 1_000L
    val parent = recurringParent().copy(
      kind = ScheduleKind.AFFAIR,
      timing = AtomicField(TimingInput(TimingKind.TIMED, startAt = start, endAt = start + 3_600_000L), 10),
      linkedToCourse = AtomicField(true, 10),
      todoState = AtomicField(null, 20),
    )
    val completed = testAdjustmentResource(remoteId = 71L, version = 2).copy(
      status = AtomicField(OccurrenceStatus.COMPLETED, 10),
    )
    // 模拟远端完整快照：同一条历史调整跨清单关联变化始终保持原始状态和身份。
    listOf(null, TodoState.OPEN).forEach { todoState ->
      val snapshot = assertIs<ScheduleSnapshotProjection.Success>(projector.project(
        "2020214988", TimeZone.UTC, emptyList(),
        listOf(testScheduleState(parent.copy(todoState = AtomicField(todoState, 30)))),
        listOf(testAdjustmentState(completed)),
      )).snapshot
      assertEquals(UiOccurrenceStatus.COMPLETED, snapshot.occurrenceAdjustments.single().status)
      val occurrence = snapshot.occurrencesInRange(
        MinuteTimeDate(2026, 5, 1, 0, 0), MinuteTimeDate(2026, 5, 2, 0, 0),
      ).single()
      assertEquals(
        if (todoState == null) UiOccurrenceStatus.ACTIVE else UiOccurrenceStatus.COMPLETED,
        occurrence.status,
      )
    }
  }

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
