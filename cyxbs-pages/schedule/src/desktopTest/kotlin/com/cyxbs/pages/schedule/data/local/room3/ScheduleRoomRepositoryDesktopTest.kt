package com.cyxbs.pages.schedule.data.local.room3

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.schedule.data.failure.ScheduleFailureRecord
import com.cyxbs.pages.schedule.data.failure.ScheduleFailureRecordSink
import com.cyxbs.pages.schedule.data.failure.scheduleIds
import com.cyxbs.pages.schedule.data.remote.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.ConfirmedResult
import com.cyxbs.pages.schedule.data.remote.ConfirmedResultCode
import com.cyxbs.pages.schedule.data.remote.DeleteResult
import com.cyxbs.pages.schedule.data.remote.MutationRequest
import com.cyxbs.pages.schedule.data.remote.MutationResourceResponse
import com.cyxbs.pages.schedule.data.remote.MutationResponse
import com.cyxbs.pages.schedule.data.remote.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentSyncResponse
import com.cyxbs.pages.schedule.data.remote.RecurrenceFrequency as WireRecurrenceFrequency
import com.cyxbs.pages.schedule.data.remote.ResultReason
import com.cyxbs.pages.schedule.data.remote.ScheduleCallResult
import com.cyxbs.pages.schedule.data.remote.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.SyncRequest
import com.cyxbs.pages.schedule.data.remote.SyncResponse
import com.cyxbs.pages.schedule.data.remote.TodoState as WireTodoState
import com.cyxbs.pages.schedule.data.remote.UpsertResult
import com.cyxbs.pages.schedule.data.repository.TEST_OCCURRENCE_DATE
import com.cyxbs.pages.schedule.data.repository.TEST_SCHEDULE_ID
import com.cyxbs.pages.schedule.data.repository.testAdjustmentResource
import com.cyxbs.pages.schedule.data.repository.testAdjustmentState
import com.cyxbs.pages.schedule.data.repository.testCategoryResource
import com.cyxbs.pages.schedule.data.repository.testCategoryState
import com.cyxbs.pages.schedule.data.repository.testScheduleResource
import com.cyxbs.pages.schedule.data.repository.testScheduleState
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency as DomainRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind as SyncScheduleKind
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.ui.category.ScheduleCategoryColorPresets
import com.cyxbs.pages.schedule.ui.category.encodeScheduleCategoryColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 真实 Desktop SQLite 上验证 repository 的本地优先与逐资源响应语义。 */
class ScheduleRoomRepositoryDesktopTest {

  /** 初始化发布本地快照并执行一次完整同步。 */
  @Test
  fun initializePublishesReadySnapshotAndSyncsOnce() = runTest {
    withRepository { repository, gateway, _, _ ->
      repository.initialize()

      assertEquals(1, gateway.syncCalls)
      assertEquals(ACCOUNT_ID, repository.snapshot.value.accountId)
      assertIs<ScheduleRepositoryStatus.Ready>(repository.snapshot.value.status)
    }
  }

  /** 首次同步失败只能标记远端不可用；后续显式同步成功必须恢复 Ready，不能写入虚假确认状态。 */
  @Test
  fun failedInitialSyncCanRecoverOnLaterSuccessfulSync() = runTest {
    withRepository { repository, gateway, _, _ ->
      gateway.syncResult = {
        ScheduleCallResult.TransportFailure(null, IllegalStateException("offline"))
      }

      repository.initialize()

      assertIs<ScheduleRepositoryStatus.Unavailable>(repository.snapshot.value.status)
      gateway.syncResult = { request -> completed(emptySyncResponse(request)) }
      val result = repository.execute(ScheduleCommand.RequestSync)

      assertIs<ScheduleSyncResult.Success>(result)
      assertEquals(2, gateway.syncCalls)
      assertIs<ScheduleRepositoryStatus.Ready>(repository.snapshot.value.status)
    }
  }

  /** 日常创建先落本地，成功响应再推进 version 并清除 pending。 */
  @Test
  fun createPersistsCanonicalVersionAndClearsPending() = runTest {
    withRepository { repository, gateway, database, _ ->
      repository.initialize()

      val result = repository.execute(ScheduleCommand.Create(schedule("复习高数")))

      assertIs<ScheduleSyncResult.Success>(result)
      assertEquals(1, gateway.createCalls)
      val state = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).schedules.single()
      assertEquals(1uL, state.remoteSnapshot?.version)
      assertEquals(null, state.pendingOperation)
      assertEquals("复习高数", repository.snapshot.value.schedules.single().title)
    }
  }

  /** 不确定网络失败保留本地可见数据和 pending，等待后续同步。 */
  @Test
  fun transportFailureRetainsLocalScheduleAndPending() = runTest {
    withRepository { repository, gateway, database, _ ->
      repository.initialize()
      gateway.createResult = {
        ScheduleCallResult.TransportFailure(null, IllegalStateException("offline"))
      }

      val result = repository.execute(ScheduleCommand.Create(schedule("离线创建")))

      assertIs<ScheduleSyncResult.Failure>(result)
      assertIs<ScheduleRemoteError.Unexpected>(result.error)
      assertEquals("离线创建", repository.snapshot.value.schedules.single().title)
      assertEquals(
        SchedulePendingOperation.UPSERT,
        ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).schedules.single().pendingOperation,
      )
    }
  }

  /** CREATE 响应丢失后必须以同一 Schedule UUID 重试，成功回包只能确认原记录而不能生成第二条。 */
  @Test
  fun responseLossRetriesSameScheduleIdentityWithoutDuplicate() = runTest {
    withRepository { repository, gateway, database, _ ->
      repository.initialize()
      var firstRequestId: String? = null
      gateway.createResult = { request ->
        firstRequestId = request.schedules.upserts.single().id
        ScheduleCallResult.TransportFailure(null, IllegalStateException("response lost"))
      }

      val initial = repository.execute(ScheduleCommand.Create(schedule("响应丢失后重试")))
      assertIs<ScheduleSyncResult.Failure>(initial)
      gateway.syncResult = { request ->
        assertEquals(firstRequestId, request.schedules.upserts.single().id)
        completed(emptySyncResponse(request))
      }

      val retried = repository.execute(ScheduleCommand.RequestSync)

      assertIs<ScheduleSyncResult.Success>(retried)
      val local = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).schedules.single()
      assertEquals(firstRequestId, local.scheduleId)
      assertEquals(1uL, local.remoteSnapshot?.version)
      assertEquals(null, local.pendingOperation)
      assertEquals(1, repository.snapshot.value.schedules.size)
      assertEquals(ScheduleId(requireNotNull(firstRequestId)), repository.snapshot.value.schedules.single().id)
    }
  }

  /** 新分类、重复、提醒和课表关联必须在一次 CREATE 中建立引用并共同收敛。 */
  @Test
  fun createTodoWithNewCategoryAndAllOptionsUsesOneRequest() = runTest {
    withRepository { repository, gateway, database, _ ->
      repository.initialize()
      gateway.createResult = { request ->
        val response = successResponse(request)
        val input = request.schedules.upserts.single()
        completed(
          response.copy(
            schedules = response.schedules.copy(
              upsertResults = listOf(
                UpsertResult(
                  MutationResultCode.SUCCESS,
                  resource = input.copy(
                    categoryId = input.categoryId.copy(data = 41L),
                    categoryLocalId = null,
                    version = 1uL,
                  ),
                ),
              ),
            ),
          ),
        )
      }
      val category = ScheduleCategory(
        id = CategoryId("01a06750-0000-7000-8000-000000000001"),
        revision = 0,
        name = "一次创建的分类",
        color = null,
        sortOrder = 4,
      )
      val value = schedule("一次创建完整清单").copy(
        categoryId = category.id,
        timing = ScheduleTiming.AllDay(Date(2026, 9, 3)),
        recurrence = RecurrenceRule(DomainRecurrenceFrequency.DAILY),
        reminder = ScheduleReminder(10),
        linkedToCourse = true,
      )

      val result = repository.execute(ScheduleCommand.SaveScheduleWithNewCategory(category, value))

      assertIs<ScheduleSyncResult.Success>(result)
      val request = requireNotNull(gateway.lastCreate)
      assertEquals(1, request.categories.upserts.size)
      assertEquals(1, request.schedules.upserts.size)
      val scheduleInput = request.schedules.upserts.single()
      assertEquals(category.id.value, scheduleInput.categoryLocalId)
      assertEquals(null, scheduleInput.categoryId.data)
      assertEquals(10, scheduleInput.reminder.data?.minutesBefore)
      assertEquals(WireRecurrenceFrequency.DAILY, scheduleInput.recurrence.data?.frequency)
      assertTrue(scheduleInput.linkedToCourse.data)
      val snapshot = repository.snapshot.value
      assertEquals(category.id, snapshot.categories.single().id)
      assertEquals(category.id, snapshot.schedules.single().categoryId)
      val persisted = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID)
      assertTrue(persisted.categories.single().pendingOperation == null)
      assertTrue(persisted.schedules.single().pendingOperation == null)
    }
  }

  /** 连续修改同一分类只覆盖一条 pending 快照，后续同步不得重放中间名称、颜色或顺序。 */
  @Test
  fun repeatedCategoryEditsCollapseIntoLatestPending() = runTest {
    withRepository { repository, gateway, database, _ ->
      val remote = testCategoryResource(remoteId = 41L, version = 2, name = "原分类")
      ScheduleRoomStateStore(database).replaceAccountState(
        accountId = ACCOUNT_ID,
        categories = listOf(testCategoryState(remote).toRoomEntity(ACCOUNT_ID)),
        schedules = emptyList(),
        occurrenceAdjustments = emptyList(),
      )
      repository.initialize()
      gateway.updateResult = {
        ScheduleCallResult.TransportFailure(null, IllegalStateException("offline"))
      }

      val renamed = repository.snapshot.value.categories.single().copy(name = "最终名称")
      repository.execute(ScheduleCommand.UpdateCategory(renamed))
      val recolored = repository.snapshot.value.categories.single().copy(
        color = """{"background":"#FFF1F4FF","content":"#FF405080"}""",
      )
      repository.execute(ScheduleCommand.UpdateCategory(recolored))
      val reordered = repository.snapshot.value.categories.single().copy(sortOrder = 7)
      repository.execute(ScheduleCommand.UpdateCategory(reordered))

      val persisted = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).categories.single()
      assertEquals(SchedulePendingOperation.UPSERT, persisted.pendingOperation)
      assertEquals("最终名称", persisted.pendingSnapshot?.name?.data)
      assertEquals(recolored.color, persisted.pendingSnapshot?.color?.data)
      assertEquals(7, persisted.pendingSnapshot?.sortOrder?.data)
      assertEquals(41L, persisted.pendingSnapshot?.id)
      assertEquals(2uL, persisted.pendingSnapshot?.version)
      assertEquals(3, gateway.updateCalls)
      assertEquals(1, gateway.lastUpdate?.categories?.upserts?.size)
      assertEquals(CategoryId(remote.identity.id), repository.snapshot.value.categories.single().id)
    }
  }

  /** 18 套候选配色必须原样经过 command、wire、canonical 响应和 Room，不得丢失深色字段。 */
  @Test
  fun everyCategoryColorRoundTripsThroughRepositoryAndWire() = runTest {
    withRepository { repository, gateway, database, _ ->
      repository.initialize()
      val wireColors = mutableListOf<String>()
      gateway.createResult = { request ->
        val input = request.categories.upserts.single()
        wireColors += requireNotNull(input.color.data)
        val response = successResponse(request)
        completed(
          response.copy(
            categories = response.categories.copy(
              upsertResults = listOf(
                UpsertResult(
                  MutationResultCode.SUCCESS,
                  resource = input.copy(
                    localId = null,
                    id = 100L + gateway.createCalls,
                    version = 1uL,
                  ),
                ),
              ),
            ),
          ),
        )
      }

      ScheduleCategoryColorPresets.forEachIndexed { index, preset ->
        val result = repository.execute(
          ScheduleCommand.CreateCategory(
            ScheduleCategory(
              id = CategoryId("01a06730-0000-7000-8000-${index.toString().padStart(12, '0')}"),
              revision = 0,
              name = "配色-$index",
              color = preset.value.encodeScheduleCategoryColor(),
              sortOrder = index,
            ),
          ),
        )
        assertIs<ScheduleSyncResult.Success>(result)
      }

      val expected = ScheduleCategoryColorPresets.map { it.value.encodeScheduleCategoryColor() }
      assertEquals(expected, wireColors)
      val persisted = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).categories
        .sortedBy { it.remoteSnapshot?.sortOrder?.data }
      assertEquals(expected, persisted.map { it.remoteSnapshot?.color?.data })
      assertTrue(persisted.all { it.pendingOperation == null })
      assertEquals((101L..118L).toList(), persisted.map { it.remoteSnapshot?.id })
    }
  }

  /** TODO 与 AFFAIR 共用分类时，编辑分类只能改变分类本身，全部日程引用必须保持同一 identity。 */
  @Test
  fun categoryEditKeepsTodoAndAffairReferencesStable() = runTest {
    withRepository { repository, gateway, database, _ ->
      val category = testCategoryResource(remoteId = 41L, version = 2, name = "共同分类")
      val todo = testScheduleResource(
        version = 1,
        categoryLocalId = category.identity.id,
      )
      val affair = todo.copy(
        identity = ScheduleIdentity("019d0000-0000-7000-8000-000000000002"),
        kind = SyncScheduleKind.AFFAIR,
        timing = AtomicField(
          TimingInput(
            kind = TimingKind.TIMED,
            startAt = TEST_OCCURRENCE_DATE + 10 * 60 * 60 * 1_000,
            endAt = TEST_OCCURRENCE_DATE + 11 * 60 * 60 * 1_000,
          ),
          todo.timing.modifiedAt,
        ),
        todoState = AtomicField(null, todo.todoState.modifiedAt),
        linkedToCourse = AtomicField(true, todo.linkedToCourse.modifiedAt),
      )
      ScheduleRoomStateStore(database).replaceAccountState(
        accountId = ACCOUNT_ID,
        categories = listOf(testCategoryState(category).toRoomEntity(ACCOUNT_ID)),
        schedules = listOf(
          testScheduleState(todo).toRoomEntity(ACCOUNT_ID) { localId ->
            category.takeIf { it.identity.id == localId }
          },
          testScheduleState(affair).toRoomEntity(ACCOUNT_ID) { localId ->
            category.takeIf { it.identity.id == localId }
          },
        ),
        occurrenceAdjustments = emptyList(),
      )
      repository.initialize()

      val edited = repository.snapshot.value.categories.single().copy(
        name = "更新后的共同分类",
        color = """{"background":"#FFFFE38A","content":"#FF5B4800","darkBackground":"#BF584C10"}""",
        sortOrder = 9,
      )
      val result = repository.execute(ScheduleCommand.UpdateCategory(edited))

      assertIs<ScheduleSyncResult.Success>(result)
      assertEquals(1, gateway.updateCalls)
      assertTrue(gateway.lastUpdate?.schedules?.upserts?.isEmpty() == true)
      val snapshot = repository.snapshot.value
      assertEquals(CategoryId(category.identity.id), snapshot.categories.single().id)
      assertEquals("更新后的共同分类", snapshot.categories.single().name)
      assertEquals(
        setOf(ScheduleKind.TODO, ScheduleKind.AFFAIR),
        snapshot.schedules.map { it.kind }.toSet(),
      )
      assertTrue(snapshot.schedules.all { it.categoryId == CategoryId(category.identity.id) })
      val persisted = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID)
      assertEquals(41L, persisted.categories.single().remoteSnapshot?.id)
      assertTrue(
        persisted.schedules.all {
          it.remoteSnapshot?.categoryId?.data == 41L && it.pendingOperation == null
        },
      )
    }
  }

  /** 事务关联清单后必须保存完成态并发起 UPDATE，不能被旧的 Room 约束拦在网络请求之前。 */
  @Test
  fun linkingAffairToTodoPersistsAndUpdatesRemote() = runTest {
    withRepository { repository, gateway, database, _ ->
      val affair = testScheduleResource(version = 2).copy(
        kind = SyncScheduleKind.AFFAIR,
        timing = AtomicField(
          TimingInput(
            kind = TimingKind.TIMED,
            startAt = TEST_OCCURRENCE_DATE + 10 * 60 * 60 * 1_000,
            endAt = TEST_OCCURRENCE_DATE + 11 * 60 * 60 * 1_000,
          ),
          10,
        ),
        todoState = AtomicField(null, 11),
        linkedToCourse = AtomicField(true, 12),
      )
      ScheduleRoomStateStore(database).replaceAccountState(
        accountId = ACCOUNT_ID,
        categories = emptyList(),
        schedules = listOf(testScheduleState(affair).toRoomEntity(ACCOUNT_ID) { null }),
        occurrenceAdjustments = emptyList(),
      )
      repository.initialize()

      val linked = repository.snapshot.value.schedules.single().copy(
        todoState = ScheduleTodoState.PENDING,
      )
      val result = repository.execute(ScheduleCommand.Update(linked))

      assertIs<ScheduleSyncResult.Success>(result)
      assertEquals(1, gateway.updateCalls)
      assertEquals(WireTodoState.OPEN, gateway.lastUpdate?.schedules?.upserts?.single()?.todoState?.data)
      assertEquals(ScheduleTodoState.PENDING, repository.snapshot.value.schedules.single().todoState)
      val persisted = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).schedules.single()
      assertEquals(WireTodoState.OPEN, persisted.remoteSnapshot?.todoState?.data)
      assertEquals(null, persisted.pendingOperation)
    }
  }

  /** 单条业务拒绝不回滚本地日程，并记录可修复源数据。 */
  @Test
  fun rejectedCreateRetainsPendingAndRecordsFailure() = runTest {
    withRepository { repository, gateway, database, failures ->
      repository.initialize()
      gateway.createResult = { request -> rejectedResponse(request) }

      repository.execute(ScheduleCommand.Create(schedule("服务端拒绝")))

      assertEquals(
        SchedulePendingOperation.UPSERT,
        ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).schedules.single().pendingOperation,
      )
      assertTrue(failures.observe(ACCOUNT_ID).value.isNotEmpty())
    }
  }

  /** 被拒绝的新建在用户修正后再次提交成功，必须清除同一日程的旧失败记录。 */
  @Test
  fun successfulRetryRemovesRejectedCreateFailure() = runTest {
    withRepository { repository, gateway, _, failures ->
      repository.initialize()
      gateway.createResult = { request -> rejectedResponse(request) }
      repository.execute(ScheduleCommand.Create(schedule("待修正标题")))
      assertEquals(1, failures.observe(ACCOUNT_ID).value.size)

      gateway.createResult = { request -> completed(successResponse(request)) }
      val edited = repository.snapshot.value.schedules.single().copy(title = "已修正标题")
      val result = repository.execute(ScheduleCommand.Update(edited))

      assertIs<ScheduleSyncResult.Success>(result)
      assertTrue(failures.observe(ACCOUNT_ID).value.isEmpty())
      assertEquals("已修正标题", repository.snapshot.value.schedules.single().title)
    }
  }

  /** 被拒绝的新建在本地删除后必须清除失败记录，并用幂等 DELETE 收敛不确定的远端状态。 */
  @Test
  fun deletingRejectedLocalCreateRemovesFailureAndConvergesRemoteState() = runTest {
    withRepository { repository, gateway, _, failures ->
      repository.initialize()
      gateway.createResult = { request -> rejectedResponse(request) }
      repository.execute(ScheduleCommand.Create(schedule("放弃的本地日程")))
      assertEquals(1, failures.observe(ACCOUNT_ID).value.size)

      val result = repository.execute(ScheduleCommand.Delete(ScheduleId(TEST_SCHEDULE_ID)))

      assertIs<ScheduleSyncResult.Success>(result)
      assertTrue(repository.snapshot.value.schedules.isEmpty())
      assertTrue(failures.observe(ACCOUNT_ID).value.isEmpty())
      assertEquals(
        listOf(TEST_SCHEDULE_ID),
        gateway.lastDelete?.schedules?.deletes?.map { it.id },
      )
    }
  }

  /** 已确认远端日程即使先更新失败，后续幂等删除成功也必须清除 pending 与旧失败记录。 */
  @Test
  fun deletingConfirmedScheduleAfterRejectedUpdateClearsLocalFailureState() = runTest {
    withRepository { repository, gateway, database, failures ->
      val remote = testScheduleResource(version = 2, title = "远端日程")
      ScheduleRoomStateStore(database).replaceAccountState(
        accountId = ACCOUNT_ID,
        categories = emptyList(),
        schedules = listOf(testScheduleState(remote).toRoomEntity(ACCOUNT_ID) { null }),
        occurrenceAdjustments = emptyList(),
      )
      repository.initialize()
      gateway.updateResult = { request -> rejectedResponse(request) }
      repository.execute(
        ScheduleCommand.Update(repository.snapshot.value.schedules.single().copy(title = "被拒绝的修改")),
      )
      assertEquals(1, failures.observe(ACCOUNT_ID).value.size)

      val result = repository.execute(ScheduleCommand.Delete(ScheduleId(TEST_SCHEDULE_ID)))

      assertIs<ScheduleSyncResult.Success>(result)
      assertTrue(repository.snapshot.value.schedules.isEmpty())
      assertTrue(failures.observe(ACCOUNT_ID).value.isEmpty())
      assertTrue(ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).schedules.isEmpty())
    }
  }

  /** 删除父日程时，已同步单次调整在同一请求中物理删除。 */
  @Test
  fun deletingScheduleAlsoDeletesRemoteAdjustment() = runTest {
    withRepository { repository, gateway, database, _ ->
      val schedule = testScheduleResource(version = 2)
      val adjustment = testAdjustmentResource(remoteId = 71L, version = 2)
      ScheduleRoomStateStore(database).replaceAccountState(
        accountId = ACCOUNT_ID,
        categories = emptyList(),
        schedules = listOf(testScheduleState(schedule).toRoomEntity(ACCOUNT_ID) { null }),
        occurrenceAdjustments = listOf(
          testAdjustmentState(adjustment).toRoomEntity(ACCOUNT_ID) { null },
        ),
      )
      repository.initialize()

      val result = repository.execute(ScheduleCommand.Delete(ScheduleId(TEST_SCHEDULE_ID)))

      assertIs<ScheduleSyncResult.Success>(result)
      assertEquals(listOf(TEST_SCHEDULE_ID), gateway.lastDelete?.schedules?.deletes?.map { it.id })
      assertEquals(listOf(71L), gateway.lastDelete?.occurrenceAdjustments?.deletes?.map { it.id })
      val local = ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID)
      assertTrue(local.schedules.isEmpty())
      assertTrue(local.occurrenceAdjustments.isEmpty())
    }
  }

  /** 关闭重复会物理删除全部单次调整；同一日程重新开启相同规则也不能复活旧调整。 */
  @Test
  fun disablingAndReenablingRecurrenceDoesNotRestoreDeletedAdjustments() = runTest {
    withRepository { repository, _, database, _ ->
      val recurringSchedule = testScheduleResource(
        version = 2,
        timing = TimingInput(TimingKind.ALL_DAY, date = TEST_OCCURRENCE_DATE),
      ).copy(
        recurrence = AtomicField(
          RecurrenceInput(RecurrenceFrequency.DAILY, 1, TEST_OCCURRENCE_DATE),
          10,
        ),
      )
      val adjustment = testAdjustmentResource(remoteId = 71L, version = 2)
      ScheduleRoomStateStore(database).replaceAccountState(
        accountId = ACCOUNT_ID,
        categories = emptyList(),
        schedules = listOf(testScheduleState(recurringSchedule).toRoomEntity(ACCOUNT_ID) { null }),
        occurrenceAdjustments = listOf(
          testAdjustmentState(adjustment).toRoomEntity(ACCOUNT_ID) { null },
        ),
      )
      repository.initialize()
      val recurrence = requireNotNull(repository.snapshot.value.schedules.single().recurrence)

      val disabled = repository.execute(
        ScheduleCommand.Update(repository.snapshot.value.schedules.single().copy(recurrence = null)),
      )

      assertIs<ScheduleSyncResult.Success>(disabled)
      assertTrue(ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).occurrenceAdjustments.isEmpty())

      val reenabled = repository.execute(
        ScheduleCommand.Update(repository.snapshot.value.schedules.single().copy(recurrence = recurrence)),
      )

      assertIs<ScheduleSyncResult.Success>(reenabled)
      assertTrue(repository.snapshot.value.occurrenceAdjustments.isEmpty())
      assertTrue(ScheduleRoomStateStore(database).readAccountState(ACCOUNT_ID).occurrenceAdjustments.isEmpty())
    }
  }

  /** 为每个 case 创建隔离数据库和无外部 Settings 的 repository。 */
  private suspend fun withRepository(
    block: suspend (
      RoomScheduleRepository,
      FakeGateway,
      ScheduleRoomDatabase,
      InMemoryFailureRecords,
    ) -> Unit,
  ) {
    val path = Files.createTempFile("schedule-repository-", ".db")
    Files.deleteIfExists(path)
    val database = buildScheduleRoomDatabase(path.toString())
    val gateway = FakeGateway()
    val failures = InMemoryFailureRecords()
    val repository = RoomScheduleRepositoryFactory(
      database = database,
      gatewayFactory = { gateway },
      nowMillis = { 100L },
      failureRecords = failures,
    ).create(AccountSession(
      1,
      AccountState.Login(ACCOUNT_ID),
      CoroutineScope(SupervisorJob()),
      tokenState = null,
    )) as RoomScheduleRepository
    try {
      block(repository, gateway, database, failures)
    } finally {
      database.closeScheduleRoomDatabase()
      Files.deleteIfExists(path)
      Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
      Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
    }
  }

  /** 记录调用并默认返回与请求位置严格对齐的成功响应。 */
  private class FakeGateway : ScheduleRepositoryGateway {
    var syncCalls = 0
    var createCalls = 0
    var updateCalls = 0
    var lastCreate: MutationRequest? = null
    var lastUpdate: MutationRequest? = null
    var lastDelete: MutationRequest? = null
    var syncResult: suspend (SyncRequest) -> ScheduleCallResult<SyncResponse> = { request ->
      completed(emptySyncResponse(request))
    }
    var createResult: suspend (MutationRequest) -> ScheduleCallResult<MutationResponse> = { request ->
      completed(successResponse(request))
    }
    var updateResult: suspend (MutationRequest) -> ScheduleCallResult<MutationResponse> = { request ->
      completed(successResponse(request))
    }

    override suspend fun sync(accountId: String, request: SyncRequest): ScheduleCallResult<SyncResponse> {
      syncCalls += 1
      return syncResult(request)
    }

    override suspend fun createSchedule(
      accountId: String,
      input: MutationRequest,
    ): ScheduleCallResult<MutationResponse> {
      createCalls += 1
      lastCreate = input
      return createResult(input)
    }

    override suspend fun updateSchedule(
      accountId: String,
      input: MutationRequest,
    ): ScheduleCallResult<MutationResponse> {
      updateCalls += 1
      lastUpdate = input
      return updateResult(input)
    }

    override suspend fun deleteSchedule(
      accountId: String,
      input: MutationRequest,
    ): ScheduleCallResult<MutationResponse> {
      lastDelete = input
      return completed(successResponse(input))
    }
  }

  /** 内存失败记录替身只验证 repository 的新增、刷新与删除时机。 */
  private class InMemoryFailureRecords : ScheduleFailureRecordSink {
    private val records = mutableMapOf<String, MutableStateFlow<List<ScheduleFailureRecord>>>()

    override fun observe(accountId: String): StateFlow<List<ScheduleFailureRecord>> =
      records.getOrPut(accountId) { MutableStateFlow(emptyList()) }

    override fun record(accountId: String, records: List<ScheduleFailureRecord>) {
      this.records.getOrPut(accountId) { MutableStateFlow(emptyList()) }.value = records
    }

    override fun refreshSources(
      accountId: String,
      request: MutationRequest,
      adjustmentDeleteScheduleIds: Map<Long, String>,
    ) {
      val affected = request.scheduleIds() + adjustmentDeleteScheduleIds.values
      val source = request.schedules.upserts.associateBy { it.id }
      val state = records.getOrPut(accountId) { MutableStateFlow(emptyList()) }
      state.value = state.value.map { record ->
        if (record.scheduleId !in affected) record
        else record.copy(
          sourceSchedule = source[record.scheduleId] ?: record.sourceSchedule,
          sourceRequest = request,
        )
      }
    }

    override fun remove(accountId: String, scheduleIds: Set<String>) {
      val state = records.getOrPut(accountId) { MutableStateFlow(emptyList()) }
      state.value = state.value.filterNot { it.scheduleId in scheduleIds }
    }

    override fun clear(accountId: String) {
      records.getOrPut(accountId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }
  }

  /** 构造一个不依赖分类、可直接创建的清单日程。 */
  private fun schedule(title: String) = Schedule(
    id = ScheduleId(TEST_SCHEDULE_ID),
    revision = 0,
    title = title,
    description = "",
    categoryId = null,
    timing = ScheduleTiming.Unscheduled,
    recurrence = null,
    reminder = null,
    todoState = ScheduleTodoState.PENDING,
    createdAt = Instant.fromEpochMilliseconds(10),
    updatedAt = Instant.fromEpochMilliseconds(10),
    kind = ScheduleKind.TODO,
  )

  internal companion object {
    const val ACCOUNT_ID = "repository-account"

    /** 将成功的 wire data 包回统一网络外壳。 */
    fun <T> completed(data: T): ScheduleCallResult<T> =
      ScheduleCallResult.Completed(ApiWrapper(data, 10000, "ok"))

    /** 构造与完整 inventory 对齐且不发现额外资源的同步响应。 */
    fun emptySyncResponse(request: SyncRequest) = SyncResponse(
      categories = CategorySyncResponse(
        request.categories.confirmed.map { ConfirmedResult(it.id, ConfirmedResultCode.CONFIRMED) },
        emptyList(),
        request.categories.upserts.map { upsert ->
          UpsertResult(
            MutationResultCode.SUCCESS,
            resource = upsert.copy(localId = null, id = 41L, version = 1uL),
          )
        },
        request.categories.deletes.map { DeleteResult(it.id, MutationResultCode.SUCCESS) },
      ),
      schedules = ScheduleSyncResponse(
        request.schedules.confirmed.map { ConfirmedResult(it.id, ConfirmedResultCode.CONFIRMED) },
        emptyList(),
        request.schedules.upserts.map { upsert ->
          UpsertResult(MutationResultCode.SUCCESS, resource = upsert.copy(version = 1uL))
        },
        request.schedules.deletes.map { DeleteResult(it.id, MutationResultCode.SUCCESS) },
      ),
      occurrenceAdjustments = OccurrenceAdjustmentSyncResponse(
        request.occurrenceAdjustments.confirmed.map {
          ConfirmedResult(it.id, ConfirmedResultCode.CONFIRMED)
        },
        emptyList(),
        request.occurrenceAdjustments.upserts.map { upsert ->
          UpsertResult(
            MutationResultCode.SUCCESS,
            resource = upsert.copy(localId = null, id = 71L, version = 1uL),
          )
        },
        request.occurrenceAdjustments.deletes.map {
          DeleteResult(it.id, MutationResultCode.SUCCESS)
        },
      ),
    )

    /** 构造日常新增、修改、删除共用的逐资源成功响应。 */
    fun successResponse(request: MutationRequest) = MutationResponse(
      categories = MutationResourceResponse(
        request.categories.upserts.map { upsert ->
          UpsertResult(
            MutationResultCode.SUCCESS,
            resource = upsert.copy(localId = null, id = upsert.id ?: 41L, version = upsert.version + 1uL),
          )
        },
        request.categories.deletes.map { DeleteResult(it.id, MutationResultCode.SUCCESS) },
      ),
      schedules = MutationResourceResponse(
        request.schedules.upserts.map { upsert ->
          UpsertResult(
            MutationResultCode.SUCCESS,
            resource = upsert.copy(version = upsert.version + 1uL),
          )
        },
        request.schedules.deletes.map { DeleteResult(it.id, MutationResultCode.SUCCESS) },
      ),
      occurrenceAdjustments = MutationResourceResponse(
        request.occurrenceAdjustments.upserts.map { upsert ->
          UpsertResult(
            MutationResultCode.SUCCESS,
            resource = upsert.copy(
              localId = null,
              id = upsert.id ?: 71L,
              version = upsert.version + 1uL,
            ),
          )
        },
        request.occurrenceAdjustments.deletes.map {
          DeleteResult(it.id, MutationResultCode.SUCCESS)
        },
      ),
    )

    /** 构造所有 upsert 被业务拒绝的日常响应。 */
    fun rejectedResponse(request: MutationRequest): ScheduleCallResult<MutationResponse> = completed(
      MutationResponse(
        categories = MutationResourceResponse(
          request.categories.upserts.map {
            UpsertResult(MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST, "分类无效")
          },
          request.categories.deletes.map {
            DeleteResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
          },
        ),
        schedules = MutationResourceResponse(
          request.schedules.upserts.map {
            UpsertResult(MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST, "标题不能为空")
          },
          request.schedules.deletes.map {
            DeleteResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
          },
        ),
        occurrenceAdjustments = MutationResourceResponse(
          request.occurrenceAdjustments.upserts.map {
            UpsertResult(MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST, "单次调整无效")
          },
          request.occurrenceAdjustments.deletes.map {
            DeleteResult(it.id, MutationResultCode.REJECTED, ResultReason.INVALID_REQUEST)
          },
        ),
      ),
    )
  }
}
