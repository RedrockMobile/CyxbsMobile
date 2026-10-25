package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.data.remote.AtomicField as WireAtomicField
import com.cyxbs.pages.schedule.data.remote.CategoryInput
import com.cyxbs.pages.schedule.data.remote.FieldPatch as WireFieldPatch
import com.cyxbs.pages.schedule.data.remote.OccurrenceAdjustmentInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceStatus as WireOccurrenceStatus
import com.cyxbs.pages.schedule.data.remote.OccurrenceTimeInput as WireOccurrenceTimeInput
import com.cyxbs.pages.schedule.data.remote.OccurrenceTimeKind as WireOccurrenceTimeKind
import com.cyxbs.pages.schedule.data.remote.PatchMode
import com.cyxbs.pages.schedule.data.remote.RecurrenceFrequency as WireRecurrenceFrequency
import com.cyxbs.pages.schedule.data.remote.RecurrenceInput as WireRecurrenceInput
import com.cyxbs.pages.schedule.data.remote.ReminderInput as WireReminderInput
import com.cyxbs.pages.schedule.data.remote.ScheduleInput
import com.cyxbs.pages.schedule.data.remote.ScheduleKind as WireScheduleKind
import com.cyxbs.pages.schedule.data.remote.TimingInput as WireTimingInput
import com.cyxbs.pages.schedule.data.remote.TimingKind as WireTimingKind
import com.cyxbs.pages.schedule.data.remote.TodoState as WireTodoState
import com.cyxbs.pages.schedule.data.remote.Weekday as WireWeekday
import com.cyxbs.pages.schedule.domain.sync.AtomicField as DomainAtomicField
import com.cyxbs.pages.schedule.domain.sync.CategoryIdentity
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.FieldPatch as DomainFieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentIdentity
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus as DomainOccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeInput as DomainOccurrenceTimeInput
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeKind as DomainOccurrenceTimeKind
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency as DomainRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput as DomainRecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.ReminderInput as DomainReminderInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleIdentity
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind as DomainScheduleKind
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.TimingInput as DomainTimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind as DomainTimingKind
import com.cyxbs.pages.schedule.domain.sync.TodoState as DomainTodoState
import com.cyxbs.pages.schedule.domain.sync.Weekday as DomainWeekday

/** 将分类本地快照映射为创建或更新输入；localId 只在首次创建时上传。 */
internal fun CategoryResource.toWire(): CategoryInput = CategoryInput(
  localId = identity.id.takeIf { remoteId == null },
  id = remoteId,
  version = version.toULong(),
  name = name.toWire(),
  color = color.toWire(),
  sortOrder = sortOrder.toWire(),
)

/** 将服务端分类映射到调用方指定的本地 UUID；响应中的分类必须已有远端 ID。 */
internal fun CategoryInput.toDomain(identity: CategoryIdentity): CategoryResource = CategoryResource(
  identity = identity,
  remoteId = requireNotNull(id) { "remote category must carry id" }.also {
    require(localId == null) { "remote category must not echo localId" }
  },
  version = version.toDomainVersion(),
  name = name.toDomain(),
  color = color.toDomain(),
  sortOrder = sortOrder.toDomain(),
)

/**
 * 将 Room 中的分类快照恢复为本地资源。
 *
 * 待创建快照只有 [CategoryInput.localId]，已上传快照只有远端 [CategoryInput.id]；两者都必须与 Room 行保存的
 * [identity] 对应，避免损坏的 JSON 被映射到其他本地分类。
 */
internal fun CategoryInput.toLocalDomain(identity: CategoryIdentity): CategoryResource {
  require(
    (id == null && localId == identity.id && version == 0uL) ||
        (id != null && localId == null && version > 0uL),
  ) { "local category id/version shape is invalid" }
  return CategoryResource(
    identity = identity,
    remoteId = id,
    version = version.toDomainVersion(),
    name = name.toDomain(),
    color = color.toDomain(),
    sortOrder = sortOrder.toDomain(),
  )
}

/**
 * 将日程映射为 wire 快照。
 *
 * 普通日程 ID 使用 UUID v7，旧数据迁移使用确定性的 UUID v5；分类若已有远端 ID 就上传 categoryId，
 * 否则使用同请求 categoryLocalId。
 */
internal fun ScheduleResource.toWire(categoryByLocalId: (String) -> CategoryResource?): ScheduleInput {
  val category = categoryId.data?.let { localId ->
    requireNotNull(categoryByLocalId(localId)) {
      "schedule references a missing local category"
    }
  }
  return ScheduleInput(
    id = identity.id,
    version = version.toULong(),
    kind = kind.toWire(),
    title = title.toWire(),
    description = description.toWire(),
    categoryId = WireAtomicField(category?.remoteId, categoryId.modifiedAt),
    categoryLocalId = category?.identity?.id?.takeIf { category.remoteId == null },
    timing = WireAtomicField(timing.data.toWire(), timing.modifiedAt),
    recurrence = WireAtomicField(recurrence.data?.toWire(), recurrence.modifiedAt),
    reminder = WireAtomicField(reminder.data?.toWire(), reminder.modifiedAt),
    todoState = WireAtomicField(todoState.data?.toWire(), todoState.modifiedAt),
    linkedToCourse = linkedToCourse.toWire(),
  )
}

/** 将服务端日程映射为本地快照；数字分类 ID 必须先由分类同步结果解析成本地 UUID。 */
internal fun ScheduleInput.toDomain(categoryLocalId: (Long) -> String?): ScheduleResource {
  require(this.categoryLocalId == null) { "remote schedule must not echo categoryLocalId" }
  return ScheduleResource(
    identity = ScheduleIdentity(id),
    version = version.toDomainVersion(),
    kind = kind.toDomain(),
    title = title.toDomain(),
    description = description.toDomain(),
    categoryId = DomainAtomicField(
      categoryId.data?.let { remoteId ->
        requireNotNull(categoryLocalId(remoteId)) { "remote schedule references an unknown category" }
      },
      categoryId.modifiedAt,
    ),
    timing = DomainAtomicField(timing.data.toDomain(), timing.modifiedAt),
    recurrence = DomainAtomicField(recurrence.data?.toDomain(), recurrence.modifiedAt),
    reminder = DomainAtomicField(reminder.data?.toDomain(), reminder.modifiedAt),
    todoState = DomainAtomicField(todoState.data?.toDomain(), todoState.modifiedAt),
    linkedToCourse = linkedToCourse.toDomain(),
  )
}

/**
 * 将 Room 中的日程快照恢复为本地资源。
 *
 * 新分类尚无远端 ID 时读取 [ScheduleInput.categoryLocalId]；其余情况把数字分类 ID 解析回客户端 UUID。
 */
internal fun ScheduleInput.toLocalDomain(categoryByRemoteId: (Long) -> String?): ScheduleResource {
  require(categoryLocalId == null || categoryId.data == null) {
    "schedule cannot carry categoryId and categoryLocalId together"
  }
  return ScheduleResource(
    identity = ScheduleIdentity(id),
    version = version.toDomainVersion(),
    kind = kind.toDomain(),
    title = title.toDomain(),
    description = description.toDomain(),
    categoryId = DomainAtomicField(
      categoryLocalId ?: categoryId.data?.let { remoteId ->
        requireNotNull(categoryByRemoteId(remoteId)) { "schedule references an unknown category" }
      },
      categoryId.modifiedAt,
    ),
    timing = DomainAtomicField(timing.data.toDomain(), timing.modifiedAt),
    recurrence = DomainAtomicField(recurrence.data?.toDomain(), recurrence.modifiedAt),
    reminder = DomainAtomicField(reminder.data?.toDomain(), reminder.modifiedAt),
    todoState = DomainAtomicField(todoState.data?.toDomain(), todoState.modifiedAt),
    linkedToCourse = linkedToCourse.toDomain(),
  )
}

/** 将单次调整映射为 wire；分类替换同样支持引用本轮新建分类。 */
internal fun OccurrenceAdjustmentResource.toWire(
  categoryByLocalId: (String) -> CategoryResource?,
): OccurrenceAdjustmentInput {
  val categoryReference = categoryId.data.resolveCategoryReference(categoryByLocalId)
  return OccurrenceAdjustmentInput(
    localId = identity.localId.takeIf { remoteId == null },
    id = remoteId,
    scheduleId = identity.scheduleId,
    originalOccurrenceDate = identity.originalOccurrenceDate,
    version = version.toULong(),
    status = WireAtomicField(status.data.toWire(), status.modifiedAt),
    date = WireAtomicField(date.data.toWireDatePatch(), date.modifiedAt),
    time = WireAtomicField(time.data.toWireTimePatch(), time.modifiedAt),
    title = WireAtomicField(title.data.toWireStringPatch(), title.modifiedAt),
    description = WireAtomicField(description.data.toWireStringPatch(), description.modifiedAt),
    categoryId = WireAtomicField(categoryReference.patch, categoryId.modifiedAt),
    categoryLocalId = categoryReference.localId,
    reminder = WireAtomicField(reminder.data.toWireReminderPatch(), reminder.modifiedAt),
  )
}

/** 将服务端单次调整映射到调用方指定的本地 UUID。 */
internal fun OccurrenceAdjustmentInput.toDomain(
  identity: OccurrenceAdjustmentIdentity,
  categoryLocalId: (Long) -> String?,
): OccurrenceAdjustmentResource {
  require(localId == null) { "remote occurrence adjustment must not echo localId" }
  require(this.categoryLocalId == null) { "remote occurrence adjustment must not echo categoryLocalId" }
  return OccurrenceAdjustmentResource(
    identity = identity,
    remoteId = requireNotNull(id) { "remote occurrence adjustment must carry id" },
    version = version.toDomainVersion(),
    status = DomainAtomicField(status.data.toDomain(), status.modifiedAt),
    date = DomainAtomicField(date.data.toDomainDatePatch(), date.modifiedAt),
    time = DomainAtomicField(time.data.toDomainTimePatch(), time.modifiedAt),
    title = DomainAtomicField(title.data.toDomainStringPatch(), title.modifiedAt),
    description = DomainAtomicField(description.data.toDomainStringPatch(), description.modifiedAt),
    categoryId = DomainAtomicField(categoryId.data.toDomainCategoryPatch(categoryLocalId), categoryId.modifiedAt),
    reminder = DomainAtomicField(reminder.data.toDomainReminderPatch(), reminder.modifiedAt),
  )
}

/**
 * 将 Room 中的单次调整快照恢复为本地资源。
 *
 * 待创建调整使用客户端 UUID，已确认调整使用服务端数字 ID；分类引用规则与日程快照一致。
 */
internal fun OccurrenceAdjustmentInput.toLocalDomain(
  identity: OccurrenceAdjustmentIdentity,
  categoryByRemoteId: (Long) -> String?,
): OccurrenceAdjustmentResource {
  require(scheduleId == identity.scheduleId && originalOccurrenceDate == identity.originalOccurrenceDate) {
    "occurrence adjustment logical slot mismatch"
  }
  require(
    (id == null && localId == identity.localId && version == 0uL) ||
        (id != null && localId == null && version > 0uL),
  ) { "local occurrence adjustment id/version shape is invalid" }
  require(categoryLocalId == null || categoryId.data.value == null) {
    "occurrence adjustment cannot carry categoryId and categoryLocalId together"
  }
  val localCategoryPatch = if (categoryLocalId != null) {
    require(categoryId.data.mode == PatchMode.REPLACE) {
      "categoryLocalId requires a REPLACE category patch"
    }
    DomainFieldPatch.Replace(categoryLocalId)
  } else {
    categoryId.data.toDomainCategoryPatch(categoryByRemoteId)
  }
  return OccurrenceAdjustmentResource(
    identity = identity,
    remoteId = id,
    version = version.toDomainVersion(),
    status = DomainAtomicField(status.data.toDomain(), status.modifiedAt),
    date = DomainAtomicField(date.data.toDomainDatePatch(), date.modifiedAt),
    time = DomainAtomicField(time.data.toDomainTimePatch(), time.modifiedAt),
    title = DomainAtomicField(title.data.toDomainStringPatch(), title.modifiedAt),
    description = DomainAtomicField(description.data.toDomainStringPatch(), description.modifiedAt),
    categoryId = DomainAtomicField(localCategoryPatch, categoryId.modifiedAt),
    reminder = DomainAtomicField(reminder.data.toDomainReminderPatch(), reminder.modifiedAt),
  )
}

private data class WireCategoryReference(
  val patch: WireFieldPatch<Long>,
  val localId: String?,
)

/** 将领域分类补丁拆成服务端数字 ID 或同请求本地引用。 */
private fun DomainFieldPatch<String>.resolveCategoryReference(
  categoryByLocalId: (String) -> CategoryResource?,
): WireCategoryReference = when (this) {
  DomainFieldPatch.Inherit -> WireCategoryReference(WireFieldPatch(PatchMode.INHERIT), null)
  DomainFieldPatch.Clear -> WireCategoryReference(WireFieldPatch(PatchMode.CLEAR), null)
  is DomainFieldPatch.Replace -> {
    val category = requireNotNull(categoryByLocalId(value)) {
      "occurrence adjustment references a missing local category"
    }
    if (category.remoteId != null) {
      WireCategoryReference(WireFieldPatch(PatchMode.REPLACE, category.remoteId), null)
    } else {
      WireCategoryReference(WireFieldPatch(PatchMode.REPLACE), category.identity.id)
    }
  }
}

private fun <T> DomainAtomicField<T>.toWire(): WireAtomicField<T> = WireAtomicField(data, modifiedAt)
private fun <T> WireAtomicField<T>.toDomain(): DomainAtomicField<T> = DomainAtomicField(data, modifiedAt)

private fun DomainTimingInput.toWire() = WireTimingInput(
  kind = WireTimingKind.valueOf(kind.name), startAt = startAt, endAt = endAt, dueAt = dueAt, date = date,
)
private fun WireTimingInput.toDomain() = DomainTimingInput(
  kind = DomainTimingKind.valueOf(kind.name), startAt = startAt, endAt = endAt, dueAt = dueAt, date = date,
)
private fun DomainOccurrenceTimeInput.toWire() = WireOccurrenceTimeInput(
  kind = WireOccurrenceTimeKind.valueOf(kind.name),
  startMinuteOfDay = startMinuteOfDay,
  durationMinutes = durationMinutes,
  minuteOfDay = minuteOfDay,
)
private fun WireOccurrenceTimeInput.toDomain() = DomainOccurrenceTimeInput(
  kind = DomainOccurrenceTimeKind.valueOf(kind.name),
  startMinuteOfDay = startMinuteOfDay,
  durationMinutes = durationMinutes,
  minuteOfDay = minuteOfDay,
)
private fun DomainRecurrenceInput.toWire() = WireRecurrenceInput(
  frequency = WireRecurrenceFrequency.valueOf(frequency.name),
  interval = interval,
  anchorDate = anchorDate,
  count = count,
  untilDate = untilDate,
  weekdays = weekdays.map { WireWeekday.valueOf(it.name) }.sortedBy { it.ordinal },
  monthDays = monthDays.sorted(),
  months = months.sorted(),
)
private fun WireRecurrenceInput.toDomain() = DomainRecurrenceInput(
  frequency = DomainRecurrenceFrequency.valueOf(frequency.name),
  interval = interval,
  anchorDate = anchorDate,
  count = count,
  untilDate = untilDate,
  weekdays = weekdays.map { DomainWeekday.valueOf(it.name) }.toSet(),
  monthDays = monthDays.toSet(),
  months = months.toSet(),
)
private fun DomainReminderInput.toWire() = WireReminderInput(minutesBefore)
private fun WireReminderInput.toDomain() = DomainReminderInput(minutesBefore)
private fun DomainTodoState.toWire() = WireTodoState.valueOf(name)
private fun WireTodoState.toDomain() = DomainTodoState.valueOf(name)
private fun DomainScheduleKind.toWire() = WireScheduleKind.valueOf(name)
private fun WireScheduleKind.toDomain() = DomainScheduleKind.valueOf(name)
private fun DomainOccurrenceStatus.toWire() = WireOccurrenceStatus.valueOf(name)
private fun WireOccurrenceStatus.toDomain() = DomainOccurrenceStatus.valueOf(name)

private fun DomainFieldPatch<String>.toWireStringPatch(): WireFieldPatch<String> = when (this) {
  DomainFieldPatch.Inherit -> WireFieldPatch(PatchMode.INHERIT)
  DomainFieldPatch.Clear -> WireFieldPatch(PatchMode.CLEAR)
  is DomainFieldPatch.Replace -> WireFieldPatch(PatchMode.REPLACE, value)
}
private fun DomainFieldPatch<Long>.toWireDatePatch(): WireFieldPatch<Long> = when (this) {
  DomainFieldPatch.Inherit -> WireFieldPatch(PatchMode.INHERIT)
  DomainFieldPatch.Clear -> WireFieldPatch(PatchMode.CLEAR)
  is DomainFieldPatch.Replace -> WireFieldPatch(PatchMode.REPLACE, value)
}
private fun DomainFieldPatch<DomainOccurrenceTimeInput>.toWireTimePatch(): WireFieldPatch<WireOccurrenceTimeInput> =
  when (this) {
    DomainFieldPatch.Inherit -> WireFieldPatch(PatchMode.INHERIT)
    DomainFieldPatch.Clear -> WireFieldPatch(PatchMode.CLEAR)
    is DomainFieldPatch.Replace -> WireFieldPatch(PatchMode.REPLACE, value.toWire())
  }
private fun DomainFieldPatch<DomainReminderInput>.toWireReminderPatch(): WireFieldPatch<WireReminderInput> =
  when (this) {
    DomainFieldPatch.Inherit -> WireFieldPatch(PatchMode.INHERIT)
    DomainFieldPatch.Clear -> WireFieldPatch(PatchMode.CLEAR)
    is DomainFieldPatch.Replace -> WireFieldPatch(PatchMode.REPLACE, value.toWire())
  }

private fun WireFieldPatch<String>.toDomainStringPatch(): DomainFieldPatch<String> = when (mode) {
  PatchMode.INHERIT -> DomainFieldPatch.Inherit
  PatchMode.CLEAR -> DomainFieldPatch.Clear
  PatchMode.REPLACE -> DomainFieldPatch.Replace(requireNotNull(value) { "REPLACE string requires value" })
}
private fun WireFieldPatch<Long>.toDomainDatePatch(): DomainFieldPatch<Long> = when (mode) {
  PatchMode.INHERIT -> DomainFieldPatch.Inherit
  PatchMode.CLEAR -> DomainFieldPatch.Clear
  PatchMode.REPLACE -> DomainFieldPatch.Replace(requireNotNull(value) { "REPLACE date requires value" })
}
private fun WireFieldPatch<WireOccurrenceTimeInput>.toDomainTimePatch(): DomainFieldPatch<DomainOccurrenceTimeInput> =
  when (mode) {
    PatchMode.INHERIT -> DomainFieldPatch.Inherit
    PatchMode.CLEAR -> DomainFieldPatch.Clear
    PatchMode.REPLACE -> DomainFieldPatch.Replace(requireNotNull(value).toDomain())
  }
private fun WireFieldPatch<WireReminderInput>.toDomainReminderPatch(): DomainFieldPatch<DomainReminderInput> =
  when (mode) {
    PatchMode.INHERIT -> DomainFieldPatch.Inherit
    PatchMode.CLEAR -> DomainFieldPatch.Clear
    PatchMode.REPLACE -> DomainFieldPatch.Replace(requireNotNull(value).toDomain())
  }
private fun WireFieldPatch<Long>.toDomainCategoryPatch(
  categoryLocalId: (Long) -> String?,
): DomainFieldPatch<String> = when (mode) {
  PatchMode.INHERIT -> DomainFieldPatch.Inherit
  PatchMode.CLEAR -> DomainFieldPatch.Clear
  PatchMode.REPLACE -> DomainFieldPatch.Replace(
    requireNotNull(categoryLocalId(requireNotNull(value))) { "remote category is missing locally" },
  )
}

private fun ULong.toDomainVersion(): Long {
  require(this <= Long.MAX_VALUE.toULong()) { "wire version exceeds client Long range" }
  return toLong()
}
