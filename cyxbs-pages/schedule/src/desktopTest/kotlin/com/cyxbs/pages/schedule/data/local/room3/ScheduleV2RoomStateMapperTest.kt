package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.repository.v3.toWire
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CompletionStatus
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.v2.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind
import com.cyxbs.pages.schedule.domain.sync.v2.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Room 双快照 state 与 common state 的无损映射测试。 */
class ScheduleV2RoomStateMapperTest {

  @Test
  fun threeKindsOfRemoteAndUpsertRoundTripThroughAccountState() {
    val roomState = ScheduleV2RoomAccountState(
      categories = listOf(categoryUpsertEntity()),
      schedules = listOf(scheduleUpsertEntity()),
      occurrenceOverrides = listOf(overrideUpsertEntity()),
    )

    assertEquals(roomState, roomState.toCommonAccountState(ACCOUNT_ID).toRoomAccountState())
  }

  @Test
  fun threeKindsOfDeleteRoundTripWithoutSnapshot() {
    val category = categoryDeleteEntity()
    val schedule = scheduleDeleteEntity()
    val override = overrideDeleteEntity()

    assertEquals(category, category.toCommonSyncState().toRoomEntity(ACCOUNT_ID))
    assertEquals(schedule, schedule.toCommonSyncState().toRoomEntity(ACCOUNT_ID))
    assertEquals(override, override.toCommonSyncState().toRoomEntity(ACCOUNT_ID))
  }

  @Test
  fun createUpsertWithNewerLocalRevisionRoundTripsAlongsideRemoteV1() {
    val remote = categoryResource("category-create", "服务端既有分类", version = 1)
    val pending = categoryResource("category-create", "本地新建分类", version = 0)
    val entity = ScheduleV2CategoryStateEntity(
      accountId = ACCOUNT_ID,
      categoryId = remote.identity.id,
      remoteSnapshot = CategoryRemoteSnapshot(remote, ServerResourceMeta(40, 41)).toWire(),
      pendingOperation = ScheduleV2PendingOperation.UPSERT,
      pendingSnapshot = pending.toWire(),
      pendingLocalModifiedAt = null,
      localRevision = 9,
      localBatchId = null,
    )

    // R 已收到 version=1 时，本地更高 revision 的 CREATE(U, version=0) 仍是有效 pending。
    assertEquals(entity, entity.toCommonSyncState().toRoomEntity(ACCOUNT_ID))
  }

  @Test
  fun remoteOnlyAndRemoteWithDeleteKeepConfirmedSnapshot() {
    val remoteOnly = categoryUpsertEntity().copy(
      pendingOperation = null,
      pendingSnapshot = null,
      pendingLocalModifiedAt = null,
      localRevision = null,
      localBatchId = null,
    )
    val remoteAndDelete = categoryDeleteEntity().copy(
      remoteSnapshot = CategoryRemoteSnapshot(
        categoryResource("category-delete", "待删除的远端分类"),
        ServerResourceMeta(50, 51),
      ).toWire(),
    )

    assertEquals(remoteOnly, remoteOnly.toCommonSyncState().toRoomEntity(ACCOUNT_ID))
    assertEquals(remoteAndDelete, remoteAndDelete.toCommonSyncState().toRoomEntity(ACCOUNT_ID))
  }

  @Test
  fun illegalPendingShapesAndPartitionMismatchFailFast() {
    val category = categoryUpsertEntity()
    val delete = categoryDeleteEntity()
    val schedule = scheduleUpsertEntity()
    val override = overrideUpsertEntity()

    // pending 列的完整性由 mapper 在读取数据库时守住，避免坏数据进入 common applier。
    assertFailsWith<IllegalArgumentException> {
      category.copy(pendingOperation = null).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      category.copy(pendingSnapshot = null).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      category.copy(pendingLocalModifiedAt = 1).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      category.copy(localRevision = null).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      delete.copy(pendingSnapshot = category.pendingSnapshot).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      delete.copy(pendingLocalModifiedAt = null).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      category.copy(categoryId = "another-category").toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      schedule.copy(pendingSnapshot = null).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      override.copy(occurrenceDate = override.occurrenceDate + 86_400_000).toCommonSyncState()
    }
    assertFailsWith<IllegalArgumentException> {
      ScheduleV2RoomAccountState(
        categories = listOf(category.copy(accountId = "another-account")),
        schedules = emptyList(),
        occurrenceOverrides = emptyList(),
      ).toCommonAccountState(ACCOUNT_ID)
    }
  }

  /** 构造同时拥有 confirmed remote 与待更新快照的 Category 行。 */
  private fun categoryUpsertEntity(): ScheduleV2CategoryStateEntity {
    val remote = categoryResource("category-1", "远端分类")
    val pending = categoryResource("category-1", "本地分类")
    return ScheduleV2CategoryStateEntity(
      accountId = ACCOUNT_ID,
      categoryId = remote.identity.id,
      remoteSnapshot = CategoryRemoteSnapshot(remote, ServerResourceMeta(10, 11)).toWire(),
      pendingOperation = ScheduleV2PendingOperation.UPSERT,
      pendingSnapshot = pending.toWire(),
      pendingLocalModifiedAt = null,
      localRevision = 3,
      localBatchId = "batch-1",
    )
  }

  /** 构造同时拥有 confirmed remote 与待更新快照的 Schedule 行。 */
  private fun scheduleUpsertEntity(): ScheduleV2ScheduleStateEntity {
    val remote = scheduleResource("schedule-1", "远端日程")
    val pending = scheduleResource("schedule-1", "本地日程")
    return ScheduleV2ScheduleStateEntity(
      accountId = ACCOUNT_ID,
      scheduleId = remote.identity.id,
      remoteSnapshot = ScheduleRemoteSnapshot(remote, ServerResourceMeta(20, 21), 86_400_000).toWire(),
      pendingOperation = ScheduleV2PendingOperation.UPSERT,
      pendingSnapshot = pending.toWire(),
      pendingLocalModifiedAt = null,
      localRevision = 4,
      localBatchId = "batch-1",
    )
  }

  /** 构造同时拥有 confirmed remote 与待更新快照的 OccurrenceOverride 行。 */
  private fun overrideUpsertEntity(): ScheduleV2OccurrenceOverrideStateEntity {
    val remote = overrideResource("schedule-1", 172_800_000, "远端覆盖")
    val pending = overrideResource("schedule-1", 172_800_000, "本地覆盖")
    return ScheduleV2OccurrenceOverrideStateEntity(
      accountId = ACCOUNT_ID,
      scheduleId = remote.identity.scheduleId,
      occurrenceDate = remote.identity.occurrenceDate,
      remoteSnapshot = OccurrenceOverrideRemoteSnapshot(remote, ServerResourceMeta(30, 31)).toWire(),
      pendingOperation = ScheduleV2PendingOperation.UPSERT,
      pendingSnapshot = pending.toWire(),
      pendingLocalModifiedAt = null,
      localRevision = 5,
      localBatchId = "batch-1",
    )
  }

  /** DELETE 行只保留 identity、操作时刻与纯本地 revision。 */
  private fun categoryDeleteEntity() = ScheduleV2CategoryStateEntity(
    accountId = ACCOUNT_ID,
    categoryId = "category-delete",
    remoteSnapshot = null,
    pendingOperation = ScheduleV2PendingOperation.DELETE,
    pendingSnapshot = null,
    pendingLocalModifiedAt = 100,
    localRevision = 6,
    localBatchId = "batch-2",
  )

  /** DELETE 行只保留 identity、操作时刻与纯本地 revision。 */
  private fun scheduleDeleteEntity() = ScheduleV2ScheduleStateEntity(
    accountId = ACCOUNT_ID,
    scheduleId = "schedule-delete",
    remoteSnapshot = null,
    pendingOperation = ScheduleV2PendingOperation.DELETE,
    pendingSnapshot = null,
    pendingLocalModifiedAt = 101,
    localRevision = 7,
    localBatchId = "batch-2",
  )

  /** DELETE 行只保留 identity、操作时刻与纯本地 revision。 */
  private fun overrideDeleteEntity() = ScheduleV2OccurrenceOverrideStateEntity(
    accountId = ACCOUNT_ID,
    scheduleId = "schedule-delete",
    occurrenceDate = 259_200_000,
    remoteSnapshot = null,
    pendingOperation = ScheduleV2PendingOperation.DELETE,
    pendingSnapshot = null,
    pendingLocalModifiedAt = 102,
    localRevision = 8,
    localBatchId = "batch-2",
  )

  private fun categoryResource(id: String, name: String, version: Long = 1) = CategoryResource(
    identity = CategoryIdentity(id),
    version = version,
    name = AtomicField(name, 1),
    color = AtomicField(null, 2),
    sortOrder = AtomicField(1, 3),
  )

  private fun scheduleResource(id: String, title: String) = ScheduleResource(
    identity = ScheduleIdentity(id),
    version = 2,
    title = AtomicField(title, 10),
    description = AtomicField("说明", 11),
    categoryId = AtomicField("category-1", 12),
    timing = AtomicField(TimingInput(TimingKind.TIMED, startAt = 100, endAt = 200), 13),
    recurrence = AtomicField(
      RecurrenceInput(
        frequency = RecurrenceFrequency.WEEKLY,
        interval = 1,
        anchorDate = 86_400_000,
        weekdays = setOf(Weekday.MO),
      ),
      14,
    ),
    reminders = AtomicField(listOf(ReminderInput(15, "reminder-1")), 15),
    completion = AtomicField(CompletionStatus.OPEN, 16),
  )

  private fun overrideResource(
    scheduleId: String,
    occurrenceDate: Long,
    title: String,
  ) = OccurrenceOverrideResource(
    identity = OccurrenceOverrideIdentity(scheduleId, occurrenceDate),
    version = 3,
    status = AtomicField(OccurrenceStatus.COMPLETED, 20),
    title = AtomicField(FieldPatch.Replace(title), 21),
    description = AtomicField(FieldPatch.Clear, 22),
    reminders = AtomicField(FieldPatch.Replace(listOf(ReminderInput(5, "reminder-2"))), 23),
  )

  private companion object {
    const val ACCOUNT_ID = "account-1"
  }
}
