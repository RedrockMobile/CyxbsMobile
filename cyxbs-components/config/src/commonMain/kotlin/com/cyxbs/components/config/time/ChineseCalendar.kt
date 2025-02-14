package com.cyxbs.components.config.time

import androidx.compose.runtime.Stable
import com.cyxbs.components.config.time.ChineseCalendar.Companion.lunarInfo

/**
 * 农历日期
 * 农历是根据星体运转的交汇来计算出的，与新历并没有统一的转换公式，只能通过枚举来获取农历日期
 * 参考以下文章进行了算法优化：
 * - https://github.com/jjonline/calendar.js
 * - https://blog.jjonline.cn/userInterFace/173.html
 *
 * @author 985892345
 * 2024/2/14 18:54
 */
@Stable
data class ChineseCalendar(
  val year: Int,
  val monthPosition: Int, // 从 1 开始
  val dayOfMonth: Int,
) {

  val month: Int
    get() {
      val leapMonth = getLeapMonth(year)
      return if (leapMonth == 0) monthPosition else if (monthPosition > leapMonth) monthPosition - 1 else monthPosition
    }

  // 检测当前月是否是润月
  fun isLeapMonth(): Boolean {
    val leapMonth = getLeapMonth(year)
    if (leapMonth == 0) return false
    return monthPosition == leapMonth + 1
  }

  fun getMonthStr(): String {
    val str = when (month) {
      1 -> "正月"
      2 -> "二月"
      3 -> "三月"
      4 -> "四月"
      5 -> "五月"
      6 -> "六月"
      7 -> "七月"
      8 -> "八月"
      9 -> "九月"
      10 -> "十月"
      11 -> "冬月"
      12 -> "腊月"
      else -> ""
    }
    return if (isLeapMonth()) "润$str" else str
  }

  /**
   * 1 - 10:    初
   * 11 - 19:   十
   * 20:        二十
   * 21 - 29:   廿
   * 30:        三十
   */
  fun getDayStr(): String {
    if (dayOfMonth == 20) return "二十"
    if (dayOfMonth == 30) return "三十"
    val a = when ((dayOfMonth - 1) / 10) {
      0 -> "初"
      1 -> "十"
      2 -> "廿"
      else -> ""
    }
    val b = when (dayOfMonth % 10) {
      0 -> "十"
      1 -> "一"
      2 -> "二"
      3 -> "三"
      4 -> "四"
      5 -> "五"
      6 -> "六"
      7 -> "七"
      8 -> "八"
      9 -> "九"
      else -> ""
    }
    return a + b
  }

  fun lengthOfMonth(): Int {
    return if ((lunarInfo[year - 1900] and (0x10000 ushr monthPosition - 1)) != 0) 30 else 29
  }

  companion object {

    fun getSpringFestivalDate(year: Int): Date {
      val info = lunarInfo[year - 1900]
      val month = ((info and 0x400000) ushr 22) + 1
      val day = (info and 0x3E0000) ushr 17
      return Date(year, month, day)
    }

    // 返回润的哪个月，如果不存在润月，则返回 0
    fun getLeapMonth(year: Int): Int {
      return lunarInfo[year - 1900] and 0xF
    }

    /**
     * 2023: 0x2c9b52
     * 0010 1100 1001 1011 0101 0010
     * 前两位 00 为一月，01 为二月，这里 2023 是 1 月
     * 后 5 位表示号数: 10110 = 22，结合前两位的月份，则可以知道 2023 年是 1 月 22 号正月初一
     * 后 12 位表示连续的农历月是大月还是小月，0 -> 29天，1 -> 30 天
     * 最后 4 位表示闰月的月数，0010 = 2
     */
    internal val lunarInfo = intArrayOf(
      0x3e96d8,
      0x6695c0,
      0x514ae0,
      0x3aa4d5,
      0x61a4c0,
      0x49b2a0,
      0x32d554,
      0x5aad40,
      0x4535a0,
      0x2c95d2, // 1900-1909
      0x5495c0,
      0x3d49b6,
      0x6549a0,
      0x4da4a0,
      0x35aa55,
      0x5d6a80,
      0x47ad40,
      0x2f2da2,
      0x572b60,
      0x429377, // 1910-1919
      0x6892e0,
      0x514960,
      0x3964b5,
      0x60d4a0,
      0x4ada80,
      0x315b54,
      0x5a56c0,
      0x452ae0,
      0x2e92f2,
      0x5492e0, // 1920-1929
      0x3cc966,
      0x63a940,
      0x4dd4a0,
      0x34da95,
      0x5cb5a0,
      0x4856c0,
      0x3126e3,
      0x5725c0,
      0x3f92d7,
      0x6792a0, // 1930-1939
      0x51a940,
      0x37b4a6,
      0x5f6aa0,
      0x4aad40,
      0x3355b4,
      0x5a4ba0,
      0x4525a0,
      0x2d92b2,
      0x5552a0,
      0x3b6957, // 1940-1949
      0x62d940,
      0x4d6aa0,
      0x36ab55,
      0x5c9b40,
      0x474b60,
      0x30a573,
      0x58a560,
      0x3f52a8,
      0x65d2a0,
      0x50d540, // 1950-1959
      0x395aa6,
      0x5f56a0,
      0x4a96c0,
      0x334ae4,
      0x5b4ae0,
      0x44a4c0,
      0x2bd263,
      0x53b2a0,
      0x3cb557,
      0x62ad40, // 1960-1969
      0x4d2da0,
      0x3695d5,
      0x5e95a0,
      0x4749a0,
      0x2fa4d4,
      0x57a4a0,
      0x3faa58,
      0x656a80,
      0x4f6d40,
      0x392da6, // 1970-1979
      0x612b60,
      0x4a9360,
      0x334974,
      0x5b4960,
      0x4564ba,
      0x68d4a0,
      0x52da80,
      0x3b5b46,
      0x6356c0,
      0x4d2ae0, // 1980-1989
      0x3692f5,
      0x5e92e0,
      0x48c960,
      0x2ed4a3,
      0x55d4a0,
      0x3ed658,
      0x66b580,
      0x4f56c0,
      0x3926d5,
      0x6125c0, // 1990-1999
      0x4b92c0,
      0x31a954,
      0x59a940,
      0x43b4a0,
      0x2cb552,
      0x52ad40,
      0x3b55b7,
      0x644ba0,
      0x4f25a0,
      0x3592b5, // 2000-2009
      0x5d52a0,
      0x476940,
      0x2f6aa4,
      0x555aa0,
      0x3eab59,
      0x669740,
      0x514b60,
      0x38a576,
      0x60a560,
      0x4b5260, // 2010-2019
      0x32e954,
      0x58d540,
      0x435aa0,
      0x2c9b52,
      0x5496c0,
      0x3b4ae6,
      0x6349c0,
      0x4da4c0,
      0x35d265,
      0x5baa60, // 2020-2029
      0x46b540,
      0x2ed6a3,
      0x572da0,
      0x3e95db,
      0x6695a0,
      0x5149a0,
      0x39a4b6,
      0x5fa4a0,
      0x49aa40,
      0x31b545, // 2030-2039
      0x596b40,
      0x42ada0,
      0x2c95b2,
      0x549360,
      0x3d4977,
      0x634960,
      0x4d54a0,
      0x356a55,
      0x5cda40,
      0x455b40, // 2040-2049
      0x2eab63,
      0x5726e0,
      0x4292f8,
      0x6692e0,
      0x50c960,
      0x38d4a6,
      0x5fd4a0,
      0x48d640,
      0x3156c4,
      0x5955c0, // 2050-2059
      0x4525c0,
      0x2b92e3,
      0x5392c0,
      0x3ba957,
      0x63a940,
      0x4bb4a0,
      0x34b555,
      0x5cad40,
      0x474da0,
      0x2ea5d4, // 2060-2069
      0x56a5a0,
      0x3f52b8,
      0x6752a0,
      0x4f6940,
      0x376aa6,
      0x5f5aa0,
      0x4aab40,
      0x314ba4,
      0x594b60,
      0x44a560, // 2070-2079
      0x2d5273,
      0x52d260,
      0x3ae537,
      0x62d540,
      0x4d5aa0,
      0x349b55,
      0x5c96c0,
      0x474ae0,
      0x30a4e4,
      0x55a2c0, // 2080-2089
      0x3dd268,
      0x65aa40,
      0x4fb540,
      0x36d6a6,
      0x5eada0,
      0x4a95c0,
      0x3349d4,
      0x5945a0,
      0x43a2a0,
      0x2bb252, // 2090-2099
      0x53aa40,
    )
  }
}

fun Date.toChineseCalendar(): ChineseCalendar {
  var year = year
  var info = lunarInfo[year - 1900]
  // 取出 year 那年的春节日期
  var springFestivalDate = ChineseCalendar.getSpringFestivalDate(year)
  if (this < springFestivalDate) {
    year--
    info = lunarInfo[year - 1900]
    springFestivalDate = ChineseCalendar.getSpringFestivalDate(year)
  }
  // diff 不会小于 0，因为全面判断了日期是否在正月初一之前
  var diff = toEpochDays() - springFestivalDate.toEpochDays()
  var lengthOfLMonth = 0
  var index = 0
  while (diff >= 0) {
    lengthOfLMonth = if ((info and (0x10000 ushr index)) != 0) 30 else 29
    diff -= lengthOfLMonth
    index++
  }
  diff += lengthOfLMonth
  return ChineseCalendar(year, index, diff + 1)
}