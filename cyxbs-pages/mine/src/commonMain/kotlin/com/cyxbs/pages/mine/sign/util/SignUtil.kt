package com.cyxbs.pages.mine.sign.util

import com.cyxbs.components.config.time.SchoolCalendar
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Clock

/**  
 * description: 签到页面的工具类
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 17:10
 */
object SignUtil {

  private fun currentLocalDateTime() =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

  /**
   * 获取当前学年（比如2026-2027）
   */
  fun getYearPair(): String {
    val localDateTime = currentLocalDateTime()
    val currentYear = localDateTime.year
    val currentMonth = localDateTime.month
    return if (currentMonth < Month.AUGUST) {
      "${currentYear - 1}-${currentYear}"
    } else {
      "${currentYear}-${currentYear + 1}"
    }
  }

  /**
   * 获取当前周数
   */
  fun getWeekOfTerm(): Int {
    return SchoolCalendar.getWeekOfTerm() ?: 0
  }

  /**
   * 获取当前是春季学期还是秋季学期
   */
  fun getSemesterOfTerm(): String {
    val localDateTime = currentLocalDateTime()
    val isFirstSemester = localDateTime.month > Month.JULY
    return if (isFirstSemester) "秋" else "春"
  }

  /**
   * 获取当前开学天数，为负说明还没开学
   */
  fun getDayOfTerm(): Int {
    return SchoolCalendar.getDayOfTerm() ?: 0
  }

  /**
   * 获取中文形式的周数
   */
  fun getChineseWeekOfTerm(): String {
    val number = abs(getWeekOfTerm())
    require(number in 0..29) { "数字必须在 0..29 范围内，当前值为：$number" }

    val digits = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    return when {
      number < 10 -> digits[number]
      number == 10 -> "十"
      number < 20 -> "十${digits[number % 10]}"
      number == 20 -> "二十"
      else -> "二十${digits[number % 10]}"
    }
  }

  /**
   * 获取当天星期几（星期一为0，星期天为6）
   */
  fun getTodayOfWeek(): Int {
    return currentLocalDateTime().dayOfWeek.ordinal
  }

}
