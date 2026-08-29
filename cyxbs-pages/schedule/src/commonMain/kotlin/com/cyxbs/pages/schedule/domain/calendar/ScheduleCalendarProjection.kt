package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.ReminderChannel
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator
import com.cyxbs.pages.schedule.domain.validation.canonicalized
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 平台无关的外部日历时间语义。
 *
 * Android adapter 后续负责转换成 `DTEND`、`DURATION` 与 UTC 全天边界；common 层刻意保留 IANA 时区
 * 和日期区间，避免按当前设备时区或固定 24 小时提前损失业务语义。
 */
sealed interface CalendarTiming {
  /** 有明确墙上开始时间、正时长和 IANA 时区的定时事件。 */
  data class Timed(
    val start: MinuteTimeDate,
    val durationMinutes: Int,
    val timeZoneId: String,
  ) : CalendarTiming

  /** 只保留日期与天数的全天事件；一天不等价于固定 24 小时。 */
  data class AllDay(
    val startDate: Date,
    val durationDays: Int,
  ) : CalendarTiming

  /** 时间点任务的外部展示语义；平台日历使用开始、结束相同的零时长事件保留该语义。 */
  data class Deadline(
    val due: MinuteTimeDate,
    val timeZoneId: String,
  ) : CalendarTiming
}

/**
 * 一个可交给平台 gateway 的规范目标事件。
 *
 * [fingerprint] 仅由本对象的规范业务字段得到，不包含 Provider eventId、同步时间或设备默认时区，因此可
 * 用于跨进程幂等对账。系统日历中的修改不是业务输入，下一次同步应以该投影覆盖外部副本。
 */
data class CalendarEventProjection(
  val id: CalendarProjectionId,
  val externalUri: String,
  val title: String,
  val description: String,
  val timing: CalendarTiming,
  val recurrenceRule: String?,
  val deviceReminderMinutes: List<Int>,
  val fingerprint: String,
  /** 仅显式 capability 消费者可接收的 Android 原生 occurrence exception 子计划；顶层身份仍是 series master。 */
  val nativeOccurrenceExceptions: List<CalendarOccurrenceExceptionProjection> = emptyList(),
)

/** Android 原生例外最终写入 Provider 的语义；完成与取消都以显式取消例外隐藏原始 occurrence。 */
enum class CalendarOccurrenceExceptionOperation {
  UPSERT,
  CANCEL,
}

/**
 * 一个已证明属于 series master 的原生 occurrence exception 目标。
 *
 * [id] 永远保留原始 recurrenceId；移动只改变 [timing]。该对象不携带 Provider eventId 或 master row id，平台
 * gateway 必须在 fresh preflight 后绑定精确 master relationship。
 */
data class CalendarOccurrenceExceptionProjection(
  val id: CalendarProjectionId,
  val externalUri: String,
  val title: String,
  val description: String,
  val timing: CalendarTiming,
  val deviceReminderMinutes: List<Int>,
  val operation: CalendarOccurrenceExceptionOperation,
  val fingerprint: String,
)

/** 当前 common 投影明确拒绝导出的原因；调用方应展示或记录，而不能悄悄生成有限窗口事件。 */
enum class UnsupportedCalendarProjectionReason {
  /** 重复系列包含独立 occurrence 例外，尚未建立可靠的原生 Provider exception 映射。 */
  OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
}

/** 一条未导出事实及其原因，保留 Schedule 身份以便 UI 或诊断定位。 */
data class UnsupportedCalendarProjection(
  val scheduleId: ScheduleId,
  val reason: UnsupportedCalendarProjectionReason,
)

/** 纯投影的完整结果；两个集合均使用稳定顺序，便于测试、指纹和后续 planner 消费。 */
data class ScheduleCalendarProjectionResult(
  val events: List<CalendarEventProjection>,
  val unsupported: List<UnsupportedCalendarProjection>,
)

/**
 * 投影消费者已经完整实现的可选能力。
 *
 * capability 必须由最终消费并写入平台的边界显式声明，不能仅因当前运行在 Android 就默认开启；否则共享投影会把
 * 未被 outbound runtime、canonical baseline 或 iOS mapper 理解的新字段提前送入既有链路。
 */
enum class ScheduleCalendarProjectionCapability {
  /** 消费者能校验、对账、写入并回读聚合 fingerprint 中的原生 occurrence exception。 */
  NATIVE_OCCURRENCE_EXCEPTIONS,
}

/**
 * 把 Schedule v2 事实映射为外部日历目标投影。
 *
 * 本工厂不访问账号、Clock、默认时区或平台 API。包含 exception 的重复系列只有在最终消费者显式声明原生例外
 * capability 时才生成子计划；默认继续整体标记为 unsupported，绝不让尚未升级的 Android/iOS 消费者误判 Update。
 */
object ScheduleCalendarProjectionFactory {
  /**
   * 生成指定 [scope] 下的规范投影。
   *
   * 非重复完成项和未排期项不产生事件；无例外的重复系列保留为单个 RRULE master。只有 DEVICE reminder
   * 会映射到系统提醒，零分钟提醒有效。[capabilities] 默认为空，以兼容所有尚未完整支持原生例外的消费者。
   */
  fun project(
    source: ScheduleCalendarSource,
    scope: CalendarExportScope,
    capabilities: Set<ScheduleCalendarProjectionCapability> = emptySet(),
  ): ScheduleCalendarProjectionResult {
    val duplicateScheduleIds = source.schedules.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    require(duplicateScheduleIds.isEmpty()) { "Calendar source contains duplicate schedule IDs: $duplicateScheduleIds" }
    val scheduleIds = source.schedules.mapTo(mutableSetOf()) { it.id }
    require(source.exceptions.all { it.scheduleId in scheduleIds }) {
      "Calendar source contains orphan occurrence exceptions"
    }
    val exceptionsBySchedule = source.exceptions.groupBy { it.scheduleId }
    val unsupported = mutableListOf<UnsupportedCalendarProjection>()
    val events = source.schedules.sortedBy { it.id.value }.mapNotNull { schedule ->
      val validationIssues = ScheduleValidator.validate(schedule, pushSupported = true)
      require(validationIssues.isEmpty()) {
        "Calendar source contains invalid schedule ${schedule.id}: $validationIssues"
      }
      val exceptions = exceptionsBySchedule[schedule.id].orEmpty()
      require(schedule.recurrence != null || exceptions.isEmpty()) {
        "Non-recurring schedule ${schedule.id} cannot own occurrence exceptions"
      }
      require(exceptions.map { it.recurrenceId }.distinct().size == exceptions.size) {
        "Calendar source contains duplicate occurrence exception identities"
      }
      exceptions.forEach { exception ->
        val exceptionIssues = ScheduleValidator.validate(exception)
        require(exceptionIssues.isEmpty()) {
          "Calendar source contains invalid occurrence exception: $exceptionIssues"
        }
      }
      if (exceptions.isNotEmpty() &&
        ScheduleCalendarProjectionCapability.NATIVE_OCCURRENCE_EXCEPTIONS !in capabilities
      ) {
        // 默认门禁必须在生成聚合 fingerprint 前终止；旧消费者只认识 master 字段，不能安全接收 exception-only Update。
        unsupported += UnsupportedCalendarProjection(
          schedule.id,
          UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
        )
        return@mapNotNull null
      }
      if (exceptions.isNotEmpty() && schedule.timing is ScheduleTiming.Deadline) {
        // Deadline 的稳定顶层身份仍是 kind=deadline，而 Android 原生例外链当前只接受 kind=series 的 master。
        // 在 identity 合同统一前必须明确 Unsupported，不能生成后续 planner/gateway 必然拒绝的半合法子计划。
        unsupported += UnsupportedCalendarProjection(
          schedule.id,
          UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
        )
        return@mapNotNull null
      }
      if (schedule.timing == ScheduleTiming.Unscheduled) return@mapNotNull null
      if (schedule.kind == ScheduleKind.TODO &&
        schedule.recurrence == null &&
        schedule.todoState == ScheduleTodoState.COMPLETED
      ) {
        return@mapNotNull null
      }
      val nativeExceptions = runCatching {
        exceptions.map { projectOccurrenceException(schedule, it, scope) }
          .sortedBy { it.externalUri }
      }.getOrElse {
        unsupported += UnsupportedCalendarProjection(
          schedule.id,
          UnsupportedCalendarProjectionReason.OCCURRENCE_EXCEPTIONS_NOT_SUPPORTED,
        )
        return@mapNotNull null
      }
      val kind = when {
        schedule.timing is ScheduleTiming.Deadline -> CalendarProjectionKind.DEADLINE
        schedule.recurrence == null -> CalendarProjectionKind.SINGLE
        else -> CalendarProjectionKind.SERIES_MASTER
      }
      val id = CalendarProjectionId(scope, schedule.id, kind)
      val timing = schedule.timing.toCalendarTiming()
      val recurrence = schedule.recurrence?.let { encodeRecurrenceRule(it, schedule.timing) }
      val reminders = schedule.reminders.deviceReminderMinutes()
      val externalUri = CalendarProjectionUriCodec.encode(id)
      val fingerprint = CalendarProjectionFingerprint.compute(
        externalUri = externalUri,
        title = schedule.title,
        description = schedule.description,
        timing = timing,
        recurrenceRule = recurrence,
        reminderMinutes = reminders,
        nativeOccurrenceExceptions = nativeExceptions,
      )
      CalendarEventProjection(
        id = id,
        externalUri = externalUri,
        title = schedule.title,
        description = schedule.description,
        timing = timing,
        recurrenceRule = recurrence,
        deviceReminderMinutes = reminders,
        fingerprint = fingerprint,
        nativeOccurrenceExceptions = nativeExceptions,
      )
    }
    return ScheduleCalendarProjectionResult(
      events = events.sortedBy { it.externalUri },
      unsupported = unsupported.sortedBy { it.scheduleId.value },
    )
  }

  /**
   * 将一个已验证 occurrence 解析为 Android 可写的原生例外目标。
   *
   * 这里使用精确 recurrence identity 查询证明它确实由 RRULE 生成，不借可见窗口展开有限 singleton。ACTIVE 必须
   * 至少包含一个会影响外部日历的 patch；无 patch 或仅分类变化无法证明需要 Provider exception，继续 fail closed。
   */
  private fun projectOccurrenceException(
    schedule: Schedule,
    exception: ScheduleOccurrenceException,
    scope: CalendarExportScope,
  ): CalendarOccurrenceExceptionProjection {
    val patch = exception.patch
    if (exception.status == OccurrenceStatus.ACTIVE) {
      require(patch != null && patch.hasCalendarVisibleChange()) {
        "Active occurrence exception must change exported calendar fields"
      }
    }
    // CANCELLED 也要先按 ACTIVE 精确物化一次，以取得原始/移动后的 canonical 内容；身份仍使用原 recurrenceId。
    val materialized = requireNotNull(
      RecurrenceEngine.resolveOccurrenceByIdentity(
        schedule = schedule,
        exceptions = listOf(exception.copy(status = OccurrenceStatus.ACTIVE)),
        recurrenceId = exception.recurrenceId,
      ),
    )
    val id = CalendarProjectionId(
      scope = scope,
      scheduleId = schedule.id,
      kind = CalendarProjectionKind.OCCURRENCE_EXCEPTION,
      recurrenceId = exception.recurrenceId,
    )
    val externalUri = CalendarProjectionUriCodec.encode(id)
    val operation = if (exception.status == OccurrenceStatus.ACTIVE) {
      CalendarOccurrenceExceptionOperation.UPSERT
    } else {
      CalendarOccurrenceExceptionOperation.CANCEL
    }
    val timing = materialized.timing.toCalendarTiming()
    val reminders = materialized.reminders.deviceReminderMinutes()
    val fingerprint = CalendarProjectionFingerprint.computeOccurrenceException(
      externalUri = externalUri,
      title = materialized.title,
      description = materialized.description,
      timing = timing,
      reminderMinutes = reminders,
      operation = operation,
    )
    return CalendarOccurrenceExceptionProjection(
      id = id,
      externalUri = externalUri,
      title = materialized.title,
      description = materialized.description,
      timing = timing,
      deviceReminderMinutes = reminders,
      operation = operation,
      fingerprint = fingerprint,
    )
  }

  /** 分类只影响应用内语义；其他四个 patch 字段才需要创建 Android Provider exception。 */
  private fun com.cyxbs.pages.schedule.domain.model.OccurrencePatch.hasCalendarVisibleChange(): Boolean =
    timing != FieldPatch.Inherit || title != FieldPatch.Inherit ||
        description != FieldPatch.Inherit || reminders != FieldPatch.Inherit

  /** 仅 DEVICE reminder 进入系统日历；排序和去重同时供 master 与 occurrence 复用。 */
  private fun List<ScheduleReminder>.deviceReminderMinutes(): List<Int> = asSequence()
    .filter { it.channel == ReminderChannel.DEVICE }
    .map { it.offsetMinutes }
    .distinct()
    .sorted()
    .toList()

  /** 将四态领域时间收窄为可导出的三态；Unscheduled 已由调用方过滤。 */
  private fun ScheduleTiming.toCalendarTiming(): CalendarTiming = when (this) {
    is ScheduleTiming.Timed -> CalendarTiming.Timed(start, durationMinutes, timeZoneId)
    is ScheduleTiming.AllDay -> CalendarTiming.AllDay(startDate, durationDays)
    is ScheduleTiming.Deadline -> CalendarTiming.Deadline(due, timeZoneId)
    ScheduleTiming.Unscheduled -> error("Unscheduled timing cannot be exported")
  }

  /**
   * 编码 Schedule v2 支持的受限 RFC 5545 RRULE，并固定集合字段顺序。
   *
   * UNTIL 保留本地墙上时间形式，平台 adapter 写入时必须结合 Schedule 的 IANA 时区处理，不能按设备默认
   * 时区解释。这里不接受 RDATE、EXDATE、BYSETPOS 等领域模型外字段。
   */
  internal fun encodeRecurrenceRule(rule: RecurrenceRule, timing: ScheduleTiming? = null): String {
    val value = rule.canonicalized()
    return buildList {
      add("FREQ=${value.frequency.name}")
      if (value.interval != 1) add("INTERVAL=${value.interval}")
      if (value.byWeekDays.isNotEmpty()) add("BYDAY=${value.byWeekDays.joinToString(",") { it.rfc5545 }}")
      if (value.byMonthDays.isNotEmpty()) add("BYMONTHDAY=${value.byMonthDays.joinToString(",")}")
      if (value.byMonths.isNotEmpty()) add("BYMONTH=${value.byMonths.joinToString(",")}")
      when (val end = value.end) {
        RecurrenceEnd.Never -> Unit
        is RecurrenceEnd.Count -> add("COUNT=${end.value}")
        is RecurrenceEnd.Until -> add("UNTIL=${formatUntil(end.date, timing)}")
      }
    }.joinToString(";")
  }

  /**
   * 按 DTSTART 的 value type 编码 UNTIL。
   *
   * 全天事件的 DTSTART 是 DATE，因此 UNTIL 必须同为 `yyyyMMdd`；定时/截止事件才按日程 IANA 时区
   * 转为 UTC DATE-TIME。混用 DATE 与 DATE-TIME 会被部分 Android Provider 拒绝或改变末次实例语义。
   */
  private fun formatUntil(date: Date, timing: ScheduleTiming?): String {
    if (timing is ScheduleTiming.AllDay) return date.toRfc5545Date()
    val timeZone = when (timing) {
      is ScheduleTiming.Timed -> TimeZone.of(timing.timeZoneId)
      is ScheduleTiming.Deadline -> TimeZone.of(timing.timeZoneId)
      ScheduleTiming.Unscheduled, null -> error("Recurring calendar export requires scheduled timing for UNTIL")
      is ScheduleTiming.AllDay -> error("All-day UNTIL is handled as DATE")
    }
    // 产品只设置截止日期；定时系列用该日期最后一秒转换为 RFC 5545 要求的 UTC DATE-TIME。
    val inclusiveEnd = LocalDateTime(
      date.year, date.monthNumber, date.dayOfMonth, 23, 59, 59,
    )
    val utc = inclusiveEnd.toInstant(timeZone).toLocalDateTime(TimeZone.UTC)
    return utc.toRfc5545UtcDateTime()
  }
}

/**
 * Android Provider 回读 RRULE 的受限语义规范化器。
 *
 * 仅接受 Schedule v2 能生成的字段集合，忽略字段顺序并恢复固定顺序；重复字段、未知字段、非法值或
 * DTSTART/UNTIL value type 不匹配均返回 null。这样可兼容 Provider 的等价重排，同时不会把无法理解的规则
 * 错判为 NoOp 后覆盖或认领。
 */
internal object CalendarRecurrenceCanonicalizer {
  fun canonicalizeOrNull(value: String, allDay: Boolean): String? {
    if (value.isBlank()) return null
    val fields = linkedMapOf<String, String>()
    for (part in value.split(';')) {
      val separator = part.indexOf('=')
      if (separator <= 0 || separator == part.lastIndex) return null
      val key = part.substring(0, separator).uppercase()
      val raw = part.substring(separator + 1).uppercase()
      if (key !in SUPPORTED_FIELDS || fields.put(key, raw) != null) return null
    }
    val frequency = fields["FREQ"]?.takeIf { it in FREQUENCIES } ?: return null
    val interval = fields["INTERVAL"]?.toIntOrNull()?.takeIf { it > 0 }
      ?: if ("INTERVAL" in fields) return null else 1
    val byDay = fields["BYDAY"]?.split(',')?.map { day ->
      DAY_ORDER.indexOf(day).takeIf { it >= 0 } ?: return null
    }?.distinct()?.sorted()?.map(DAY_ORDER::get).orEmpty()
    val byMonthDay = fields["BYMONTHDAY"]?.let { parseIntList(it, -31..31, rejectZero = true) } ?:
      if ("BYMONTHDAY" in fields) return null else emptyList()
    val byMonth = fields["BYMONTH"]?.let { parseIntList(it, 1..12) } ?:
      if ("BYMONTH" in fields) return null else emptyList()
    val count = fields["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
    if ("COUNT" in fields && count == null || count != null && "UNTIL" in fields) return null
    val until = fields["UNTIL"]?.takeIf { candidate ->
      if (allDay) DATE_REGEX.matches(candidate) else UTC_DATE_TIME_REGEX.matches(candidate)
    } ?: if ("UNTIL" in fields) return null else null
    return buildList {
      add("FREQ=$frequency")
      if (interval != 1) add("INTERVAL=$interval")
      if (byDay.isNotEmpty()) add("BYDAY=${byDay.joinToString(",")}")
      if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=${byMonthDay.joinToString(",")}")
      if (byMonth.isNotEmpty()) add("BYMONTH=${byMonth.joinToString(",")}")
      if (count != null) add("COUNT=$count")
      if (until != null) add("UNTIL=$until")
    }.joinToString(";")
  }

  private fun parseIntList(value: String, range: IntRange, rejectZero: Boolean = false): List<Int>? {
    val parsed = value.split(',').map { it.toIntOrNull() ?: return null }
    if (parsed.any { it !in range || rejectZero && it == 0 }) return null
    return parsed.distinct().sorted()
  }

  private val SUPPORTED_FIELDS = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL")
  private val FREQUENCIES = setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
  private val DAY_ORDER = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
  private val DATE_REGEX = Regex("""\d{8}""")
  private val UTC_DATE_TIME_REGEX = Regex("""\d{8}T\d{6}Z""")
}

private fun Date.toRfc5545Date(): String =
  year.toString().padStart(4, '0') + monthNumber.toString().padStart(2, '0') + dayOfMonth.toString().padStart(2, '0')

private fun LocalDateTime.toRfc5545UtcDateTime(): String = buildString {
  append(date.year.toString().padStart(4, '0'))
  append(date.month.number.toString().padStart(2, '0'))
  append(date.day.toString().padStart(2, '0'))
  append('T')
  append(hour.toString().padStart(2, '0'))
  append(minute.toString().padStart(2, '0'))
  append(second.toString().padStart(2, '0'))
  append('Z')
}

/**
 * 生成可跨平台逐字比较的版本化投影指纹。
 *
 * 第一版保留 canonical 文本而不引入平台差异明显的摘要实现；后续若改用 hash，必须新增版本而不能复用
 * `calendar-projection-v1`，避免升级后把所有事件误判为内容变化。
 */
internal object CalendarProjectionFingerprint {
  /**
   * 按固定字段顺序拼接一个投影的 canonical 内容。
   *
   * [externalUri] 必须是调用方刚通过 [CalendarProjectionUriCodec] 生成的规范身份；复用它避免重复编码。
   * 返回值供 planner 逐字比较，字段或编码变化必须同步提升版本前缀，否则会破坏已有事件的幂等判断。
   */
  fun compute(
    externalUri: String,
    title: String,
    description: String,
    timing: CalendarTiming,
    recurrenceRule: String?,
    reminderMinutes: List<Int>,
    nativeOccurrenceExceptions: List<CalendarOccurrenceExceptionProjection> = emptyList(),
  ): String = buildString {
    append("calendar-projection-v1|")
    appendField(externalUri)
    appendField(title)
    appendField(description)
    appendField(timing.canonicalValue())
    appendField(recurrenceRule.orEmpty())
    appendField(reminderMinutes.joinToString(","))
    if (nativeOccurrenceExceptions.isNotEmpty()) {
      appendField(nativeOccurrenceExceptions.joinToString("\n") { it.fingerprint })
    }
  }

  /** occurrence 指纹显式包含操作类型；取消不能与普通内容相同的 active exception 混淆。 */
  fun computeOccurrenceException(
    externalUri: String,
    title: String,
    description: String,
    timing: CalendarTiming,
    reminderMinutes: List<Int>,
    operation: CalendarOccurrenceExceptionOperation,
  ): String = buildString {
    append("calendar-occurrence-exception-v1|")
    appendField(externalUri)
    appendField(title)
    appendField(description)
    appendField(timing.canonicalValue())
    appendField(reminderMinutes.joinToString(","))
    appendField(operation.name)
  }

  /** 长度前缀避免标题或描述中的分隔符造成两组字段拥有相同拼接文本。 */
  private fun StringBuilder.appendField(value: String) {
    append(value.encodeToByteArray().size).append(':').append(value).append('|')
  }

  private fun CalendarTiming.canonicalValue(): String = when (this) {
    is CalendarTiming.Timed -> "timed|$start|$durationMinutes|$timeZoneId"
    is CalendarTiming.AllDay -> "all-day|$startDate|$durationDays"
    is CalendarTiming.Deadline -> "deadline|$due|$timeZoneId"
  }
}
