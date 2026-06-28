package com.cyxbs.pages.schedule.recurrence

import com.cyxbs.components.config.time.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 重复频率单位，对应 RFC5545 RRULE 的 FREQ。
 *
 * 仅支持日程场景需要的四档；不支持 SECONDLY/MINUTELY/HOURLY。
 */
@Serializable
enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * RFC5545 RRULE 的结构化表示（裁剪子集）。
 *
 * v1 支持：FREQ + INTERVAL + BYDAY + BYMONTHDAY + BYMONTH + UNTIL/COUNT。
 * v1 暂不支持：BYSETPOS / BYWEEKNO / BYYEARDAY / BYDAY 的序号前缀（如 1MO）。
 *
 * @param freq 频率单位
 * @param interval 间隔，单位由 [freq] 决定；如每隔一周 = WEEKLY + interval=2
 * @param byDay 星期，ISO 取值 1..7（周一=1）；仅 WEEKLY/MONTHLY/YEARLY 生效
 * @param byMonthDay 月内日期，1..31，负数表示倒数（-1=当月最后一天）；MONTHLY/YEARLY 生效
 * @param byMonth 月份 1..12；YEARLY 生效
 * @param until 结束日期（含当日），与 [count] 互斥
 * @param count 总发生次数（计入被 EXDATE 删除前的原始发生），与 [until] 互斥
 */
@Serializable
data class RRule(
  @SerialName("freq") val freq: Freq,
  @SerialName("interval") val interval: Int = 1,
  @SerialName("by_day") val byDay: List<Int> = emptyList(),
  @SerialName("by_month_day") val byMonthDay: List<Int> = emptyList(),
  @SerialName("by_month") val byMonth: List<Int> = emptyList(),
  @SerialName("until") val until: Date? = null,
  @SerialName("count") val count: Int? = null,
)
