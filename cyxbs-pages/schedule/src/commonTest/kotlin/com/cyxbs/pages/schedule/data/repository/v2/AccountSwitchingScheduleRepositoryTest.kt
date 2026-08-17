package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryAccountRequiredException
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryFactory
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 新账号 façade 的最小并发与委托合同测试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitchingScheduleRepositoryTest {
  @Test
  fun loginCreatesAndInitializesCurrentDelegate() = runTest {
    val delegates = mutableMapOf<String, FakeRepository>()
    val repository = createRepository { accountId ->
      FakeRepository(accountId).also { delegates[accountId] = it }
    }
    val sessions = MutableStateFlow(loginSession(1, "A"))

    repository.bindAccounts(sessions)
    assertEquals("A", repository.snapshot.value.accountId)
    assertEquals(ScheduleRepositoryStatus.Loading, repository.snapshot.value.status)
    runCurrent()

    assertEquals(1, delegates.getValue("A").initializeCalls)
    assertTrue(repository.snapshot.value.status is ScheduleRepositoryStatus.Ready)
  }

  @Test
  fun switchingAccountCancelsOldInitializationAndDropsLateSnapshot() = runTest {
    val oldInitialization = CompletableDeferred<Unit>()
    val delegates = mutableMapOf<String, FakeRepository>()
    val repository = createRepository { accountId ->
      FakeRepository(
        accountId = accountId,
        initializationGate = oldInitialization.takeIf { accountId == "A" },
      ).also { delegates[accountId] = it }
    }
    val sessions = MutableStateFlow(loginSession(1, "A"))
    repository.bindAccounts(sessions)
    runCurrent()

    sessions.value = loginSession(2, "B")
    assertEquals("B", repository.snapshot.value.accountId)
    runCurrent()
    delegates.getValue("A").publishReady()

    assertEquals("B", repository.snapshot.value.accountId)
    assertTrue(repository.snapshot.value.status is ScheduleRepositoryStatus.Ready)
    assertTrue(delegates.getValue("A").initializationCancelled)
  }

  @Test
  fun logoutPublishesEmptyReadOnlyStateAndRejectsCommands() = runTest {
    val repository = createRepository(::FakeRepository)
    val sessions = MutableStateFlow(loginSession(1, "A"))
    repository.bindAccounts(sessions)
    runCurrent()

    sessions.value = AccountSession(2, AccountState.Logout(null))

    assertEquals(null, repository.snapshot.value.accountId)
    assertEquals(emptyList(), repository.snapshot.value.schedules)
    assertEquals(ScheduleRepositoryMutationMode.READ_ONLY, repository.mutationMode)
    assertFailsWith<ScheduleRepositoryAccountRequiredException> {
      repository.execute(ScheduleCommand.RequestSync)
    }
  }

  @Test
  fun commandsIncludingRequestSyncAreDelegatedToOneExactAccount() = runTest {
    val delegates = mutableMapOf<String, FakeRepository>()
    val repository = createRepository { accountId ->
      FakeRepository(accountId).also { delegates[accountId] = it }
    }
    val sessions = MutableStateFlow(loginSession(1, "A"))
    repository.bindAccounts(sessions)
    runCurrent()
    val commands = listOf(
      ScheduleCommand.Delete(ScheduleId("018f0f7c-6000-7000-8000-000000000101")),
      ScheduleCommand.RequestSync,
    )

    val results = repository.executeSerially(commands) { true }

    assertEquals(commands, delegates.getValue("A").commands)
    assertEquals(2, results.size)
  }

  @Test
  fun commandStartedBeforeSwitchMayFinishOnlyOnOldDelegate() = runTest {
    val executeGate = CompletableDeferred<Unit>()
    val delegates = mutableMapOf<String, FakeRepository>()
    val repository = createRepository { accountId ->
      FakeRepository(
        accountId = accountId,
        executeGate = executeGate.takeIf { accountId == "A" },
      ).also { delegates[accountId] = it }
    }
    val sessions = MutableStateFlow(loginSession(1, "A"))
    repository.bindAccounts(sessions)
    runCurrent()

    val result = async { repository.execute(ScheduleCommand.RequestSync) }
    runCurrent()
    sessions.value = loginSession(2, "B")
    assertEquals("B", repository.snapshot.value.accountId)
    executeGate.complete(Unit)

    assertEquals(ScheduleSyncResult.Success(), result.await())
    assertEquals(listOf<ScheduleCommand>(ScheduleCommand.RequestSync), delegates.getValue("A").commands)
    assertEquals(emptyList<ScheduleCommand>(), delegates.getValue("B").commands)
  }

  @Test
  fun calendarChangesNeverCrossAccountBoundary() = runTest {
    val delegates = mutableMapOf<String, FakeRepository>()
    val repository = createRepository { accountId ->
      FakeRepository(accountId).also { delegates[accountId] = it }
    }
    val sessions = MutableStateFlow(loginSession(1, "A"))
    repository.bindAccounts(sessions)
    runCurrent()
    val observed = mutableListOf<ScheduleCalendarChange>()
    backgroundScope.launch { repository.calendarChanges.collect(observed::add) }
    runCurrent()

    delegates.getValue("A").emitCalendarChange("018f0f7c-6000-7000-8000-000000000102")
    runCurrent()
    sessions.value = loginSession(2, "B")
    assertEquals("B", repository.snapshot.value.accountId)
    runCurrent()
    delegates.getValue("A").emitCalendarChange("018f0f7c-6000-7000-8000-000000000103")
    delegates.getValue("B").emitCalendarChange("018f0f7c-6000-7000-8000-000000000104")
    runCurrent()

    assertEquals(listOf("A", "B"), observed.map(ScheduleCalendarChange::accountId))
  }

  @Test
  fun initializationFailureDoesNotBlockFutureAccount() = runTest {
    val delegates = mutableMapOf<String, FakeRepository>()
    val repository = createRepository { accountId ->
      FakeRepository(
        accountId = accountId,
        initializationFailure = IllegalStateException("A failed").takeIf { accountId == "A" },
      ).also { delegates[accountId] = it }
    }
    val sessions = MutableStateFlow(loginSession(1, "A"))
    repository.bindAccounts(sessions)
    runCurrent()

    sessions.value = loginSession(2, "B")
    runCurrent()

    assertEquals(1, delegates.getValue("B").initializeCalls)
    assertEquals("B", repository.snapshot.value.accountId)
    assertTrue(repository.snapshot.value.status is ScheduleRepositoryStatus.Ready)
  }

  /** 使用 test scope 的 backgroundScope 持有 façade 的账号监听和 delegate 任务。 */
  private fun TestScope.createRepository(
    factory: (String) -> ScheduleRepository,
  ): AccountSwitchingScheduleRepository = AccountSwitchingScheduleRepository(
    factory = ScheduleRepositoryFactory { session -> factory(requireNotNull(session.accountId)) },
    scope = backgroundScope,
  )

  /** 构造不可与其他 generation 混用的登录会话。 */
  private fun loginSession(generation: Long, accountId: String): AccountSession =
    AccountSession(generation, AccountState.Login(accountId))

  /** 只实现 façade 测试需要的快照、初始化、命令与日历事件边界。 */
  private class FakeRepository(
    private val accountId: String,
    private val initializationGate: CompletableDeferred<Unit>? = null,
    private val initializationFailure: Throwable? = null,
    private val executeGate: CompletableDeferred<Unit>? = null,
  ) : ScheduleRepository {
    private val mutableSnapshot = MutableStateFlow(ScheduleSnapshot(accountId = accountId))
    private val mutableCalendarChanges = MutableSharedFlow<ScheduleCalendarChange>(extraBufferCapacity = 8)
    override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
    override val calendarChanges: Flow<ScheduleCalendarChange> = mutableCalendarChanges
    override val mutationMode: ScheduleRepositoryMutationMode = ScheduleRepositoryMutationMode.LOCAL_FIRST
    var initializeCalls: Int = 0
      private set
    var initializationCancelled: Boolean = false
      private set
    val commands = mutableListOf<ScheduleCommand>()

    /** 可选 gate 用于验证切号会取消旧初始化；正常完成后发布 Ready。 */
    override suspend fun initialize() {
      initializeCalls += 1
      try {
        initializationGate?.await()
      } finally {
        initializationCancelled = initializationGate != null && !initializationGate.isCompleted
      }
      initializationFailure?.let { throw it }
      publishReady()
    }

    /** 记录进入的 delegate 后再等待，用于证明切号不会重定向在途调用。 */
    override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult {
      commands += command
      executeGate?.await()
      return ScheduleSyncResult.Success()
    }

    /** 发布当前账号的可信空快照。 */
    fun publishReady() {
      mutableSnapshot.value = ScheduleSnapshot(
        accountId = accountId,
        status = ScheduleRepositoryStatus.Ready(pendingCount = 0, hasPendingDeletes = false),
      )
    }

    /** 发出指定账号的最小 Schedule 变化事件。 */
    fun emitCalendarChange(scheduleId: String) {
      mutableCalendarChanges.tryEmit(
        ScheduleCalendarChange.SchedulesCommitted(
          accountId = accountId,
          scheduleIds = setOf(ScheduleId(scheduleId)),
        ),
      )
    }
  }
}
