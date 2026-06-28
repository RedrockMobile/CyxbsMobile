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
 * - 用 [Dispatchers.setMain] 安装独立 scheduler 的测试主调度器：仓库防抖走 desktop 兜底的
 *   appCoroutineScope(=Main)，其上任务被排队但不推进 → 永不触发、不干扰断言；
 *   同步逻辑通过显式 [ScheduleSyncRepository.sync] 验证。
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

  // 验证新建：本地写入该 todo，并记录一条 UPSERT 待同步操作
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

  // 验证完成单次（无重复）：直接删除
  @Test
  fun complete_single_deletes() = runTest {
    val repo = newRepo()
    repo.createSchedule(title = "x")
    val id = repo.todos.value.first().todoId
    repo.completeSchedule(id)
    assertNull(local.getById(TEST_STU_NUM, id))
  }

  // 验证完成重复的某一次：把该次加入 EXDATE，系列保留
  @Test
  fun complete_recurring_adds_exdate() = runTest {
    val repo = newRepo()
    repo.updateSchedule(entity(1, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    repo.completeSchedule(1, occurrenceDate = Date(2026, 1, 12))
    val saved = local.getById(TEST_STU_NUM, 1)!!
    assertTrue(Date(2026, 1, 12) in saved.recurrence!!.exdate)
  }

  // 验证删除某一次（三态之"删此次"）：写入 EXDATE
  @Test
  fun delete_this_occurrence_adds_exdate() = runTest {
    val repo = newRepo()
    repo.updateSchedule(entity(1, recurrence = Recurrence(RRule(Freq.WEEKLY))))
    repo.deleteThisOccurrence(1, Date(2026, 1, 12))
    assertTrue(Date(2026, 1, 12) in local.getById(TEST_STU_NUM, 1)!!.recurrence!!.exdate)
  }

  // 验证"此次及后续"：原系列 UNTIL 截断到前一天，并新建一条从该次起的新系列(新 id)
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

  // 验证全量同步：用服务端 /list 结果整体替换本地
  @Test
  fun sync_full_rebuild_replaces_local() = runTest {
    val repo = newRepo()
    remote.listResponse = ScheduleListResponse(changedScheduleArray = listOf(entity(7)), syncTime = 100)
    repo.sync()
    assertEquals(listOf(7L), local.getAll(TEST_STU_NUM).map { it.todoId })
  }

  // 验证增量同步：sync_time 存在且更新时，拉取增量 changed 合并到本地
  @Test
  fun sync_incremental_merges_changes() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 60, isSyncTimeExist = true)
    remote.deltaResponse = ScheduleDeltaResponse(changedScheduleArray = listOf(entity(8)), syncTime = 60)
    repo.sync()
    assertEquals(listOf(8L), local.getAll(TEST_STU_NUM).map { it.todoId })
  }

  // 验证 pending 上传(UPSERT)：sync 时把待同步的新增推送到服务端并清空 pending
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

  // 验证置顶：isPinned 置 1（作为完整 upsert，不走 /pin）
  @Test
  fun pin_sets_pinned() = runTest {
    val repo = newRepo()
    repo.createSchedule(title = "x")
    val id = repo.todos.value.first().todoId
    repo.pinSchedule(id, true)
    assertEquals(1, local.getById(TEST_STU_NUM, id)!!.isPinned)
  }

  // 验证删除链路 + pending DELETE 上传：sync 时调用远端删除、清空 pending、本地无残留
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

  // 验证同步失败：异常被捕获并置为 Error 状态（不崩溃）
  @Test
  fun sync_failure_sets_error_state() = runTest {
    val repo = newRepo()
    remote.failNext = RuntimeException("boom") // 全量重建 getAllSchedules 失败
    repo.sync()
    assertTrue(repo.syncState.value is ScheduleSyncState.Error)
  }

  // 验证 pending 上传失败：标记 needsFullRebuild 以便下次走全量重建，并置 Error
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

  // 验证增量基线失效(sync_time 不存在)：回退到全量重建
  @Test
  fun incremental_falls_back_to_full_when_sync_time_missing() = runTest {
    val repo = newRepo()
    local.seedSyncTime(50)
    remote.syncTimeResponse = SyncTimeResponse(syncTime = 0, isSyncTimeExist = false) // 基线失效
    remote.listResponse = ScheduleListResponse(changedScheduleArray = listOf(entity(9)), syncTime = 70)
    repo.sync()
    assertEquals(listOf(9L), local.getAll(TEST_STU_NUM).map { it.todoId })
  }

  // 验证增量同步合并删除：delArray 中的 id 从本地移除
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

  // 验证仅修改某一次（三态之"仅此次"）：写入对应 recurrenceId 的 override
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
