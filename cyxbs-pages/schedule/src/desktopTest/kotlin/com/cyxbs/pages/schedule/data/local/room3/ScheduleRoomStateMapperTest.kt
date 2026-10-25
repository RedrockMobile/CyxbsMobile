package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.pages.schedule.data.repository.TEST_CATEGORY_LOCAL_ID
import com.cyxbs.pages.schedule.data.repository.testAdjustmentResource
import com.cyxbs.pages.schedule.data.repository.testAdjustmentState
import com.cyxbs.pages.schedule.data.repository.testCategoryResource
import com.cyxbs.pages.schedule.data.repository.testCategoryState
import com.cyxbs.pages.schedule.data.repository.testScheduleResource
import com.cyxbs.pages.schedule.data.repository.testScheduleState
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 最终本地双快照与 Room typed entity 的往返测试。 */
class ScheduleRoomStateMapperTest {

  /** 已同步分类保留服务端自增 ID，本地修改则保留同一 UUID 与 localRevision。 */
  @Test
  fun categoryRemoteAndPendingRoundTrip() {
    val remote = testCategoryResource(remoteId = 41L, version = 3)
    val state = testCategoryState(
      remote,
      PendingUpsert(remote.copy(name = AtomicField("本地修改", 20)), 4),
    )

    val restored = state.toRoomEntity(ACCOUNT_ID).toCommonSyncState()

    assertEquals(state, restored)
    assertEquals(41L, restored.remoteSnapshot?.resource?.remoteId)
  }

  /** 日程 JSON 在落库时保存远端分类 ID，读回后恢复客户端分类 UUID。 */
  @Test
  fun scheduleCategoryReferenceRoundTrip() {
    val category = testCategoryResource(remoteId = 41L, version = 2)
    val schedule = testScheduleResource(version = 3, categoryLocalId = category.identity.id)
    val common = ScheduleCommonAccountState(
      accountId = ACCOUNT_ID,
      categories = listOf(testCategoryState(category)),
      schedules = listOf(testScheduleState(schedule)),
      occurrenceAdjustments = emptyList(),
    )

    val room = common.toRoomAccountState()
    assertEquals(41L, room.schedules.single().remoteSnapshot?.categoryId?.data)

    val restored = room.toCommonAccountState(ACCOUNT_ID)
    assertEquals(TEST_CATEGORY_LOCAL_ID, restored.schedules.single().effectiveResource()?.categoryId?.data)
  }

  /** 单次调整使用本地 UUID 作 Room 主键，同时保留服务端数字 ID 和不可变日期槽。 */
  @Test
  fun occurrenceAdjustmentRoundTrip() {
    val remote = testAdjustmentResource(remoteId = 71L, version = 4)
    val state = testAdjustmentState(
      remote,
      PendingDelete(remote.identity, localModifiedAt = 30, localRevision = 5),
    )

    val entity = state.toRoomEntity(ACCOUNT_ID) { null }
    val restored = entity.toCommonSyncState { null }

    assertEquals(state, restored)
    assertEquals(remote.identity.localId, entity.localId)
    assertEquals(remote.identity.originalOccurrenceDate, entity.originalOccurrenceDate)
    assertIs<PendingDelete<*, *>>(restored.pending)
  }

  /** pending 操作与 payload 形状不一致时必须拒绝读取，不能吞掉损坏状态。 */
  @Test
  fun malformedPendingShapeFailsClosed() {
    val entity = testCategoryState(testCategoryResource()).toRoomEntity(ACCOUNT_ID).copy(
      pendingOperation = SchedulePendingOperation.UPSERT,
      pendingSnapshot = null,
      localRevision = 1,
    )

    assertFailsWith<IllegalArgumentException> { entity.toCommonSyncState() }
  }

  private companion object {
    const val ACCOUNT_ID = "2020214988"
  }
}
