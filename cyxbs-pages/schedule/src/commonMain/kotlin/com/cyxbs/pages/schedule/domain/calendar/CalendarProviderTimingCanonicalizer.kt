package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.config.time.toMinuteTimeDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Android Calendar Provider 回读值的纯 canonicalization 边界。
 *
 * Provider 能保存秒和毫秒，而 Schedule 领域时间只精确到分钟。这里必须先证明平台值可无损映射，
 * 再构造 [CalendarTiming]；任何近似截断都会让外部修改被错误判为 `NoOp`，因此统一返回 `null`
 * 让 gateway 安全终止本轮对账。
 */
internal object CalendarProviderTimingCanonicalizer {
  private const val MILLIS_PER_MINUTE = 60_000L
  private const val MINUTES_PER_DAY = 24L * 60L
  private const val MILLIS_PER_DAY = MINUTES_PER_DAY * MILLIS_PER_MINUTE

  /**
   * 从 Provider 字段重建规范时间。
   *
   * [recurring] 冻结 `单次 = DTEND`、`重复 = DURATION` 的写入形状，避免同时存在或缺失字段时猜测
   * Provider 意图；[projectionKind] 只允许 canonical URI 明确标记的 Deadline 使用零时长语义。
   */
  fun reconstructOrNull(
    dtStart: Long?,
    dtEnd: Long?,
    duration: String?,
    timeZoneId: String?,
    allDay: Boolean,
    recurring: Boolean,
    projectionKind: CalendarProjectionKind,
  ): CalendarTiming? {
    val startMillis = dtStart ?: return null
    if (recurring == (dtEnd != null) || recurring == (duration == null)) return null

    return if (allDay) {
      reconstructAllDay(startMillis, dtEnd, duration, timeZoneId, recurring)
    } else {
      reconstructTimed(startMillis, dtEnd, duration, timeZoneId, recurring, projectionKind)
    }
  }

  /** 全天事件只接受 UTC 午夜和正整数天，不使用设备默认时区或固定日期猜测。 */
  private fun reconstructAllDay(
    dtStart: Long,
    dtEnd: Long?,
    duration: String?,
    timeZoneId: String?,
    recurring: Boolean,
  ): CalendarTiming.AllDay? {
    if (timeZoneId != TimeZone.UTC.id || !dtStart.isUtcMidnight()) return null
    val durationDays = if (recurring) {
      parseDurationDays(duration)
    } else {
      val endMillis = dtEnd ?: return null
      if (!endMillis.isUtcMidnight() || endMillis <= dtStart) return null
      ((endMillis / MILLIS_PER_DAY) - (dtStart / MILLIS_PER_DAY)).toPositiveIntOrNull()
    } ?: return null
    val startDate = Instant.fromEpochMilliseconds(dtStart).toLocalDateTime(TimeZone.UTC).date
    // 直接构造领域 Date，避免调用 toDate() 扩展触发 DateKt.<clinit>，使纯合同不依赖 Android host 未 mock 的主 Looper。
    return CalendarTiming.AllDay(
      com.cyxbs.components.config.time.Date(
        startDate.year,
        startDate.month.number,
        startDate.day,
      ),
      durationDays,
    )
  }

  /** 定时事件只接受整分钟 instant；普通时间段必须为正时长，Deadline 必须为零时长。 */
  private fun reconstructTimed(
    dtStart: Long,
    dtEnd: Long?,
    duration: String?,
    timeZoneId: String?,
    recurring: Boolean,
    projectionKind: CalendarProjectionKind,
  ): CalendarTiming? {
    if (!dtStart.isWholeMinute()) return null
    val timeZone = try {
      TimeZone.of(timeZoneId ?: return null)
    } catch (_: IllegalArgumentException) {
      return null
    }
    val localStart = Instant.fromEpochMilliseconds(dtStart).toLocalDateTime(timeZone)
    // 历史时区可能包含秒级 offset；即使 epoch 是整分钟，也不能把本地秒数静默截断。
    if (localStart.second != 0 || localStart.nanosecond != 0) return null

    val durationMinutes = if (recurring) {
      parseDurationMinutes(duration)
    } else {
      val endMillis = dtEnd ?: return null
      if (!endMillis.isWholeMinute() || endMillis < dtStart) return null
      ((endMillis / MILLIS_PER_MINUTE) - (dtStart / MILLIS_PER_MINUTE)).toNonNegativeIntOrNull()
    } ?: return null

    return if (projectionKind == CalendarProjectionKind.DEADLINE) {
      if (durationMinutes != 0) null
      else CalendarTiming.Deadline(localStart.toMinuteTimeDate(), timeZone.id)
    } else {
      if (durationMinutes == 0) null
      else CalendarTiming.Timed(localStart.toMinuteTimeDate(), durationMinutes, timeZone.id)
    }
  }

  /** 仅接受 `P<n>D`，全天 DURATION 不允许混入小时或其他单位。 */
  private fun parseDurationDays(duration: String?): Int? {
    val match = duration?.let { Regex("""^P(\d+)D$""").matchEntire(it) } ?: return null
    return match.groupValues[1].toLongOrNull()?.toPositiveIntOrNull()
  }

  /** 接受可无损表示整分钟的 RFC 5545 天、时、分组合，明确拒绝秒与小数。 */
  private fun parseDurationMinutes(duration: String?): Int? {
    val match = duration?.let {
      Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$""").matchEntire(it)
    } ?: return null
    val daysText = match.groupValues[1]
    val hoursText = match.groupValues[2]
    val minutesText = match.groupValues[3]
    // 零时长必须由 `P0D`/`PT0M` 等显式分量表达；裸 `P` 没有任何 duration 语义。
    if (daysText.isEmpty() && hoursText.isEmpty() && minutesText.isEmpty()) return null
    // RFC 5545 的 `T` 分隔符后至少要有一个时间分量；`P1DT` 不能被近似成一天。
    if ('T' in duration && hoursText.isEmpty() && minutesText.isEmpty()) return null
    // 缺失分量才等于零；存在但超出 Long 的数字属于损坏输入，不能静默降级成零。
    val days = daysText.toLongOrNull() ?: if (daysText.isEmpty()) 0L else return null
    val hours = hoursText.toLongOrNull() ?: if (hoursText.isEmpty()) 0L else return null
    val minutes = minutesText.toLongOrNull() ?: if (minutesText.isEmpty()) 0L else return null
    if (days > Long.MAX_VALUE / MINUTES_PER_DAY) return null
    val dayMinutes = days * MINUTES_PER_DAY
    if (hours > (Long.MAX_VALUE - dayMinutes) / 60L) return null
    val hourMinutes = hours * 60L
    if (minutes > Long.MAX_VALUE - dayMinutes - hourMinutes) return null
    return (dayMinutes + hourMinutes + minutes).toNonNegativeIntOrNull()
  }

  private fun Long.isWholeMinute(): Boolean = this % MILLIS_PER_MINUTE == 0L

  private fun Long.isUtcMidnight(): Boolean = this % MILLIS_PER_DAY == 0L

  private fun Long.toPositiveIntOrNull(): Int? = takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()

  private fun Long.toNonNegativeIntOrNull(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
}
