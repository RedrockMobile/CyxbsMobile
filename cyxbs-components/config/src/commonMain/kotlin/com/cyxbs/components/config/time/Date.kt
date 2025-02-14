package com.cyxbs.components.config.time

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.cyxbs.components.init.appCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * 使用 value class 压缩存储日期，可以用来代替 LocalDate
 *
 * 大部分计算借用 java 的 LocalDate
 * 同时添加 @Stable 标志设置为稳定类，Kotlin 的 LocalDate 不稳定
 *
 * @author 985892345
 * @date 2024/2/25 15:17
 */
@Stable
@kotlin.jvm.JvmInline
@Serializable(DateSerializer::class)
value class Date(
  val value: Int,
) : Comparable<Date> {

  constructor(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    noOverflow: Boolean = false, // 防止溢出，如果日溢出则改为该月最后一天，如果月溢出则年 + 1
  ) : this(getTime(year, month, dayOfMonth, noOverflow))

  val year: Int
    get() = value ushr 9

  val month: Month
    get() = Month(monthNumber)

  // 从 1 开始
  val monthNumber: Int
    get() = value ushr 5 and 0xF // 最多占 4 位，2^4 - 1

  val dayOfMonth: Int
    get() = value and 0x1F // 最多占 5 位，2^5 - 1

  val dayOfWeek: DayOfWeek
    get() = DayOfWeek(((toEpochDays() + 3) % 7).let { if (it < 0) it + 7 else it } + 1)

  // 从 0 开始，星期一为0
  val dayOfWeekOrdinal: Int
    get() = dayOfWeek.ordinal

  // 从 1 开始，星期一为1
  val dayOfWeekNumber: Int
    get() = dayOfWeek.isoDayNumber

  val lengthOfMonth: Int
    get() = lengthOfMonth(year, monthNumber)

  val firstDate: Date
    get() = copy(dayOfMonth = 1)

  val lastDate: Date
    get() = copy(dayOfMonth = lengthOfMonth)

  val weekBeginDate: Date
    get() = minusDays(dayOfWeekOrdinal)

  val weekFinalDate: Date
    get() = plusDays(6 - dayOfWeekOrdinal)

  fun daysUntil(date: Date): Int {
    if (this.value == date.value) return 0
    if (this.year == date.year && this.month == date.month) {
      return date.dayOfMonth - this.dayOfMonth
    }
    return date.toEpochDays() - toEpochDays()
  }

  fun isLeapYear(): Boolean {
    return isLeapYear(year)
  }

  /**
   * 获取时间戳
   * CV 自 Java 的 Calendar
   */
  fun toEpochDays(): Int {
    val y = year
    val m = monthNumber
    var total = 365 * y
    if (y >= 0) {
      total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400
    } else {
      total -= y / -4 - y / -100 + y / -400
    }
    total += (367 * m - 362) / 12
    total += dayOfMonth - 1
    if (m > 2) {
      total--
      if (!isLeapYear()) {
        total--
      }
    }
    return total - DAYS_0000_TO_1970
  }

  fun copy(
    year: Int = this.year,
    monthNumber: Int = this.monthNumber,
    dayOfMonth: Int = this.dayOfMonth,
    noOverflow: Boolean = false, // 防止溢出，如果日溢出则改为该月最后一天，如果月溢出则年 + 1
  ) : Date {
    return Date(year, monthNumber, dayOfMonth, noOverflow)
  }

  fun plusDays(daysToAdd: Int): Date {
    if (daysToAdd == 0) return this
    val dom = dayOfMonth + daysToAdd
    if (dom > 0) {
      if (dom <= 28) {
        return Date(year, monthNumber, dom)
      } else if (dom <= 59) { // 59th Jan is 28th Feb, 59th Feb is 31st Mar
        val monthLen = lengthOfMonth
        return if (dom <= monthLen) {
          Date(year, monthNumber, dom)
        } else if (monthNumber < 12) {
          Date(year, monthNumber + 1, dom - monthLen)
        } else {
          Date(year + 1, 1, dom - monthLen)
        }
      }
    }
    val mjDay = toEpochDays() + daysToAdd
    return ofEpochDay(mjDay)
  }

  fun plusWeeks(weeksToAdd: Int): Date {
    return plusDays(weeksToAdd * 7)
  }

  fun plusMonths(monthsToAdd: Int): Date {
    if (monthsToAdd == 0) return this
    return copy(monthNumber = monthNumber + monthsToAdd, noOverflow = true)
  }

  fun plusYears(yearsToAdd: Int): Date {
    if (yearsToAdd == 0) return this
    return copy(year = year + yearsToAdd, noOverflow = true)
  }

  fun minusDays(daysToMinus: Int): Date {
    return plusDays(-daysToMinus)
  }

  fun minusWeeks(weeksToMinus: Int): Date {
    return plusWeeks(-weeksToMinus)
  }

  fun minusMonths(monthsToMinus: Int): Date {
    return plusMonths(-monthsToMinus)
  }

  fun minusYears(yearsToMinus: Int): Date {
    return plusYears(-yearsToMinus)
  }

  override fun toString(): String {
    return "${year}-" + toStringMonthDay()

  }

  fun toStringMonthDay(): String {
    return "${monthNumber.toString().padStart(2, '0')}-" +
        dayOfMonth.toString().padStart(2, '0')
  }

  companion object {

    private const val DAYS_PER_CYCLE = 146097

    /**
     * The number of days from year zero to year 1970.
     * There are five 400 year cycles from year zero to 2000.
     * There are 7 leap years from 1970 to 2000.
     */
    const val DAYS_0000_TO_1970 = DAYS_PER_CYCLE * 5 - (30 * 365 + 7)

    /**
     * @param noOverflow 防止溢出，如果日溢出则改为该月最后一天(负数则为1)，如果月溢出则年 + 1 或 -1
     */
    private fun getTime(
      year: Int,
      monthNumber: Int,
      dayOfMonth: Int,
      noOverflow: Boolean,
    ): Int {
      val y: Int
      val m: Int
      val d: Int
      if (noOverflow) {
        y = year + if (monthNumber > 0) (monthNumber - 1) / 12 else monthNumber / 12 - 1
        m = if (monthNumber > 0) (monthNumber - 1) % 12 + 1 else monthNumber % 12 + 12
        d = dayOfMonth.coerceIn(1, lengthOfMonth(y, m))
      } else {
        require(year > 0) { "year = $year, must > 0" }
        require(monthNumber in 1..12) { ", month = $monthNumber, must in 1..12" }
        val lengthOfMonth = lengthOfMonth(year, monthNumber)
        require(dayOfMonth in 1..lengthOfMonth) {
          "month = $monthNumber, dayOfMonth = $dayOfMonth, must in 1..$lengthOfMonth"
        }
        y = year
        m = monthNumber
        d = dayOfMonth
      }
      return (y shl 9) + (m shl 5) + d
    }

    fun isLeapYear(year: Int): Boolean {
      return year and 3 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    fun lengthOfMonth(year: Int, month: Int): Int {
      return when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
      }
    }

    fun lengthOfMonth(date: LocalDate): Int {
      return lengthOfMonth(date.year, date.dayOfMonth)
    }

    fun ofEpochDay(epochDay: Int): Date {
      var zeroDay = epochDay + DAYS_0000_TO_1970
      // find the march-based year
      zeroDay -= 60 // adjust to 0000-03-01 so leap day is at end of four year cycle

      var adjust = 0
      if (zeroDay < 0) {
        // adjust negative years to positive for calculation
        val adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1
        adjust = adjustCycles * 400
        zeroDay += -adjustCycles * DAYS_PER_CYCLE
      }
      var yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE
      var doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
      if (doyEst < 0) {
        // fix estimate
        yearEst--
        doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
      }
      yearEst += adjust // reset any negative year

      val marchDoy0 = doyEst

      // convert march-based values back to january-based
      val marchMonth0 = (marchDoy0 * 5 + 2) / 153
      val month = (marchMonth0 + 2) % 12 + 1
      val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1
      yearEst += marchMonth0 / 10

      return Date(yearEst, month, dom)
    }

    fun now(): Date {
      return Clock.System.todayIn(TimeZone.currentSystemDefault()).toDate()
    }
  }

  override fun compareTo(other: Date): Int {
    return value.compareTo(other.value)
  }
}

fun LocalDate.toDate(): Date {
  return Date(year, monthNumber, dayOfMonth)
}

object DateSerializer : KSerializer<Date> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Date = deserialize(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: Date) = encoder.encodeString(serialize(value))

  fun deserialize(value: String): Date = value.split("-")
    .let { Date(it[0].toInt(), it[1].toInt(), it[2].toInt()) }

  fun serialize(date: Date): String = date.toString()
}


fun DayOfWeek.toChinese(prefix: String = "周"): String {
  return prefix + when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
    else -> error("")
  }
}

/**
 * 获取当前时间
 *
 * 注意使用了 mutableStateOf，存在有副作用
 * 若想取消副作用，请使用 [TodayNoEffect]
 */
val Today by mutableStateOf(
  Clock.System.todayIn(TimeZone.currentSystemDefault()).toDate()
).apply {
  appCoroutineScope.launch {
    while (true) {
      val now = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time.toMillisecondOfDay().milliseconds
      delay(1.days - now)
      value = value.plusDays(1)
    }
  }
}

val TodayNoEffect: Date
  get() = Snapshot.withoutReadObservation { Today }