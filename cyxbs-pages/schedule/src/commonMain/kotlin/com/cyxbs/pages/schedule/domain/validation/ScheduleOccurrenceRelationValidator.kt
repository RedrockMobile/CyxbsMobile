package com.cyxbs.pages.schedule.domain.validation

import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine

/**
 * 校验单次重复实例例外与父 Schedule、分类集合之间的关系约束。
 *
 * 该边界同时供本地 Store 解码和 Repository 写入复用，避免两处对 recurrence identity、时间类型与时区的
 * 判定逐渐漂移。调用前仍应分别执行 [ScheduleValidator] 的单体校验。
 */
object ScheduleOccurrenceRelationValidator {

  /**
   * 校验 [exception] 确实属于 [parent] 的某次 RRULE occurrence，并验证 patch 引用及时间替换边界。
   * [categoryIds] 是当前 envelope 中真实存在的分类 ID；失败以 require 异常 fail-closed。
   */
  fun requireValid(
    parent: Schedule,
    exception: ScheduleOccurrenceException,
    categoryIds: Set<String>,
  ) {
    require(parent.id == exception.scheduleId) { "occurrence exception parent does not match scheduleId" }
    require(parent.recurrence != null) { "occurrence exception requires recurring schedule" }
    require(parent.categoryId == null || parent.categoryId.value in categoryIds) { "schedule category not found" }
    if (exception.patch?.categoryId is FieldPatch.Replace) {
      require(exception.patch.categoryId.value.value in categoryIds) { "patch category not found" }
    }
    require(exception.recurrenceId.allDay == (parent.timing is ScheduleTiming.AllDay)) {
      "recurrence identity kind does not match parent timing"
    }
    val parentZone = parent.timing.zoneOrNull()
    require(parent.timing != ScheduleTiming.Unscheduled) { "unscheduled schedule cannot recur" }
    require(exception.recurrenceId.timeZoneId == parentZone) {
      "recurrence identity timezone does not match parent"
    }
    if (exception.patch?.timing is FieldPatch.Replace) {
      val replacement = exception.patch.timing.value
      require(replacement != ScheduleTiming.Unscheduled) { "occurrence timing cannot become unscheduled" }
      require(replacement::class == parent.timing::class) { "occurrence timing kind must match parent" }
      require(replacement.zoneOrNull() == parentZone) { "occurrence timing timezone must match parent" }
    }
    // identity 仅验证原规则生成性；不应用 patch，也不以半开可见性窗口替代 identity 查询。
    RecurrenceEngine.requireGeneratedIdentity(parent, exception.recurrenceId)
  }

  /** 全天没有时区；Timed/Deadline 的时区是 occurrence identity 与替换 timing 的共同约束。 */
  private fun ScheduleTiming.zoneOrNull(): String? = when (this) {
    is ScheduleTiming.Timed -> timeZoneId
    is ScheduleTiming.Deadline -> timeZoneId
    is ScheduleTiming.AllDay, ScheduleTiming.Unscheduled -> null
  }
}
