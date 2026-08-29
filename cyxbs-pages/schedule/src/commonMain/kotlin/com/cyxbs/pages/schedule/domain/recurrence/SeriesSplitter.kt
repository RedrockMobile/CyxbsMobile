package com.cyxbs.pages.schedule.domain.recurrence

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming

/** “此次及后续”拆分结果：两个有效系列，以及按原始 occurrence identity 分区的例外。 */
data class SeriesSplitResult(
  val previousSchedule: Schedule,
  val followingSchedule: Schedule,
  val previousExceptions: List<ScheduleOccurrenceException>,
  val followingExceptions: List<ScheduleOccurrenceException>,
)

/**
 * 在已有 [RecurrenceId] 边界拆分重复系列的纯领域操作。
 *
 * 旧系列在边界前终止，新系列从相同原始墙上时刻开始，因此 occurrence identity 保持稳定。例外按原始
 * 身份分区；迁入新系列的例外只更换 schedule ID，不重写 recurrence ID，防止 DST 或移动覆盖导致丢失。
 *
 * 该纯 API 会验证例外单体、scheduleId、完整 RRULE identity 生成性，以及替换 timing 的类型/时区兼容性。
 * 分类引用需要 envelope 中的 categoryIds 才能判定，不能在这里伪造集合，仍由 Repository/Store 完整边界负责。
 */
object SeriesSplitter {
  /** 判断 [boundary] 是否是可拆分的非首个有效 occurrence；非法 identity 返回 false。 */
  fun canSplitAt(schedule: Schedule, boundary: RecurrenceId): Boolean =
    runCatching { RecurrenceEngine.requireGeneratedIdentity(schedule, boundary).occurrenceIndex > 0 }
      .getOrDefault(false)

  /**
   * 在 [boundary] 前截断重复系列。
   *
   * 边界本身不再属于返回系列；首次 occurrence 不能截断，否则会产生没有任何实例的空系列。
   */
  fun truncateBefore(schedule: Schedule, boundary: RecurrenceId): Schedule {
    require(schedule.recurrence != null) { "only recurring schedules can be truncated" }
    val boundaryPosition = RecurrenceEngine.requireGeneratedIdentity(schedule, boundary)
    require(boundaryPosition.occurrenceIndex > 0) { "truncate boundary must follow the first occurrence" }
    val previousStart = requireNotNull(boundaryPosition.previousOriginalStart) {
      "truncate boundary must have a previous occurrence"
    }
    return schedule.copy(
      recurrence = schedule.recurrence.copy(end = RecurrenceEnd.Until(previousStart.date)),
      todoState = schedule.todoState?.let { ScheduleTodoState.PENDING },
    )
  }

  /**
   * 在 [boundary] 拆分 [schedule]。
   *
   * 首次发生不能作为拆分点，因为那会留下空旧系列；调用方应在该情形改为编辑整个系列。
   */
  fun split(
    schedule: Schedule,
    exceptions: List<ScheduleOccurrenceException>,
    boundary: RecurrenceId,
    followingId: ScheduleId,
  ): SeriesSplitResult {
    require(schedule.recurrence != null) { "only recurring schedules can be split" }
    require(followingId != schedule.id) { "following series requires a new ScheduleId" }
    require(exceptions.map { it.recurrenceId }.distinct().size == exceptions.size) {
      "duplicate exception recurrenceId"
    }
    // 必须在分区及改写 scheduleId 前验证原始输入，防止 forged identity 或不兼容 timing 被迁入新系列。
    exceptions.forEach { RecurrenceEngine.requireStructurallyCompatibleException(schedule, it) }
    // identity 查询与窗口可见性解耦：拆分必须按原规则序号找前驱，不能受时长、移动 patch 或半开窗口影响。
    val boundaryPosition = RecurrenceEngine.requireGeneratedIdentity(schedule, boundary)
    require(boundaryPosition.occurrenceIndex > 0) { "split boundary must follow the first occurrence" }
    val previousStart = requireNotNull(boundaryPosition.previousOriginalStart) {
      "split boundary must have a previous occurrence"
    }
    val newEnd = when (val end = schedule.recurrence.end) {
      is RecurrenceEnd.Count -> RecurrenceEnd.Count(end.value - boundaryPosition.occurrenceIndex).also {
        require(it.value > 0) { "split boundary lies beyond COUNT" }
      }
      else -> end
    }
    val newTiming = moveTiming(schedule.timing, boundary.originalDateTime)
    // 复用上面已经定位出的前驱，避免边界离 anchor 很远时重复展开一次规则。
    val previousSchedule = schedule.copy(
      recurrence = schedule.recurrence.copy(end = RecurrenceEnd.Until(previousStart.date)),
      todoState = schedule.todoState?.let { ScheduleTodoState.PENDING },
    )
    val followingSchedule = schedule.copy(
      id = followingId,
      revision = 0,
      timing = newTiming,
      recurrence = schedule.recurrence.copy(end = newEnd),
      todoState = schedule.todoState?.let { ScheduleTodoState.PENDING },
      // 新系列沿用边界 occurrence 的稳定日期 identity；实际 timing 后续可由编辑结果独立移动。
      recurrenceAnchorDate = boundary.originalDateTime.date,
    )

    val (before, after) = exceptions.partition {
      it.recurrenceId.originalDateTime < boundary.originalDateTime
    }
    return SeriesSplitResult(
      previousSchedule = previousSchedule,
      followingSchedule = followingSchedule,
      previousExceptions = before,
      followingExceptions = after.map { it.copy(scheduleId = followingId) },
    )
  }

  /** 只移动 DTSTART 语义；时长与 IANA 时区保持不变，避免拆分悄然改变事件含义。 */
  private fun moveTiming(timing: ScheduleTiming, start: MinuteTimeDate): ScheduleTiming = when (timing) {
    is ScheduleTiming.Timed -> timing.copy(start = start)
    is ScheduleTiming.Deadline -> timing.copy(due = start)
    is ScheduleTiming.AllDay -> timing.copy(startDate = start.date)
    ScheduleTiming.Unscheduled -> error("unscheduled series cannot be split")
  }
}
