package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.data.remote.MutationResponse
import com.cyxbs.pages.schedule.data.remote.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.MutationResourceResponse
import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.ResultReason
import com.cyxbs.pages.schedule.data.remote.UpsertResult
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.domain.sync.TodoState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 日常请求只验证一次本地命令产生的逐资源变更，不引入额外批次模型。 */
class ScheduleDailyMutationBridgeTest {
  private val bridge = ScheduleDailyMutationBridge()

  @Test
  fun captureIncludesOlderPendingCategoryReferencedByCurrentSchedule() {
    val category = categoryState(revision = 1)
    val schedule = scheduleState(version = 0, revision = 2)

    val captured = assertIs<ScheduleDailyMutationCapture.Ready>(
      bridge.capture(2, listOf(category), listOf(schedule), emptyList()),
    )

    assertEquals(ScheduleDailyMutationMethod.CREATE, captured.method)
    assertEquals(listOf(CATEGORY_ID), captured.request.categories.upserts.map { it.localId })
    assertEquals(listOf(SCHEDULE_ID), captured.request.schedules.upserts.map { it.id })
  }

  @Test
  fun sameRevisionCategoryAndScheduleAreUploadedTogether() {
    val category = categoryState(revision = 7)
    val remote = scheduleResource(version = 3, title = "remote")
    val schedule = ScheduleSyncState(
      identity = remote.identity,
      remoteSnapshot = ScheduleRemoteSnapshot(remote),
      pending = PendingUpsert(remote.copy(title = AtomicField("updated", 7)), localRevision = 7),
    )

    val captured = assertIs<ScheduleDailyMutationCapture.Ready>(
      bridge.capture(7, listOf(category), listOf(schedule), emptyList()),
    )

    assertEquals(ScheduleDailyMutationMethod.UPDATE, captured.method)
    assertEquals(listOf(CATEGORY_ID), captured.request.categories.upserts.map { it.localId })
    assertEquals(listOf(SCHEDULE_ID), captured.request.schedules.upserts.map { it.id })
  }

  /** 修改到一个已同步分类时，只解析其远端 ID，不把无 pending 的分类重复上传。 */
  @Test
  fun updateCanReferenceSyncedCategoryWithoutUploadingCategory() {
    val category = categoryState(revision = 1).let { state ->
      val pending = assertIs<PendingUpsert<CategoryIdentity, CategoryResource>>(state.pending)
      val remote = pending.resource.copy(remoteId = 41L, version = 1)
      CategorySyncState(remote.identity, CategoryRemoteSnapshot(remote), null)
    }
    val remoteSchedule = scheduleResource(version = 1, title = "remote")
    val schedule = ScheduleSyncState(
      identity = remoteSchedule.identity,
      remoteSnapshot = ScheduleRemoteSnapshot(remoteSchedule.copy(categoryId = AtomicField(null, 1))),
      pending = PendingUpsert(
        remoteSchedule.copy(categoryId = AtomicField(CATEGORY_ID, 2)),
        localRevision = 2,
      ),
    )

    val captured = assertIs<ScheduleDailyMutationCapture.Ready>(
      bridge.capture(2, listOf(category), listOf(schedule), emptyList()),
    )

    assertEquals(ScheduleDailyMutationMethod.UPDATE, captured.method)
    assertEquals(emptyList(), captured.request.categories.upserts)
    assertEquals(41L, captured.request.schedules.upserts.single().categoryId.data)
    assertNull(captured.request.schedules.upserts.single().categoryLocalId)
  }

  @Test
  fun rejectedResourceKeepsPending() = runTest {
    val category = categoryState(revision = 1)
    val schedule = scheduleState(version = 0, revision = 2)
    val captured = assertIs<ScheduleDailyMutationCapture.Ready>(
      bridge.capture(2, listOf(category), listOf(schedule), emptyList()),
    )
    val response = emptyResponse(
      categoryUpserts = listOf(
        UpsertResult(
          result = MutationResultCode.SUCCESS,
          resource = captured.request.categories.upserts.single().copy(
            localId = null,
            id = 41L,
            version = 1uL,
          ),
        ),
      ),
      scheduleUpserts = listOf(
        UpsertResult(
          result = MutationResultCode.REJECTED,
          reason = ResultReason.INVALID_REQUEST,
          info = "标题不能为空",
        ),
      ),
    )

    val applied = assertIs<ScheduleApplyResult.Success>(
      bridge.apply(captured, response, listOf(category), listOf(schedule), emptyList()),
    )

    assertEquals(2, assertNotNull(applied.schedules.single().pending).localRevision)
  }

  @Test
  fun parentDeleteUsesDeleteRoute() {
    val remote = scheduleResource(version = 7)
    val schedule = ScheduleSyncState(
      remote.identity,
      ScheduleRemoteSnapshot(remote),
      PendingDelete(remote.identity, localModifiedAt = 10, localRevision = 6),
    )

    val captured = assertIs<ScheduleDailyMutationCapture.Ready>(
      bridge.capture(6, emptyList(), listOf(schedule), emptyList()),
    )

    assertEquals(ScheduleDailyMutationMethod.DELETE, captured.method)
    assertEquals(listOf(SCHEDULE_ID), captured.request.schedules.deletes.map { it.id })
  }

  private fun emptyResponse(
    categoryUpserts: List<UpsertResult<CategoryInput>> = emptyList(),
    scheduleUpserts: List<UpsertResult<com.cyxbs.pages.schedule.data.remote.ScheduleInput>> = emptyList(),
  ) = MutationResponse(
    categories = MutationResourceResponse(categoryUpserts, emptyList()),
    schedules = MutationResourceResponse(scheduleUpserts, emptyList()),
    occurrenceAdjustments = MutationResourceResponse(emptyList(), emptyList()),
  )

  private fun categoryState(revision: Long): CategorySyncState {
    val resource = CategoryResource(
      CategoryIdentity(CATEGORY_ID),
      remoteId = null,
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

  private fun scheduleResource(version: Long, title: String = "日程"): ScheduleResource = ScheduleResource(
    identity = ScheduleIdentity(SCHEDULE_ID),
    version = version,
    kind = ScheduleKind.TODO,
    title = AtomicField(title, 1),
    description = AtomicField("", 1),
    categoryId = AtomicField(CATEGORY_ID, 1),
    timing = AtomicField(TimingInput(TimingKind.UNSCHEDULED), 1),
    recurrence = AtomicField(null, 1),
    reminder = AtomicField(null, 1),
    todoState = AtomicField(TodoState.OPEN, 1),
    linkedToCourse = AtomicField(false, 1),
  )

  private companion object {
    const val CATEGORY_ID = "0197f000-0000-7000-8000-000000000010"
    const val SCHEDULE_ID = "0197f000-0000-7000-8000-000000000001"
  }
}
