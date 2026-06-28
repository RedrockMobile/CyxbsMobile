package com.cyxbs.pages.schedule.data.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.recurrence.Occurrence
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.recurrence.RecurrenceEngine

/**
 * [ScheduleEntity] 与 [RecurrenceEngine] 的桥接：
 * 解析中文时间串得到锚点（DTSTART + 时长），并把一条日程展开成区间内的 occurrence。
 *
 * 时间串格式沿用旧端/后端的 "yyyy年M月d日 HH:mm"（允许 H:m、缺省时分）。
 */
object ScheduleOccurrences {

  /** 解析 "2024年1月15日 10:30" 等为 [MinuteTimeDate]；空或非法返回 null。 */
  fun parseDateTime(s: String?): MinuteTimeDate? {
    if (s.isNullOrBlank()) return null
    val nums = Regex("\\d+").findAll(s).map { it.value }.toList()
    if (nums.size < 3) return null
    val year = nums[0].toIntOrNull() ?: return null
    val month = nums[1].toIntOrNull() ?: return null
    val day = nums[2].toIntOrNull() ?: return null
    val hour = nums.getOrNull(3)?.toIntOrNull() ?: 0
    val minute = nums.getOrNull(4)?.toIntOrNull() ?: 0
    return runCatching { MinuteTimeDate(year, month, day, hour, minute) }.getOrNull()
  }

  /** [MinuteTimeDate] 格式化回 "yyyy年M月d日 HH:mm"。 */
  fun formatDateTime(mtd: MinuteTimeDate): String =
    "${mtd.date.year}年${mtd.date.monthNumber}月${mtd.date.dayOfMonth}日 " +
      mtd.time.hour.toString().padStart(2, '0') + ":" +
      mtd.time.minute.toString().padStart(2, '0')

  /**
   * 解析一条日程的锚点：返回 (anchorDate, anchorStart, anchorEnd)。
   * - 截止型(startTime 为空)：anchorStart = null，anchorEnd = endTime 时刻；
   * - 时间段型：anchorStart/anchorEnd 取 start/end；锚点日期取 start 当天。
   * 无有效 endTime（未排期）返回 null。
   */
  fun anchor(todo: ScheduleEntity): Triple<Date, MinuteTime?, MinuteTime>? {
    val end = parseDateTime(todo.endTime) ?: return null
    val start = parseDateTime(todo.startTime)
    val anchorDate = (start ?: end).date
    return Triple(anchorDate, start?.time, end.time)
  }

  /** 把一条日程展开为 [rangeStart, rangeEnd] 内的 occurrence；未排期返回空。 */
  fun expandInRange(todo: ScheduleEntity, rangeStart: Date, rangeEnd: Date): List<Occurrence> {
    val (date, start, end) = anchor(todo) ?: return emptyList()
    val recurrence = todo.recurrence ?: Recurrence()
    return RecurrenceEngine.expandInRange(recurrence, date, start, end, rangeStart, rangeEnd)
  }
}
