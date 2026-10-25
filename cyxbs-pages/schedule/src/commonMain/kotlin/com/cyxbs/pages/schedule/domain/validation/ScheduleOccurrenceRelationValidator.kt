package com.cyxbs.pages.schedule.domain.validation

import com.cyxbs.pages.schedule.domain.model.FieldPatch
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceAdjustment
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.recurrence.RecurrenceEngine

/**
 * 校验单次调整与父 Schedule、分类集合之间的关系约束。
 *
 * 该边界同时供本地 Store 解码和 Repository 写入复用，避免两处对 recurrence identity、时间类型与时区的
 * 判定逐渐漂移。调用前仍应分别执行 [ScheduleValidator] 的单体校验。
 */
object ScheduleOccurrenceRelationValidator {

  /**
   * 校验 [adjustment] 确实属于 [parent] 的某次 RRULE occurrence，并验证字段补丁引用及时间替换边界。
   * [categoryIds] 是当前 envelope 中真实存在的分类 ID；失败以 require 异常 fail-closed。
   */
  fun requireValid(
    parent: Schedule,
    adjustment: ScheduleOccurrenceAdjustment,
    categoryIds: Set<String>,
  ) {
    require(parent.id == adjustment.scheduleId) { "occurrence adjustment parent does not match scheduleId" }
    require(parent.recurrence != null) { "occurrence adjustment requires recurring schedule" }
    require(adjustment.status != OccurrenceStatus.COMPLETED || parent.todoState != null) {
      "an occurrence can be completed only when its parent belongs to todo"
    }
    require(parent.categoryId == null || parent.categoryId.value in categoryIds) { "schedule category not found" }
    if (adjustment.patch?.categoryId is FieldPatch.Replace) {
      require(adjustment.patch.categoryId.value.value in categoryIds) { "patch category not found" }
    }
    // identity 仅验证原规则生成性；日期/时间覆盖独立应用，不能以 effective timing 反推 identity。
    RecurrenceEngine.requireStructurallyCompatibleAdjustment(parent, adjustment)
  }
}
