package com.cyxbs.pages.schedule.data.repository.v3

import com.cyxbs.pages.schedule.data.remote.v3.AtomicField as WireAtomicField
import com.cyxbs.pages.schedule.data.remote.v3.CategoryCurrent
import com.cyxbs.pages.schedule.data.remote.v3.CategoryInput
import com.cyxbs.pages.schedule.data.remote.v3.CompletionStatus as WireCompletionStatus
import com.cyxbs.pages.schedule.data.remote.v3.FieldPatch as WireFieldPatch
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideCurrent
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceOverrideInput
import com.cyxbs.pages.schedule.data.remote.v3.OccurrenceStatus as WireOccurrenceStatus
import com.cyxbs.pages.schedule.data.remote.v3.PatchMode
import com.cyxbs.pages.schedule.data.remote.v3.RecurrenceFrequency as WireRecurrenceFrequency
import com.cyxbs.pages.schedule.data.remote.v3.RecurrenceInput as WireRecurrenceInput
import com.cyxbs.pages.schedule.data.remote.v3.ReminderInput as WireReminderInput
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleCurrent
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.v3.ServerResourceMeta as WireServerResourceMeta
import com.cyxbs.pages.schedule.data.remote.v3.TimingInput as WireTimingInput
import com.cyxbs.pages.schedule.data.remote.v3.TimingKind as WireTimingKind
import com.cyxbs.pages.schedule.data.remote.v3.Weekday as WireWeekday
import com.cyxbs.pages.schedule.domain.sync.v2.AtomicField as DomainAtomicField
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.v2.CompletionStatus as DomainCompletionStatus
import com.cyxbs.pages.schedule.domain.sync.v2.FieldPatch as DomainFieldPatch
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceOverrideResource
import com.cyxbs.pages.schedule.domain.sync.v2.OccurrenceStatus as DomainOccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceFrequency as DomainRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.v2.RecurrenceInput as DomainRecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.v2.ReminderInput as DomainReminderInput
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.v2.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.v2.ServerResourceMeta as DomainServerResourceMeta
import com.cyxbs.pages.schedule.domain.sync.v2.TimingInput as DomainTimingInput
import com.cyxbs.pages.schedule.domain.sync.v2.TimingKind as DomainTimingKind
import com.cyxbs.pages.schedule.domain.sync.v2.Weekday as DomainWeekday

/** Category 完整领域快照到 wire payload 的无损映射。 */
internal fun CategoryResource.toWire(): CategoryInput = CategoryInput(
  id = identity.id,
  version = version.toULong(),
  name = WireAtomicField(name.data, name.modifiedAt),
  color = WireAtomicField(color.data, color.modifiedAt),
  sortOrder = WireAtomicField(sortOrder.data, sortOrder.modifiedAt),
)

/** wire Category 完整 payload 到领域资源的无损映射。 */
internal fun CategoryInput.toDomain(): CategoryResource = CategoryResource(
  identity = CategoryIdentity(id),
  version = version.toDomainVersion(),
  name = DomainAtomicField(name.data, name.modifiedAt),
  color = DomainAtomicField(color.data, color.modifiedAt),
  sortOrder = DomainAtomicField(sortOrder.data, sortOrder.modifiedAt),
)

/** Schedule 七个原子字段到 wire payload 的显式映射。 */
internal fun ScheduleResource.toWire(): ScheduleInput = ScheduleInput(
  id = identity.id,
  version = version.toULong(),
  title = WireAtomicField(title.data, title.modifiedAt),
  description = WireAtomicField(description.data, description.modifiedAt),
  categoryId = WireAtomicField(categoryId.data, categoryId.modifiedAt),
  timing = WireAtomicField(timing.data.toWire(), timing.modifiedAt),
  recurrence = WireAtomicField(recurrence.data?.toWire(), recurrence.modifiedAt),
  reminders = WireAtomicField(reminders.data.map { it.toWire() }, reminders.modifiedAt),
  completion = WireAtomicField(completion.data.toWire(), completion.modifiedAt),
)

/** wire Schedule 完整 payload 到领域七原子资源的无损映射。 */
internal fun ScheduleInput.toDomain(): ScheduleResource = ScheduleResource(
  identity = ScheduleIdentity(id),
  version = version.toDomainVersion(),
  title = DomainAtomicField(title.data, title.modifiedAt),
  description = DomainAtomicField(description.data, description.modifiedAt),
  categoryId = DomainAtomicField(categoryId.data, categoryId.modifiedAt),
  timing = DomainAtomicField(timing.data.toDomain(), timing.modifiedAt),
  recurrence = DomainAtomicField(recurrence.data?.toDomain(), recurrence.modifiedAt),
  reminders = DomainAtomicField(reminders.data.map { it.toDomain() }, reminders.modifiedAt),
  completion = DomainAtomicField(completion.data.toDomain(), completion.modifiedAt),
)

/** OccurrenceOverride 只映射协议允许的状态、标题、详情和提醒四个原子。 */
internal fun OccurrenceOverrideResource.toWire(): OccurrenceOverrideInput = OccurrenceOverrideInput(
  scheduleId = identity.scheduleId,
  occurrenceDate = identity.occurrenceDate,
  version = version.toULong(),
  status = WireAtomicField(status.data.toWire(), status.modifiedAt),
  title = WireAtomicField(title.data.toWireStringPatch(), title.modifiedAt),
  description = WireAtomicField(description.data.toWireStringPatch(), description.modifiedAt),
  reminders = WireAtomicField(reminders.data.toWireReminderPatch(), reminders.modifiedAt),
)

/** wire OccurrenceOverride payload 到领域四原子资源的无损映射。 */
internal fun OccurrenceOverrideInput.toDomain(): OccurrenceOverrideResource =
  OccurrenceOverrideResource(
    identity = OccurrenceOverrideIdentity(scheduleId, occurrenceDate),
    version = version.toDomainVersion(),
    status = DomainAtomicField(status.data.toDomain(), status.modifiedAt),
    title = DomainAtomicField(title.data.toDomainStringPatch(), title.modifiedAt),
    description = DomainAtomicField(description.data.toDomainStringPatch(), description.modifiedAt),
    reminders = DomainAtomicField(reminders.data.toDomainReminderPatch(), reminders.modifiedAt),
  )

/** wire canonical Category 到本地 remoteSnapshot 的无损映射。 */
internal fun CategoryCurrent.toDomain(): CategoryRemoteSnapshot = CategoryRemoteSnapshot(
  resource = resource.toDomain(),
  meta = meta.toDomain(),
)

/** 本地 Category remoteSnapshot 到 wire canonical Current 的无损映射。 */
internal fun CategoryRemoteSnapshot.toWire(): CategoryCurrent = CategoryCurrent(
  resource = resource.toWire(),
  meta = meta.toWire(),
)

/** wire canonical Schedule 到本地 remoteSnapshot 的无损映射。 */
internal fun ScheduleCurrent.toDomain(): ScheduleRemoteSnapshot = ScheduleRemoteSnapshot(
  resource = resource.toDomain(),
  meta = meta.toDomain(),
  firstRecurrenceAnchorDate = firstRecurrenceAnchorDate,
)

/** 本地 Schedule remoteSnapshot 到 wire Current；保留服务端元数据与首个重复锚点。 */
internal fun ScheduleRemoteSnapshot.toWire(): ScheduleCurrent = ScheduleCurrent(
  resource = resource.toWire(),
  meta = meta.toWire(),
  firstRecurrenceAnchorDate = firstRecurrenceAnchorDate,
)

/** wire canonical OccurrenceOverride 到本地四原子 remoteSnapshot 的无损映射。 */
internal fun OccurrenceOverrideCurrent.toDomain(): OccurrenceOverrideRemoteSnapshot =
  OccurrenceOverrideRemoteSnapshot(
    resource = resource.toDomain(),
    meta = meta.toDomain(),
  )

/** 本地 OccurrenceOverride remoteSnapshot 到 wire canonical Current 的无损映射。 */
internal fun OccurrenceOverrideRemoteSnapshot.toWire(): OccurrenceOverrideCurrent =
  OccurrenceOverrideCurrent(
    resource = resource.toWire(),
    meta = meta.toWire(),
  )

private fun DomainTimingInput.toWire(): WireTimingInput = WireTimingInput(
  kind = when (kind) {
    DomainTimingKind.TIMED -> WireTimingKind.TIMED
    DomainTimingKind.DEADLINE -> WireTimingKind.DEADLINE
    DomainTimingKind.ALL_DAY -> WireTimingKind.ALL_DAY
    DomainTimingKind.UNSCHEDULED -> WireTimingKind.UNSCHEDULED
  },
  startAt = startAt,
  endAt = endAt,
  dueAt = dueAt,
)

private fun WireTimingInput.toDomain(): DomainTimingInput = DomainTimingInput(
  kind = when (kind) {
    WireTimingKind.TIMED -> DomainTimingKind.TIMED
    WireTimingKind.DEADLINE -> DomainTimingKind.DEADLINE
    WireTimingKind.ALL_DAY -> DomainTimingKind.ALL_DAY
    WireTimingKind.UNSCHEDULED -> DomainTimingKind.UNSCHEDULED
  },
  startAt = startAt,
  endAt = endAt,
  dueAt = dueAt,
)

private fun DomainRecurrenceInput.toWire(): WireRecurrenceInput = WireRecurrenceInput(
  frequency = when (frequency) {
    DomainRecurrenceFrequency.DAILY -> WireRecurrenceFrequency.DAILY
    DomainRecurrenceFrequency.WEEKLY -> WireRecurrenceFrequency.WEEKLY
  },
  interval = interval,
  anchorDate = anchorDate,
  count = count,
  untilDate = untilDate,
  weekdays = weekdays.map { it.toWire() }.sortedBy { it.ordinal },
)

private fun WireRecurrenceInput.toDomain(): DomainRecurrenceInput = DomainRecurrenceInput(
  frequency = when (frequency) {
    WireRecurrenceFrequency.DAILY -> DomainRecurrenceFrequency.DAILY
    WireRecurrenceFrequency.WEEKLY -> DomainRecurrenceFrequency.WEEKLY
  },
  interval = interval,
  anchorDate = anchorDate,
  count = count,
  untilDate = untilDate,
  weekdays = weekdays.map { it.toDomain() }.toSet(),
)

private fun DomainReminderInput.toWire(): WireReminderInput =
  WireReminderInput(minutesBefore = minutesBefore, message = message)

private fun WireReminderInput.toDomain(): DomainReminderInput =
  DomainReminderInput(minutesBefore = minutesBefore, message = message)

private fun DomainCompletionStatus.toWire(): WireCompletionStatus = when (this) {
  DomainCompletionStatus.OPEN -> WireCompletionStatus.OPEN
  DomainCompletionStatus.COMPLETED -> WireCompletionStatus.COMPLETED
}

private fun WireCompletionStatus.toDomain(): DomainCompletionStatus = when (this) {
  WireCompletionStatus.OPEN -> DomainCompletionStatus.OPEN
  WireCompletionStatus.COMPLETED -> DomainCompletionStatus.COMPLETED
}

private fun DomainOccurrenceStatus.toWire(): WireOccurrenceStatus = when (this) {
  DomainOccurrenceStatus.ACTIVE -> WireOccurrenceStatus.ACTIVE
  DomainOccurrenceStatus.COMPLETED -> WireOccurrenceStatus.COMPLETED
  DomainOccurrenceStatus.CANCELLED -> WireOccurrenceStatus.CANCELLED
}

private fun WireOccurrenceStatus.toDomain(): DomainOccurrenceStatus = when (this) {
  WireOccurrenceStatus.ACTIVE -> DomainOccurrenceStatus.ACTIVE
  WireOccurrenceStatus.COMPLETED -> DomainOccurrenceStatus.COMPLETED
  WireOccurrenceStatus.CANCELLED -> DomainOccurrenceStatus.CANCELLED
}

private fun DomainWeekday.toWire(): WireWeekday = when (this) {
  DomainWeekday.MO -> WireWeekday.MO
  DomainWeekday.TU -> WireWeekday.TU
  DomainWeekday.WE -> WireWeekday.WE
  DomainWeekday.TH -> WireWeekday.TH
  DomainWeekday.FR -> WireWeekday.FR
  DomainWeekday.SA -> WireWeekday.SA
  DomainWeekday.SU -> WireWeekday.SU
}

private fun WireWeekday.toDomain(): DomainWeekday = when (this) {
  WireWeekday.MO -> DomainWeekday.MO
  WireWeekday.TU -> DomainWeekday.TU
  WireWeekday.WE -> DomainWeekday.WE
  WireWeekday.TH -> DomainWeekday.TH
  WireWeekday.FR -> DomainWeekday.FR
  WireWeekday.SA -> DomainWeekday.SA
  WireWeekday.SU -> DomainWeekday.SU
}

private fun DomainFieldPatch<String>.toWireStringPatch(): WireFieldPatch<String> = when (this) {
  DomainFieldPatch.Inherit -> WireFieldPatch(PatchMode.INHERIT)
  DomainFieldPatch.Clear -> WireFieldPatch(PatchMode.CLEAR)
  is DomainFieldPatch.Replace -> WireFieldPatch(PatchMode.REPLACE, value)
}

private fun DomainFieldPatch<List<DomainReminderInput>>.toWireReminderPatch():
  WireFieldPatch<List<WireReminderInput>> = when (this) {
  DomainFieldPatch.Inherit -> WireFieldPatch(PatchMode.INHERIT)
  DomainFieldPatch.Clear -> WireFieldPatch(PatchMode.CLEAR)
  is DomainFieldPatch.Replace -> WireFieldPatch(PatchMode.REPLACE, value.map { it.toWire() })
}

private fun WireFieldPatch<String>.toDomainStringPatch(): DomainFieldPatch<String> = when (mode) {
  PatchMode.INHERIT -> DomainFieldPatch.Inherit
  PatchMode.CLEAR -> DomainFieldPatch.Clear
  PatchMode.REPLACE -> DomainFieldPatch.Replace(requireNotNull(value) { "REPLACE title/description requires value" })
}

private fun WireFieldPatch<List<WireReminderInput>>.toDomainReminderPatch():
  DomainFieldPatch<List<DomainReminderInput>> = when (mode) {
  PatchMode.INHERIT -> DomainFieldPatch.Inherit
  PatchMode.CLEAR -> DomainFieldPatch.Clear
  PatchMode.REPLACE -> DomainFieldPatch.Replace(
    requireNotNull(value) { "REPLACE reminders requires value" }.map { it.toDomain() },
  )
}

private fun WireServerResourceMeta.toDomain(): DomainServerResourceMeta =
  DomainServerResourceMeta(createdAt = createdAt, remoteModifiedAt = remoteModifiedAt)

private fun DomainServerResourceMeta.toWire(): WireServerResourceMeta =
  WireServerResourceMeta(createdAt = createdAt, remoteModifiedAt = remoteModifiedAt)

private fun ULong.toDomainVersion(): Long {
  require(this <= Long.MAX_VALUE.toULong()) { "wire version exceeds client Long range" }
  return toLong()
}
