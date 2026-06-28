package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime

/**
 * 重复规则展开后的单次发生（运行时对象，不参与序列化）。
 *
 * @param date 该次发生的日期
 * @param start 开始时间；null 表示截止型（只有一个时刻，课表上画标记线/点）
 * @param end 结束时间（截止型时即该时刻）
 * @param isOverridden 是否被 [RecurrenceOverride] 改写过
 * @param recurrenceId 对应的原始系列锚点日期（override 改期后仍指向原锚点），
 *                     用于"仅此次/删此次"等按原始日期定位的操作
 */
data class Occurrence(
  val date: Date,
  val start: MinuteTime?,
  val end: MinuteTime,
  val isOverridden: Boolean,
  val recurrenceId: Date,
)
