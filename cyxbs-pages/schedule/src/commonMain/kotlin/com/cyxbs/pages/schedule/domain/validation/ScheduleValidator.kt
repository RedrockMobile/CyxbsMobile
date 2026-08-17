package com.cyxbs.pages.schedule.domain.validation

import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrencePatch
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleCompletion
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import kotlinx.datetime.TimeZone

/** Schedule v2 纯校验器返回的稳定、机器可读问题；[code] 可供 UI 映射文案。 */
data class ScheduleValidationIssue(val field: String, val message: String)

/**
 * Schedule v2 领域对象的纯校验与规范化边界。
 *
 * 数据构造器刻意保持适合传输与反序列化，不把每条规则塞进 `init`；创建和映射边界应调用此处，从而
 * 一次收集全部问题，而不是遇到首个异常就停止，也避免 DTO/Record 层复制领域规则。
 */
object ScheduleValidator {
  /**
   * 校验完整日程及其嵌套时间、重复规则和提醒，返回全部问题而非只返回首项。
   *
   * [pushSupported] 只表示当前消费边界能够安全保留或忽略 PUSH；常规仓库仍使用默认 `false`。系统日历
   * 投影会传 `true`，因为它明确过滤 PUSH 而不是尝试投递，不能让无关渠道阻止 DEVICE reminder 导出。
   */
  fun validate(schedule: Schedule, pushSupported: Boolean = false): List<ScheduleValidationIssue> = buildList {
    if (schedule.revision < 0) issue("revision", "must be non-negative")
    if (schedule.title.isBlank()) issue("title", "must not be blank")
    if (schedule.updatedAt < schedule.createdAt) issue("updatedAt", "must not precede createdAt")
    addAll(validate(schedule.timing))
    schedule.recurrence?.let { addAll(validate(it)) }
    // 重复展开必须以实际发生时间为锚点；未排期事项没有该锚点，禁止持久化后再由查询层失败。
    if (schedule.timing == ScheduleTiming.Unscheduled && schedule.recurrence != null) {
      issue("recurrence", "unscheduled schedules cannot have recurrence")
    }
    addAll(validateReminders(schedule.reminders, schedule.timing, pushSupported))
    if (schedule.recurrence != null && schedule.completion != ScheduleCompletion.PENDING) {
      issue("completion", "a recurring schedule must remain PENDING")
    }
  }

  /** 校验正时长与可解析 IANA 时区；拒绝模糊缩写，避免各平台解释不同。 */
  fun validate(timing: ScheduleTiming): List<ScheduleValidationIssue> = buildList {
    when (timing) {
      is ScheduleTiming.Timed -> {
        if (timing.durationMinutes <= 0) issue("timing.durationMinutes", "must be positive")
        validateTimeZone(timing.timeZoneId, "timing.timeZoneId")?.let(::add)
      }
      is ScheduleTiming.Deadline ->
        validateTimeZone(timing.timeZoneId, "timing.timeZoneId")?.let(::add)
      is ScheduleTiming.AllDay ->
        if (timing.durationDays <= 0) issue("timing.durationDays", "must be positive")
      ScheduleTiming.Unscheduled -> Unit
    }
  }

  /** 校验受支持的 RRULE 子集与数值范围；子集外语义不得静默降级。 */
  fun validate(rule: RecurrenceRule): List<ScheduleValidationIssue> = buildList {
    if (rule.interval < 1) issue("recurrence.interval", "must be at least 1")
    rule.byMonthDays.filter { it == 0 || it !in -31..31 }.forEach {
      issue("recurrence.byMonthDays", "$it is outside -31..-1 or 1..31")
    }
    rule.byMonths.filter { it !in 1..12 }.forEach {
      issue("recurrence.byMonths", "$it is outside 1..12")
    }
    // 当前 WEEKLY 展开器只实现 BYDAY/BYMONTH；不能接受后静默忽略 BYMONTHDAY。
    if (rule.frequency == RecurrenceFrequency.WEEKLY && rule.byMonthDays.isNotEmpty()) {
      issue("recurrence.byMonthDays", "is unsupported for WEEKLY recurrence")
    }
    if (rule.end is RecurrenceEnd.Count && rule.end.value <= 0) {
      issue("recurrence.end.count", "must be positive")
    }
  }

  /**
   * 校验提醒身份唯一性及其时间语义。
   *
   * 偏移量只能非负，因为模型表达“提前多少分钟”；未排期事项没有可计算锚点；后端明确声明能力前拒绝
   * PUSH，避免本地看似保存成功但永远无法投递。
   */
  fun validateReminders(
    reminders: List<ScheduleReminder>,
    timing: ScheduleTiming,
    pushSupported: Boolean = false,
  ): List<ScheduleValidationIssue> = buildList {
    val duplicateIds = reminders.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicateIds.isNotEmpty()) issue("reminders.id", "reminder IDs must be unique")
    reminders.forEachIndexed { index, reminder ->
      if (reminder.offsetMinutes < 0) issue("reminders[$index].offsetMinutes", "must be non-negative")
      if (reminder.channel == ReminderChannel.PUSH && !pushSupported) {
        issue("reminders[$index].channel", "PUSH is not supported")
      }
    }
    if (timing == ScheduleTiming.Unscheduled && reminders.isNotEmpty()) {
      issue("reminders", "unscheduled items cannot have reminders")
    }
  }

  /** 校验分类 revision 与用户可见名称。 */
  fun validate(category: ScheduleCategory): List<ScheduleValidationIssue> = buildList {
    if (category.revision < 0) issue("revision", "must be non-negative")
    if (category.name.isBlank()) issue("name", "must not be blank")
  }

  /** 校验稳定匹配 occurrence 所需的重复身份语义，包括时区与全天属性的一致性。 */
  fun validate(recurrenceId: RecurrenceId): List<ScheduleValidationIssue> = buildList {
    if (recurrenceId.allDay) {
      if (recurrenceId.timeZoneId != null) issue("recurrenceId.timeZoneId", "must be null for all-day identity")
      if (recurrenceId.originalDateTime.minuteOfDay != 0) {
        issue("recurrenceId.originalDateTime", "all-day identity must use midnight")
      }
    } else {
      val timeZoneId = recurrenceId.timeZoneId
      if (timeZoneId == null) issue("recurrenceId.timeZoneId", "is required for timed identity")
      else validateTimeZone(timeZoneId, "recurrenceId.timeZoneId")?.let(::add)
    }
  }

  /**
   * 校验 occurrence 例外的 revision、身份、时间戳及可选覆盖。
   *
   * 状态与 patch 相互正交：移动后的实例仍可完成或取消，恢复 ACTIVE 也不应丢弃既有编辑投影。
   */
  fun validate(exception: ScheduleOccurrenceException): List<ScheduleValidationIssue> = buildList {
    if (exception.revision < 0) issue("revision", "must be non-negative")
    if (exception.updatedAt < exception.createdAt) issue("updatedAt", "must not precede createdAt")
    addAll(validate(exception.recurrenceId))
    exception.patch?.let { addAll(validate(it)) }
  }

  /** 校验 occurrence 三态编辑；timing 与 title 不允许清空，timing 替换始终按完整联合原子校验。 */
  fun validate(patch: OccurrencePatch): List<ScheduleValidationIssue> = buildList {
    when (val timing = patch.timing) {
      FieldPatch.Clear -> issue("patch.timing", "must not be cleared")
      FieldPatch.Inherit -> Unit
      is FieldPatch.Replace -> addAll(validate(timing.value).map { it.copy(field = "patch.${it.field}") })
    }
    when (val title = patch.title) {
      FieldPatch.Clear -> issue("patch.title", "must not be cleared")
      FieldPatch.Inherit -> Unit
      is FieldPatch.Replace -> if (title.value.isBlank()) issue("patch.title", "must not be blank")
    }
    if (patch.description is FieldPatch.Replace && patch.description.value.isBlank()) {
      issue("patch.description", "use CLEAR instead of a blank replacement")
    }
    if (patch.reminders is FieldPatch.Replace) {
      val reminders = patch.reminders.value
      val duplicateIds = reminders.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
      if (duplicateIds.isNotEmpty()) issue("patch.reminders.id", "reminder IDs must be unique")
      reminders.forEachIndexed { index, reminder ->
        if (reminder.offsetMinutes < 0) issue("patch.reminders[$index].offsetMinutes", "must be non-negative")
        if (reminder.channel == ReminderChannel.PUSH) issue("patch.reminders[$index].channel", "PUSH is not supported")
      }
    }
  }

  private fun validateTimeZone(value: String, field: String): ScheduleValidationIssue? {
    if (value.isBlank()) return ScheduleValidationIssue(field, "must not be blank")
    return try {
      TimeZone.of(value)
      null
    } catch (_: IllegalArgumentException) {
      ScheduleValidationIssue(field, "must be a valid IANA time-zone ID")
    }
  }

  private fun MutableList<ScheduleValidationIssue>.issue(field: String, message: String) {
    add(ScheduleValidationIssue(field, message))
  }
}

/**
 * 返回集合迭代顺序确定的规范副本。
 *
 * Kotlin 集合相等性虽忽略顺序，但编码器与指纹计算需要稳定插入顺序，才能在所有平台产生逐字节一致的
 * 输出；该规范化不改变 RRULE 的集合语义。
 */
fun RecurrenceRule.canonicalized(): RecurrenceRule = copy(
  byWeekDays = byWeekDays.sortedBy { it.isoNumber }.toCollection(LinkedHashSet()),
  byMonthDays = byMonthDays.sorted().toCollection(LinkedHashSet()),
  byMonths = byMonths.sorted().toCollection(LinkedHashSet()),
)
