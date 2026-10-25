package com.cyxbs.pages.schedule.data.migration

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.uuid.stableUuidV5
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** 旧数据映射后的日程及只保留在端上的清单偏好。 */
internal data class LegacyScheduleMigrationItem(
  val schedule: Schedule,
  val categoryName: String?,
  val pinned: Boolean,
)

/**
 * 将已成功解码的旧事务与旧清单转换成 Schedule 领域对象。
 *
 * 映射器不访问网络、Room 或 Settings；调用方提供账号、学期锚点和当前时间，便于迁移重试时保持边界清楚。
 * 单条非法旧记录会被跳过，不会让其他有效记录无法迁移。
 */
internal object LegacyScheduleMapper {

  /** 将当前学期旧事务拆为一个或多个时间段日程。 */
  fun mapTransactions(
    accountId: String,
    transactions: List<LegacyTransactionDto>,
    firstMonday: Date,
    maxWeek: Int,
  ): List<LegacyScheduleMigrationItem> = buildList {
    val timeZoneId = TimeZone.currentSystemDefault().id
    transactions.forEach { transaction ->
      val title = transaction.title.trim()
      if (title.isEmpty()) return@forEach
      transaction.date.forEach timeLoop@{ time ->
        if (time.day !in 0..6) return@timeLoop
        val timing = legacyTransactionTiming(time.beginLesson, time.period) ?: return@timeLoop

        if (time.week.contains(0)) {
          val date = firstMonday.plusDays(time.day)
          add(
            transactionItem(
              accountId = accountId,
              transaction = transaction,
              time = time,
              identitySuffix = "all",
              start = MinuteTimeDate(
                date,
                timing.startMinute / 60,
                timing.startMinute % 60,
              ),
              durationMinutes = timing.durationMinutes,
              recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                byWeekDays = setOf(requireNotNull(IsoWeekDay.fromIsoNumber(time.day + 1))),
                end = RecurrenceEnd.Count(maxWeek.coerceAtLeast(1)),
              ),
              timeZoneId = timeZoneId,
            )
          )
        } else {
          time.week.asSequence().distinct().filter { it in 1..maxWeek }.forEach { week ->
            val date = firstMonday.plusDays((week - 1) * 7 + time.day)
            add(
              transactionItem(
                accountId = accountId,
                transaction = transaction,
                time = time,
                identitySuffix = "week:$week",
                start = MinuteTimeDate(
                  date,
                  timing.startMinute / 60,
                  timing.startMinute % 60,
                ),
                durationMinutes = timing.durationMinutes,
                recurrence = null,
                timeZoneId = timeZoneId,
              )
            )
          }
        }
      }
    }
  }

  /** 将旧清单转换为 Deadline 或 Unscheduled；旧模型没有可无损恢复的时间段与全天语义。 */
  fun mapTodos(
    accountId: String,
    todos: List<LegacyTodoDto>,
    now: MinuteTimeDate,
    nowEpochMillis: Long,
  ): List<LegacyScheduleMigrationItem> = todos.mapNotNull { todo ->
    val title = todo.title.trim()
    if (title.isEmpty()) return@mapNotNull null

    val end = parseLegacyDateTime(todo.endTime)
    val notify = parseLegacyDateTime(todo.remindMode.notifyDateTime)
    val nonRepeating = createNonRepeatingTodoTiming(end, notify)
    val recurring = createRecurringTodoTiming(todo.remindMode, end, notify, now)
    val mapped = when {
      recurring != null -> recurring
      todo.remindMode.repeatMode != LegacyTodoRemindModeDto.NONE ->
        createUnsupportedRepeatFallback(end, notify)
      else -> nonRepeating
    }
    val timestamp = normalizeLegacyTimestamp(todo.lastModifyTime, nowEpochMillis)
    val id = ScheduleId(
      deterministicScheduleUuid("$accountId|todo|${todo.todoId}")
    )
    val isRepeating = mapped.recurrence != null
    val schedule = Schedule(
      id = id,
      revision = 0,
      title = title,
      description = todo.detail,
      categoryId = null,
      timing = mapped.timing,
      recurrence = mapped.recurrence,
      reminder = mapped.reminderOffset?.let { offset ->
        ScheduleReminder(offset)
      },
      todoState = if (todo.isDone == 1 && !isRepeating) {
        ScheduleTodoState.COMPLETED
      } else {
        ScheduleTodoState.PENDING
      },
      createdAt = Instant.fromEpochMilliseconds(timestamp),
      updatedAt = Instant.fromEpochMilliseconds(timestamp),
      kind = ScheduleKind.TODO,
      linkedToCourse = false,
      recurrenceAnchorDate = mapped.recurrence?.let { timingDate(mapped.timing) },
    )
    LegacyScheduleMigrationItem(
      schedule = schedule,
      categoryName = legacyCategoryName(todo.type),
      pinned = todo.isPinned == 1,
    )
  }

  /** 构造单个旧事务时间位置，提醒分钟数为 0 时仍表示旧接口没有提醒。 */
  private fun transactionItem(
    accountId: String,
    transaction: LegacyTransactionDto,
    time: LegacyTransactionTimeDto,
    identitySuffix: String,
    start: MinuteTimeDate,
    durationMinutes: Int,
    recurrence: RecurrenceRule?,
    timeZoneId: String,
  ): LegacyScheduleMigrationItem {
    val timestamp = start.date.toEpochMillis()
    val id = ScheduleId(
      deterministicScheduleUuid(
        "$accountId|affair|${transaction.remoteId}|${time.day}|${time.beginLesson}|" +
          "${time.period}|$identitySuffix",
      )
    )
    return LegacyScheduleMigrationItem(
      schedule = Schedule(
        id = id,
        revision = 0,
        title = transaction.title.trim(),
        description = transaction.content,
        categoryId = null,
        timing = ScheduleTiming.Timed(start, durationMinutes, timeZoneId),
        recurrence = recurrence,
        reminder = transaction.time.takeIf { it > 0 }?.let { offset ->
          ScheduleReminder(offset)
        },
        todoState = null,
        createdAt = Instant.fromEpochMilliseconds(timestamp),
        updatedAt = Instant.fromEpochMilliseconds(timestamp),
        kind = ScheduleKind.AFFAIR,
        linkedToCourse = true,
        recurrenceAnchorDate = recurrence?.let { start.date },
      ),
      categoryName = null,
      pinned = false,
    )
  }

  /**
   * 按旧事务课表行规则换算开始分钟与持续时间。
   *
   * [beginLesson] 除 1～12 节外还可能使用 -1/-2 表示中午和傍晚；[period] 可以跨越这些课间行。
   * 这里保留旧 Android 事务的精确语义，让 Android 与 iOS 迁移同一条旧记录时产生相同时间。
   * 非法节次、非正时长或超出旧课表第 12 节的记录返回 null，由调用方只跳过该时间位置。
   */
  private fun legacyTransactionTiming(beginLesson: Int, period: Int): LegacyTransactionTiming? {
    if (period <= 0) return null
    val startRow = when (beginLesson) {
      in 1..4 -> beginLesson - 1
      -1 -> 4
      in 5..8 -> beginLesson
      -2 -> 9
      in 9..12 -> beginLesson + 1
      else -> return null
    }
    val endRow = startRow + period - 1
    if (endRow !in startRow..LEGACY_MAX_COURSE_END_ROW) return null
    val startMinute = LEGACY_START_MINUTES[startRow]
    val durationMinutes = LEGACY_END_MINUTES[endRow] - startMinute
    return durationMinutes.takeIf { it > 0 }?.let {
      LegacyTransactionTiming(startMinute, it)
    }
  }

  /** 非重复旧清单以截止时间为 timing；只有通知时间时将其作为截止点和准时提醒。 */
  private fun createNonRepeatingTodoTiming(
    end: MinuteTimeDate?,
    notify: MinuteTimeDate?,
  ): MappedTodoTiming {
    val due = end ?: notify ?: return MappedTodoTiming(ScheduleTiming.Unscheduled, null, null)
    val reminderOffset = notify?.minutesUntil(due)?.takeIf { it >= 0 }
    return MappedTodoTiming(
      timing = ScheduleTiming.Deadline(due, TimeZone.currentSystemDefault().id),
      recurrence = null,
      reminderOffset = reminderOffset,
    )
  }

  /**
   * 旧重复选择器非法或无法由当前规则无损表达时，只保留旧服务已经算出的下一次通知。
   *
   * 优先使用 notify 而不是系列 end，避免把“重复结束日期”误当成下一次待办；没有 notify 时才退回 end。
   */
  private fun createUnsupportedRepeatFallback(
    end: MinuteTimeDate?,
    notify: MinuteTimeDate?,
  ): MappedTodoTiming {
    val due = notify ?: end ?: return MappedTodoTiming(ScheduleTiming.Unscheduled, null, null)
    return MappedTodoTiming(
      timing = ScheduleTiming.Deadline(due, TimeZone.currentSystemDefault().id),
      recurrence = null,
      reminderOffset = if (notify != null) 0 else null,
    )
  }

  /**
   * 构造旧重复清单。
   *
   * 旧服务保存的是“下一次通知时间”，优先用它作为新系列锚点；缺失时从今天起按旧选择器寻找下一次。
   * 选择器无效或结束日期不晚于锚点时返回 null，由调用方降为一次性清单。
   */
  private fun createRecurringTodoTiming(
    remindMode: LegacyTodoRemindModeDto,
    end: MinuteTimeDate?,
    notify: MinuteTimeDate?,
    now: MinuteTimeDate,
  ): MappedTodoTiming? {
    if (remindMode.repeatMode == LegacyTodoRemindModeDto.NONE) return null
    val recurrence = createRecurrenceRule(remindMode) ?: return null
    val anchor = notify ?: findNextLegacyOccurrence(remindMode, now, end?.minuteOfDay ?: 0) ?: return null
    if (end != null && end <= anchor) return null
    return MappedTodoTiming(
      timing = ScheduleTiming.Deadline(anchor, TimeZone.currentSystemDefault().id),
      recurrence = recurrence.copy(
        end = end?.let { RecurrenceEnd.Until(it.date) } ?: RecurrenceEnd.Never,
      ),
      reminderOffset = if (notify != null) 0 else null,
    )
  }

  /** 将旧重复选择器精确映射为 Schedule 规则；非法或无法无损表达的选择器不做猜测。 */
  private fun createRecurrenceRule(remindMode: LegacyTodoRemindModeDto): RecurrenceRule? =
    when (remindMode.repeatMode) {
      LegacyTodoRemindModeDto.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY)
      LegacyTodoRemindModeDto.WEEKLY -> remindMode.week
        .mapNotNull(::legacyCalendarDayToIso)
        .toSet()
        .takeIf { it.isNotEmpty() }
        ?.let { RecurrenceRule(RecurrenceFrequency.WEEKLY, byWeekDays = it) }
      LegacyTodoRemindModeDto.MONTHLY -> remindMode.day
        .filter { it in 1..31 }
        .toSet()
        .takeIf { it.isNotEmpty() }
        ?.let { RecurrenceRule(RecurrenceFrequency.MONTHLY, byMonthDays = it) }
      LegacyTodoRemindModeDto.YEARLY -> exactLegacyYearSelectors(remindMode.date)?.let { (months, monthDays) ->
        RecurrenceRule(
          frequency = RecurrenceFrequency.YEARLY,
          byMonthDays = monthDays,
          byMonths = months,
        )
      }
      else -> null
    }

  /**
   * 解析旧年重复的 `M.d` 日期，并确认其能由 RRULE 的 BYMONTH × BYMONTHDAY 精确表达。
   *
   * 例如 `3.8, 4.9` 会被笛卡尔积扩成四天，因此必须降级；`3.8, 4.8` 则可以无损表达。
   */
  private fun exactLegacyYearSelectors(values: List<String>): Pair<Set<Int>, Set<Int>>? {
    val pairs = values.mapNotNull(::parseLegacyMonthDay).toSet()
    if (pairs.isEmpty()) return null
    val months = pairs.mapTo(linkedSetOf()) { it.first }
    val monthDays = pairs.mapTo(linkedSetOf()) { it.second }
    val expanded = months.flatMapTo(linkedSetOf()) { month -> monthDays.map { day -> month to day } }
    return if (expanded == pairs) months to monthDays else null
  }

  /** 解析旧服务年重复使用的 `M.d`，以闰年校验 2 月 29 日等合法日期。 */
  private fun parseLegacyMonthDay(value: String): Pair<Int, Int>? {
    val match = LEGACY_MONTH_DAY_REGEX.matchEntire(value.trim()) ?: return null
    val month = match.groupValues[1].toInt()
    val day = match.groupValues[2].toInt()
    return runCatching { Date(2000, month, day) }.getOrNull()?.let { month to day }
  }

  /**
   * 从当前时刻起找到旧日/周选择器的下一次发生时间。
   *
   * 当天属于选择日但目标时分已经过去时必须继续寻找下一天/周，不能迁出一个已经过期的系列锚点。
   */
  private fun findNextLegacyOccurrence(
    remindMode: LegacyTodoRemindModeDto,
    now: MinuteTimeDate,
    minuteOfDay: Int,
  ): MinuteTimeDate? {
    val candidateDates = when (remindMode.repeatMode) {
      LegacyTodoRemindModeDto.DAILY -> (0..1).asSequence().map(now.date::plusDays)
      LegacyTodoRemindModeDto.WEEKLY -> {
        val days = remindMode.week.mapNotNull(::legacyCalendarDayToIso).toSet()
        (0..7).asSequence().map(now.date::plusDays).filter { candidate ->
          IsoWeekDay.fromIsoNumber(candidate.dayOfWeekNumber) in days
        }
      }
      LegacyTodoRemindModeDto.MONTHLY -> {
        val days = remindMode.day.filter { it in 1..31 }.toSet()
        (0..62).asSequence().map(now.date::plusDays).filter { candidate ->
          candidate.dayOfMonth in days
        }
      }
      LegacyTodoRemindModeDto.YEARLY -> {
        val pairs = remindMode.date.mapNotNull(::parseLegacyMonthDay).toSet()
        // 覆盖闰日从普通年份跨到下一个闰年的最长等待，仍只在一次迁移中做有界扫描。
        (0..1461).asSequence().map(now.date::plusDays).filter { candidate ->
          candidate.monthNumber to candidate.dayOfMonth in pairs
        }
      }
      else -> return null
    }
    val safeMinute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return candidateDates
      .map { date -> MinuteTimeDate(date, safeMinute / 60, safeMinute % 60) }
      .firstOrNull { candidate -> candidate >= now }
  }

  /** java.util.Calendar 的 1=周日、2=周一转换为 ISO 的 1=周一、7=周日。 */
  private fun legacyCalendarDayToIso(day: Int): IsoWeekDay? =
    IsoWeekDay.fromIsoNumber(if (day == 1) 7 else day - 1)

  /** 解析旧客户端固定的“yyyy年M月d日HH:mm”，空串和非法日期返回 null。 */
  internal fun parseLegacyDateTime(value: String?): MinuteTimeDate? {
    val match = LEGACY_DATE_TIME_REGEX.matchEntire(value?.trim().orEmpty()) ?: return null
    return runCatching {
      MinuteTimeDate(
        year = match.groupValues[1].toInt(),
        month = match.groupValues[2].toInt(),
        dayOfMonth = match.groupValues[3].toInt(),
        hour = match.groupValues[4].toInt(),
        minute = match.groupValues[5].toInt(),
      )
    }.getOrNull()
  }

  /** 旧时间戳同时出现过秒和毫秒；无效值只影响显示顺序，使用迁移时刻兜底。 */
  private fun normalizeLegacyTimestamp(value: Long, fallback: Long): Long = when {
    value <= 0 -> fallback
    value < 100_000_000_000L -> value * 1_000L
    else -> value
  }

  /** 固定分类只保留产品语义名称，实际 identity 由协调器结合已同步分类解析。 */
  private fun legacyCategoryName(value: String): String = when (value.trim().lowercase()) {
    "study", "学习" -> "学习"
    "life", "生活" -> "生活"
    else -> "其他"
  }

  /** 为重复系列返回领域锚点日期。 */
  private fun timingDate(timing: ScheduleTiming): Date = when (timing) {
    is ScheduleTiming.Timed -> timing.start.date
    is ScheduleTiming.Deadline -> timing.due.date
    is ScheduleTiming.AllDay -> timing.date
    ScheduleTiming.Unscheduled -> error("Unscheduled cannot anchor recurrence")
  }

  /**
   * 由旧资源特征生成标准 UUIDv5。
   *
   * [identity] 必须包含账号、旧资源类型与旧主键。相同输入在重试、重装和多设备迁移时得到同一个
   * Schedule ID，从而利用服务端主键自然去重；摘要不可反推出这些业务特征。
   */
  internal fun deterministicScheduleUuid(identity: String): String {
    require(identity.isNotBlank()) { "legacy schedule identity must not be blank" }
    return stableUuidV5("com.cyxbs.schedule.migration", identity)
  }

  private data class MappedTodoTiming(
    val timing: ScheduleTiming,
    val recurrence: RecurrenceRule?,
    val reminderOffset: Int?,
  )

  /** 旧事务节次换算后的稳定时间结果。 */
  private data class LegacyTransactionTiming(
    val startMinute: Int,
    val durationMinutes: Int,
  )

  private val LEGACY_DATE_TIME_REGEX =
    Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日(\d{1,2}):(\d{1,2})""")
  private val LEGACY_MONTH_DAY_REGEX = Regex("""(\d{1,2})\.(\d{1,2})""")
  private val LEGACY_START_MINUTES = intArrayOf(
    8 * 60,
    8 * 60 + 55,
    10 * 60 + 15,
    11 * 60 + 10,
    11 * 60 + 55,
    14 * 60,
    14 * 60 + 55,
    16 * 60 + 15,
    17 * 60 + 10,
    17 * 60 + 55,
    19 * 60,
    19 * 60 + 55,
    20 * 60 + 50,
    21 * 60 + 45,
  )
  private val LEGACY_END_MINUTES = intArrayOf(
    8 * 60 + 45,
    9 * 60 + 40,
    11 * 60,
    11 * 60 + 55,
    14 * 60,
    14 * 60 + 45,
    15 * 60 + 40,
    17 * 60,
    17 * 60 + 55,
    19 * 60,
    19 * 60 + 45,
    20 * 60 + 40,
    21 * 60 + 35,
    22 * 60 + 30,
  )
  private const val LEGACY_MAX_COURSE_END_ROW = 13
}

/** 固定默认分类 identity，避免迁移模块依赖 Compose UI 目录。 */
internal val LegacyDefaultCategoryIds: Map<String, CategoryId> = mapOf(
  "学习" to CategoryId("019d0000-0000-7000-8000-000000000001"),
  "生活" to CategoryId("019d0000-0000-7000-8000-000000000002"),
  "其他" to CategoryId("019d0000-0000-7000-8000-000000000003"),
)
