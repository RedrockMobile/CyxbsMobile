package com.cyxbs.pages.schedule.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Schedule 本地双快照状态的核心不变量测试。 */
class ScheduleSyncStateTest {
  @Test
  fun remoteOnlyStateProjectsRemoteResource() {
    val remote = categoryRemote(version = 7)
    val state = CategorySyncState(remote.identity, remote)

    assertEquals(remote.resource, state.effectiveResource())
  }

  @Test
  fun pendingCreateProjectsVersionZeroResourceWithoutRemote() {
    val identity = CategoryIdentity("category-create")
    val create = categoryResource(identity = identity, version = 0)
    val state = CategorySyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(create, localRevision = 1),
    )

    assertEquals(create, state.effectiveResource())
  }

  @Test
  fun categoryWithoutCustomColorKeepsNullInEffectiveResource() {
    val identity = CategoryIdentity("category-no-color")
    val resource = categoryResource(identity = identity, version = 0, color = null)
    val state = CategorySyncState(
      identity = identity,
      remoteSnapshot = null,
      pending = PendingUpsert(resource, localRevision = 1),
    )

    assertNull(state.effectiveResource()?.color?.data)
  }

  @Test
  fun pendingUpdateUsesRemoteVersionAndOverridesRemoteProjection() {
    val remote = categoryRemote(version = 7)
    val update = remote.resource.copy(
      name = AtomicField("本地修改", modifiedAt = 1_700_000_001_000),
    )
    val state = CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = remote,
      pending = PendingUpsert(update, localRevision = 9),
    )

    assertEquals(update, state.effectiveResource())
  }

  @Test
  fun remoteAdvanceKeepsOlderPendingUpdateWithoutClientRebase() {
    val remoteVersion7 = categoryRemote(version = 7)
    val pendingVersion7 = PendingUpsert(
      remoteVersion7.resource.copy(name = AtomicField("请求期间的修改", 1_700_000_001_000)),
      localRevision = 9,
    )
    val initial = CategorySyncState(
      identity = remoteVersion7.identity,
      remoteSnapshot = remoteVersion7,
      pending = pendingVersion7,
    )
    val remoteVersion8 = categoryRemote(version = 8)

    val afterRemoteAdvance = initial.copy(remoteSnapshot = remoteVersion8)

    assertEquals(pendingVersion7, afterRemoteAdvance.pending)
    assertEquals(7L, afterRemoteAdvance.effectiveResource()?.version)
    assertEquals("请求期间的修改", afterRemoteAdvance.effectiveResource()?.name?.data)
  }

  @Test
  fun pendingDeleteHidesRemoteResourceAndCarriesNoVersion() {
    val remote = categoryRemote(version = 7)
    val delete = PendingDelete<CategoryIdentity, CategoryResource>(
      identity = remote.identity,
      localModifiedAt = 1_700_000_002_000,
      localRevision = 10,
    )
    val state = CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = remote,
      pending = delete,
    )

    assertNull(state.effectiveResource())
  }

  @Test
  fun newPendingReplacesPreviousPendingForSameIdentity() {
    val remote = categoryRemote(version = 7)
    val initial = CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = remote,
      pending = PendingUpsert(
        remote.resource.copy(name = AtomicField("第一次", 1_700_000_001_000)),
        localRevision = 11,
      ),
    )
    val latest = PendingUpsert(
      remote.resource.copy(name = AtomicField("第二次", 1_700_000_002_000)),
      localRevision = 12,
    )

    val replaced = initial.replacePending(latest)

    assertEquals(latest, replaced.pending)
    assertEquals("第二次", replaced.effectiveResource()?.name?.data)
  }

  @Test
  fun replacePendingRejectsOlderLocalRevision() {
    val remote = categoryRemote(version = 7)
    val state = CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = remote,
      pending = PendingUpsert(remote.resource, localRevision = 12),
    )

    assertFailsWith<IllegalArgumentException> {
      state.replacePending(PendingUpsert(remote.resource, localRevision = 11))
    }
  }

  @Test
  fun identityVersionAndRevisionRulesAreEnforced() {
    val remote = categoryRemote(version = 7)

    assertFailsWith<IllegalArgumentException> {
      CategorySyncState(
        identity = CategoryIdentity("other-category"),
        remoteSnapshot = remote,
      )
    }
    // CREATE 请求 R 发出期间产生的 U 仍是 version=0；R 推进 remote 后必须原样保留到下一轮。
    val createRequestUpdate = CategorySyncState(
      identity = remote.identity,
      remoteSnapshot = remote,
      pending = PendingUpsert(remote.resource.copy(remoteId = null, version = 0), localRevision = 1),
    )
    assertEquals(0, createRequestUpdate.effectiveResource()?.version)
    assertFailsWith<IllegalArgumentException> {
      PendingUpsert(remote.resource, localRevision = 0)
    }
    assertFailsWith<IllegalArgumentException> {
      AtomicField("非法时间", modifiedAt = -1)
    }
    assertFailsWith<IllegalArgumentException> {
      PendingDelete<CategoryIdentity, CategoryResource>(
        identity = remote.identity,
        localModifiedAt = -1,
        localRevision = 1,
      )
    }
  }

  @Test
  fun occurrenceAdjustmentSeparatesLocalAndRemoteIdentityAndKeepsSevenAtomicFields() {
    val identity = OccurrenceAdjustmentIdentity(
      localId = "local-override-1",
      scheduleId = "schedule-1",
      originalOccurrenceDate = 1_728_000_000_000,
    )
    val resource = OccurrenceAdjustmentResource(
      identity = identity,
      remoteId = 31L,
      version = 3,
      status = AtomicField(OccurrenceStatus.COMPLETED, 1_700_000_000_001),
      date = AtomicField(FieldPatch.Inherit, 1_700_000_000_001),
      time = AtomicField(FieldPatch.Inherit, 1_700_000_000_001),
      title = AtomicField(FieldPatch.Replace("补做一次"), 1_700_000_000_002),
      description = AtomicField(FieldPatch.Clear, 1_700_000_000_003),
      categoryId = AtomicField(FieldPatch.Inherit, 1_700_000_000_003),
      reminder = AtomicField(FieldPatch.Inherit, 1_700_000_000_004),
    )
    val remote = OccurrenceAdjustmentRemoteSnapshot(resource)
    val state = OccurrenceAdjustmentSyncState(identity, remote)

    assertEquals(identity, state.effectiveResource()?.identity)
    assertEquals(OccurrenceStatus.COMPLETED, state.effectiveResource()?.status?.data)
    assertEquals(FieldPatch.Replace("补做一次"), state.effectiveResource()?.title?.data)
    assertEquals(FieldPatch.Clear, state.effectiveResource()?.description?.data)
    assertEquals(FieldPatch.Inherit, state.effectiveResource()?.reminder?.data)
    assertFailsWith<IllegalArgumentException> {
      OccurrenceAdjustmentIdentity(
        localId = "local-override-invalid",
        scheduleId = "schedule-1",
        originalOccurrenceDate = 1,
      )
    }
    assertFailsWith<IllegalArgumentException> {
      resource.copy(remoteId = null, version = 3)
    }
  }

  /** 构造服务端已确认分类，方便聚焦同步状态而非字段填充。 */
  private fun categoryRemote(version: Long): CategoryRemoteSnapshot {
    val resource = categoryResource(
      identity = CategoryIdentity("category-1"),
      version = version,
    )
    return CategoryRemoteSnapshot(resource)
  }

  /** 构造完整 CategoryInput；null 表示分类没有自定义颜色，仍是完整上传 payload。 */
  private fun categoryResource(
    identity: CategoryIdentity,
    version: Long,
    color: String? = "#123456",
  ): CategoryResource = CategoryResource(
    identity = identity,
    remoteId = version.takeIf { it > 0 },
    version = version,
    name = AtomicField("分类", 1_700_000_000_100),
    color = AtomicField(color, 1_700_000_000_200),
    sortOrder = AtomicField(10, 1_700_000_000_300),
  )
}
