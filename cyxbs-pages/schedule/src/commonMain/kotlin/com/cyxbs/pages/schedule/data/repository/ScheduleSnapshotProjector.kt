package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toDate
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.schedule.data.remote.ScheduleInput
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceTime
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.sync.CategoryResource
import com.cyxbs.pages.schedule.domain.sync.CategorySyncState
import com.cyxbs.pages.schedule.domain.sync.FieldPatch
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentResource
import com.cyxbs.pages.schedule.domain.sync.OccurrenceAdjustmentSyncState
import com.cyxbs.pages.schedule.domain.sync.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeInput
import com.cyxbs.pages.schedule.domain.sync.OccurrenceTimeKind
import com.cyxbs.pages.schedule.domain.sync.PendingDelete
import com.cyxbs.pages.schedule.domain.sync.PendingUpsert
import com.cyxbs.pages.schedule.domain.sync.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.sync.RecurrenceInput
import com.cyxbs.pages.schedule.domain.sync.ReminderInput
import com.cyxbs.pages.schedule.domain.sync.ScheduleKind
import com.cyxbs.pages.schedule.domain.sync.ScheduleRemoteSnapshot
import com.cyxbs.pages.schedule.domain.sync.ScheduleResource
import com.cyxbs.pages.schedule.domain.sync.ScheduleSyncState
import com.cyxbs.pages.schedule.domain.sync.TimingInput
import com.cyxbs.pages.schedule.domain.sync.TimingKind
import com.cyxbs.pages.schedule.domain.sync.TodoState
import com.cyxbs.pages.schedule.domain.sync.Weekday
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.cyxbs.pages.schedule.domain.model.FieldPatch as UiFieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus as UiOccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency as UiRecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.ScheduleKind as UiScheduleKind

private const val UTC_DAY_MILLIS = 86_400_000L
private const val MINUTE_MILLIS = 60_000L

/** common typed 状态投影为旧 UI 快照的受控结果。 */
sealed interface ScheduleSnapshotProjection {
  /** 全部资源均满足最终领域合同时返回的完整快照。 */
  data class Success(val snapshot: ScheduleSnapshot) : ScheduleSnapshotProjection

  /** 任一资源无法无损表达时整次失败；调用方不得发布部分列表。 */
  data class Failure(val message: String) : ScheduleSnapshotProjection
}

/**
 * 将三类 Schedule 双快照状态纯投影为 UI 使用的 [ScheduleSnapshot]。
 *
 * pending UPSERT 由 effective 规则覆盖 remote，pending DELETE 不可见。绝对毫秒没有携带原始时区，
 * 因此 Timed/Deadline 必须使用调用方传入的 [timeZone] 恢复墙上时间；本类不访问 Room 或网络。
 * recurrence.anchorDate 是稳定的 occurrence 日期轴；整个系列移动实际日期时只修改 timing，
 * 仍以该锚点生成 occurrence identity。
 * 服务端只保存当前唯一提醒的提前分钟数，不为单个本地实现字段引入 sidecar。
 */
class ScheduleSnapshotProjector {
  /**
   * 原子地投影当前账号的三类 typed 状态。
   *
   * @param accountId 发布快照所属账号，必须非空。
   * @param timeZone 恢复 Timed/Deadline 墙上时间所用的明确时区。
   * @return 成功时返回完整快照；任何坏行、重复 identity 或缺失 parent 均返回 [ScheduleSnapshotProjection.Failure]。
   */
  fun project(
    accountId: String,
    timeZone: TimeZone,
    categories: List<CategorySyncState>,
    schedules: List<ScheduleSyncState>,
    occurrenceAdjustments: List<OccurrenceAdjustmentSyncState>,
  ): ScheduleSnapshotProjection = try {
    requireProjection(accountId.isNotBlank(), "accountId must not be blank")
    requireUnique(categories.map { it.identity }, "Category")
    requireUnique(schedules.map { it.identity }, "Schedule")
    requireUnique(occurrenceAdjustments.map { it.identity }, "OccurrenceAdjustment")

    val visibleSchedules = schedules.mapNotNull { state ->
      state.effectiveResource()?.let { state.toUi(it, timeZone) }
    }
    val scheduleStatesById = schedules.associateBy { it.identity.id }
    val schedulesById = visibleSchedules.associateBy { it.id.value }
    val visibleAdjustments = occurrenceAdjustments.mapNotNull { state ->
      state.effectiveResource()?.let { resource ->
        val parentState = scheduleStatesById[resource.identity.scheduleId]
          ?: abortProjection("OccurrenceAdjustment parent Schedule is missing")
        // parent pending DELETE 时 UI 同时隐藏 parent 与 child；这不是坏行，也不能发布孤立单次调整。
        if (parentState.effectiveResource() == null) return@mapNotNull null
        val parent = schedulesById[resource.identity.scheduleId]
          ?: abortProjection("OccurrenceAdjustment effective parent was not projected")
        val recurrenceDate = resource.identity.originalOccurrenceDate.toUtcDate()
        // 规则暂时不再生成该原始日期时只休眠，不删除 Room/远端资源；规则改回来后会重新参与投影。
        if (parent.recurrence == null || !RecurrenceEngine.containsRecurrenceDate(parent, recurrenceDate)) {
          return@mapNotNull null
        }
        state.toUi(resource, parent, timeZone)
      }
    }
    val pendingCount = categories.count { it.pending != null } +
      schedules.count { it.pending != null } +
      occurrenceAdjustments.count { it.pending != null }
    val hasDeletes = categories.any { it.pending is PendingDelete } ||
      schedules.any { it.pending is PendingDelete } ||
      occurrenceAdjustments.any { it.pending is PendingDelete }

    ScheduleSnapshotProjection.Success(
      ScheduleSnapshot(
        schedules = visibleSchedules,
        occurrenceAdjustments = visibleAdjustments,
        categories = categories.mapNotNull { state ->
          state.effectiveResource()?.let { it.toUi() }
        },
        status = ScheduleRepositoryStatus.Ready(pendingCount, hasDeletes),
        accountId = accountId,
      ),
    )
  } catch (failure: ProjectionAbort) {
    ScheduleSnapshotProjection.Failure(failure.message ?: "Schedule projection failed")
  } catch (failure: IllegalArgumentException) {
    ScheduleSnapshotProjection.Failure(failure.message ?: "Schedule projection is invalid")
  }

  /**
   * 将失败记录中的单条完整请求源恢复成编辑器使用的 [Schedule]。
   *
   * version=0 按本地 CREATE pending 投影；正版本按最小 canonical 快照投影。两条路径最终都复用 [project]，
   * 因而时间、重复规则、提醒和完成态不会与正常清单产生第二套转换语义。
   */
  internal fun projectFailureSource(
    input: ScheduleInput,
    timeZone: TimeZone,
  ): Schedule? {
    val resource = input.toDomain { remoteId -> "remote-category:$remoteId" }
    val state = if (resource.version == 0L) {
      ScheduleSyncState(
        identity = resource.identity,
        remoteSnapshot = null,
        pending = PendingUpsert(resource, localRevision = 1),
      )
    } else {
      ScheduleSyncState(
        identity = resource.identity,
        remoteSnapshot = ScheduleRemoteSnapshot(resource),
      )
    }
    return (project(
      accountId = "failure-record",
      timeZone = timeZone,
      categories = emptyList(),
      schedules = listOf(state),
      occurrenceAdjustments = emptyList(),
    ) as? ScheduleSnapshotProjection.Success)?.snapshot?.schedules?.singleOrNull()
  }

  /**
   * 把失败请求中的单次调整日期槽恢复成领域 recurrence identity。
   *
   * 日期槽本身没有原始时分、时区与全天语义，必须与父 [input] 一起解析；失败页据此定位具体实例，
   * 不能把一次单次调整错误地打开为整个父系列。
   */
  internal fun projectFailureRecurrenceId(
    input: ScheduleInput,
    originalOccurrenceDate: Long,
    timeZone: TimeZone,
  ): RecurrenceId? = projectFailureSource(input, timeZone)?.toRecurrenceId(originalOccurrenceDate)

  private fun CategoryResource.toUi(): ScheduleCategory = ScheduleCategory(
    id = CategoryId(identity.id),
    revision = version,
    name = name.data,
    // Category color 的 nullable AtomicField 已直接表达“未设置”，不把空串擅自改写为 null。
    color = color.data,
    sortOrder = sortOrder.data.toIntExact("Category sortOrder"),
  )

  private fun ScheduleSyncState.toUi(
    resource: ScheduleResource,
    timeZone: TimeZone,
  ): Schedule {
    val atomTimes = listOf(
      resource.title.modifiedAt,
      resource.description.modifiedAt,
      resource.categoryId.modifiedAt,
      resource.timing.modifiedAt,
      resource.recurrence.modifiedAt,
      resource.reminder.modifiedAt,
      resource.todoState.modifiedAt,
      resource.linkedToCourse.modifiedAt,
    )
    val (createdAt, updatedAt) = timestamps(atomTimes)
    val timing = resource.timing.data.toUi(timeZone)
    val schedule = Schedule(
      id = ScheduleId(resource.identity.id),
      revision = resource.version,
      title = resource.title.data,
      description = resource.description.data,
      categoryId = resource.categoryId.data?.let(::CategoryId),
      timing = timing,
      recurrence = resource.recurrence.data?.toUi(),
      reminder = resource.reminder.data?.toUiReminder(),
      todoState = resource.todoState.data?.toUi(),
      createdAt = createdAt,
      updatedAt = updatedAt,
      kind = resource.kind.toUi(),
      linkedToCourse = resource.linkedToCourse.data,
      recurrenceAnchorDate = resource.recurrence.data?.anchorDate?.toUtcDate(),
    )
    // 服务端快照属于不可信输入：wire 形状合法不代表完整领域组合合法，发布前必须统一闭合校验。
    val issues = ScheduleValidator.validate(schedule)
    requireProjection(
      issues.isEmpty(),
      issues.joinToString(prefix = "invalid Schedule: ") { "${it.field} ${it.message}" },
    )
    return schedule
  }

  /** wire 只保留绝对毫秒，恢复本地墙上时间时必须显式使用 repository 选择的时区。 */
  private fun TimingInput.toUi(timeZone: TimeZone): ScheduleTiming = when (kind) {
    TimingKind.TIMED -> {
      val start = startAt ?: abortProjection("TIMED startAt is required")
      val end = endAt ?: abortProjection("TIMED endAt is required")
      requireProjection(dueAt == null && date == null, "invalid TIMED extra fields")
      requireProjection(start >= 0 && end > start, "TIMED duration must be positive")
      requireProjection(
        start % MINUTE_MILLIS == 0L && end % MINUTE_MILLIS == 0L,
        "TIMED bounds must align to whole minutes",
      )
      val duration = end - start
      requireProjection(duration % MINUTE_MILLIS == 0L, "TIMED duration must be whole minutes")
      ScheduleTiming.Timed(
        start = Instant.fromEpochMilliseconds(start).toLocalDateTime(timeZone).toMinuteTimeDate(),
        durationMinutes = (duration / MINUTE_MILLIS).toIntExact("TIMED durationMinutes"),
        timeZoneId = timeZone.id,
      )
    }
    TimingKind.DEADLINE -> {
      val due = dueAt ?: abortProjection("DEADLINE dueAt is required")
      requireProjection(startAt == null && endAt == null && date == null, "invalid DEADLINE extra fields")
      requireProjection(due >= 0, "DEADLINE dueAt must not be negative")
      requireProjection(due % MINUTE_MILLIS == 0L, "DEADLINE dueAt must align to a whole minute")
      ScheduleTiming.Deadline(
        due = Instant.fromEpochMilliseconds(due).toLocalDateTime(timeZone).toMinuteTimeDate(),
        timeZoneId = timeZone.id,
      )
    }
    TimingKind.ALL_DAY -> {
      val allDayDate = date ?: abortProjection("ALL_DAY date is required")
      requireProjection(startAt == null && endAt == null && dueAt == null, "invalid ALL_DAY extra fields")
      requireDateSlot(allDayDate, "ALL_DAY date")
      ScheduleTiming.AllDay(date = allDayDate.toUtcDate())
    }
    TimingKind.UNSCHEDULED -> {
      requireProjection(startAt == null && endAt == null && dueAt == null && date == null, "invalid UNSCHEDULED fields")
      ScheduleTiming.Unscheduled
    }
  }

  private fun RecurrenceInput.toUi(): RecurrenceRule {
    requireDateSlot(anchorDate, "recurrence anchorDate")
    untilDate?.let { requireDateSlot(it, "recurrence untilDate") }
    // anchorDate 是稳定 occurrence 日期轴；当前 timing 允许相对它产生正负偏移，不能要求二者日期相同。
    requireProjection(untilDate == null || untilDate >= anchorDate, "recurrence untilDate precedes anchorDate")
    val uiWeekdays = weekdays.map { it.toUi() }.toSet()
    when (frequency) {
      RecurrenceFrequency.DAILY -> requireProjection(
        uiWeekdays.isEmpty() && monthDays.isEmpty() && months.isEmpty(),
        "DAILY must not carry recurrence selectors",
      )
      RecurrenceFrequency.WEEKLY -> requireProjection(
        uiWeekdays.isNotEmpty() && monthDays.isEmpty() && months.isEmpty(),
        "WEEKLY requires only weekdays",
      )
      RecurrenceFrequency.MONTHLY -> requireProjection(
        uiWeekdays.isEmpty() && monthDays.isNotEmpty() && months.isEmpty(),
        "MONTHLY requires only monthDays",
      )
      RecurrenceFrequency.YEARLY -> requireProjection(
        uiWeekdays.isEmpty() && monthDays.isNotEmpty() && months.isNotEmpty(),
        "YEARLY requires monthDays and months",
      )
    }
    return RecurrenceRule(
      frequency = when (frequency) {
        RecurrenceFrequency.DAILY -> UiRecurrenceFrequency.DAILY
        RecurrenceFrequency.WEEKLY -> UiRecurrenceFrequency.WEEKLY
        RecurrenceFrequency.MONTHLY -> UiRecurrenceFrequency.MONTHLY
        RecurrenceFrequency.YEARLY -> UiRecurrenceFrequency.YEARLY
      },
      interval = interval,
      byWeekDays = uiWeekdays,
      byMonthDays = monthDays.toSet(),
      byMonths = months.toSet(),
      end = when {
        count != null -> RecurrenceEnd.Count(count)
        untilDate != null -> RecurrenceEnd.Until(untilDate.toUtcDate())
        else -> RecurrenceEnd.Never
      },
    )
  }

  /** 把服务端当前唯一提醒投影为领域值。 */
  private fun ReminderInput.toUiReminder(): ScheduleReminder {
    requireProjection(minutesBefore >= 0, "reminder minutesBefore must not be negative")
    return ScheduleReminder(offsetMinutes = minutesBefore)
  }

  private fun OccurrenceAdjustmentSyncState.toUi(
    resource: OccurrenceAdjustmentResource,
    parent: Schedule,
    timeZone: TimeZone,
  ): ScheduleOccurrenceAdjustment {
    val atomTimes = listOf(
      resource.status.modifiedAt,
      resource.date.modifiedAt,
      resource.time.modifiedAt,
      resource.title.modifiedAt,
      resource.description.modifiedAt,
      resource.categoryId.modifiedAt,
      resource.reminder.modifiedAt,
    )
    val (createdAt, updatedAt) = timestamps(atomTimes)
    val recurrenceId = parent.toRecurrenceId(resource.identity.originalOccurrenceDate)
    return ScheduleOccurrenceAdjustment(
      scheduleId = parent.id,
      recurrenceId = recurrenceId,
      revision = resource.version,
      status = resource.status.data.toUi(),
      patch = OccurrencePatch(
        date = resource.date.data.toUiDatePatch(),
        time = resource.time.data.toUiTimePatch(timeZone),
        title = resource.title.data.toUiTitlePatch(),
        description = resource.description.data.toUiStringPatch(),
        categoryId = resource.categoryId.data.toUiCategoryPatch(),
        reminder = resource.reminder.data.toUiReminderPatch(),
      ),
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
  }

  /** 单次日期 REPLACE 必须是 UTC 午夜槽；原始槽仍使用 originalOccurrenceDate，不取该显示日期。 */
  private fun FieldPatch<Long>.toUiDatePatch(): UiFieldPatch<Date> = when (this) {
    FieldPatch.Inherit -> UiFieldPatch.Inherit
    FieldPatch.Clear -> abortProjection("OccurrenceAdjustment date cannot be Clear")
    is FieldPatch.Replace -> UiFieldPatch.Replace(value.toUtcDate())
  }

  /** 单次时间覆盖按当前账号明确时区恢复；值本身不携带日期。 */
  private fun FieldPatch<OccurrenceTimeInput>.toUiTimePatch(timeZone: TimeZone):
    UiFieldPatch<OccurrenceTime> = when (this) {
    FieldPatch.Inherit -> UiFieldPatch.Inherit
    FieldPatch.Clear -> abortProjection("OccurrenceAdjustment time cannot be Clear")
    is FieldPatch.Replace -> UiFieldPatch.Replace(value.toUiOccurrenceTime(timeZone))
  }

  /** 校验联合字段组合后转成领域时间形态。 */
  private fun OccurrenceTimeInput.toUiOccurrenceTime(timeZone: TimeZone): OccurrenceTime = when (kind) {
    OccurrenceTimeKind.TIME_RANGE -> {
      requireProjection(startMinuteOfDay in 0 until 24 * 60, "Occurrence time range start is invalid")
      requireProjection(durationMinutes != null && durationMinutes > 0, "Occurrence time range duration is invalid")
      requireProjection(minuteOfDay == null, "Occurrence time range contains point field")
      OccurrenceTime.TimeRange(
        requireNotNull(startMinuteOfDay),
        requireNotNull(durationMinutes),
        timeZone.id,
      )
    }
    OccurrenceTimeKind.TIME_POINT -> {
      requireProjection(minuteOfDay in 0 until 24 * 60, "Occurrence time point is invalid")
      requireProjection(startMinuteOfDay == null && durationMinutes == null, "Occurrence time point contains range fields")
      OccurrenceTime.TimePoint(requireNotNull(minuteOfDay), timeZone.id)
    }
    OccurrenceTimeKind.ALL_DAY -> {
      requireProjection(
        startMinuteOfDay == null && durationMinutes == null && minuteOfDay == null,
        "All-day occurrence time contains timed fields",
      )
      OccurrenceTime.AllDay
    }
  }

  private fun FieldPatch<String>.toUiCategoryPatch(): UiFieldPatch<CategoryId> = when (this) {
    FieldPatch.Inherit -> UiFieldPatch.Inherit
    FieldPatch.Clear -> UiFieldPatch.Clear
    is FieldPatch.Replace -> UiFieldPatch.Replace(CategoryId(value))
  }

  /** originalOccurrenceDate 提供原始日期槽，parent timing 提供稳定墙上时刻、时区与 allDay 语义。 */
  private fun Schedule.toRecurrenceId(originalOccurrenceDate: Long): RecurrenceId {
    requireDateSlot(originalOccurrenceDate, "OccurrenceAdjustment originalOccurrenceDate")
    val date = originalOccurrenceDate.toUtcDate()
    return when (val parentTiming = timing) {
      is ScheduleTiming.Timed -> RecurrenceId(
        MinuteTimeDate(date, parentTiming.start.time),
        parentTiming.timeZoneId,
        allDay = false,
      )
      is ScheduleTiming.Deadline -> RecurrenceId(
        MinuteTimeDate(date, parentTiming.due.time),
        parentTiming.timeZoneId,
        allDay = false,
      )
      is ScheduleTiming.AllDay -> RecurrenceId(
        MinuteTimeDate(date, 0, 0),
        timeZoneId = null,
        allDay = true,
      )
      ScheduleTiming.Unscheduled -> abortProjection("Unscheduled parent cannot restore RecurrenceId")
    }
  }

  private fun FieldPatch<String>.toUiTitlePatch(): UiFieldPatch<String> = when (this) {
    FieldPatch.Inherit -> UiFieldPatch.Inherit
    FieldPatch.Clear -> abortProjection("OccurrenceAdjustment title cannot be Clear")
    is FieldPatch.Replace -> UiFieldPatch.Replace(value)
  }

  private fun FieldPatch<String>.toUiStringPatch(): UiFieldPatch<String> = when (this) {
    FieldPatch.Inherit -> UiFieldPatch.Inherit
    FieldPatch.Clear -> UiFieldPatch.Clear
    is FieldPatch.Replace -> UiFieldPatch.Replace(value)
  }

  private fun FieldPatch<ReminderInput>.toUiReminderPatch():
    UiFieldPatch<ScheduleReminder> = when (this) {
    FieldPatch.Inherit -> UiFieldPatch.Inherit
    FieldPatch.Clear -> UiFieldPatch.Clear
    is FieldPatch.Replace -> UiFieldPatch.Replace(value.toUiReminder())
  }

  private fun TodoState.toUi(): ScheduleTodoState = when (this) {
    TodoState.OPEN -> ScheduleTodoState.PENDING
    TodoState.COMPLETED -> ScheduleTodoState.COMPLETED
  }

  private fun ScheduleKind.toUi(): UiScheduleKind = when (this) {
    ScheduleKind.TODO -> UiScheduleKind.TODO
    ScheduleKind.AFFAIR -> UiScheduleKind.AFFAIR
  }

  private fun OccurrenceStatus.toUi(): UiOccurrenceStatus = when (this) {
    OccurrenceStatus.ACTIVE -> UiOccurrenceStatus.ACTIVE
    OccurrenceStatus.COMPLETED -> UiOccurrenceStatus.COMPLETED
    OccurrenceStatus.CANCELLED -> UiOccurrenceStatus.CANCELLED
  }

  private fun Weekday.toUi(): IsoWeekDay = when (this) {
    Weekday.MO -> IsoWeekDay.MONDAY
    Weekday.TU -> IsoWeekDay.TUESDAY
    Weekday.WE -> IsoWeekDay.WEDNESDAY
    Weekday.TH -> IsoWeekDay.THURSDAY
    Weekday.FR -> IsoWeekDay.FRIDAY
    Weekday.SA -> IsoWeekDay.SATURDAY
    Weekday.SU -> IsoWeekDay.SUNDAY
  }

  private fun timestamps(atomTimes: List<Long>): Pair<Instant, Instant> {
    requireProjection(atomTimes.isNotEmpty(), "resource must contain atomic fields")
    return Instant.fromEpochMilliseconds(atomTimes.min()) to
      Instant.fromEpochMilliseconds(atomTimes.max())
  }

  private fun Long.toUtcDate(): Date =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toDate()

  private fun requireDateSlot(value: Long, label: String) {
    requireProjection(value >= 0 && value % UTC_DAY_MILLIS == 0L, "$label must be a UTC date slot")
  }

  private fun Long.toIntExact(label: String): Int {
    requireProjection(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong(), "$label is out of Int range")
    return toInt()
  }

  private fun <T> requireUnique(values: List<T>, label: String) {
    requireProjection(values.size == values.toSet().size, "$label contains duplicate identities")
  }
}

private class ProjectionAbort(message: String) : IllegalStateException(message)

private fun requireProjection(condition: Boolean, message: String) {
  if (!condition) abortProjection(message)
}

private fun abortProjection(message: String): Nothing = throw ProjectionAbort(message)
