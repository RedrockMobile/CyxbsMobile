package com.cyxbs.pages.schedule.data.model

import com.cyxbs.pages.schedule.recurrence.Freq
import com.cyxbs.pages.schedule.recurrence.RRule
import com.cyxbs.pages.schedule.recurrence.Recurrence

/**
 * 旧重复模型（[ScheduleRemindMode] 的 repeatMode/week/day/date）到 RFC5545 [Recurrence] 的读时懒迁移。
 *
 * schedule 与旧 todo 共用同一 magipoke-todo 后端：存量数据只有 repeat_mode/week/day，没有 recurrence。
 * 读取时若 [ScheduleEntity.recurrence] 为空且旧字段表示重复，则按规则合成 recurrence，
 * 让 RecurrenceEngine 能正确展开；写回时自然落新结构。
 */
object LegacyRecurrenceMigration {

  fun migrate(todo: ScheduleEntity): ScheduleEntity {
    if (todo.recurrence != null) return todo
    val rm = todo.remindMode
    val rrule: RRule? = when (rm.repeatMode) {
      ScheduleRemindMode.DAY -> RRule(Freq.DAILY)
      ScheduleRemindMode.WEEK -> RRule(Freq.WEEKLY, byDay = rm.week) // week 为 1..7 协议值
      ScheduleRemindMode.MONTH -> RRule(Freq.MONTHLY, byMonthDay = rm.day)
      ScheduleRemindMode.YEAR -> {
        val months = mutableListOf<Int>()
        val days = mutableListOf<Int>()
        rm.date.forEach { s ->
          val p = s.split(".")
          if (p.size == 2) {
            val m = p[0].toIntOrNull()
            val d = p[1].toIntOrNull()
            if (m != null && d != null) { months.add(m); days.add(d) }
          }
        }
        RRule(Freq.YEARLY, byMonth = months.distinct(), byMonthDay = days.distinct())
      }
      else -> null // NONE：单次，recurrence 保持 null
    }
    return if (rrule != null) todo.copy(recurrence = Recurrence(rrule = rrule)) else todo
  }

  /** 批量迁移。 */
  fun migrate(todos: List<ScheduleEntity>): List<ScheduleEntity> = todos.map(::migrate)
}
