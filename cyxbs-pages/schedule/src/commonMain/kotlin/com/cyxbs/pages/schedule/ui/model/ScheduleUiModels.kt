package com.cyxbs.pages.schedule.ui.model

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidationIssue
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * UI 可编辑字段的纯值草稿，是 Compose 输入状态与领域命令之间的边界。
 * TextFieldState 等生命周期对象不会跨过此层；[id] 在新建表单中允许是占位值，但写入前必须由命令边界替换。
 */
data class ScheduleDraft(
  val id: ScheduleId,
  val title: String = "",
  val description: String = "",
  val categoryId: CategoryId? = null,
  val timing: ScheduleTiming = ScheduleTiming.Unscheduled,
  val recurrence: RecurrenceRule? = null,
  val reminder: ScheduleReminder? = null,
  val todoState: ScheduleTodoState? = ScheduleTodoState.PENDING,
  /** 创建来源不可修改；清单归属和课表投射可由课表详情中的关联设置更新。 */
  val kind: ScheduleKind = ScheduleKind.TODO,
  val linkedToCourse: Boolean = false,
)

/** 使用完整领域校验器检查新建草稿；[now] 与 [revision] 仅用于补齐临时领域对象，不产生写入副作用。 */
fun ScheduleDraft.validate(now: Instant, revision: Long = 0): List<ScheduleValidationIssue> =
  ScheduleValidator.validate(toNewDomain(now, revision))

/**
 * 构造新日程领域对象，并在边界处裁剪文本。调用方负责传入正式 identity、revision 与时间戳；本函数不写仓库。
 */
fun ScheduleDraft.toNewDomain(now: Instant, revision: Long = 0): Schedule = Schedule(
  id = id,
  revision = revision,
  title = title.trim(),
  description = description.trim(),
  categoryId = categoryId,
  timing = timing,
  recurrence = recurrence,
  reminder = reminder,
  todoState = todoState,
  createdAt = now,
  updatedAt = now,
  kind = kind,
  linkedToCourse = linkedToCourse,
)

/**
 * 将可编辑字段覆盖到 [origin]，保留创建时间、revision 等仓库拥有的元数据。
 * 重复系列的完成态属于实例例外，因此改为重复规则后强制保持系列 PENDING，避免系列级完成污染所有 occurrence。
 */
fun ScheduleDraft.toUpdatedDomain(origin: Schedule, now: Instant): Schedule = origin.copy(
  title = title.trim(),
  description = description.trim(),
  categoryId = categoryId,
  timing = timing,
  recurrence = recurrence,
  reminder = reminder,
  todoState = if (recurrence == null) todoState else todoState?.let { ScheduleTodoState.PENDING },
  linkedToCourse = linkedToCourse,
  updatedAt = now,
)

/**
 * 主页面、Feed、时间轴与课表共用的只读实例投影。
 * [scheduleId] 标识系列，[recurrenceId] 标识重复系列中的原始实例锚点；即使实例被移动，二者组合仍保持稳定，
 * 不能使用当前展示时间充当 Lazy key、导航参数或命令 identity。
 */
data class ScheduleUiOccurrence(
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId?,
  val title: String,
  val description: String,
  val categoryId: CategoryId?,
  val timing: ScheduleTiming,
  val reminder: ScheduleReminder?,
  val status: OccurrenceStatus,
  val isAdjusted: Boolean,
)

/** 将领域展开实例无损映射为 UI 模型，保留四态 timing、状态及稳定 recurrence identity。 */
fun ScheduleOccurrence.toUiModel(): ScheduleUiOccurrence = ScheduleUiOccurrence(
  scheduleId, recurrenceId, title, description, categoryId, timing, reminder, status, isAdjusted,
)

/**
 * 判断 ACTIVE 实例是否已越过可操作边界。
 *
 * 时间段以自身时区中的结束 Instant 为界，截止项以 due Instant 为界；全天项没有事件时区，按 [viewerTimeZone]
 * 在“次日零点”过期，以兼容跨时区查看和 DST 日长变化。已完成、已取消及未排期实例永不显示为过期。
 * 边界时刻本身仍不算过期，只有 [now] 严格晚于边界才返回 true。
 */
fun ScheduleUiOccurrence.isExpired(now: Instant, viewerTimeZone: TimeZone): Boolean {
  if (status != OccurrenceStatus.ACTIVE) return false
  val boundary = when (val value = timing) {
    is ScheduleTiming.Timed -> value.start.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId)) + value.durationMinutes.minutes
    is ScheduleTiming.Deadline -> value.due.toLocalDateTime().toInstant(TimeZone.of(value.timeZoneId))
    is ScheduleTiming.AllDay -> value.date.plusDays(1).toLocalDate()
      .atStartOfDayIn(viewerTimeZone)
    ScheduleTiming.Unscheduled -> return false
  }
  return now > boundary
}

/**
 * 仅在调用方给定的半开可见本地窗口内展开快照，并返回不含 DTO 的实例 UI 模型。
 *
 * 未排期日程没有有界时间，默认不返回；Feed 若需把它们排到末尾，必须显式传入 [includeUnscheduled]。
 * 其余日程统一交给业务 `RecurrenceEngine`，由它合并移动、取消和完成等单次调整。调用方必须明确日/周窗口
 * 边界，避免无界展开。
 */
fun ScheduleSnapshot.occurrencesInRange(
  startInclusive: MinuteTimeDate,
  endExclusive: MinuteTimeDate,
  includeUnscheduled: Boolean = false,
): List<ScheduleUiOccurrence> = schedules.flatMap { schedule ->
  if (schedule.timing == ScheduleTiming.Unscheduled) {
    if (!includeUnscheduled) emptyList() else listOf(ScheduleOccurrence(
      schedule.id, null, schedule.timing, schedule.title, schedule.description, schedule.categoryId,
      schedule.reminder,
      if (schedule.todoState == ScheduleTodoState.COMPLETED) OccurrenceStatus.COMPLETED else OccurrenceStatus.ACTIVE,
      false,
    ))
  } else {
    com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine.expandInRange(
      schedule, occurrenceAdjustments.filter { it.scheduleId == schedule.id }, startInclusive, endExclusive,
    )
  }
}.map(ScheduleOccurrence::toUiModel)

/**
 * 按系列与稳定 recurrence identity 从当前快照解析一个实例。
 *
 * 详情页只能持有 identity，不能长期保存点击瞬间的 [ScheduleUiOccurrence]：单次调整保存、还原或远端合并后，
 * 同一 identity 的标题、时间与完成态都可能变化。每次快照更新后重新调用本方法，才能让仍打开的详情展示
 * canonical 最新状态；被取消或已删除的实例返回 `null`，调用方应关闭详情。
 */
internal fun ScheduleSnapshot.occurrenceByIdentity(
  scheduleId: ScheduleId,
  recurrenceId: RecurrenceId?,
): ScheduleUiOccurrence? {
  val schedule = schedules.firstOrNull { it.id == scheduleId } ?: return null
  val occurrence = if (recurrenceId == null) {
    if (schedule.recurrence != null) return null
    ScheduleOccurrence(
      scheduleId = schedule.id,
      recurrenceId = null,
      timing = schedule.timing,
      title = schedule.title,
      description = schedule.description,
      categoryId = schedule.categoryId,
      reminder = schedule.reminder,
      status = if (schedule.todoState == ScheduleTodoState.COMPLETED) {
        OccurrenceStatus.COMPLETED
      } else {
        OccurrenceStatus.ACTIVE
      },
      isAdjusted = false,
    )
  } else {
    if (schedule.recurrence == null) return null
    com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine.resolveOccurrenceByIdentity(
      schedule = schedule,
      occurrenceAdjustments = occurrenceAdjustments.filter { it.scheduleId == scheduleId },
      recurrenceId = recurrenceId,
    ) ?: return null
  }
  return occurrence.toUiModel()
}
