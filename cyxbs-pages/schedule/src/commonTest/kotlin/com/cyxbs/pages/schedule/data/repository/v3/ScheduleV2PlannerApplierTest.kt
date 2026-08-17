package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResult
import com.cyxbs.pages.schedule.data.remote.v3.AtomicBatchResultCode
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.CategoryAtomicUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.CategoryCurrent
import com.cyxbs.pages.schedule.data.remote.v3.CategoryInput
import com.cyxbs.pages.schedule.data.remote.v3.CategorySyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.CategoryTombstone
import com.cyxbs.pages.schedule.data.remote.v3.CategoryUpsertResult
import com.cyxbs.pages.schedule.data.remote.v3.MutationResultCode
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleAtomicResultBlock
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleSyncResponse
import com.cyxbs.pages.schedule.data.remote.v3.ServerResourceMeta as WireServerResourceMeta
import com.cyxbs.pages.schedule.data.remote.v3.SyncResponse
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideSyncState
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.v2.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v3 planner/applier 的纯状态合同测试，不依赖 Room、Ktor 或旧同步协议。 */
class ScheduleV2PlannerApplierTest {
  private val planner = ScheduleV2RequestPlanner()
  private val applier = ScheduleV2ResponseApplier()

  @Test
  fun captureKeepsConfirmedAlongsidePendingAndSeparatesOrdinaryFromBatch() {
    val ordinary = categoryState(
      id = "ordinary",
      remoteVersion = 3,
      pendingRevision = 4,
    )
    val batchedDelete = CategorySyncState(
      identity = CategoryIdentity("batched-delete"),
      remoteSnapshot = categoryRemote("batched-delete", 2),
      pending = PendingDelete(
        identity = CategoryIdentity("batched-delete"),
        localModifiedAt = 20,
        localRevision = 5,
        localBatchId = "batch-1",
      ),
    )
    val overrideIdentity = OccurrenceOverrideIdentity("schedule-1", 1_728_000_000_000)
    val override = OccurrenceOverrideSyncState(
      identity = overrideIdentity,
      remoteSnapshot = null,
      pending = PendingUpsert(
        resource = OccurrenceOverrideResource(
          identity = overrideIdentity,
          version = 0,
          status = AtomicField(OccurrenceStatus.ACTIVE, 1),
          title = AtomicField(FieldPatch.Replace("一次覆盖"), 2),
          description = AtomicField(FieldPatch.Clear, 3),
          reminders = AtomicField(FieldPatch.Inherit, 4),
        ),
        localRevision = 6,
        localBatchId = "batch-1",
      ),
    )

    val capture = planner.capture(
      syncRequestId = "sync-1",
      categories = listOf(ordinary, batchedDelete),
      schedules = emptyList(),
      occurrenceOverrides = listOf(override),
    )

    assertEquals(listOf("ordinary", "batched-delete"), capture.request.categories.confirmed.map { it.id })
    assertEquals(listOf("ordinary"), capture.request.categories.upserts.map { it.id })
    assertTrue(capture.request.categories.deletes.isEmpty())
    assertEquals(1, capture.request.atomicBatches.size)
    assertEquals("batched-delete", capture.request.atomicBatches.single().categories.deletes.single().id)
    val wireOverride =
      capture.request.atomicBatches.single().occurrenceOverrides.upserts.single()
    assertEquals(overrideIdentity.scheduleId, wireOverride.scheduleId)
    assertEquals(4, listOf(wireOverride.status, wireOverride.title, wireOverride.description, wireOverride.reminders).size)
    assertEquals(2, capture.atomicBatches.single().categories.size + capture.atomicBatches.single().occurrenceOverrides.size)
  }

  @Test
  fun responseUsesDeleteWinsAndOtherwiseHighestLiveVersion() {
    val deleted = CategorySyncState(
      CategoryIdentity("deleted"),
      categoryRemote("deleted", 1),
    )
    val upgraded = CategorySyncState(
      CategoryIdentity("upgraded"),
      categoryRemote("upgraded", 1),
    )
    val states = listOf(deleted, upgraded)
    val capture = planner.capture("sync-2", states, emptyList(), emptyList())
    val response = emptyResponse(
      syncId = "sync-2",
      categoryUpserts = listOf(
        wireCategoryCurrent("deleted", 4, "live-before-delete"),
        wireCategoryCurrent("upgraded", 2, "v2"),
        wireCategoryCurrent("upgraded", 5, "v5"),
      ),
      categoryDeletes = listOf(CategoryTombstone("deleted", deletedAt = 50)),
    )

    val result = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, states, emptyList(), emptyList()),
    )

    assertTrue(result.categories.none { it.identity.id == "deleted" })
    assertEquals(5, result.categories.single { it.identity.id == "upgraded" }.remoteSnapshot?.version)
    assertEquals(
      "v5",
      result.categories.single { it.identity.id == "upgraded" }.remoteSnapshot?.resource?.name?.data,
    )
  }

  @Test
  fun responseFailsClosedOnSameVersionConflictAndRemoteRegression() {
    val state = CategorySyncState(CategoryIdentity("category"), categoryRemote("category", 3))
    val capture = planner.capture("sync-3", listOf(state), emptyList(), emptyList())
    val conflict = emptyResponse(
      "sync-3",
      categoryUpserts = listOf(
        wireCategoryCurrent("category", 4, "first"),
        wireCategoryCurrent("category", 4, "second"),
      ),
    )

    val conflictResult = assertIs<ScheduleV2ApplyResult.Failure>(
      applier.apply(capture, conflict, listOf(state), emptyList(), emptyList()),
    )
    assertEquals(ScheduleV2ApplyFailureReason.SAME_VERSION_CONFLICT, conflictResult.reason)

    val regression = emptyResponse(
      "sync-3",
      categoryUpserts = listOf(wireCategoryCurrent("category", 2, "older")),
    )
    val regressionResult = assertIs<ScheduleV2ApplyResult.Failure>(
      applier.apply(capture, regression, listOf(state), emptyList(), emptyList()),
    )
    assertEquals(ScheduleV2ApplyFailureReason.REMOTE_VERSION_REGRESSION, regressionResult.reason)
  }

  @Test
  fun ordinaryCompareAndClearPreservesNewerPendingAndClearsMatchingRevision() {
    val uploaded = categoryState("category", remoteVersion = 7, pendingRevision = 10)
    val capture = planner.capture("sync-4", listOf(uploaded), emptyList(), emptyList())
    val response = emptyResponse(
      "sync-4",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(
          id = "category",
          code = MutationResultCode.APPLIED,
          current = wireCategoryCurrent("category", 8, "server-merged"),
        ),
      ),
    )
    val newerPending = PendingUpsert(
      categoryPendingResource(uploaded).copy(name = AtomicField("U", 99)),
      localRevision = 11,
    )
    val currentU = uploaded.replacePending(newerPending)

    val kept = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(currentU), emptyList(), emptyList()),
    ).categories.single()
    assertEquals(8, kept.remoteSnapshot?.version)
    assertEquals(11, kept.pending?.localRevision)
    assertEquals("U", kept.effectiveResource()?.name?.data)

    val cleared = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(uploaded), emptyList(), emptyList()),
    ).categories.single()
    assertNull(cleared.pending)
    assertEquals("server-merged", cleared.effectiveResource()?.name?.data)
  }

  @Test
  fun createResponseKeepsNewerVersionZeroPendingThenNextCaptureConverges() {
    val identity = CategoryIdentity("create-r-u")
    val createR = CategoryResource(
      identity = identity,
      version = 0,
      name = AtomicField("R", 10),
      color = AtomicField("#123456", 11),
      sortOrder = AtomicField(1, 12),
    )
    val initial = CategorySyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(createR, localRevision = 1),
    )
    val captureR = planner.capture("sync-create-r", listOf(initial), emptyList(), emptyList())
    val createU = createR.copy(name = AtomicField("U", 20))
    val currentU = initial.replacePending(PendingUpsert(createU, localRevision = 2))
    val createdResponse = emptyResponse(
      "sync-create-r",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(
          id = identity.id,
          code = MutationResultCode.CREATED,
          current = wireCategoryCurrent(identity.id, 1, "R"),
        ),
      ),
    )

    val afterR = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(captureR, createdResponse, listOf(currentU), emptyList(), emptyList()),
    ).categories.single()
    assertEquals(1, afterR.remoteSnapshot?.version)
    assertEquals(2, afterR.pending?.localRevision)
    assertEquals(createU, categoryPendingResource(afterR))
    assertEquals(0, categoryPendingResource(afterR).version)
    assertEquals(AtomicField("U", 20), categoryPendingResource(afterR).name)

    val captureU = planner.capture("sync-create-u", listOf(afterR), emptyList(), emptyList())
    assertEquals(1UL, captureU.request.categories.upserts.single().version)
    // planner 只投影 wire version，不能把 U 变成客户端 rebase 状态。
    assertEquals(createU, categoryPendingResource(afterR))
    val appliedResponse = emptyResponse(
      "sync-create-u",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(
          id = identity.id,
          code = MutationResultCode.APPLIED,
          current = wireCategoryCurrent(identity.id, 2, "U"),
        ),
      ),
    )

    val converged = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(captureU, appliedResponse, listOf(afterR), emptyList(), emptyList()),
    ).categories.single()
    assertNull(converged.pending)
    assertEquals(2, converged.remoteSnapshot?.version)
    assertEquals("U", converged.effectiveResource()?.name?.data)
  }

  @Test
  fun rejectedOrdinaryResultClearsMatchingPending() {
    val state = categoryState("category", remoteVersion = 3, pendingRevision = 4)
    val capture = planner.capture("sync-5", listOf(state), emptyList(), emptyList())
    val response = emptyResponse(
      "sync-5",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(id = "category", code = MutationResultCode.REJECTED),
      ),
    )

    val result = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(state), emptyList(), emptyList()),
    )

    assertNull(result.categories.single().pending)
  }

  @Test
  fun alreadyExistsClearsMatchingPending() {
    val state = categoryState("category", remoteVersion = 3, pendingRevision = 4)
    val capture = planner.capture("sync-already-exists", listOf(state), emptyList(), emptyList())
    val response = emptyResponse(
      "sync-already-exists",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(id = "category", code = MutationResultCode.ALREADY_EXISTS),
      ),
    )

    val result = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(state), emptyList(), emptyList()),
    )

    assertNull(result.categories.single().pending)
  }

  @Test
  fun invalidRequestDiscardsOnlyUploadedRevision() {
    val uploaded = categoryState("category", remoteVersion = 3, pendingRevision = 4)
    val capture = planner.capture("sync-invalid", listOf(uploaded), emptyList(), emptyList())

    val exact = assertIs<ScheduleV2ApplyResult.Success>(
      applier.discardUploaded(capture, listOf(uploaded), emptyList(), emptyList()),
    )
    assertNull(exact.categories.single().pending)

    val newer = uploaded.replacePending(
      PendingUpsert(
        categoryPendingResource(uploaded).copy(name = AtomicField("U", 200)),
        localRevision = 5,
      ),
    )
    val afterU = assertIs<ScheduleV2ApplyResult.Success>(
      applier.discardUploaded(capture, listOf(newer), emptyList(), emptyList()),
    )
    assertEquals(5, afterU.categories.single().pending?.localRevision)
    assertEquals("U", afterU.categories.single().effectiveResource()?.name?.data)
  }

  @Test
  fun resourceDeletedTombstoneClearsMatchingPendingAndRemovesRemote() {
    val state = categoryState("category", remoteVersion = 3, pendingRevision = 4)
    val capture = planner.capture("sync-resource-deleted", listOf(state), emptyList(), emptyList())
    val response = emptyResponse(
      "sync-resource-deleted",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(
          id = "category",
          code = MutationResultCode.RESOURCE_DELETED,
          tombstone = CategoryTombstone(id = "category", deletedAt = 100),
        ),
      ),
    )

    val result = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(state), emptyList(), emptyList()),
    )

    assertTrue(result.categories.isEmpty())
  }

  @Test
  fun tombstoneClearsNewerPositivePatchAndConverges() {
    val captured = categoryState("category", remoteVersion = 3, pendingRevision = 4)
    val capture = planner.capture(
      "sync-deleted-stale-patch",
      listOf(captured),
      emptyList(),
      emptyList(),
    )
    val currentU = captured.replacePending(
      PendingUpsert(
        categoryPendingResource(captured).copy(name = AtomicField("U", 200)),
        localRevision = 5,
      ),
    )
    val response = emptyResponse(
      "sync-deleted-stale-patch",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(
          id = "category",
          code = MutationResultCode.RESOURCE_DELETED,
          tombstone = CategoryTombstone(id = "category", deletedAt = 300),
        ),
      ),
    )

    val result = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(currentU), emptyList(), emptyList()),
    )
    assertTrue(result.categories.isEmpty())
    // tombstone 是删除最高优先事实；正版本 U 已无基线，不能留下永久 rejected pending。
    assertEquals(5, currentU.pending?.localRevision)
    assertEquals("U", currentU.effectiveResource()?.name?.data)
  }

  @Test
  fun tombstoneAlsoClearsNewerVersionZeroCreate() {
    val identity = CategoryIdentity("deleted-create")
    val createR = CategoryResource(
      identity = identity,
      version = 0,
      name = AtomicField("R", 10),
      color = AtomicField("#123456", 11),
      sortOrder = AtomicField(1, 12),
    )
    val captured = CategorySyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(createR, localRevision = 1),
    )
    val capture = planner.capture("sync-deleted-create", listOf(captured), emptyList(), emptyList())
    val currentU = captured.replacePending(
      PendingUpsert(createR.copy(name = AtomicField("U", 20)), localRevision = 2),
    )
    val response = emptyResponse(
      "sync-deleted-create",
      categoryUpsertResults = listOf(
        CategoryUpsertResult(
          id = identity.id,
          code = MutationResultCode.RESOURCE_DELETED,
          tombstone = CategoryTombstone(id = identity.id, deletedAt = 300),
        ),
      ),
    )

    val result = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(currentU), emptyList(), emptyList()),
    )
    assertTrue(result.categories.isEmpty())
  }

  @Test
  fun malformedOrdinaryResultCountOrIdentityFailsClosed() {
    val state = categoryState("category", remoteVersion = 3, pendingRevision = 4)
    val capture = planner.capture("sync-malformed", listOf(state), emptyList(), emptyList())

    val missingResult = assertIs<ScheduleV2ApplyResult.Failure>(
      applier.apply(
        capture,
        emptyResponse("sync-malformed"),
        listOf(state),
        emptyList(),
        emptyList(),
      ),
    )
    assertEquals(ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION, missingResult.reason)

    val wrongIdentity = assertIs<ScheduleV2ApplyResult.Failure>(
      applier.apply(
        capture,
        emptyResponse(
          "sync-malformed",
          categoryUpsertResults = listOf(
            CategoryUpsertResult(id = "other", code = MutationResultCode.REJECTED),
          ),
        ),
        listOf(state),
        emptyList(),
        emptyList(),
      ),
    )
    assertEquals(ScheduleV2ApplyFailureReason.RESPONSE_CORRELATION, wrongIdentity.reason)
  }

  @Test
  fun atomicBatchClearsAllOrKeepsAllWhenAnyRevisionChanged() {
    val first = categoryState("first", remoteVersion = 1, pendingRevision = 2, batchId = "batch")
    val second = categoryState("second", remoteVersion = 1, pendingRevision = 2, batchId = "batch")
    val capturedStates = listOf(first, second)
    val capture = planner.capture("sync-6", capturedStates, emptyList(), emptyList())
    val response = emptyResponse(
      "sync-6",
      atomicResults = listOf(
        AtomicBatchResult(
          batchId = "batch",
          code = AtomicBatchResultCode.APPLIED,
          categories = CategoryAtomicResultBlock(
            upsertResults = listOf(
              CategoryAtomicUpsertResult("first", AtomicBatchResultCode.APPLIED),
              CategoryAtomicUpsertResult("second", AtomicBatchResultCode.APPLIED),
            ),
            deleteResults = emptyList(),
            relatedUpserts = listOf(
              wireCategoryCurrent("first", 2, "server-first"),
              wireCategoryCurrent("second", 2, "server-second"),
            ),
            relatedDeletes = emptyList(),
          ),
          schedules = emptyScheduleAtomicResult(),
          occurrenceOverrides = emptyOverrideAtomicResult(),
        ),
      ),
    )
    val newerSecond = second.replacePending(
      PendingUpsert(
        categoryPendingResource(second).copy(name = AtomicField("U2", 100)),
        localRevision = 3,
        localBatchId = "batch",
      ),
    )

    val kept = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, listOf(first, newerSecond), emptyList(), emptyList()),
    )
    assertEquals(listOf(2L, 3L), kept.categories.map { it.pending?.localRevision })

    val cleared = assertIs<ScheduleV2ApplyResult.Success>(
      applier.apply(capture, response, capturedStates, emptyList(), emptyList()),
    )
    assertTrue(cleared.categories.all { it.pending == null })
    assertEquals(listOf(2L, 2L), cleared.categories.map { it.remoteSnapshot?.version })
  }

  private fun categoryState(
    id: String,
    remoteVersion: Long,
    pendingRevision: Long,
    batchId: String? = null,
  ): CategorySyncState {
    val remote = categoryRemote(id, remoteVersion)
    return CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = remote,
      pending = PendingUpsert(
        resource = remote.resource.copy(name = AtomicField("pending-$id", 10)),
        localRevision = pendingRevision,
        localBatchId = batchId,
      ),
    )
  }

  /** 读取测试状态中的完整 Category pending，保持泛型类型信息。 */
  private fun categoryPendingResource(state: CategorySyncState): CategoryResource {
    val pending = state.pending
    check(pending is PendingUpsert) { "test state must contain an upsert" }
    return pending.resource
  }

  private fun categoryRemote(id: String, version: Long): CategoryRemoteSnapshot =
    CategoryRemoteSnapshot(
      resource = CategoryResource(
        identity = CategoryIdentity(id),
        version = version,
        name = AtomicField("remote-$id", 1),
        color = AtomicField("#000000", 2),
        sortOrder = AtomicField(0, 3),
      ),
      meta = ServerResourceMeta(createdAt = 1, remoteModifiedAt = version),
    )

  private fun wireCategoryCurrent(id: String, version: Long, name: String): CategoryCurrent =
    CategoryCurrent(
      resource = CategoryInput(
        id = id,
        version = version.toULong(),
        name = com.cyxbs.pages.schedule.data.remote.v3.AtomicField(name, version),
        color = com.cyxbs.pages.schedule.data.remote.v3.AtomicField("#000000", 2),
        sortOrder = com.cyxbs.pages.schedule.data.remote.v3.AtomicField(0, 3),
      ),
      meta = WireServerResourceMeta(createdAt = 1, remoteModifiedAt = version),
    )

  private fun emptyResponse(
    syncId: String,
    categoryUpserts: List<CategoryCurrent> = emptyList(),
    categoryDeletes: List<CategoryTombstone> = emptyList(),
    categoryUpsertResults: List<CategoryUpsertResult> = emptyList(),
    atomicResults: List<AtomicBatchResult> = emptyList(),
  ): SyncResponse = SyncResponse(
    syncRequestId = syncId,
    categories = CategorySyncResponse(
      upserts = categoryUpserts,
      deletes = categoryDeletes,
      upsertResults = categoryUpsertResults,
      deleteResults = emptyList(),
    ),
    schedules = ScheduleSyncResponse(
      upserts = emptyList(),
      deletes = emptyList(),
      upsertResults = emptyList(),
      deleteResults = emptyList(),
    ),
    occurrenceOverrides = OccurrenceOverrideSyncResponse(
      upserts = emptyList(),
      deletes = emptyList(),
      upsertResults = emptyList(),
      deleteResults = emptyList(),
    ),
    atomicBatchResults = atomicResults,
  )

  private fun emptyScheduleAtomicResult(): ScheduleAtomicResultBlock =
    ScheduleAtomicResultBlock(
      upsertResults = emptyList(),
      deleteResults = emptyList(),
      relatedUpserts = emptyList(),
      relatedDeletes = emptyList(),
    )

  private fun emptyOverrideAtomicResult(): OccurrenceOverrideAtomicResultBlock =
    OccurrenceOverrideAtomicResultBlock(
      upsertResults = emptyList(),
      deleteResults = emptyList(),
      relatedUpserts = emptyList(),
      relatedDeletes = emptyList(),
    )
}
