package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.SchedulePendingOperation
import com.cyxbs.pages.schedule.data.remote.ScheduleDeltaResponse
import com.cyxbs.pages.schedule.data.remote.ScheduleListResponse
import com.cyxbs.pages.schedule.data.remote.SyncTimeResponse
import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceOverride
import com.cyxbs.pages.schedule.support.FakeScheduleLocalDataSource
import com.cyxbs.pages.schedule.support.FakeScheduleRemoteDataSource
import com.cyxbs.pages.schedule.support.TEST_STU_NUM
import com.cyxbs.pages.schedule.support.registerFakeAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ScheduleSyncRepository 同步编排集成测试。
 *
 * - account 通过 KtProvider hook 注册假实现（生产代码不动）；
 * - local/remote 用内存假实现注入；
 * - scope 用 runTest 的 backgroundScope，防抖时长设极大 → 防抖任务在虚拟时间内永不触发、
 *   测试结束自动取消，不干扰断言；同步逻辑通过显式 [ScheduleSyncRepository.sync] 验证。
 */
class ScheduleSyncRepositoryTest {

  private lateinit var local: FakeScheduleLocalDataSource
  private lateinit var remote: FakeScheduleRemoteDataSource

  @BeforeTest
  fun setup() {
    // 用独立 scheduler 的测试主调度器：appCoroutineScope(desktop 兜底)走它，
    // 防抖任务被排队但不推进 → 永不触发，不干扰断言；同步逻辑通过显式 sync() 验证。
    Dispatchers.setMain(StandardTestDispatcher())
    registerFakeAccount()
    local = FakeScheduleLocalDataSource()
    remote = FakeScheduleRemoteDataSource()
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun newRepo() = ScheduleSyncRepository(
    localDataSource = local,
    remoteDataSource = remote,
  )

  private fun entity(
    id: Long,
    recurrence: Recurrence? = null,
    startTime: String? = "2026年1月5日 08:00",
    endTime: String = "2026年1月5日 09:00",
  ) = ScheduleEntity(
    todoId = id, title = "t$id", lastModifyTime = 0L,
    recurrence = recurrence, startTime = startTime, endTime = endTime,
  )

  @Test
  fun create_adds_local_and_pending() = runTest {
    val repo = newRepo()
    repo.createSchedule(title = "买菜")
    val all = local.getAll(TEST_STU_NUM)
    assertEquals(1, all.size)
    assertEquals("买菜", all.first().title)
    assertEquals(
      1,
      local.getPendingOperations(TEST_STU_NUM).count { it.kind == SchedulePendingOperation.Kind.UPSERT },
    )
  }

  @Test
  fun complete_single_deletes() = runTest {
    val repo = newRepo()
    repo.createSchedule(title = "x")
    val id = repo.todos.value.first().todoId
    repo.completeSchedule(id)
    assertNull(local.getById(TEST_STU_NUM, id))
  }

  @Test
  fun complete_recurring_adds_exdate() = runTest {
    val repo = newRepo()
    repo.updateSchedule(entity(1, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    repo.completeSchedule(1, occurrenceDate = Date(2026, 1, 12))
    val saved = local.getById(TEST_STU_NUM, 1)!!
    assertTrue(Date(2026, 1, 12) in saved.recurrence!!.exdate)
  }

  @Test
  fun delete_this_occurrence_adds_exdate() = runTest {
    val repo = newRepo()
    repo.updateSchedule(entity(1, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    repo.deleteThisOccurrence(1, Date(2026, 1, 12))
    assertTrue(Date(2026, 1, 12) in local.getById(TEST_STU_NUM, 1)!!.recurrence!!.exdate)
  }

  @Test
  fun edit_this_and_following_truncates_and_creates_new() = runTest {
    val repo = newRepo()
    repo.updateSchedule(entity(1, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    repo.editThisAndFollowing(1, Date(2026, 1, 19), entity(999, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    val orig = local.getById(TEST_STU_NUM, 1)!!
    assertEquals(Date(2026, 1, 18), orig.recurrence!!.rrule!!.until)
    assertEquals(2, local.getAll(TEST_STU_NUM).size) // 原系列 + 新系列
    assertNull(local.getById(TEST_STU_NUM, 999)) // 新系列使用生成 id，而非传入的 999
  }

  @Test
  fun sync_full_rebuild_replaces_local() = runTest {
    val repo = newRepo()
    remote.listResponse = ScheduleListResponse(changedScheduleArray = listOf(entity(7)), syncTime = 100)
    repo.sync()
    assertEquals(listOf(7L), local.getAll(TEST_STU_NUM).map { it.todoId })
  }

  @Test
  fun sync_incremental_merges_changes() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 60, isSyncTimeExist = true)
    remote.deltaResponse = ScheduleDeltaResponse(changedScheduleArray = listOf(entity(8)), syncTime = 60)
    repo.sync()
    assertEquals(listOf(8L), local.getAll(TEST_STU_NUM).map { it.todoId })
  }

  @Test
  fun flush_pending_pushes_upsert() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 50, isSyncTimeExist = true) // 相同 → 不增量拉取
    remote.pushSyncTime = 70
    repo.createSchedule(title = "上传我")
    repo.sync()
    assertTrue(remote.pushed.flatten().any { it.title == "上传我" })
    assertTrue(local.getPendingOperations(TEST_STU_NUM).isEmpty())
  }

  @Test
  fun pin_sets_pinned() = runTest {
    val repo = newRepo()
    repo.createSchedule(title = "x")
    val id = repo.todos.value.first().todoId
    repo.pinSchedule(id, true)
    assertEquals(1, local.getById(TEST_STU_NUM, id)!!.isPinned)
  }

  @Test
  fun delete_and_flush_pending_delete() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 50, isSyncTimeExist = true) // 不增量拉取
    remote.deleteSyncTime = 80
    repo.createSchedule(title = "待删")
    val id = repo.todos.value.first().todoId
    repo.deleteSchedule(id)
    repo.sync()
    assertTrue(remote.deleted.flatten().contains(id))
    assertTrue(local.getPendingOperations(TEST_STU_NUM).isEmpty())
    assertNull(local.getById(TEST_STU_NUM, id))
  }

  @Test
  fun sync_failure_sets_error_state() = runTest {
    val repo = newRepo()
    remote.failNext = RuntimeException("boom") // 全量重建 getAllSchedules 失败
    repo.sync()
    assertTrue(repo.syncState.value is ScheduleSyncState.Error)
  }

  @Test
  fun flush_pending_failure_marks_needs_rebuild() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 50, isSyncTimeExist = true) // 不拉取
    remote.failPush = RuntimeException("push boom")
    repo.createSchedule(title = "x")
    repo.sync()
    assertTrue(repo.syncState.value is ScheduleSyncState.Error)
    assertTrue(local.loadSnapshot(TEST_STU_NUM).meta.needsFullRebuild)
  }

  @Test
  fun incremental_falls_back_to_full_when_sync_time_missing() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 0, isSyncTimeExist = false) // 基线失效
    remote.listResponse = ScheduleListResponse(changedScheduleArray = listOf(entity(9)), syncTime = 70)
    repo.sync()
    assertEquals(listOf(9L), local.getAll(TEST_STU_NUM).map { it.todoId })
  }

  @Test
  fun incremental_merges_deletions() = runTest {
    val repo = newRepo()
    local.replaceAll(TEST_STU_NUM, listOf(entity(1)), syncTime = 50, preservePending = true)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 60, isSyncTimeExist = true)
    remote.deltaResponse = ScheduleDeltaResponse(
      changedScheduleArray = emptyList(),
      delScheduleArray = listOf(1L),
      syncTime = 60,
    )
    repo.sync()
    assertTrue(local.getAll(TEST_STU_NUM).isEmpty())
  }

  @Test
  fun edit_this_occurrence_writes_override() = runTest {
    val repo = newRepo()
    repo.updateSchedule(entity(1, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    repo.editThisOccurrence(1, Date(2026, 1, 12), RecurrenceOverride(recurrenceId = Date(2026, 1, 12), title = "改"))
    val rec = local.getById(TEST_STU_NUM, 1)!!.recurrence!!
    assertEquals(1, rec.overrides.size)
    assertEquals("改", rec.overrides.first().title)
  }
}
