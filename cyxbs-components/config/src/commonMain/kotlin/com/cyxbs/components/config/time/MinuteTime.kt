package com.cyxbs.components.config.time

import androidx.compose.runtime.Stable
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * .
 *
 * @author 985892345
 * 2024/3/9 15:30
 */
@Stable
@kotlin.jvm.JvmInline
@Serializable(MinuteTimeSerializer::class)
value class MinuteTime(val value: Int) : Comparable<MinuteTime> {

  constructor(hour: Int, minute: Int) : this(
    (checkHour(hour) shl 6) or checkMinute(minute)
  )

  val hour: Int
    get() = value ushr 6 // 最多占 5 位，2^5 - 1

  val minute: Int
    get() = value and 0x3F // 最多占 6 位，2^6 - 1

  val minuteOfDay: Int
    get() = hour * 60 + minute

  fun minutesUntil(time: MinuteTime, cyclic: Boolean = false): Int {
    if (this > time && cyclic) {
      return (24 + time.hour - hour) * 60 + (time.minute - minute)
    }
    return (time.hour - hour) * 60 + (time.minute - minute)
  }

  fun plusMinutes(minutes: Int): MinuteTime {
    val hourDiff = (minute + minutes) / 60
    return MinuteTime(
      (hour + hourDiff) % 24,
      (minute + minutes) % 60,
    )
  }

  fun minusMinutes(minutes: Int): MinuteTime {
    return plusMinutes(-minutes)
  }

  fun plusHours(hours: Int): MinuteTime {
    return MinuteTime(
      (hour + hours) % 24,
      minute
    )
  }

  fun minusHours(hours: Int): MinuteTime {
    return plusHours(-hours)
  }

  override fun compareTo(other: MinuteTime): Int {
    return value.compareTo(other.value)
  }

  override fun toString(): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
  }

  companion object {
    fun checkHour(hour: Int): Int {
      if (hour in 0..23) return hour
      throw IllegalArgumentException("hour=$hour must in 0..23")
    }

    fun checkMinute(minute: Int): Int {
      if (minute in 0..59) return minute
      throw IllegalArgumentException("minute=$minute must in 0..59")
    }

    fun now(): MinuteTime {
      val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
      return MinuteTime(time.hour, time.minute)
    }

  }
}

object MinuteTimeSerializer : KSerializer<MinuteTime> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MinuteTime", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): MinuteTime = deserialize(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: MinuteTime) = encoder.encodeString(value.toString())

  fun deserialize(value: String): MinuteTime = value.split(":")
    .let { MinuteTime(it[0].toInt(), it[1].toInt()) }

  fun serialize(time: MinuteTime): String = time.toString()
}