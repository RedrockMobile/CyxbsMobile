package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一条日程的完整重复定义（RFC5545 子集）。
 *
 * - [rrule] 为 null 且 [rdate] 为空 表示单次日程（不重复）。
 * - [rrule] 规则型重复；[rdate] 额外枚举的散点日期；二者取并集。
 * - [exdate] 从展开结果中剔除的日期（删除某一次 / 完成某一次）。
 * - [overrides] 对某一次的改写（仅此次编辑 / 移动单次）。
 */
@Serializable
data class Recurrence(
  @SerialName("rrule") val rrule: RRule? = null,
  @SerialName("rdate") val rdate: List<Date> = emptyList(),
  @SerialName("exdate") val exdate: List<Date> = emptyList(),
  @SerialName("overrides") val overrides: List<RecurrenceOverride> = emptyList(),
)

/**
 * 对重复系列中某一次的改写，对应 RFC5545 的 RECURRENCE-ID 覆盖。
 *
 * @param recurrenceId 命中的原始 occurrence 日期（系列锚点意义上的那一天）
 * @param newDate 改期后的新日期；null 表示日期不变
 * @param newStart 改后的开始时间；null 表示不变
 * @param newEnd 改后的结束时间；null 表示不变
 * @param title 改后的标题；null 表示不变
 * @param detail 改后的备注；null 表示不变
 * @param cancelled 为 true 时等价于把该次加入 EXDATE（保留语义信息）
 */
@Serializable
data class RecurrenceOverride(
  @SerialName("recurrence_id") val recurrenceId: Date,
  @SerialName("new_date") val newDate: Date? = null,
  @SerialName("new_start") val newStart: MinuteTime? = null,
  @SerialName("new_end") val newEnd: MinuteTime? = null,
  @SerialName("title") val title: String? = null,
  @SerialName("detail") val detail: String? = null,
  @SerialName("cancelled") val cancelled: Boolean = false,
)
