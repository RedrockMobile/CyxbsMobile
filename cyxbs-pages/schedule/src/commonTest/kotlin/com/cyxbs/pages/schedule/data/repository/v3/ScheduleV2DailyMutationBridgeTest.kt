package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleCurrent
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleTombstone
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ServerResourceMeta as WireServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CompletionStatus
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 单条日常 Schedule mutation 对 planner/applier 复用边界的合同测试。 */
class ScheduleV2DailyMutationBridgeTest {
  private val bridge = ScheduleV2DailyMutationBridge()

  @Test
  fun captureDistinguishesCreateUpdateDeleteAndProjectsRemoteVersion() {
    val createResource = scheduleResource(version = 0, title = "create")
    val createState = ScheduleSyncState(
      createResource.identity,
      remoteSnapshot = null,
      pending = PendingUpsert(createResource, localRevision = 1),
    )
    val create = assertIs<ScheduleV2DailyMutationCapture.Upsert>(bridge.capture(createState))
    assertTrue(create.create)
    assertEquals(0uL, create.input.version)
    assertEquals("create", create.input.title.data)

    val remote = scheduleResource(version = 8, title = "remote")
    val stalePatch = remote.copy(version = 7, title = AtomicField("update", 20))
    val updateState = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      PendingUpsert(stalePatch, localRevision = 2),
    )
    val update = assertIs<ScheduleV2DailyMutationCapture.Upsert>(bridge.capture(updateState))
    assertEquals(false, update.create)
    assertEquals(8uL, update.input.version)
    assertEquals("update", update.input.title.data)
    assertEquals(7, assertIs<PendingUpsert<*, *>>(updateState.pending).resource.version)

    val deleteState = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      PendingDelete(remote.identity, localModifiedAt = 30, localRevision = 3),
    )
    val delete = assertIs<ScheduleV2DailyMutationCapture.Delete>(bridge.capture(deleteState))
    assertEquals(SCHEDULE_ID, delete.input.id)
    assertEquals(30, delete.input.localModifiedAt)
  }

  @Test
  fun matchingUpsertClearsPendingAndAcceptsCanonicalCurrent() {
    val state = updateState(pendingRevision = 10)
    val captured = assertIs<ScheduleV2DailyMutationCapture.Upsert>(bridge.capture(state))
    val result = ScheduleUpsertResult(
      id = SCHEDULE_ID,
      code = MutationResultCode.APPLIED,
      current = current(version = 8, title = "merged"),
    )

    val applied = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.applyUpsert(captured, result, emptyList(), listOf(state), emptyList()),
    ).schedules.single()

    assertNull(applied.pending)
    assertEquals(8, applied.remoteSnapshot?.version)
    assertEquals("merged", applied.effectiveResource()?.title?.data)
  }

  @Test
  fun newerUpsertIsPreservedByCompareAndClear() {
    val capturedState = updateState(pendingRevision = 10)
    val captured = assertIs<ScheduleV2DailyMutationCapture.Upsert>(bridge.capture(capturedState))
    val currentU = capturedState.replacePending(
      PendingUpsert(
        pendingResource(capturedState).copy(title = AtomicField("U", 99)),
        localRevision = 11,
      ),
    )

    val applied = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.applyUpsert(
        captured,
        ScheduleUpsertResult(
          id = SCHEDULE_ID,
          code = MutationResultCode.APPLIED,
          current = current(version = 8, title = "R"),
        ),
        emptyList(),
        listOf(currentU),
        emptyList(),
      ),
    ).schedules.single()

    assertEquals(8, applied.remoteSnapshot?.version)
    assertEquals(11, applied.pending?.localRevision)
    assertEquals("U", applied.effectiveResource()?.title?.data)
  }

  @Test
  fun rejectedUpsertKeepsPending() {
    val state = updateState(pendingRevision = 10)
    val captured = assertIs<ScheduleV2DailyMutationCapture.Upsert>(bridge.capture(state))

    val applied = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.applyUpsert(
        captured,
        ScheduleUpsertResult(SCHEDULE_ID, MutationResultCode.REJECTED),
        emptyList(),
        listOf(state),
        emptyList(),
      ),
    ).schedules.single()

    assertEquals(10, applied.pending?.localRevision)
  }

  @Test
  fun tombstoneClearsNewerUpsertAndMatchingDelete() {
    val capturedState = updateState(pendingRevision = 10)
    val capturedUpsert = assertIs<ScheduleV2DailyMutationCapture.Upsert>(bridge.capture(capturedState))
    val newerU = capturedState.replacePending(
      PendingUpsert(pendingResource(capturedState), localRevision = 11),
    )
    val upsertDeleted = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.applyUpsert(
        capturedUpsert,
        ScheduleUpsertResult(
          id = SCHEDULE_ID,
          code = MutationResultCode.RESOURCE_DELETED,
          tombstone = ScheduleTombstone(SCHEDULE_ID, deletedAt = 100),
        ),
        emptyList(),
        listOf(newerU),
        emptyList(),
      ),
    )
    assertTrue(upsertDeleted.schedules.isEmpty())

    val remote = scheduleResource(version = 7)
    val deleteState = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      PendingDelete(remote.identity, localModifiedAt = 30, localRevision = 12),
    )
    val capturedDelete = assertIs<ScheduleV2DailyMutationCapture.Delete>(bridge.capture(deleteState))
    val deleteApplied = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.applyDelete(
        capturedDelete,
        ScheduleDeleteResult(
          id = SCHEDULE_ID,
          code = MutationResultCode.DELETED,
          tombstone = ScheduleTombstone(SCHEDULE_ID, deletedAt = 101),
        ),
        emptyList(),
        listOf(deleteState),
        emptyList(),
      ),
    )
    assertTrue(deleteApplied.schedules.isEmpty())
  }

  @Test
  fun atomicPendingIsRejectedForDailyEndpoint() {
    val remote = scheduleResource(version = 7)
    val state = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      PendingUpsert(remote, localRevision = 10, localBatchId = "batch-1"),
    )

    val failure = assertIs<ScheduleV2DailyMutationCapture.Failure>(bridge.capture(state))

    assertTrue(failure.message.contains("atomic"))
  }

  private fun updateState(pendingRevision: Long): ScheduleSyncState {
    val remote = scheduleResource(version = 7, title = "remote")
    return ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      PendingUpsert(
        remote.copy(title = AtomicField("pending", 10)),
        localRevision = pendingRevision,
      ),
    )
  }

  private fun pendingResource(state: ScheduleSyncState): ScheduleResource {
    val pending = assertIs<PendingUpsert<*, *>>(state.pending)
    return assertIs(pending.resource)
  }

  private fun current(version: Long, title: String): ScheduleCurrent = ScheduleCurrent(
    resource = scheduleResource(version, title).toWire(),
    meta = WireServerResourceMeta(createdAt = 1, remoteModifiedAt = 2),
  )

  private fun scheduleResource(
    version: Long,
    title: String = "schedule",
  ): ScheduleResource = ScheduleResource(
    identity = ScheduleIdentity(SCHEDULE_ID),
    version = version,
    title = AtomicField(title, 1),
    description = AtomicField("description", 2),
    categoryId = AtomicField("category-1", 3),
    timing = AtomicField(TimingInput(TimingKind.UNSCHEDULED), 4),
    recurrence = AtomicField(null, 5),
    reminders = AtomicField(listOf(ReminderInput(15, "")), 6),
    completion = AtomicField(CompletionStatus.OPEN, 7),
  )

  private companion object {
    const val SCHEDULE_ID = "0197f000-0000-7000-8000-000000000001"
  }
}
