package com.cyxbs.pages.schedule.data.failure

import com.cyxbs.pages.schedule.data.remote.CategoryMutationRequest
import com.cyxbs.pages.schedule.data.remote.DeleteResource
import com.cyxbs.pages.schedule.data.remote.DeleteResult
import com.cyxbs.pages.schedule.data.remote.MutationRequest
import com.cyxbs.pages.schedule.data.remote.MutationResourceResponse
import com.cyxbs.pages.schedule.data.remote.MutationResponse
import com.cyxbs.pages.schedule.data.remote.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentMutationRequest
import com.cyxbs.pages.schedule.data.remote.ResultReason
import com.cyxbs.pages.schedule.data.remote.ScheduleMutationRequest
import com.cyxbs.pages.schedule.data.repository.testCategoryResource
import com.cyxbs.pages.schedule.data.repository.testCategoryState
import com.cyxbs.pages.schedule.data.repository.testScheduleResource
import com.cyxbs.pages.schedule.data.repository.testScheduleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 失败记录只读取真正失败的日程，并能保留其分类引用。 */
class ScheduleFailureRecordsTest {

  /** 全部成功时不应扫描无关日程，更不能因其带分类而中断成功响应的落库。 */
  @Test
  fun acceptedResponseDoesNotBuildFailureSnapshots() {
    val category = testCategoryResource(remoteId = 41L)
    val schedule = testScheduleResource(version = 1, categoryLocalId = category.identity.id)

    val records = createRejectedScheduleFailureRecords(
      operation = ScheduleFailureOperation.SYNC,
      request = emptyMutationRequest(),
      response = emptyMutationResponse(),
      currentCategories = listOf(testCategoryState(category)),
      currentSchedules = listOf(testScheduleState(schedule)),
      adjustmentDeleteScheduleIds = emptyMap(),
      failedAt = 100,
    )

    assertTrue(records.isEmpty())
  }

  /** 删除被拒绝时，请求不含日程快照，必须从当前状态恢复包含远端分类 ID 的编辑源数据。 */
  @Test
  fun rejectedDeleteKeepsCategoryInFailureSnapshot() {
    val category = testCategoryResource(remoteId = 41L)
    val schedule = testScheduleResource(version = 1, categoryLocalId = category.identity.id)
    val request = emptyMutationRequest().copy(
      schedules = ScheduleMutationRequest(
        upserts = emptyList(),
        deletes = listOf(DeleteResource(schedule.identity.id)),
      ),
    )
    val response = emptyMutationResponse().copy(
      schedules = MutationResourceResponse(
        upsertResults = emptyList(),
        deleteResults = listOf(
          DeleteResult(
            id = schedule.identity.id,
            result = MutationResultCode.REJECTED,
            reason = ResultReason.RESOURCE_CHANGED,
            info = "日程已在远端更新",
          ),
        ),
      ),
    )

    val record = createRejectedScheduleFailureRecords(
      operation = ScheduleFailureOperation.DELETE,
      request = request,
      response = response,
      currentCategories = listOf(testCategoryState(category)),
      currentSchedules = listOf(testScheduleState(schedule)),
      adjustmentDeleteScheduleIds = emptyMap(),
      failedAt = 100,
    ).single()

    assertEquals(41L, record.sourceSchedule.categoryId.data)
    assertEquals(ResultReason.RESOURCE_CHANGED.name, record.reasonCode)
  }

  /** 构造不含任何逐资源操作的请求。 */
  private fun emptyMutationRequest(): MutationRequest = MutationRequest(
    categories = CategoryMutationRequest(emptyList(), emptyList()),
    schedules = ScheduleMutationRequest(emptyList(), emptyList()),
    occurrenceAdjustments = OccurrenceAdjustmentMutationRequest(emptyList(), emptyList()),
  )

  /** 构造与空请求逐项对齐的响应。 */
  private fun emptyMutationResponse(): MutationResponse = MutationResponse(
    categories = MutationResourceResponse(emptyList(), emptyList()),
    schedules = MutationResourceResponse(emptyList(), emptyList()),
    occurrenceAdjustments = MutationResourceResponse(emptyList(), emptyList()),
  )
}
