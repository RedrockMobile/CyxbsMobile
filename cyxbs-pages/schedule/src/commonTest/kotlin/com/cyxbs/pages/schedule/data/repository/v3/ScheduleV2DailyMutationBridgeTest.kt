package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResult
import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResultCode
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.ResultReason
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicDeleteResult
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicUpsertResult
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.TodoState
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.PendingChange
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Schedule 日常聚合 capture 与业务拒绝清理语义的纯 common 合同测试。 */
class ScheduleV2DailyMutationBridgeTest {
  private val bridge = ScheduleV2DailyMutationBridge()

  @Test
  fun scheduleCreateCarriesPendingCategoryAndOccurrenceInOneBatch() {
    val category = categoryState(revision = 1)
    val schedule = scheduleState(version = 0, revision = 2)
    val override = overrideState(revision = 3)

    val captured = assertIs<ScheduleV2DailyMutationCapture.Batch>(
      bridge.capture("daily-create", 2, listOf(category), listOf(schedule), listOf(override)),
    )

    assertEquals(ScheduleV2DailyMutationMethod.CREATE, captured.method)
    assertEquals(listOf(CATEGORY_ID), captured.batch.categories.upserts.map { it.id })
    assertEquals(listOf(SCHEDULE_ID), captured.batch.schedules.upserts.map { it.id })
    assertEquals(listOf(OCCURRENCE_DATE), captured.batch.occurrenceOverrides.upserts.map { it.occurrenceDate })
    assertEquals(3, captured.capture.atomicBatches.single().let {
      it.categories.size + it.schedules.size + it.occurrenceOverrides.size
    })
  }

  /** 新分类不应把既有 Schedule PATCH 的日常路由从 PUT 误判为 POST。 */
  @Test
  fun scheduleUpdateWithNewCategoryUsesUpdateRoute() {
    val category = categoryState(revision = 7)
    val remote = scheduleResource(version = 3, title = "remote")
    val schedule = ScheduleSyncState(
      identity = remote.identity,
      remoteSnapshot = ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      pending = PendingUpsert(remote.copy(title = AtomicField("updated", 7)), localRevision = 7),
    )

    val captured = assertIs<ScheduleV2DailyMutationCapture.Batch>(
      bridge.capture("daily-update-category", 7, listOf(category), listOf(schedule), emptyList()),
    )

    assertEquals(ScheduleV2DailyMutationMethod.UPDATE, captured.method)
    assertEquals(listOf(CATEGORY_ID), captured.batch.categories.upserts.map { it.id })
    assertEquals(listOf(SCHEDULE_ID), captured.batch.schedules.upserts.map { it.id })
  }

  @Test
  fun explicitRejectedClearsMatchingBatchButPreservesNewerU() {
    val category = categoryState(revision = 1)
    val scheduleR = scheduleState(version = 0, revision = 2)
    val override = overrideState(revision = 3)
    val captured = assertIs<ScheduleV2DailyMutationCapture.Batch>(
      bridge.capture("daily-rejected", 2, listOf(category), listOf(scheduleR), listOf(override)),
    )
    val rejected = rejectedResult(captured)

    val cleared = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.apply(captured, rejected, listOf(category), listOf(scheduleR), listOf(override)),
    )
    assertTrue(cleared.categories.isEmpty())
    assertTrue(cleared.schedules.isEmpty())
    assertTrue(cleared.occurrenceOverrides.isEmpty())

    val scheduleU = scheduleR.replacePending(
      PendingUpsert(scheduleResource(version = 0, title = "U"), localRevision = 4),
    )
    val kept = assertIs<ScheduleV2ApplyResult.Success>(
      bridge.apply(captured, rejected, listOf(category), listOf(scheduleU), listOf(override)),
    )
    assertEquals(listOf(1L), kept.categories.map { it.pending?.localRevision })
    assertEquals(listOf(4L), kept.schedules.map { it.pending?.localRevision })
    assertEquals(listOf(3L), kept.occurrenceOverrides.map { it.pending?.localRevision })
  }

  @Test
  fun categoryOnlyAndParentDeleteStillUseOneAggregateRequest() {
    val category = categoryState(revision = 5)
    val categoryCapture = assertIs<ScheduleV2DailyMutationCapture.Batch>(
      bridge.capture("daily-category", 5, listOf(category), emptyList(), emptyList()),
    )
    assertEquals(ScheduleV2DailyMutationMethod.CREATE, categoryCapture.method)
    assertEquals(1, categoryCapture.batch.categories.upserts.size)

    val remote = scheduleResource(version = 7)
    val scheduleDelete = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote, ServerResourceMeta(1, 2)),
      PendingDelete(remote.identity, localModifiedAt = 10, localRevision = 6, localBatchId = "delete-6"),
    )
    val override = overrideState(
      revision = 6,
      delete = true,
      batchId = "delete-6",
    )
    val deleteCapture = assertIs<ScheduleV2DailyMutationCapture.Batch>(
      bridge.capture("daily-delete", 6, emptyList(), listOf(scheduleDelete), listOf(override)),
    )
    assertEquals(ScheduleV2DailyMutationMethod.DELETE, deleteCapture.method)
    assertEquals(1, deleteCapture.batch.schedules.deletes.size)
    assertEquals(1, deleteCapture.batch.occurrenceOverrides.deletes.size)
  }

  /** 构造与 capture 六个列表逐项对齐的业务拒绝结果。 */
  private fun rejectedResult(captured: ScheduleV2DailyMutationCapture.Batch): AtomicBatchResult {
    val batch = captured.batch
    return AtomicBatchResult(
      batchId = batch.batchId,
      code = AtomicBatchResultCode.REJECTED,
      reason = ResultReason.FINAL_GRAPH_INVALID,
      categories = CategoryAtomicResultBlock(
        batch.categories.upserts.map { CategoryAtomicUpsertResult(it.id, AtomicBatchResultCode.REJECTED) },
        batch.categories.deletes.map { CategoryAtomicDeleteResult(it.id, AtomicBatchResultCode.REJECTED) },
        emptyList(),
        emptyList(),
      ),
      schedules = ScheduleAtomicResultBlock(
        batch.schedules.upserts.map { ScheduleAtomicUpsertResult(it.id, AtomicBatchResultCode.REJECTED) },
        batch.schedules.deletes.map { ScheduleAtomicDeleteResult(it.id, AtomicBatchResultCode.REJECTED) },
        emptyList(),
        emptyList(),
      ),
      occurrenceOverrides = OccurrenceOverrideAtomicResultBlock(
        batch.occurrenceOverrides.upserts.map {
          OccurrenceOverrideAtomicUpsertResult(it.scheduleId, it.occurrenceDate, AtomicBatchResultCode.REJECTED)
        },
        batch.occurrenceOverrides.deletes.map {
          OccurrenceOverrideAtomicDeleteResult(it.scheduleId, it.occurrenceDate, AtomicBatchResultCode.REJECTED)
        },
        emptyList(),
        emptyList(),
      ),
    )
  }

  private fun categoryState(revision: Long): CategorySyncState {
    val resource = CategoryResource(
      CategoryIdentity(CATEGORY_ID),
      version = 0,
      name = AtomicField("分类", 1),
      color = AtomicField(null, 1),
      sortOrder = AtomicField(0, 1),
    )
    return CategorySyncState(resource.identity, null, PendingUpsert(resource, revision))
  }

  private fun scheduleState(version: Long, revision: Long): ScheduleSyncState {
    val resource = scheduleResource(version)
    return ScheduleSyncState(resource.identity, null, PendingUpsert(resource, revision))
  }

  private fun overrideState(
    revision: Long,
    delete: Boolean = false,
    batchId: String? = null,
  ): OccurrenceOverrideSyncState {
    val identity = OccurrenceOverrideIdentity(SCHEDULE_ID, OCCURRENCE_DATE)
    val resource = OccurrenceOverrideResource(
      identity,
      version = 0,
      status = AtomicField(OccurrenceStatus.ACTIVE, 1),
      timing = AtomicField(FieldPatch.Inherit, 1),
      title = AtomicField(FieldPatch.Inherit, 1),
      description = AtomicField(FieldPatch.Inherit, 1),
      categoryId = AtomicField(FieldPatch.Inherit, 1),
      reminders = AtomicField(FieldPatch.Inherit, 1),
    )
    val pending: PendingChange<OccurrenceOverrideIdentity, OccurrenceOverrideResource> = if (delete) {
      PendingDelete(identity, localModifiedAt = 10, localRevision = revision, localBatchId = batchId)
    } else {
      PendingUpsert(resource, localRevision = revision, localBatchId = batchId)
    }
    return OccurrenceOverrideSyncState(identity, null, pending)
  }

  private fun scheduleResource(version: Long, title: String = "日程"): ScheduleResource = ScheduleResource(
    identity = ScheduleIdentity(SCHEDULE_ID),
    version = version,
    kind = ScheduleKind.TODO,
    title = AtomicField(title, 1),
    description = AtomicField("", 1),
    categoryId = AtomicField(CATEGORY_ID, 1),
    timing = AtomicField(TimingInput(TimingKind.UNSCHEDULED), 1),
    recurrence = AtomicField(null, 1),
    reminders = AtomicField(emptyList(), 1),
    todoState = AtomicField(TodoState.OPEN, 1),
    linkedToCourse = AtomicField(false, 1),
  )

  private companion object {
    const val CATEGORY_ID = "0197f000-0000-7000-8000-000000000010"
    const val SCHEDULE_ID = "0197f000-0000-7000-8000-000000000001"
    const val OCCURRENCE_DATE = 1_786_665_600_000L
  }
}
