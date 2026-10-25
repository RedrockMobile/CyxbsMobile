package com.cyxbs.components.config.time

import androidx.compose.runtime.Stable
import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Clock

/**
 * .
 *
 * @author 985892345
 * 2024/3/9 15:37
 */
@Stable
@kotlin.jvm.JvmInline
@Serializable(MinuteTimeDateSerializer::class)
value class MinuteTimeDate(val value: Int) : Comparable<MinuteTimeDate> {

  constructor(date: Date, time: MinuteTime) : this((date.value shl 11) or time.value)

  constructor(date: Date, hour: Int, minute: Int) : this(date, MinuteTime(hour, minute))

  constructor(year: Int, month: Int, dayOfMonth: Int, time: MinuteTime) : this(
    Date(year, month, dayOfMonth), time
  )

  constructor(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int = 0) : this(
    Date(year, month, dayOfMonth), MinuteTime(hour, minute)
  )

  val date: Date
    get() = Date(value ushr 11) // 最多能保存到公元 4095 年，应该够了

  val time: MinuteTime
    get() = MinuteTime(value and 0x7FF) // 最多占 11 位

  val minuteOfDay: Int
    get() = time.minuteOfDay

  fun minutesUntil(other: MinuteTimeDate): Int {
    return date.daysUntil(other.date) * 24 * 60 + time.minutesUntil(other.time)
  }

  /**
   * 增减分钟并自动归一化日期与时刻。
   *
   * [minutes] 可为负数，也可跨越一天或多天；结果中的小时和分钟始终保持在合法范围内。
   */
  fun plusMinutes(minutes: Int): MinuteTimeDate {
    val totalMinutes = minuteOfDay + minutes
    val dateDiff = totalMinutes.floorDiv(MINUTES_PER_DAY)
    val normalizedMinuteOfDay = totalMinutes.mod(MINUTES_PER_DAY)
    return MinuteTimeDate(
      date.plusDays(dateDiff),
      normalizedMinuteOfDay / 60,
      normalizedMinuteOfDay % 60,
    )
  }

  fun minusMinutes(minutes: Int): MinuteTimeDate {
    return plusMinutes(-minutes)
  }

  fun plusDays(days: Int): MinuteTimeDate {
    return MinuteTimeDate(date.plusDays(days), time)
  }

  fun minusDays(days: Int): MinuteTimeDate {
    return plusDays(-days)
  }

  fun plusWeeks(weeks: Int): MinuteTimeDate {
    return plusDays(weeks * 7)
  }

  fun minusWeeks(weeks: Int): MinuteTimeDate {
    return plusDays(-weeks * 7)
  }

  fun plusMonths(months: Int): MinuteTimeDate {
    return MinuteTimeDate(date.plusMonths(months), time)
  }

  fun minusMonths(months: Int): MinuteTimeDate {
    return plusMonths(-months)
  }

  fun plusYears(years: Int): MinuteTimeDate {
    return MinuteTimeDate(date.plusYears(years), time)
  }

  fun minusYears(years: Int): MinuteTimeDate {
    return plusYears(-years)
  }

  fun toEpochMilliseconds(): Long {
    return LocalDateTime(date.year, date.month, date.dayOfMonth, time.hour, time.minute)
      .toInstant(TimeZone.currentSystemDefault())
      .toEpochMilliseconds()
  }

  override fun compareTo(other: MinuteTimeDate): Int {
    return value.compareTo(other.value)
  }

  override fun toString(): String {
    return "$date $time"
  }

  companion object {
    private const val MINUTES_PER_DAY = 24 * 60

    fun now(): MinuteTimeDate {
      val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      return MinuteTimeDate(now.year, now.month.number, now.day, MinuteTime(now.hour, now.minute))
    }
  }
}

fun LocalDateTime.toMinuteTimeDate(): MinuteTimeDate {
  return MinuteTimeDate(
    year = this.year,
    month = month.number,
    dayOfMonth = day,
    hour = this.hour,
    minute = this.minute,
  )
}

/** 转换为不携带时区的本地墙上时间，供时区、协议和平台边界使用。 */
fun MinuteTimeDate.toLocalDateTime(): LocalDateTime {
  return LocalDateTime(
    year = date.year,
    month = date.monthNumber,
    day = date.dayOfMonth,
    hour = time.hour,
    minute = time.minute,
  )
}

object MinuteTimeDateSerializer : KSerializer<MinuteTimeDate> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MinuteTimeDate", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): MinuteTimeDate = deserialize(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: MinuteTimeDate) = encoder.encodeString(serialize(value))

  fun deserialize(value: String): MinuteTimeDate = value.split(" ").let {
    MinuteTimeDate(DateSerializer.deserialize(it[0]), MinuteTimeSerializer.deserialize(it[1]))
  }

  fun serialize(date: MinuteTimeDate): String = date.toString()
}