package com.cyxbs.components.config.time

import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/14
 */

// 得到明天是星期几
fun DayOfWeek.next(): DayOfWeek {
  return DayOfWeek((ordinal + 1) % 7 + 1)
}

// 得到昨天是星期几
fun DayOfWeek.prev(): DayOfWeek {
  return DayOfWeek((ordinal - 1 + 7) % 7 + 1)
}

// 获取 day 后的星期数
fun DayOfWeek.add(day: Int): DayOfWeek {
  return DayOfWeek(((ordinal + day) % 7 + 7) % 7 + 1)
}