package com.cyxbs.pages.schedule.domain.recurrence

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.OccurrenceTime
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator

/**
 * 纯函数、窗口有界的 Schedule 重复规则展开器与单次调整应用器。
 *
 * 展开始终在分钟精度的本地墙上时间空间进行，使系列跨越 DST 后仍保持用户设定的时分；只有掌握
 * IANA 时区的消费方才能转换为瞬时。本引擎不使用 RDATE/EXDATE：取消、完成和移动都由带稳定身份的
 * occurrence 单次调整表达，避免平台日历对子集支持不同而改变领域语义。
 */
object RecurrenceEngine {
  private const val MAX_PERIODS = 100_000

  /**
   * 在严格半开窗口 `[rangeStartInclusive, rangeEndExclusive)` 内展开有效 occurrence。
   *
   * 可见性按例外生效后的实际占用区间判定：`occurrenceStart < rangeEndExclusive &&
   * occurrenceEndExclusive > rangeStartInclusive`。因此开始早于窗口但持续覆盖窗口的实例会保留，
   * 恰好接在窗口边界的实例不会重复；所有比较均留在 [MinuteTimeDate]/[Date] 的本地墙上时间空间。
   * 每个例外仍必须指向原规则真实生成的 identity。identity 只描述原始规则开始时间，而 effective
   * timing 才描述应用 patch 后的显示与占用时间，所以移动实例不会重写 recurrenceId。该 API 可直接接收
   * 尚未经过 Repository 的例外，因此会在展开前校验例外单体和完整 identity 生成性；分类引用存在性仍由
   * 持有完整 envelope 的 Repository/Store 边界负责。单次日期和时间形态允许独立覆盖，不要求与父系列同类型。
   */
  fun expandInRange(
    schedule: Schedule,
    occurrenceAdjustments: List<ScheduleOccurrenceAdjustment>,
    rangeStartInclusive: MinuteTimeDate,
    rangeEndExclusive: MinuteTimeDate,
  ): List<ScheduleOccurrence> {
    require(rangeEndExclusive > rangeStartInclusive) { "range must be non-empty and ordered" }
    require(ScheduleValidator.validate(schedule).isEmpty()) { "schedule is invalid" }
    require(occurrenceAdjustments.map { it.recurrenceId }.distinct().size == occurrenceAdjustments.size) {
      "duplicate occurrence adjustment recurrenceId"
    }

    val recurrence = schedule.recurrence
    if (recurrence == null) {
      require(occurrenceAdjustments.isEmpty()) { "non-recurring schedule cannot have occurrence adjustments" }
      return singleOccurrence(schedule, rangeStartInclusive, rangeEndExclusive)
    }
    require(schedule.timing != ScheduleTiming.Unscheduled) { "unscheduled items cannot recur" }

    val adjustmentMap = occurrenceAdjustments.associateBy { it.recurrenceId }
    // raw 单次调整不能绕过完整 identity 生成性；effective 日期和时间形态随后分别应用字段覆盖。
    occurrenceAdjustments.forEach { requireStructurallyCompatibleAdjustment(schedule, it) }
    val validationEnd = maxOf(
      rangeEndExclusive,
      adjustmentMap.keys.maxOfOrNull { it.originalDateTime } ?: rangeEndExclusive,
    )
    val identityAnchor = identityAnchor(schedule)
    val actualOffsetDays = identityAnchor.date.daysUntil(effectiveStart(schedule.timing).date)
    val generated = generatedStarts(
      identityAnchor,
      recurrence,
      if (actualOffsetDays < 0) validationEnd.plusDays(-actualOffsetDays) else validationEnd,
    )

    return generated.mapNotNull { originalStart ->
      val id = recurrenceId(schedule.timing, originalStart)
      val adjustment = adjustmentMap[id]
      if (adjustment?.status == OccurrenceStatus.CANCELLED) return@mapNotNull null
      occurrence(schedule, id, originalStart, adjustment)
        .takeIf { overlapsRange(it.timing, rangeStartInclusive, rangeEndExclusive) }
    }.sortedBy { effectiveStart(it.timing) }
  }

  /**
   * 按稳定 [recurrenceId] 直接定位一个重复 occurrence，而非借用任意可见性窗口。
   *
   * 会先验证父系列、所有例外的归属/identity/timing 兼容性及目标 identity 的规则生成性，再应用目标
   * patch；返回值保留原 recurrenceId。被取消的实例返回 `null`，调用方不得把取消项误当成可编辑 occurrence。
   */
  internal fun resolveOccurrenceByIdentity(
    schedule: Schedule,
    occurrenceAdjustments: List<ScheduleOccurrenceAdjustment>,
    recurrenceId: RecurrenceId,
  ): ScheduleOccurrence? {
    require(ScheduleValidator.validate(schedule).isEmpty()) { "schedule is invalid" }
    require(schedule.recurrence != null) { "identity requires recurring schedule" }
    require(schedule.timing != ScheduleTiming.Unscheduled) { "unscheduled items cannot recur" }
    require(occurrenceAdjustments.map { it.recurrenceId }.distinct().size == occurrenceAdjustments.size) {
      "duplicate occurrence adjustment recurrenceId"
    }
    occurrenceAdjustments.forEach { requireStructurallyCompatibleAdjustment(schedule, it) }
    val position = requireGeneratedIdentity(schedule, recurrenceId)
    val adjustment = occurrenceAdjustments.firstOrNull { it.recurrenceId == recurrenceId }
    return if (adjustment?.status == OccurrenceStatus.CANCELLED) null
    else occurrence(schedule, recurrenceId, position.originalStart, adjustment)
  }

  /**
   * 判断 [targetId] 是否为有限重复系列中最后一个尚未取消的实例。
   *
   * 该查询只遍历规则生成的 identity，不构造展示用 [ScheduleOccurrence]；遇到第二个未取消实例便立即返回。
   * 无限重复没有“最后一次”，固定返回 `false`。调用方可据此把最后一次单次删除提升为删除父系列，避免
   * 服务端长期保留一个全部由 `CANCELLED` 例外组成的空系列。
   */
  internal fun isOnlyRemainingOccurrence(
    schedule: Schedule,
    occurrenceAdjustments: List<ScheduleOccurrenceAdjustment>,
    targetId: RecurrenceId,
  ): Boolean {
    require(ScheduleValidator.validate(schedule).isEmpty()) { "schedule is invalid" }
    val recurrence = requireNotNull(schedule.recurrence) { "identity requires recurring schedule" }
    if (recurrence.end == RecurrenceEnd.Never) return false
    require(schedule.timing != ScheduleTiming.Unscheduled) { "unscheduled items cannot recur" }
    require(occurrenceAdjustments.map { it.recurrenceId }.distinct().size == occurrenceAdjustments.size) {
      "duplicate occurrence adjustment recurrenceId"
    }
    occurrenceAdjustments.forEach { requireStructurallyCompatibleAdjustment(schedule, it) }
    requireGeneratedIdentity(schedule, targetId)

    val cancelledIds = occurrenceAdjustments.asSequence()
      .filter { it.status == OccurrenceStatus.CANCELLED }
      .mapTo(mutableSetOf()) { it.recurrenceId }
    var remainingId: RecurrenceId? = null
    for (start in generatedStarts(identityAnchor(schedule), recurrence, validationEndInclusive = null)) {
      val candidateId = recurrenceId(schedule.timing, start)
      if (candidateId in cancelledIds) continue
      if (remainingId != null) return false
      remainingId = candidateId
    }
    return remainingId == targetId
  }

  /**
   * 查询并证明 [recurrenceId] 是 [schedule] 的原始规则实例。
   *
   * 返回从零开始的规则 occurrence 序号和前一个原始开始时间，供“此次及后续”拆分维持 COUNT 语义；
   * 此 API 从不应用例外，也不把可见性窗口伪装为 identity 查询。
   */
  internal fun requireGeneratedIdentity(
    schedule: Schedule,
    recurrenceId: RecurrenceId,
  ): RecurrenceIdentityPosition {
    require(ScheduleValidator.validate(schedule).isEmpty()) { "schedule is invalid" }
    val recurrence = requireNotNull(schedule.recurrence) { "identity requires recurring schedule" }
    require(schedule.timing != ScheduleTiming.Unscheduled) { "unscheduled items cannot recur" }
    var previousStart: MinuteTimeDate? = null
    var occurrenceIndex = 0
    for (candidate in generatedStarts(identityAnchor(schedule), recurrence, recurrenceId.originalDateTime)) {
      if (candidate > recurrenceId.originalDateTime) break
      if (candidate == recurrenceId.originalDateTime && recurrenceId(schedule.timing, candidate) == recurrenceId) {
        return RecurrenceIdentityPosition(occurrenceIndex, candidate, previousStart)
      }
      previousStart = candidate
      occurrenceIndex++
    }
    require(false) { "recurrenceId is not generated by RRULE" }
    error("unreachable")
  }

  /**
   * 判断稳定 [recurrenceDate] 当前是否仍由父系列规则生成。
   *
   * 该查询只比较原始规则日期，不应用单次日期/时间覆盖。规则暂时不再包含该日期时，持久化层仍保留单次调整，
   * 但投影层应把它休眠；未来规则重新包含该日期后会自然恢复，不需要墓碑或额外复活状态。
   */
  internal fun containsRecurrenceDate(schedule: Schedule, recurrenceDate: Date): Boolean {
    require(ScheduleValidator.validate(schedule).isEmpty()) { "schedule is invalid" }
    val recurrence = requireNotNull(schedule.recurrence) { "identity requires recurring schedule" }
    require(schedule.timing != ScheduleTiming.Unscheduled) { "unscheduled items cannot recur" }
    val target = MinuteTimeDate(recurrenceDate, identityAnchor(schedule).time)
    return generatedStarts(identityAnchor(schedule), recurrence, target)
      .any { it.date == recurrenceDate }
  }

  /**
   * 校验无需分类集合即可判定的 raw 单次调整关系约束。
   *
   * 这里刻意不依赖 validation 包中的关系校验器，避免 recurrence 与 validation 形成循环依赖；Repository/Store
   * 仍必须使用完整边界补充分类引用校验。
   */
  internal fun requireStructurallyCompatibleAdjustment(
    schedule: Schedule,
    adjustment: ScheduleOccurrenceAdjustment,
  ) {
    require(adjustment.scheduleId == schedule.id) { "adjustment belongs to another schedule" }
    require(ScheduleValidator.validate(adjustment).isEmpty()) { "adjustment is invalid" }
    require(adjustment.status != OccurrenceStatus.COMPLETED || schedule.todoState != null) {
      "an occurrence can be completed only when its parent belongs to todo"
    }
    require(adjustment.recurrenceId.allDay == (schedule.timing is ScheduleTiming.AllDay)) {
      "recurrence identity kind does not match parent timing"
    }
    val parentZone = schedule.timing.zoneOrNull()
    require(adjustment.recurrenceId.timeZoneId == parentZone) {
      "recurrence identity timezone does not match parent"
    }
    // 生成性必须按原规则完整证明，不能只比较 identity 外观或当前查询窗口。
    requireGeneratedIdentity(schedule, adjustment.recurrenceId)
  }

  /** Timed/Deadline 使用 IANA 时区参与身份，AllDay/Unscheduled 没有时区。 */
  private fun ScheduleTiming.zoneOrNull(): String? = when (this) {
    is ScheduleTiming.Timed -> timeZoneId
    is ScheduleTiming.Deadline -> timeZoneId
    is ScheduleTiming.AllDay, ScheduleTiming.Unscheduled -> null
  }

  /** 原始 identity 的规则序号与相邻前驱，不能用于读取移动 patch 后的实际时间。 */
  internal data class RecurrenceIdentityPosition(
    val occurrenceIndex: Int,
    val originalStart: MinuteTimeDate,
    val previousOriginalStart: MinuteTimeDate?,
  )

  /** 非重复日程仅在实际占用区间与严格半开窗口相交时物化唯一一次发生。 */
  private fun singleOccurrence(
    schedule: Schedule,
    rangeStartInclusive: MinuteTimeDate,
    rangeEndExclusive: MinuteTimeDate,
  ): List<ScheduleOccurrence> {
    if (schedule.timing == ScheduleTiming.Unscheduled) return emptyList()
    val occurrence = ScheduleOccurrence(
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
    return listOfNotNull(
      occurrence.takeIf { overlapsRange(it.timing, rangeStartInclusive, rangeEndExclusive) },
    )
  }

  /** 应用稀疏例外，但继续以原始 [id] 作为发生身份，移动时间不会重写身份。 */
  private fun occurrence(
    schedule: Schedule,
    id: RecurrenceId,
    originalStart: MinuteTimeDate,
    adjustment: ScheduleOccurrenceAdjustment?,
  ): ScheduleOccurrence {
    val patch = adjustment?.patch
    val seriesDateOffset = identityAnchor(schedule).date.daysUntil(effectiveStart(schedule.timing).date)
    val inheritedDate = originalStart.date.plusDays(seriesDateOffset)
    val date = patch?.date?.resolve(inheritedDate, clear = null) ?: inheritedDate
    val inheritedTime = schedule.timing.toOccurrenceTime()
    val time = patch?.time?.resolve(inheritedTime, clear = null) ?: inheritedTime
    val timing = time.toTiming(date)
    return ScheduleOccurrence(
      scheduleId = schedule.id,
      recurrenceId = id,
      timing = timing,
      title = patch?.title?.resolve(schedule.title, clear = null) ?: schedule.title,
      description = patch?.description?.resolve(schedule.description, clear = "") ?: schedule.description,
      categoryId = if (patch == null) schedule.categoryId else patch.categoryId.resolve(schedule.categoryId, clear = null),
      // Clear 的结果本身就是 null，不能再用 Elvis 回退成父日程提醒；只有没有 patch 时才继承。
      reminder = if (patch == null) schedule.reminder else patch.reminder.resolve(schedule.reminder, clear = null),
      status = adjustment?.status ?: OccurrenceStatus.ACTIVE,
      isAdjusted = adjustment != null,
    )
  }

  /** 将显式三态应用到系列值；不允许 Clear 的字段由领域校验器提前拒绝。 */
  private fun <T : Any> FieldPatch<T>.resolve(inherited: T?, clear: T?): T? = when (this) {
    FieldPatch.Inherit -> inherited
    FieldPatch.Clear -> clear
    is FieldPatch.Replace -> value
  }

  /** 将父系列当前时间形态转成不携带日期的单次继承值。 */
  private fun ScheduleTiming.toOccurrenceTime(): OccurrenceTime = when (this) {
    is ScheduleTiming.Timed -> OccurrenceTime.TimeRange(start.minuteOfDay, durationMinutes, timeZoneId)
    is ScheduleTiming.Deadline -> OccurrenceTime.TimePoint(due.minuteOfDay, timeZoneId)
    is ScheduleTiming.AllDay -> OccurrenceTime.AllDay
    ScheduleTiming.Unscheduled -> error("unscheduled recurrence was rejected")
  }

  /** 用最终日期物化单次时间形态；单次 TimeRange/TimePoint 自带时区，可跨父系列 kind 修改继续保留。 */
  private fun OccurrenceTime.toTiming(date: Date): ScheduleTiming = when (this) {
    is OccurrenceTime.TimeRange -> ScheduleTiming.Timed(
      start = MinuteTimeDate(date, startMinuteOfDay / 60, startMinuteOfDay % 60),
      durationMinutes = durationMinutes,
      timeZoneId = timeZoneId,
    )
    is OccurrenceTime.TimePoint -> ScheduleTiming.Deadline(
      due = MinuteTimeDate(date, minuteOfDay / 60, minuteOfDay % 60),
      timeZoneId = timeZoneId,
    )
    OccurrenceTime.AllDay -> ScheduleTiming.AllDay(date)
  }

  /**
   * 从稳定 identity 锚点生成至 [validationEndInclusive] 的原始开始时间。
   *
   * 调用方应先完成 identity 校验；这里保留到窗口末端之后的上界只用于让窗口外实例的 patch 有机会迁入。
   */
  private fun generatedStarts(
    anchor: MinuteTimeDate,
    rule: RecurrenceRule,
    validationEndInclusive: MinuteTimeDate?,
  ): List<MinuteTimeDate> {
    val result = ArrayList<MinuteTimeDate>()
    var period = 0
    while (period < MAX_PERIODS) {
      val candidates = candidatesForPeriod(rule, anchor, period).filter { it >= anchor }
      for (candidate in candidates) {
        if (rule.end is RecurrenceEnd.Until && candidate.date > rule.end.date) return result
        if (rule.end is RecurrenceEnd.Count && result.size >= rule.end.value) return result
        if (validationEndInclusive != null && candidate > validationEndInclusive) return result
        result += candidate
      }
      period++
    }
    error("recurrence expansion exceeded $MAX_PERIODS periods")
  }

  /** 为一个日、周、月或年周期生成并排序候选时间，保证跨平台输出顺序稳定。 */
  private fun candidatesForPeriod(
    rule: RecurrenceRule,
    anchor: MinuteTimeDate,
    period: Int,
  ): List<MinuteTimeDate> = when (rule.frequency) {
    RecurrenceFrequency.DAILY -> {
      val date = anchor.date.plusDays(period * rule.interval)
      if (matchesFilters(date, rule)) listOf(MinuteTimeDate(date, anchor.time)) else emptyList()
    }
    RecurrenceFrequency.WEEKLY -> {
      val weekStart = anchor.date.minusDays(anchor.date.dayOfWeekOrdinal)
        .plusWeeks(period * rule.interval)
      val weekDays = (rule.byWeekDays.ifEmpty {
        setOf(IsoWeekDay.fromIsoNumber(anchor.date.dayOfWeekNumber)!!)
      }).sortedBy { it.isoNumber }
      weekDays.map { weekStart.plusDays(it.isoNumber - 1) }
        .filter { rule.byMonths.isEmpty() || it.monthNumber in rule.byMonths }
        .map { MinuteTimeDate(it, anchor.time) }
    }
    RecurrenceFrequency.MONTHLY -> {
      val totalMonths = anchor.date.year * 12 + anchor.date.monthNumber - 1 + period * rule.interval
      val year = totalMonths.floorDiv(12)
      val month = totalMonths.mod(12) + 1
      if (rule.byMonths.isNotEmpty() && month !in rule.byMonths) emptyList()
      else daysInMonth(rule, anchor, year, month)
    }
    RecurrenceFrequency.YEARLY -> {
      val year = anchor.date.year + period * rule.interval
      // 具有扩展型日选择器时需遍历全年，再与 BYMONTH（若有）取交集；裸 YEARLY 才默认 DTSTART 月。
      val months = when {
        rule.byMonths.isNotEmpty() -> rule.byMonths
        rule.byMonthDays.isNotEmpty() || rule.byWeekDays.isNotEmpty() -> (1..12).toSet()
        else -> setOf(anchor.date.monthNumber)
      }
      months.sorted().flatMap { daysInMonth(rule, anchor, year, it) }
    }
  }

  /**
   * 在指定年月内解析 BYMONTHDAY、BYDAY 与默认 DTSTART 日，并对所有显式 selector 取交集。
   *
   * 正负月日先转换成当月日号；像 2 月 30 日这类不存在的值会跳过，而不是滚动到下月。
   */
  private fun daysInMonth(
    rule: RecurrenceRule,
    anchor: MinuteTimeDate,
    year: Int,
    month: Int,
  ): List<MinuteTimeDate> {
    val length = Date.lengthOfMonth(year, month)
    val monthDayCandidates = if (rule.byMonthDays.isEmpty()) null else rule.byMonthDays
      .map { if (it > 0) it else length + it + 1 }
      .filterTo(mutableSetOf()) { it in 1..length }
    val weekDayCandidates = if (rule.byWeekDays.isEmpty()) null else (1..length)
      .filterTo(mutableSetOf()) { day ->
        IsoWeekDay.fromIsoNumber(Date(year, month, day).dayOfWeekNumber) in rule.byWeekDays
      }
    val days = when {
      monthDayCandidates != null && weekDayCandidates != null -> monthDayCandidates intersect weekDayCandidates
      monthDayCandidates != null -> monthDayCandidates
      weekDayCandidates != null -> weekDayCandidates
      else -> setOf(anchor.date.dayOfMonth).filterTo(mutableSetOf()) { it in 1..length }
    }
    return days.sorted().map { MinuteTimeDate(Date(year, month, it), anchor.time) }
  }

  /** 对每日候选应用 BYMONTH、BYDAY 与 BYMONTHDAY 的交集过滤。 */
  private fun matchesFilters(date: Date, rule: RecurrenceRule): Boolean {
    if (rule.byMonths.isNotEmpty() && date.monthNumber !in rule.byMonths) return false
    if (rule.byWeekDays.isNotEmpty() &&
      IsoWeekDay.fromIsoNumber(date.dayOfWeekNumber) !in rule.byWeekDays
    ) return false
    if (rule.byMonthDays.isNotEmpty()) {
      val length = date.lengthOfMonth
      if (rule.byMonthDays.none { (if (it > 0) it else length + it + 1) == date.dayOfMonth }) return false
    }
    return true
  }

  /** 用规则原始墙上开始时间与时间语义构造稳定身份，避免 DST 或移动覆盖改变定位键。 */
  private fun recurrenceId(timing: ScheduleTiming, start: MinuteTimeDate): RecurrenceId = when (timing) {
    is ScheduleTiming.Timed -> RecurrenceId(start, timing.timeZoneId, false)
    is ScheduleTiming.Deadline -> RecurrenceId(start, timing.timeZoneId, false)
    is ScheduleTiming.AllDay -> RecurrenceId(start, null, true)
    ScheduleTiming.Unscheduled -> error("unscheduled recurrence was rejected")
  }

  /**
   * 计算 occurrence 的实际占用区间，并按严格半开公式判断相交。
   *
   * Deadline 不是零宽瞬间，而是固定展示为一分钟 `[due, due + 1 分钟)`；AllDay 只在日期墙上时间中加天，
   * 不把一天换算为 24 小时；Unscheduled 没有有界占用，永不进入窗口。
   */
  private fun overlapsRange(
    timing: ScheduleTiming,
    rangeStartInclusive: MinuteTimeDate,
    rangeEndExclusive: MinuteTimeDate,
  ): Boolean {
    val interval = effectiveInterval(timing) ?: return false
    return interval.startInclusive < rangeEndExclusive &&
      interval.endExclusive > rangeStartInclusive
  }

  /** 用本地墙上时间构造 occurrence 的半开占用区间，不读取平台默认时区。 */
  private fun effectiveInterval(timing: ScheduleTiming): HalfOpenInterval? = when (timing) {
    is ScheduleTiming.Timed -> HalfOpenInterval(timing.start, timing.start.plusMinutes(timing.durationMinutes))
    is ScheduleTiming.Deadline -> HalfOpenInterval(timing.due, timing.due.plusMinutes(1))
    is ScheduleTiming.AllDay -> {
      val start = MinuteTimeDate(timing.date, 0, 0)
      HalfOpenInterval(start, MinuteTimeDate(timing.date.plusDays(1), 0, 0))
    }
    ScheduleTiming.Unscheduled -> null
  }

  /** 仅供内部可见性判断使用的半开区间。 */
  private data class HalfOpenInterval(
    val startInclusive: MinuteTimeDate,
    val endExclusive: MinuteTimeDate,
  )

  /** 提取有效 timing 的本地开始时间；identity 使用原始开始，不能用此值替代 identity。 */
  private fun effectiveStart(timing: ScheduleTiming): MinuteTimeDate = when (timing) {
    is ScheduleTiming.Timed -> timing.start
    is ScheduleTiming.Deadline -> timing.due
    is ScheduleTiming.AllDay -> MinuteTimeDate(timing.date, 0, 0)
    ScheduleTiming.Unscheduled -> error("unscheduled timing has no occurrence start")
  }

  /** 首次锚点只决定 occurrence identity 日期；实际 timing 可以在整系列编辑后相对它平移。 */
  private fun identityAnchor(schedule: Schedule): MinuteTimeDate {
    val timingStart = effectiveStart(schedule.timing)
    return MinuteTimeDate(schedule.recurrenceAnchorDate ?: timingStart.date, timingStart.time)
  }

}
