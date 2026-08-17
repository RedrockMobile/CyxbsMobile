package com.cyxbs.pages.schedule.ui.main

import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** 主页面 local-first/read-only 编辑门禁的 ViewModel 与 UI 一致性测试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleMainMutationGateTest {
  private val dispatcher = StandardTestDispatcher()

  /** 固定 ViewModel Main dispatcher，使异步命令可被确定性观察。 */
  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  /** 恢复全局 Main dispatcher，避免影响其他 commonTest。 */
  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** local-first 在任意同步健康状态下都保留离线编辑能力。 */
  @Test
  fun `local first remains writable regardless of sync status`() {
    val statuses = listOf(
      ScheduleRepositoryStatus.Loading,
      ScheduleRepositoryStatus.Ready(0, hasPendingDeletes = true),
      ScheduleRepositoryStatus.Recovered(1, IllegalStateException("recovering")),
      ScheduleRepositoryStatus.Unavailable(1, ScheduleRemoteError.Timeout),
      ScheduleRepositoryStatus.Corrupted(IllegalStateException("corrupted")),
    )

    statuses.forEach { status ->
      val viewModel = ScheduleMainViewModel(FakeRepository(ScheduleRepositoryMutationMode.LOCAL_FIRST, status))
      assertTrue(viewModel.canSubmitMutation())
      assertTrue(isScheduleMainEditorEnabled(ScheduleRepositoryMutationMode.LOCAL_FIRST))
    }
  }

  /** read-only 在任意同步健康状态下都不能开放编辑入口。 */
  @Test
  fun `read only remains closed regardless of sync status`() {
    val statuses = listOf(
      ScheduleRepositoryStatus.Loading,
      ScheduleRepositoryStatus.Ready(0, hasPendingDeletes = false),
      ScheduleRepositoryStatus.Unavailable(0, ScheduleRemoteError.BackendNotDeployed),
    )

    statuses.forEach { status ->
      val viewModel = ScheduleMainViewModel(FakeRepository(ScheduleRepositoryMutationMode.READ_ONLY, status))
      assertFalse(viewModel.canSubmitMutation())
      assertFalse(isScheduleMainEditorEnabled(ScheduleRepositoryMutationMode.READ_ONLY))
    }
  }

  /** RequestSync 是独立对账命令，即使编辑门禁为 read-only 也应交给仓库做账号判定。 */
  @Test
  fun `request sync bypasses edit gate`() = runTest(dispatcher) {
    val repository = FakeRepository(
      mutationMode = ScheduleRepositoryMutationMode.READ_ONLY,
      status = ScheduleRepositoryStatus.Loading,
    )
    val viewModel = ScheduleMainViewModel(repository)

    viewModel.sync()
    advanceUntilIdle()

    assertEquals(listOf<ScheduleCommand>(ScheduleCommand.RequestSync), repository.commands)
  }

  /** local-first 在首条删除后同步失败仍继续提交整批本地命令。 */
  @Test
  fun `batch delete keeps local first writes after sync failure`() = runTest(dispatcher) {
    val repository = BatchDeleteRepository { it.publishUnavailable() }
    val viewModel = ScheduleMainViewModel(repository)
    val ids = listOf(
      ScheduleId("018f3ec1-0000-7000-8000-000000000101"),
      ScheduleId("018f3ec1-0000-7000-8000-000000000102"),
    )
    viewModel.enterManageMode()
    viewModel.selectAll(ids)

    viewModel.batchDelete()
    advanceUntilIdle()

    assertEquals(ids.map<ScheduleId, ScheduleCommand>(ScheduleCommand::Delete), repository.commands)
    assertFalse(viewModel.isManageMode.value)
    assertTrue(viewModel.selectedIds.value.isEmpty())
  }

  /** read-only 批量操作不执行命令，但仍收起管理态并清空选择。 */
  @Test
  fun `read only batch delete performs no command`() = runTest(dispatcher) {
    val repository = BatchDeleteRepository(mutationMode = ScheduleRepositoryMutationMode.READ_ONLY) {}
    val viewModel = ScheduleMainViewModel(repository)
    viewModel.enterManageMode()
    viewModel.selectAll(listOf(ScheduleId("018f3ec1-0000-7000-8000-000000000103")))

    viewModel.batchDelete()
    advanceUntilIdle()

    assertTrue(repository.commands.isEmpty())
    assertFalse(viewModel.isManageMode.value)
    assertTrue(viewModel.selectedIds.value.isEmpty())
  }

  /** 记录命令的最小仓库替身，用于验证同步命令不经过编辑门禁。 */
  private class FakeRepository(
    override val mutationMode: ScheduleRepositoryMutationMode,
    status: ScheduleRepositoryStatus,
  ) : ScheduleRepository {
    override val snapshot: StateFlow<ScheduleSnapshot> = MutableStateFlow(ScheduleSnapshot(status = status))
    val commands = mutableListOf<ScheduleCommand>()

    override suspend fun initialize() = Unit

    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult {
      commands += command
      return ScheduleSyncResult.Success()
    }
  }

  /** 可在每条删除后改变同步状态的仓库替身，验证门禁只读取 mutation mode。 */
  private class BatchDeleteRepository(
    override val mutationMode: ScheduleRepositoryMutationMode = ScheduleRepositoryMutationMode.LOCAL_FIRST,
    private val afterDelete: (BatchDeleteRepository) -> Unit,
  ) : ScheduleRepository {
    private val mutableSnapshot = MutableStateFlow(
      ScheduleSnapshot(status = ScheduleRepositoryStatus.Ready(0, hasPendingDeletes = false)),
    )
    override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
    val commands = mutableListOf<ScheduleCommand>()

    /** 模拟远端失败；local-first 编辑能力不得因此改变。 */
    fun publishUnavailable() {
      mutableSnapshot.value = ScheduleSnapshot(
        status = ScheduleRepositoryStatus.Unavailable(0, ScheduleRemoteError.Timeout),
      )
    }

    override suspend fun initialize() = Unit

    /** 记录本地删除并执行测试注入的同步状态变化。 */
    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult {
      check(command is ScheduleCommand.Delete) { "Unexpected batch command: $command" }
      commands += command
      afterDelete(this)
      return ScheduleSyncResult.Success()
    }
  }
}
