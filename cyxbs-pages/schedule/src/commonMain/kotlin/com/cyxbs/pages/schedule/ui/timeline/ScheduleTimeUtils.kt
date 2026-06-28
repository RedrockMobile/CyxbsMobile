package com.cyxbs.pages.schedule.ui.timeline

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleRemindMode
import com.cyxbs.pages.schedule.data.model.ScheduleRemindMode.Companion
import com.cyxbs.pages.schedule.data.model.ScheduleRemindMode.Companion.NONE

/**
 * todo 时间字符串解析与「所属日期」派生工具。
 *
 * todo 的时间字段（[ScheduleEntity.startTime] / [ScheduleEntity.endTime]）沿用老端中文格式
 * `"yyyy年M月d日 HH:mm"`（与 `ui/edit/EditScheduleDialog.kt` 写入格式一致），月/日/时/分均可能不补零。
 * 本文件全部为 commonMain 纯函数，不依赖任何平台 API，便于单测。
 */

/**
 * 解析结果。
 *
 * @param date 日期部分（一定存在）。
 * @param minuteOfDay 当天的分钟数 `0..1439`；当字符串只有日期、没有时分时为 null。
 */
data class ScheduleDateTime(
  val date: Date,
  val minuteOfDay: Int?,
)

/**
 * 解析 `"yyyy年M月d日 HH:mm"`（时分可缺省）。无法解析返回 null。
 *
 * 不使用 JVM-only 的 `SimpleDateFormat` / `String.format`，纯手动 split。
 */
fun parseScheduleDateTime(raw: String?): ScheduleDateTime? {
  if (raw.isNullOrBlank()) return null
  val s = raw.trim()
  val yi = s.indexOf('年')
  val mi = s.indexOf('月')
  val di = s.indexOf('日')
  if (yi <= 0 || mi <= yi || di <= mi) return null
  val year = s.substring(0, yi).trim().toIntOrNull() ?: return null
  val month = s.substring(yi + 1, mi).trim().toIntOrNull() ?: return null
  val day = s.substring(mi + 1, di).trim().toIntOrNull() ?: return null
  val date = runCatching { Date(year, month, day) }.getOrNull() ?: return null

  val timeStr = s.substring(di + 1).trim()
  val minuteOfDay = if (timeStr.isEmpty()) {
    null
  } else {
    val parts = timeStr.split(':', '：')
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull()
    val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    if (h == null || h !in 0..23 || m !in 0..59) null else h * 60 + m
  }
  return ScheduleDateTime(date, minuteOfDay)
}

/** 是否为「时间段类型」：同时存在开始与结束时间。 */
fun ScheduleEntity.isInterval(): Boolean = !startTime.isNullOrBlank() && endTime.isNotBlank()

/** 是否为「未排期」：既没有开始时间也没有结束时间，无所属日期。 */
fun ScheduleEntity.isUnscheduled(): Boolean = startTime.isNullOrBlank() && endTime.isBlank()

/**
 * 该 todo 所属的日期：
 * - 时间段类型取开始时间的日期；
 * - 截止类型取结束时间的日期；
 * - 未排期或无法解析返回 null。
 */
fun ScheduleEntity.ownerDate(): Date? {
  return if (!startTime.isNullOrBlank()) {
    parseScheduleDateTime(startTime)?.date
  } else {
    parseScheduleDateTime(endTime)?.date
  }
}

/**
 * 时间轴上的一条「有时刻」事件。
 *
 * @param isInterval true=时间段区间块；false=截止粗线。
 * @param startMin 起始分钟（截止类型等于 [endMin]）。
 * @param endMin 结束分钟；区间块保证 `endMin > startMin`。
 */
data class DayTimedSchedule(
  val todo: ScheduleEntity,
  val isInterval: Boolean,
  val startMin: Int,
  val endMin: Int,
)

/** 区间块在 endMin <= startMin 或跨天时，给一个最小可视时长（分钟）。 */
private const val MIN_INTERVAL_MINUTES = 30

/** 整天分钟数（0..1440）。无时刻待办铺成跨 0-24 点区间块时用它当 endMin。 */
internal const val FULL_DAY_MINUTES = 24 * 60

/**
 * 把「无具体时刻」的待办（当日全天 / 未排期）表示成跨 0-24 点的整日区间块，
 * 与有时刻事件一起进入列分配（[layoutTimedSchedules]），并排显示在时间轴里。
 *
 * 全天块 `startMin=0`，排序后落在最左列；可用 [DayTimedSchedule.isFullDay] 判断。
 */
internal fun fullDayBlocks(todos: List<ScheduleEntity>): List<DayTimedSchedule> =
  todos.map { DayTimedSchedule(it, isInterval = true, startMin = 0, endMin = FULL_DAY_MINUTES) }

/** 是否为跨 0-24 点的整日块（当日 / 未排期）。 */
internal fun DayTimedSchedule.isFullDay(): Boolean = isInterval && startMin <= 0 && endMin >= FULL_DAY_MINUTES

/**
 * 取某天「有具体时刻」的事件（用于时间轴），按 [DayTimedSchedule.startMin] 升序。
 *
 * - 时间段类型：开始/结束都需在同一天且开始有时分；结束分钟缺省或不晚于开始时，用 [MIN_INTERVAL_MINUTES] 兜底。
 * - 截止类型：结束时间在该天且有时分。
 */
internal fun timedSchedulesForDate(all: List<ScheduleEntity>, date: Date): List<DayTimedSchedule> {
  val result = ArrayList<DayTimedSchedule>()
  for (todo in all) {
    if (todo.isDone == 1) continue
    if (!startTimeIsInterval(todo)) {
      // 截止类型
      val dt = parseScheduleDateTime(todo.endTime) ?: continue
      if (dt.date != date) continue
      val min = dt.minuteOfDay ?: continue
      result += DayTimedSchedule(todo, isInterval = false, startMin = min, endMin = min)
    } else {
      val start = parseScheduleDateTime(todo.startTime) ?: continue
      if (start.date != date) continue
      val startMin = start.minuteOfDay ?: continue
      val end = parseScheduleDateTime(todo.endTime)
      val rawEndMin = end?.minuteOfDay?.takeIf { end.date == date }
      val endMin = if (rawEndMin == null || rawEndMin <= startMin) {
        (startMin + MIN_INTERVAL_MINUTES).coerceAtMost(24 * 60)
      } else {
        rawEndMin
      }
      result += DayTimedSchedule(todo, isInterval = true, startMin = startMin, endMin = endMin)
    }
  }
  result.sortBy { it.startMin }
  return result
}

/**
 * 取某天「有日期但无具体时刻」的待办（在时间轴顶部以「全天」形式展示）。
 *
 * 例如截止类型只写了日期没写时分的旧数据。
 */
internal fun allDaySchedulesForDate(all: List<ScheduleEntity>, date: Date): List<ScheduleEntity> {
  return all.filter { todo ->
    if (todo.isDone == 1 || todo.isUnscheduled()) return@filter false
    val owner = todo.ownerDate() ?: return@filter false
    if (owner != date) return@filter false
    // 没有任何时分才算「全天」
    val hasClock = if (startTimeIsInterval(todo)) {
      parseScheduleDateTime(todo.startTime)?.minuteOfDay != null
    } else {
      parseScheduleDateTime(todo.endTime)?.minuteOfDay != null
    }
    !hasClock
  }
}

/** 全部未完成且未排期的待办（常驻「未排期」区，不随日期过滤）。 */
internal fun unscheduledSchedules(all: List<ScheduleEntity>): List<ScheduleEntity> {
  return all.filter { it.isDone == 0 && it.isUnscheduled() }
}

/** 格式化时间字符串，对齐老端 `"yyyy年M月d日 HH:mm"`（时分补零）。 */
internal fun formatScheduleDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
  val h = hour.toString().padStart(2, '0')
  val m = minute.toString().padStart(2, '0')
  return "${year}年${month}月${day}日 $h:$m"
}

/** 移除第 [index] 个重复项；移除后为空则切回 [ScheduleRemindMode.NONE]。 */
internal fun removeRepeatAt(remindMode: ScheduleRemindMode, index: Int): ScheduleRemindMode {
  return when (remindMode.repeatMode) {
    ScheduleRemindMode.DAY -> remindMode.copy(repeatMode = ScheduleRemindMode.NONE)
    ScheduleRemindMode.WEEK -> {
      val newWeek = remindMode.week.toMutableList().apply { if (index in indices) removeAt(index) }
      if (newWeek.isEmpty()) remindMode.copy(repeatMode = ScheduleRemindMode.NONE, week = emptyList())
      else remindMode.copy(week = newWeek)
    }
    ScheduleRemindMode.MONTH -> {
      val newDay = remindMode.day.toMutableList().apply { if (index in indices) removeAt(index) }
      if (newDay.isEmpty()) remindMode.copy(repeatMode = ScheduleRemindMode.NONE, day = emptyList())
      else remindMode.copy(day = newDay)
    }
    else -> remindMode
  }
}

/** 把 [ScheduleRemindMode] 展开成可显示的 chip 标签列表。 */
fun buildRepeatLabels(remindMode: ScheduleRemindMode): List<String> =
  when (remindMode.repeatMode) {
    ScheduleRemindMode.DAY -> listOf("每天")
    ScheduleRemindMode.WEEK -> remindMode.week.map { "周${weekDigitToChinese(it)}" }
    ScheduleRemindMode.MONTH -> remindMode.day.map { "每月${it}日" }
    else -> emptyList()
  }

private fun weekDigitToChinese(digit: Int): String = when (digit) {
  1 -> "一"
  2 -> "二"
  3 -> "三"
  4 -> "四"
  5 -> "五"
  6 -> "六"
  7 -> "日"
  else -> ""
}

private fun startTimeIsInterval(todo: ScheduleEntity): Boolean =
  !todo.startTime.isNullOrBlank()
