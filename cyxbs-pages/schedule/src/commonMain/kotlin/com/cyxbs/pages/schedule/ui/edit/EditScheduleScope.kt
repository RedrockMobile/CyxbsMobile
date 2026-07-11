package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleOccurrences
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceOverride
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime

/**
 * 重复系列编辑/删除时的「三态」作用域，对应 iOS/Google 日历的经典三选一。
 *
 * 全部为 commonMain 纯逻辑（含两个把表单产物变成仓库入参的纯构造函数），不依赖 Compose，便于单测。
 * 路由到仓库的对应方法：
 * - [THIS_ONLY] → `editThisOccurrence` / `deleteThisOccurrence`
 * - [THIS_AND_FOLLOWING] → `editThisAndFollowing`（原系列截断 + [buildFollowingSeries] 新建）
 * - [ALL] → `updateSchedule` / `deleteSchedule`
 */
enum class EditScope { THIS_ONLY, THIS_AND_FOLLOWING, ALL }

/**
 * 「仅此次」：把表单编辑结果 [edited] 与原始 [origin] 比对，构造对 [occurrenceDate] 这一次的
 * RECURRENCE-ID 覆盖。只填发生了变化的字段（其余为 null = 不变）。
 *
 * @param occurrenceDate 被编辑那一次的原始锚点日期（occurrence.recurrenceId）。
 */
fun buildOccurrenceOverride(
  occurrenceDate: Date,
  edited: ScheduleEntity,
  origin: ScheduleEntity,
): RecurrenceOverride {
  val editedStart = ScheduleOccurrences.parseDateTime(edited.startTime)?.time
  val editedEnd = ScheduleOccurrences.parseDateTime(edited.endTime)?.time
  // 用户若把这一次挪到了别的日期：取编辑后主时间（开始优先，否则截止）的日期，与原锚点不同才算改期。
  val newDate = ScheduleOccurrences.parseDateTime(edited.startTime)?.date
    ?.takeIf { it != occurrenceDate }
  return RecurrenceOverride(
    recurrenceId = occurrenceDate,
    newDate = newDate,
    newStart = editedStart,
    newEnd = editedEnd,
    title = edited.title.takeIf { it != origin.title },
    detail = edited.detail.takeIf { it != origin.detail },
  )
}

/**
 * 「此次及后续」：以 [edited] 的内容、从 [occurrenceDate] 起新建一条独立系列。
 *
 * - 开始/截止时间的「日期」改写为 [occurrenceDate]，保留各自的「时分」；
 * - 重复规则沿用 [edited] 的 rrule，但**丢弃 exdate/overrides/rdate**（新系列从头开始，旧系列的
 *   例外只属于旧系列）；
 * - todoId/lastModifyTime 留待仓库新建时赋值。
 */
fun buildFollowingSeries(edited: ScheduleEntity, occurrenceDate: Date): ScheduleEntity {
  val rule = edited.recurrence?.rrule
  return edited.copy(
    startTime = reanchorDate(edited.startTime, occurrenceDate) ?: "",
    endTime = reanchorDate(edited.endTime, occurrenceDate) ?: "",
    recurrence = rule?.let { Recurrence(rrule = it) },
  )
}

/** 把中文时间串的「日期」部分改写为 [date]，保留原「时分」；空串/无法解析时原样返回。 */
private fun reanchorDate(time: String?, date: Date): String? {
  if (time.isNullOrBlank()) return time
  val mtd = ScheduleOccurrences.parseDateTime(time) ?: return time
  return formatScheduleDateTime(date.year, date.monthNumber, date.dayOfMonth, mtd.time.hour, mtd.time.minute)
}
