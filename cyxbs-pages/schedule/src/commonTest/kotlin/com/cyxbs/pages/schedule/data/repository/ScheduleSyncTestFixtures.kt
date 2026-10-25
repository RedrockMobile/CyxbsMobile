package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.domain.sync.AtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentSyncState
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.PendingChange
import com.cyxbs.pages.schedule.domain.sync.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.domain.sync.TodoState

internal const val TEST_CATEGORY_LOCAL_ID = "019d0000-0000-7000-8000-000000000010"
internal const val TEST_SCHEDULE_ID = "019d0000-0000-7000-8000-000000000001"
internal const val TEST_ADJUSTMENT_LOCAL_ID = "019d0000-0000-7000-8000-000000000020"
internal const val TEST_OCCURRENCE_DATE = 1_777_593_600_000L

/** 构造符合最终分类 ID/version 约束的测试资源。 */
internal fun testCategoryResource(
  remoteId: Long? = null,
  version: Long = if (remoteId == null) 0L else 1L,
  name: String = "学习",
): CategoryResource = CategoryResource(
  identity = CategoryIdentity(TEST_CATEGORY_LOCAL_ID),
  remoteId = remoteId,
  version = version,
  name = AtomicField(name, 10),
  color = AtomicField(null, 10),
  sortOrder = AtomicField(0, 10),
)

/** 构造可选远端快照和 pending 的分类状态。 */
internal fun testCategoryState(
  resource: CategoryResource,
  pending: PendingChange<CategoryIdentity, CategoryResource>? = null,
): CategorySyncState = CategorySyncState(
  identity = resource.identity,
  remoteSnapshot = resource.remoteId?.let { CategoryRemoteSnapshot(resource) },
  pending = pending,
)

/** 构造最终协议下的测试日程；分类字段保存客户端本地 UUID。 */
internal fun testScheduleResource(
  version: Long = 0,
  title: String = "复习高数",
  categoryLocalId: String? = null,
  timing: TimingInput = TimingInput(TimingKind.UNSCHEDULED),
): ScheduleResource = ScheduleResource(
  identity = ScheduleIdentity(TEST_SCHEDULE_ID),
  version = version,
  kind = ScheduleKind.TODO,
  title = AtomicField(title, 10),
  description = AtomicField("", 10),
  categoryId = AtomicField(categoryLocalId, 10),
  timing = AtomicField(timing, 10),
  recurrence = AtomicField(null, 10),
  reminder = AtomicField<ReminderInput?>(null, 10),
  todoState = AtomicField(TodoState.OPEN, 10),
  linkedToCourse = AtomicField(false, 10),
)

/** 构造可选远端快照和 pending 的日程状态。 */
internal fun testScheduleState(
  resource: ScheduleResource,
  pending: PendingChange<ScheduleIdentity, ScheduleResource>? = null,
  hasRemote: Boolean = resource.version > 0,
): ScheduleSyncState = ScheduleSyncState(
  identity = resource.identity,
  remoteSnapshot = resource.takeIf { hasRemote }?.let(::ScheduleRemoteSnapshot),
  pending = pending,
)

/** 构造最终协议下的单次调整资源。 */
internal fun testAdjustmentResource(
  remoteId: Long? = null,
  version: Long = if (remoteId == null) 0L else 1L,
): OccurrenceAdjustmentResource = OccurrenceAdjustmentResource(
  identity = OccurrenceAdjustmentIdentity(
    localId = TEST_ADJUSTMENT_LOCAL_ID,
    scheduleId = TEST_SCHEDULE_ID,
    originalOccurrenceDate = TEST_OCCURRENCE_DATE,
  ),
  remoteId = remoteId,
  version = version,
  status = AtomicField(OccurrenceStatus.ACTIVE, 10),
  date = AtomicField(FieldPatch.Inherit, 10),
  time = AtomicField(FieldPatch.Inherit, 10),
  title = AtomicField(FieldPatch.Inherit, 10),
  description = AtomicField(FieldPatch.Inherit, 10),
  categoryId = AtomicField(FieldPatch.Inherit, 10),
  reminder = AtomicField(FieldPatch.Inherit, 10),
)

/** 构造可选远端快照和 pending 的单次调整状态。 */
internal fun testAdjustmentState(
  resource: OccurrenceAdjustmentResource,
  pending: PendingChange<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>? = null,
): OccurrenceAdjustmentSyncState = OccurrenceAdjustmentSyncState(
  identity = resource.identity,
  remoteSnapshot = resource.remoteId?.let { OccurrenceAdjustmentRemoteSnapshot(resource) },
  pending = pending,
)
