package com.cyxbs.components.config.time

import com.cyxbs.components.config.sp.defaultSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 学校日历的工具类
 *
 * 这里面可以得到开学的第一天
 *
 * 后端并没有单独提供某个接口来获取当前开学周数，每次的开学周数都是从课表接口那里得到的
 *
 * @author 985892345
 * @date 2025/3/15
 */
object SchoolCalendar {

  /**
   * 得到今天与开学天数差，当天开学则返回 0
   *
   * 返回 null，则说明不知道开学第一天是好久
   *
   * # 注意：存在返回负数的情况！！！
   */
  fun getDayOfTerm(): Int? {
    return firstDateState.value?.run {
      daysUntil(Date.now())
    }
  }

  /**
   * 观察这学期过去了多少天
   *
   * # 注意：存在返回负数的情况！！！
   */
  fun observeDayOfTerm(): Flow<Int> {
    return observeFirstMonDay().map {
      it.daysUntil(Date.now())
    }
  }

  /**
   * 得到当前周数
   *
   * @return 返回 null，则说明不知道开学第一天是好久；返回 0，则表示开学前的一周（因为第一周开学）
   *
   * # 注意：存在返回负数的情况！！！
   * ```
   *     -1      0      1      2       3        4             返回值
   *  ----------------------------------------------------------->
   * -14     -7      0      7      14       21       28       天数差
   * ```
   */
  fun getWeekOfTerm(): Int? {
    val dayOfTerm = getDayOfTerm() ?: return null
    return if (dayOfTerm >= 0) dayOfTerm / 7 + 1 else dayOfTerm / 7
  }

  /**
   * 观察当前周数
   *
   * # 注意：存在返回负数的情况！！！
   */
  fun observeWeekOfTerm(): Flow<Int> {
    return observeDayOfTerm().map { if (it >= 0) it / 7 + 1 else it / 7 }
  }

  /**
   * 观察开学第一天日期
   */
  fun observeFirstMonDay(): Flow<Date> {
    return observeFirstMonDayNullable().filterNotNull()
  }

  /**
   * 观察开学第一天日期，但允许为空
   */
  fun observeFirstMonDayNullable(): StateFlow<Date?> {
    return firstDateState
  }

  /**
   * 得到开学第一天日期
   */
  fun getFirstMonDay(): Date? {
    return firstDateState.value
  }

  /**
   * 得到开学第一天的时间戳
   */
  fun getFirstMonDayTimestamp(): Long? {
    return firstDateState.value?.toEpochMillis()
  }

  private const val FIRST_MON_DAY = "first_day" // 这个与 lib_common 包中的 SchoolCalendar 保持一致

  private val firstDateState = MutableStateFlow(
    defaultSettings.getLongOrNull(FIRST_MON_DAY)?.let {
      Instant.fromEpochMilliseconds(it)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.toDate()
    }
  )

  // 目前由课表请求来更新开学第一天
  fun updateFirstCalendar(nowWeek: Int) {
    val firstDate = Date.now().weekBeginDate.minusWeeks(nowWeek - 1)
    defaultSettings.putLong(FIRST_MON_DAY, firstDate.toEpochMillis())
    firstDateState.value = firstDate
  }
}