package com.cyxbs.pages.notification.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.api.ScheduleExternalRecurrence
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import kotlinx.datetime.TimeZone

/** 没课约旧节次 DTO 转换后的日程时间和可选重复规则。 */
internal data class ItineraryScheduleTiming(
  val timing: ScheduleOccurrenceTiming.Timed,
  val recurrence: ScheduleExternalRecurrence?,
)

/**
 * 没课约传递的一组学期节次。
 *
 * [week] 为 0 表示整学期同一星期重复，正数表示指定教学周。该结构只表达一个
 * [beginLesson] + [period]，不会生成多个日期与多个时间段的交叉组合。
 */
internal data class ItineraryScheduleSlot(
  val beginLesson: Int,
  val day: Int,
  val period: Int,
  val week: Int,
)

/**
 * 将没课约的一组学期节次转换为新日程使用的绝对时间段。
 *
 * @param firstMonday 当前学期第一周星期一，缺失时无法还原绝对日期。
 * @param maxWeek 整学期重复时使用的教学周数。
 * @return 可用的日程时间；输入越界或学期信息缺失时返回 null，由调用方提示添加失败。
 */
internal fun ItineraryScheduleSlot.toScheduleTiming(
  firstMonday: Date?,
  maxWeek: Int,
): ItineraryScheduleTiming? {
  val semesterFirstMonday = firstMonday ?: return null
  if (day !in 1..7 || week < 0 || period <= 0) return null
  val startRow = when (beginLesson) {
    in 1..4 -> beginLesson - 1
    -1 -> 4
    in 5..8 -> beginLesson
    -2 -> 9
    in 9..12 -> beginLesson + 1
    else -> return null
  }
  val endRow = startRow + period - 1
  if (startRow !in START_MINUTES.indices || endRow !in END_MINUTES.indices) return null

  val occurrenceWeek = if (week == 0) 1 else week
  val date = semesterFirstMonday.plusDays((occurrenceWeek - 1) * 7 + day - 1)
  val startMinute = START_MINUTES[startRow]
  val endMinute = END_MINUTES[endRow]
  if (endMinute <= startMinute) return null
  val timing = ScheduleOccurrenceTiming.Timed(
    start = MinuteTimeDate(date, startMinute / 60, startMinute % 60),
    durationMinutes = endMinute - startMinute,
    timeZoneId = TimeZone.currentSystemDefault().id,
  )
  val recurrence = if (week == 0) {
    maxWeek.takeIf { it > 0 }?.let(ScheduleExternalRecurrence::Weekly)
      ?: return null
  } else null
  return ItineraryScheduleTiming(timing, recurrence)
}

/**
 * 旧事务把午间、傍晚也当成独立时间轴行，普通课程行则按教学节次排列。
 *
 * 两组数组必须保持相同索引：开始取选中首行，结束取选中末行，才能兼容跨越课间和大课间的旧行程。
 */
private val START_MINUTES = intArrayOf(
  8 * 60,
  8 * 60 + 55,
  10 * 60 + 15,
  11 * 60 + 10,
  11 * 60 + 55,
  14 * 60,
  14 * 60 + 55,
  16 * 60 + 15,
  17 * 60 + 10,
  17 * 60 + 55,
  19 * 60,
  19 * 60 + 55,
  20 * 60 + 50,
  21 * 60 + 45,
)

private val END_MINUTES = intArrayOf(
  8 * 60 + 45,
  9 * 60 + 40,
  11 * 60,
  11 * 60 + 55,
  14 * 60,
  14 * 60 + 45,
  15 * 60 + 40,
  17 * 60,
  17 * 60 + 55,
  19 * 60,
  19 * 60 + 45,
  20 * 60 + 40,
  21 * 60 + 35,
  22 * 60 + 30,
)
