package com.cyxbs.pages.schedule.data.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceOverride

/**
 * 日程重复编辑的纯函数（不含持久化/时间戳/全局依赖，便于单测）。
 *
 * 仓库层只负责：加载 → 调用这里的纯变换 → 打 lastModifyTime 并落盘。
 */
object ScheduleMutations {

  /** 是否为重复日程（有 rrule 或 rdate）。 */
  fun isRecurring(todo: ScheduleEntity): Boolean {
    val r = todo.recurrence ?: return false
    return r.rrule != null || r.rdate.isNotEmpty()
  }

  /** 把某一次加入 EXDATE（删除/完成某一次）。 */
  fun addExdate(todo: ScheduleEntity, occurrenceDate: Date): ScheduleEntity {
    val rec = todo.recurrence ?: Recurrence()
    if (occurrenceDate in rec.exdate) return todo
    return todo.copy(recurrence = rec.copy(exdate = rec.exdate + occurrenceDate))
  }

  /** 写入/替换某一次的 override（仅此次编辑）；按 recurrenceId 去重。 */
  fun applyOverride(todo: ScheduleEntity, patch: RecurrenceOverride): ScheduleEntity {
    val rec = todo.recurrence ?: Recurrence()
    val overrides = rec.overrides.filterNot { it.recurrenceId == patch.recurrenceId } + patch
    return todo.copy(recurrence = rec.copy(overrides = overrides))
  }

  /**
   * 「此次及后续」中对原系列的截断：UNTIL 设到 [occurrenceDate] 前一天、清空 count，
   * 并丢弃截断点之后的 rdate/exdate/override。无 recurrence 时原样返回。
   */
  fun truncateBefore(todo: ScheduleEntity, occurrenceDate: Date): ScheduleEntity {
    val rec = todo.recurrence ?: return todo
    val cut = occurrenceDate.minusDays(1)
    return todo.copy(
      recurrence = rec.copy(
        rrule = rec.rrule?.copy(until = cut, count = null),
        rdate = rec.rdate.filter { it <= cut },
        exdate = rec.exdate.filter { it <= cut },
        overrides = rec.overrides.filter { it.recurrenceId <= cut },
      ),
    )
  }
}
