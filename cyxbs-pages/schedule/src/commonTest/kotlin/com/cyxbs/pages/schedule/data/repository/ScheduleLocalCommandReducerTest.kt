package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency as SyncRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** UI 命令到最终本地 pending 状态的关键状态转换测试。 */
class ScheduleLocalCommandReducerTest {
  private val reducer = ScheduleLocalCommandReducer()

  /** 创建日程写入 version=0 的完整 pending，不制造远端快照。 */
  @Test
  fun createScheduleProducesPendingCreate() = runTest {
    val result = assertIs<ScheduleLocalCommandResult.Applied>(
      reducer.reduce(
        categories = emptyList(),
        schedules = emptyList(),
        occurrenceAdjustments = emptyList(),
        command = ScheduleCommand.Create(uiSchedule()),
        nowMillis = 100,
        localRevision = 1,
      ),
    )

    val state = result.schedules.single()
    assertNull(state.remoteSnapshot)
    assertEquals(0, state.effectiveResource()?.version)
    assertEquals(1, state.pending?.localRevision)
  }

  /** 未改变任何业务字段的更新不创建新的 pending。 */
  @Test
  fun unchangedUpdateIsNoOp() = runTest {
    val resource = testScheduleResource(version = 3)
    val state = testScheduleState(resource)
    val projected = assertIs<ScheduleSnapshotProjection.Success>(
      ScheduleSnapshotProjector().project(
        accountId = "2020214988",
        timeZone = kotlinx.datetime.TimeZone.UTC,
        categories = emptyList(),
        schedules = listOf(state),
        occurrenceAdjustments = emptyList(),
      ),
    ).snapshot.schedules.single()

    val result = reducer.reduce(
      emptyList(),
      listOf(state),
      emptyList(),
      ScheduleCommand.Update(projected),
      nowMillis = 200,
      localRevision = 2,
    )

    assertIs<ScheduleLocalCommandResult.NoOp>(result)
  }

  /** 关闭重复规则会删除已同步单次调整，并直接丢弃从未上传的单次调整。 */
  @Test
  fun disablingRecurrenceCleansChildAdjustments() = runTest {
    val parent = testScheduleResource(version = 3).copy(
      recurrence = AtomicField(
        RecurrenceInput(
          frequency = SyncRecurrenceFrequency.DAILY,
          interval = 1,
          anchorDate = TEST_OCCURRENCE_DATE,
        ),
        10,
      ),
    )
    val remoteChild = testAdjustmentResource(remoteId = 71L, version = 2)
    val localChild = testAdjustmentResource().copy(
      identity = testAdjustmentResource().identity.copy(
        localId = "019d0000-0000-7000-8000-000000000021",
      ),
    )
    val edited = uiSchedule().copy(revision = 3, recurrence = null)

    val result = assertIs<ScheduleLocalCommandResult.Applied>(
      reducer.reduce(
        emptyList(),
        listOf(testScheduleState(parent)),
        listOf(
          testAdjustmentState(remoteChild),
          testAdjustmentState(localChild, PendingUpsert(localChild, 1)),
        ),
        ScheduleCommand.Update(edited),
        nowMillis = 200,
        localRevision = 2,
      ),
    )

    assertEquals(1, result.occurrenceAdjustments.size)
    assertIs<PendingDelete<*, *>>(result.occurrenceAdjustments.single().pending)
  }

  /** 还原单次调整转换为物理删除 pending，而不是写入一组 INHERIT 补丁。 */
  @Test
  fun restoreOccurrenceAdjustmentProducesPhysicalDelete() = runTest {
    val parent = testScheduleResource(version = 3).copy(
      recurrence = AtomicField(
        RecurrenceInput(SyncRecurrenceFrequency.DAILY, 1, TEST_OCCURRENCE_DATE),
        10,
      ),
    )
    val child = testAdjustmentResource(remoteId = 71L, version = 2)
    val command = ScheduleCommand.DeleteOccurrenceAdjustment(
      scheduleId = ScheduleId(TEST_SCHEDULE_ID),
      recurrenceId = RecurrenceId(MinuteTimeDate(2026, 5, 1, 0, 0), "UTC", false),
    )

    val result = assertIs<ScheduleLocalCommandResult.Applied>(
      reducer.reduce(
        emptyList(),
        listOf(testScheduleState(parent)),
        listOf(testAdjustmentState(child)),
        command,
        nowMillis = 200,
        localRevision = 2,
      ),
    )

    assertIs<PendingDelete<*, *>>(result.occurrenceAdjustments.single().pending)
  }

  /** 分类名称在去除首尾空白、忽略大小写后重复时，本地直接拒绝。 */
  @Test
  fun duplicateCategoryNameIsRejected() = runTest {
    val remote = testCategoryResource(remoteId = 41L, version = 1, name = "Project")
    val duplicate = ScheduleCategory(
      id = CategoryId("019d0000-0000-7000-8000-000000000012"),
      revision = 0,
      name = " project ",
      color = null,
      sortOrder = 1,
    )

    val result = reducer.reduce(
      categories = listOf(CategorySyncState(remote.identity, CategoryRemoteSnapshot(remote))),
      schedules = emptyList(),
      occurrenceAdjustments = emptyList(),
      command = ScheduleCommand.CreateCategory(duplicate),
      nowMillis = 100,
      localRevision = 1,
    )

    assertEquals(
      ScheduleLocalCommandRejectionReason.INVALID_STATE,
      assertIs<ScheduleLocalCommandResult.Rejected>(result).reason,
    )
  }

  /** 空白名称无论用于创建还是更新都不能形成本地分类。 */
  @Test
  fun blankCategoryNameIsRejected() = runTest {
    val blank = ScheduleCategory(
      id = CategoryId("019d0000-0000-7000-8000-000000000012"),
      revision = 0,
      name = " \t\n ",
      color = null,
      sortOrder = 0,
    )

    val result = reducer.reduce(
      categories = emptyList(),
      schedules = emptyList(),
      occurrenceAdjustments = emptyList(),
      command = ScheduleCommand.CreateCategory(blank),
      nowMillis = 100,
      localRevision = 1,
    )

    assertEquals(
      ScheduleLocalCommandRejectionReason.INVALID_STATE,
      assertIs<ScheduleLocalCommandResult.Rejected>(result).reason,
    )

    val remote = testCategoryResource(remoteId = 41L, version = 1, name = "原分类")
    val updateResult = reducer.reduce(
      categories = listOf(CategorySyncState(remote.identity, CategoryRemoteSnapshot(remote))),
      schedules = emptyList(),
      occurrenceAdjustments = emptyList(),
      command = ScheduleCommand.UpdateCategory(
        ScheduleCategory(
          id = CategoryId(remote.identity.id),
          revision = 1,
          name = "  ",
          color = null,
          sortOrder = 0,
        ),
      ),
      nowMillis = 100,
      localRevision = 1,
    )
    assertEquals(
      ScheduleLocalCommandRejectionReason.INVALID_STATE,
      assertIs<ScheduleLocalCommandResult.Rejected>(updateResult).reason,
    )
  }

  /** 绕过 UI 创建时也只保存去除首尾空白后的 canonical 名称。 */
  @Test
  fun categoryNameIsTrimmedBeforePendingIsPersisted() = runTest {
    val category = ScheduleCategory(
      id = CategoryId("019d0000-0000-7000-8000-000000000012"),
      revision = 0,
      name = " 计划：学习 ",
      color = null,
      sortOrder = 0,
    )

    val result = assertIs<ScheduleLocalCommandResult.Applied>(
      reducer.reduce(
        categories = emptyList(),
        schedules = emptyList(),
        occurrenceAdjustments = emptyList(),
        command = ScheduleCommand.CreateCategory(category),
        nowMillis = 100,
        localRevision = 1,
      ),
    )

    assertEquals("计划：学习", result.categories.single().effectiveResource()?.name?.data)
  }

  /** 全角与半角标点是用户名称内容，不做近似折叠；只有真实同名才被拒绝。 */
  @Test
  fun categoryNameKeepsPunctuationDifference() = runTest {
    val remote = testCategoryResource(remoteId = 41L, version = 1, name = "计划：学习")
    val distinct = ScheduleCategory(
      id = CategoryId("019d0000-0000-7000-8000-000000000012"),
      revision = 0,
      name = "计划:学习",
      color = null,
      sortOrder = 1,
    )

    val result = reducer.reduce(
      categories = listOf(CategorySyncState(remote.identity, CategoryRemoteSnapshot(remote))),
      schedules = emptyList(),
      occurrenceAdjustments = emptyList(),
      command = ScheduleCommand.CreateCategory(distinct),
      nowMillis = 100,
      localRevision = 1,
    )

    assertIs<ScheduleLocalCommandResult.Applied>(result)
  }

  /** 构造与测试同步资源语义一致的未安排清单。 */
  private fun uiSchedule(): Schedule = Schedule(
    id = ScheduleId(TEST_SCHEDULE_ID),
    revision = 0,
    title = "复习高数",
    description = "",
    categoryId = null,
    timing = ScheduleTiming.Unscheduled,
    recurrence = null,
    reminder = null,
    todoState = ScheduleTodoState.PENDING,
    createdAt = Instant.fromEpochMilliseconds(10),
    updatedAt = Instant.fromEpochMilliseconds(10),
    kind = ScheduleKind.TODO,
    linkedToCourse = false,
  )
}
