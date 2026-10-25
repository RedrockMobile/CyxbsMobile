package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.ConfirmedResult
import com.cyxbs.pages.schedule.data.remote.ConfirmedResultCode
import com.cyxbs.pages.schedule.data.remote.DeleteResult
import com.cyxbs.pages.schedule.data.remote.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentSyncResponse
import com.cyxbs.pages.schedule.data.remote.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.SyncResponse
import com.cyxbs.pages.schedule.data.remote.UpsertResult
import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeInput
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeKind
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.domain.sync.TodoState
import com.cyxbs.pages.schedule.domain.sync.Weekday
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 最终逐资源同步合同的 planner/applier 回归测试。 */
class SchedulePlannerApplierTest {
  private val planner = ScheduleRequestPlanner()
  private val applier = ScheduleResponseApplier()

  /** 同步请求同时携带远端 inventory 和本地 pending，但不携带 requestId。 */
  @Test
  fun captureContainsConfirmedAndPendingResources() {
    val remote = testCategoryResource(remoteId = 41L, version = 3)
    val state = CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = CategoryRemoteSnapshot(remote),
      pending = PendingUpsert(remote.copy(name = AtomicField("本地修改", 20)), 4),
    )

    val capture = planner.capture(listOf(state), emptyList(), emptyList())

    assertEquals(listOf(41L), capture.request.categories.confirmed.map { it.id })
    assertEquals(listOf(41L), capture.request.categories.upserts.map { it.id })
    assertEquals(listOf(3uL), capture.request.categories.upserts.map { it.version })
  }

  /** 同请求新建分类和日程时，通过瞬时 categoryLocalId 建立引用。 */
  @Test
  fun newScheduleReferencesNewCategoryByTransientLocalId() {
    val category = testCategoryResource()
    val schedule = testScheduleResource(categoryLocalId = category.identity.id)
    val capture = planner.captureMutation(
      categories = listOf(testCategoryState(category, PendingUpsert(category, 1))),
      schedules = listOf(testScheduleState(schedule, PendingUpsert(schedule, 1), hasRemote = false)),
      occurrenceAdjustments = emptyList(),
    )

    assertEquals(category.identity.id, capture.request.categories.upserts.single().localId)
    assertEquals(category.identity.id, capture.request.schedules.upserts.single().categoryLocalId)
    assertNull(capture.request.schedules.upserts.single().categoryId.data)
  }

  /** 同请求创建分类和日程成功后，服务端数字分类 ID 必须重新映射回原本的客户端 UUID。 */
  @Test
  fun acceptedNewCategoryAndScheduleKeepLocalCategoryReference() = runTest {
    val category = testCategoryResource()
    val schedule = testScheduleResource(categoryLocalId = category.identity.id)
    val categoryState = testCategoryState(category, PendingUpsert(category, 1))
    val scheduleState = testScheduleState(schedule, PendingUpsert(schedule, 1), hasRemote = false)
    val capture = planner.capture(listOf(categoryState), listOf(scheduleState), emptyList())
    val canonicalCategory = capture.request.categories.upserts.single().copy(
      localId = null,
      id = 41L,
      version = 1uL,
    )
    val canonicalSchedule = capture.request.schedules.upserts.single().copy(
      version = 1uL,
      categoryId = capture.request.schedules.upserts.single().categoryId.copy(data = 41L),
      categoryLocalId = null,
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        response(
          capture,
          categoryUpserts = listOf(UpsertResult(MutationResultCode.SUCCESS, resource = canonicalCategory)),
          scheduleUpserts = listOf(UpsertResult(MutationResultCode.SUCCESS, resource = canonicalSchedule)),
        ),
        listOf(categoryState),
        listOf(scheduleState),
        emptyList(),
      ),
    )

    val appliedCategory = result.categories.single()
    val appliedSchedule = result.schedules.single()
    assertEquals(category.identity, appliedCategory.identity)
    assertEquals(41L, appliedCategory.remoteSnapshot?.resource?.remoteId)
    assertNull(appliedCategory.pending)
    assertEquals(category.identity.id, appliedSchedule.remoteSnapshot?.resource?.categoryId?.data)
    assertNull(appliedSchedule.pending)
  }

  /** 绕过端上判重的两个分类若被服务端合并为同一 ID，本地只保留一个分类并重写全部日程引用。 */
  @Test
  fun duplicateLocalCategoriesConvergeToOneCanonicalCategory() = runTest {
    val firstCategory = testCategoryResource(name = "Project")
    val secondCategory = firstCategory.copy(
      identity = CategoryIdentity("019d0000-0000-7000-8000-000000000011"),
      name = AtomicField("project", 11),
    )
    val firstSchedule = testScheduleResource(categoryLocalId = firstCategory.identity.id)
    val secondSchedule = testScheduleResource(categoryLocalId = secondCategory.identity.id).copy(
      identity = ScheduleIdentity("019d0000-0000-7000-8000-000000000002"),
    )
    val categoryStates = listOf(
      testCategoryState(firstCategory, PendingUpsert(firstCategory, 1)),
      testCategoryState(secondCategory, PendingUpsert(secondCategory, 1)),
    )
    val scheduleStates = listOf(
      testScheduleState(firstSchedule, PendingUpsert(firstSchedule, 1), hasRemote = false),
      testScheduleState(secondSchedule, PendingUpsert(secondSchedule, 1), hasRemote = false),
    )
    val capture = planner.capture(categoryStates, scheduleStates, emptyList())
    val categoryResults = capture.request.categories.upserts.mapIndexed { index, input ->
      UpsertResult(
        MutationResultCode.SUCCESS,
        resource = input.copy(localId = null, id = 41L, version = (index + 1).toULong()),
      )
    }
    val scheduleResults = capture.request.schedules.upserts.map { input ->
      UpsertResult(
        MutationResultCode.SUCCESS,
        resource = input.copy(
          version = 1uL,
          categoryId = input.categoryId.copy(data = 41L),
          categoryLocalId = null,
        ),
      )
    }

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        response(capture, categoryUpserts = categoryResults, scheduleUpserts = scheduleResults),
        categoryStates,
        scheduleStates,
        emptyList(),
      ),
    )

    val canonicalCategory = result.categories.single()
    assertEquals(41L, canonicalCategory.remoteSnapshot?.resource?.remoteId)
    assertNull(canonicalCategory.pending)
    assertEquals(
      setOf(canonicalCategory.identity.id),
      result.schedules.map { it.effectiveResource()?.categoryId?.data }.toSet(),
    )
    assertTrue(result.schedules.all { it.pending == null })
  }

  /** 部分成功只清理成功项，拒绝项继续保留等待用户修正。 */
  @Test
  fun partialSuccessClearsOnlyAcceptedPending() = runTest {
    val firstRemote = testCategoryResource(remoteId = 41L, version = 3)
    val secondRemote = firstRemote.copy(
      identity = CategoryIdentity("019d0000-0000-7000-8000-000000000011"),
      remoteId = 42L,
    )
    val first = CategorySyncState(
      firstRemote.identity,
      CategoryRemoteSnapshot(firstRemote),
      PendingUpsert(firstRemote.copy(name = AtomicField("甲", 20)), 4),
    )
    val second = CategorySyncState(
      secondRemote.identity,
      CategoryRemoteSnapshot(secondRemote),
      PendingUpsert(secondRemote.copy(name = AtomicField("乙", 20)), 4),
    )
    val capture = planner.capture(listOf(first, second), emptyList(), emptyList())
    val accepted = capture.request.categories.upserts[0].copy(
      localId = null,
      id = 41L,
      version = 4uL,
    )
    val response = response(
      capture,
      categoryUpserts = listOf(
        UpsertResult(MutationResultCode.SUCCESS, resource = accepted),
        UpsertResult(MutationResultCode.REJECTED),
      ),
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(capture, response, listOf(first, second), emptyList(), emptyList()),
    )

    assertNull(result.categories.single { it.identity == first.identity }.pending)
    assertEquals(4, result.categories.single { it.identity == second.identity }.pending?.localRevision)
  }

  /** 请求期间产生的更高 localRevision 不能被旧成功响应清掉。 */
  @Test
  fun acceptedRequestDoesNotClearNewerLocalChange() = runTest {
    val remote = testCategoryResource(remoteId = 41L, version = 3)
    val sent = CategorySyncState(
      remote.identity,
      CategoryRemoteSnapshot(remote),
      PendingUpsert(remote.copy(name = AtomicField("R", 20)), 4),
    )
    val capture = planner.capture(listOf(sent), emptyList(), emptyList())
    val newer = sent.replacePending(PendingUpsert(remote.copy(name = AtomicField("U", 30)), 5))
    val canonical = capture.request.categories.upserts.single().copy(
      localId = null,
      id = 41L,
      version = 4uL,
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        response(
          capture,
          categoryUpserts = listOf(UpsertResult(MutationResultCode.SUCCESS, resource = canonical)),
        ),
        listOf(newer),
        emptyList(),
        emptyList(),
      ),
    ).categories.single()

    assertEquals(4, result.remoteSnapshot?.version)
    assertEquals(5, result.pending?.localRevision)
    assertEquals("U", result.effectiveResource()?.name?.data)

    val retry = planner.capture(listOf(result), emptyList(), emptyList())
    assertEquals(listOf("U"), retry.request.categories.upserts.map { it.name.data })
    assertEquals(listOf(4uL), retry.request.categories.upserts.map { it.version })
  }

  /** 空本地状态必须按依赖顺序接收远端分类、日程和单次调整，并建立新的本地 UUID 映射。 */
  @Test
  fun discoveredResourcesRestoreEmptyLocalStateAndReferences() = runTest {
    val remoteCategory = testCategoryResource(remoteId = 41L, version = 2)
    val remoteSchedule = testScheduleResource(
      version = 3,
      categoryLocalId = remoteCategory.identity.id,
    )
    val remoteAdjustment = testAdjustmentResource(remoteId = 71L, version = 4).copy(
      categoryId = AtomicField(FieldPatch.Replace(remoteCategory.identity.id), 12),
    )
    val capture = planner.capture(emptyList(), emptyList(), emptyList())

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        response(
          capture,
          categoryDiscovered = listOf(remoteCategory.toWire()),
          scheduleDiscovered = listOf(remoteSchedule.toWire { remoteCategory }),
          adjustmentDiscovered = listOf(remoteAdjustment.toWire { remoteCategory }),
        ),
        emptyList(),
        emptyList(),
        emptyList(),
      ),
    )

    val category = result.categories.single()
    val schedule = result.schedules.single()
    val adjustment = result.occurrenceAdjustments.single()
    assertEquals(41L, category.remoteSnapshot?.resource?.remoteId)
    assertEquals(category.identity.id, schedule.remoteSnapshot?.resource?.categoryId?.data)
    assertEquals(
      FieldPatch.Replace(category.identity.id),
      adjustment.remoteSnapshot?.resource?.categoryId?.data,
    )
    assertEquals(remoteSchedule.identity, schedule.identity)
    assertEquals(remoteAdjustment.identity.scheduleId, adjustment.identity.scheduleId)
    assertNull(category.pending)
    assertNull(schedule.pending)
    assertNull(adjustment.pending)
  }

  /**
   * 空库冷启动必须完整恢复所有已支持的日程形态和字段。
   *
   * 该用例刻意同时覆盖分类引用、四种 timing、周/月/年重复字段、准时提醒、完成态、
   * 课表关联和单次调整 Patch，防止恢复链路只保留资源 ID/version 而静默丢失业务字段。
   */
  @Test
  fun discoveredResourcesRestoreCompleteScheduleSemantics() = runTest {
    val category = testCategoryResource(remoteId = 41L, version = 2).copy(
      color = AtomicField("{\"lightBackground\":\"#EFEFEF\"}", 12),
      sortOrder = AtomicField(3, 13),
    )
    val schedules = listOf(
      testScheduleResource(
        version = 3,
        categoryLocalId = category.identity.id,
        timing = TimingInput(TimingKind.TIMED, startAt = 1_777_651_200_000L, endAt = 1_777_656_600_000L),
      ).copy(
        identity = ScheduleIdentity("019d0000-0000-7000-8000-000000000001"),
        kind = ScheduleKind.AFFAIR,
        title = AtomicField("时间段事务", 20),
        description = AtomicField("需要在课表中展示", 21),
        recurrence = AtomicField(
          RecurrenceInput(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            anchorDate = 1_777_593_600_000L,
            untilDate = 1_779_926_400_000L,
            weekdays = setOf(Weekday.MO, Weekday.FR),
          ),
          22,
        ),
        reminder = AtomicField(ReminderInput(0), 23),
        todoState = AtomicField(null, 24),
        linkedToCourse = AtomicField(true, 25),
      ),
      testScheduleResource(
        version = 4,
        title = "时间点清单",
        timing = TimingInput(TimingKind.DEADLINE, dueAt = 1_777_744_800_000L),
      ).copy(
        identity = ScheduleIdentity("019d0000-0000-7000-8000-000000000002"),
        recurrence = AtomicField(
          RecurrenceInput(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            anchorDate = 1_777_680_000_000L,
            count = 6,
            monthDays = setOf(3, 18),
          ),
          26,
        ),
        reminder = AtomicField(ReminderInput(10), 27),
        todoState = AtomicField(TodoState.COMPLETED, 28),
      ),
      testScheduleResource(
        version = 5,
        title = "全天清单",
        timing = TimingInput(TimingKind.ALL_DAY, date = 1_777_766_400_000L),
      ).copy(
        identity = ScheduleIdentity("019d0000-0000-7000-8000-000000000003"),
        recurrence = AtomicField(
          RecurrenceInput(
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
            anchorDate = 1_777_766_400_000L,
            months = setOf(5),
            monthDays = setOf(3),
          ),
          29,
        ),
      ),
      testScheduleResource(version = 6, title = "旧清单无日期").copy(
        identity = ScheduleIdentity("019d0000-0000-7000-8000-000000000004"),
      ),
    )
    val adjustment = testAdjustmentResource(remoteId = 71L, version = 7).copy(
      identity = OccurrenceAdjustmentIdentity(
        localId = TEST_ADJUSTMENT_LOCAL_ID,
        scheduleId = schedules.first().identity.id,
        originalOccurrenceDate = TEST_OCCURRENCE_DATE,
      ),
      status = AtomicField(OccurrenceStatus.COMPLETED, 30),
      date = AtomicField(FieldPatch.Replace(1_777_680_000_000L), 31),
      time = AtomicField(
        FieldPatch.Replace(
          OccurrenceTimeInput(
            kind = OccurrenceTimeKind.TIME_RANGE,
            startMinuteOfDay = 18 * 60,
            durationMinutes = 90,
          ),
        ),
        32,
      ),
      title = AtomicField(FieldPatch.Replace("单次改名"), 33),
      description = AtomicField(FieldPatch.Clear, 34),
      categoryId = AtomicField(FieldPatch.Clear, 35),
      reminder = AtomicField(FieldPatch.Replace(ReminderInput(5)), 36),
    )
    val capture = planner.capture(emptyList(), emptyList(), emptyList())

    val applied = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture = capture,
        response = response(
          capture = capture,
          categoryDiscovered = listOf(category.toWire()),
          scheduleDiscovered = schedules.map { it.toWire { category } },
          adjustmentDiscovered = listOf(adjustment.toWire { category }),
        ),
        categories = emptyList(),
        schedules = emptyList(),
        occurrenceAdjustments = emptyList(),
      ),
    )

    val restoredCategory = applied.categories.single().remoteSnapshot!!.resource
    assertEquals(category.remoteId, restoredCategory.remoteId)
    assertEquals(category.name, restoredCategory.name)
    assertEquals(category.color, restoredCategory.color)
    assertEquals(category.sortOrder, restoredCategory.sortOrder)

    val restoredSchedules = applied.schedules.associateBy { it.identity.id }
    schedules.forEach { expected ->
      val actual = restoredSchedules.getValue(expected.identity.id).remoteSnapshot!!.resource
      assertEquals(
        expected.copy(
          categoryId = expected.categoryId.copy(
            data = expected.categoryId.data?.let { restoredCategory.identity.id },
          ),
        ),
        actual,
      )
    }

    val restoredAdjustment = applied.occurrenceAdjustments.single().remoteSnapshot!!.resource
    assertEquals(adjustment.copy(identity = restoredAdjustment.identity), restoredAdjustment)
    assertNull(applied.categories.single().pending)
    assertTrue(applied.schedules.all { it.pending == null })
    assertNull(applied.occurrenceAdjustments.single().pending)
  }

  /** confirmed 的远端删除事实必须同时清掉请求前已有的 pending，不能在下一轮把资源复活。 */
  @Test
  fun confirmedDeletedResourceWinsOverCapturedPendingUpdate() = runTest {
    val remote = testScheduleResource(version = 3, title = "远端旧值")
    val local = remote.copy(title = AtomicField("本地待提交值", 20))
    val state = testScheduleState(
      remote,
      PendingUpsert(local, localRevision = 4),
    )
    val capture = planner.capture(emptyList(), listOf(state), emptyList())
    val baseResponse = response(
      capture,
      scheduleUpserts = listOf(UpsertResult(MutationResultCode.DELETED)),
    )
    val deletedResponse = baseResponse.copy(
      schedules = baseResponse.schedules.copy(
        confirmedResults = listOf(
          ConfirmedResult(remote.identity.id, ConfirmedResultCode.DELETED),
        ),
      ),
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        deletedResponse,
        emptyList(),
        listOf(state),
        emptyList(),
      ),
    )

    assertTrue(result.schedules.isEmpty())
  }

  /** 父日程与其单次调整被远端同时删除时，父级联清理不能让后续 adjustment DELETED 失去关联。 */
  @Test
  fun confirmedParentAndAdjustmentDeletedInSameSyncAreAppliedTogether() = runTest {
    val schedule = testScheduleResource(version = 3)
    val scheduleState = testScheduleState(schedule)
    val adjustment = testAdjustmentResource(remoteId = 71L, version = 2)
    val adjustmentState = testAdjustmentState(adjustment)
    val capture = planner.capture(emptyList(), listOf(scheduleState), listOf(adjustmentState))
    val baseResponse = response(capture)
    val deletedResponse = baseResponse.copy(
      schedules = baseResponse.schedules.copy(
        confirmedResults = listOf(
          ConfirmedResult(schedule.identity.id, ConfirmedResultCode.DELETED),
        ),
      ),
      occurrenceAdjustments = baseResponse.occurrenceAdjustments.copy(
        confirmedResults = listOf(
          ConfirmedResult(71L, ConfirmedResultCode.DELETED),
        ),
      ),
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        deletedResponse,
        emptyList(),
        listOf(scheduleState),
        listOf(adjustmentState),
      ),
    )

    assertTrue(result.schedules.isEmpty())
    assertTrue(result.occurrenceAdjustments.isEmpty())
  }

  /** confirmed 的版本变化必须替换远端快照，但不能凭空产生本地 pending。 */
  @Test
  fun confirmedChangedReplacesRemoteSnapshotWithoutPending() = runTest {
    val remote = testScheduleResource(version = 3, title = "远端旧值")
    val state = testScheduleState(remote)
    val capture = planner.capture(emptyList(), listOf(state), emptyList())
    val changed = remote.copy(
      version = 4,
      title = AtomicField("远端新值", 20),
    ).toWire { error("本用例没有分类引用") }
    val baseResponse = response(capture)
    val changedResponse = baseResponse.copy(
      schedules = baseResponse.schedules.copy(
        confirmedResults = listOf(
          ConfirmedResult(remote.identity.id, ConfirmedResultCode.CHANGED, changed),
        ),
      ),
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(capture, changedResponse, emptyList(), listOf(state), emptyList()),
    ).schedules.single()

    assertEquals(4, result.remoteSnapshot?.version)
    assertEquals("远端新值", result.effectiveResource()?.title?.data)
    assertNull(result.pending)
  }

  /** 重复删除返回 DELETED 与 SUCCESS 等价，客户端都必须完成本地物理清理。 */
  @Test
  fun alreadyDeletedResultCompletesLocalDelete() = runTest {
    val remote = testScheduleResource(version = 3)
    val state = testScheduleState(
      remote,
      PendingDelete(remote.identity, localModifiedAt = 100, localRevision = 6),
    )
    val capture = planner.capture(emptyList(), listOf(state), emptyList())

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(
        capture,
        response(
          capture,
          scheduleDeletes = listOf(DeleteResult(remote.identity.id, MutationResultCode.DELETED)),
        ),
        emptyList(),
        listOf(state),
        emptyList(),
      ),
    )

    assertTrue(result.schedules.isEmpty())
  }

  /** 成功更新若返回其他日程的资源，必须整次拒绝应用，避免按位置写错本地 identity。 */
  @Test
  fun mismatchedSuccessfulUpsertIdentityFailsClosed() = runTest {
    val remote = testScheduleResource(version = 3)
    val local = remote.copy(title = AtomicField("本地修改", 20))
    val state = testScheduleState(remote, PendingUpsert(local, localRevision = 4))
    val capture = planner.capture(emptyList(), listOf(state), emptyList())
    val mismatched = capture.request.schedules.upserts.single().copy(
      id = "019d0000-0000-7000-8000-000000000099",
      version = 4uL,
    )

    val result = assertIs<ScheduleApplyResult.Failure>(
      applier.apply(
        capture,
        response(
          capture,
          scheduleUpserts = listOf(UpsertResult(MutationResultCode.SUCCESS, resource = mismatched)),
        ),
        emptyList(),
        listOf(state),
        emptyList(),
      ),
    )

    assertEquals(ScheduleApplyFailureReason.RESPONSE_CORRELATION, result.reason)
  }

  /** 物理删除成功或远端已不存在都移除本地资源。 */
  @Test
  fun physicalDeleteRemovesLocalState() = runTest {
    val remote = testAdjustmentResource(remoteId = 71L, version = 5)
    val state = testAdjustmentState(
      remote,
      PendingDelete(remote.identity, localModifiedAt = 100, localRevision = 6),
    )
    val capture = planner.capture(emptyList(), emptyList(), listOf(state))
    val response = response(
      capture,
      adjustmentDeletes = listOf(DeleteResult(71L, MutationResultCode.SUCCESS)),
    )

    val result = assertIs<ScheduleApplyResult.Success>(
      applier.apply(capture, response, emptyList(), emptyList(), listOf(state)),
    )

    assertTrue(result.occurrenceAdjustments.isEmpty())
  }

  /** 数量不对应的响应无法安全关联，必须整次拒绝应用。 */
  @Test
  fun resultCountMismatchFailsClosed() = runTest {
    val local = testCategoryResource()
    val state = testCategoryState(local, PendingUpsert(local, 1))
    val capture = planner.capture(listOf(state), emptyList(), emptyList())

    val result = assertIs<ScheduleApplyResult.Failure>(
      applier.apply(capture, response(capture), listOf(state), emptyList(), emptyList()),
    )

    assertEquals(ScheduleApplyFailureReason.RESPONSE_CORRELATION, result.reason)
  }

  /** 构造与 capture 的 inventory 自动对齐的同步响应。 */
  private fun response(
    capture: ScheduleSyncCapture,
    categoryUpserts: List<UpsertResult<CategoryInput>> = emptyList(),
    scheduleUpserts: List<UpsertResult<ScheduleInput>> = emptyList(),
    scheduleDeletes: List<DeleteResult<String, ScheduleInput>> = emptyList(),
    adjustmentDeletes: List<DeleteResult<Long, OccurrenceAdjustmentInput>> = emptyList(),
    categoryDiscovered: List<CategoryInput> = emptyList(),
    scheduleDiscovered: List<ScheduleInput> = emptyList(),
    adjustmentDiscovered: List<OccurrenceAdjustmentInput> = emptyList(),
  ): SyncResponse = SyncResponse(
    categories = CategorySyncResponse(
      confirmedResults = capture.request.categories.confirmed.map {
        ConfirmedResult(it.id, ConfirmedResultCode.CONFIRMED)
      },
      discoveredResults = categoryDiscovered,
      upsertResults = categoryUpserts,
      deleteResults = emptyList(),
    ),
    schedules = ScheduleSyncResponse(
      confirmedResults = capture.request.schedules.confirmed.map {
        ConfirmedResult(it.id, ConfirmedResultCode.CONFIRMED)
      },
      discoveredResults = scheduleDiscovered,
      upsertResults = scheduleUpserts,
      deleteResults = scheduleDeletes,
    ),
    occurrenceAdjustments = OccurrenceAdjustmentSyncResponse(
      confirmedResults = capture.request.occurrenceAdjustments.confirmed.map {
        ConfirmedResult(it.id, ConfirmedResultCode.CONFIRMED)
      },
      discoveredResults = adjustmentDiscovered,
      upsertResults = emptyList(),
      deleteResults = adjustmentDeletes,
    ),
  )
}
