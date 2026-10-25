package com.cyxbs.pages.notification.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.notification.bean.ItineraryDateBean

/**
 * 将没课约的一组节次转换为新日程使用的绝对时间段。
 *
 * [ItineraryDateBean.week] 为 0 时表示整学期同一星期重复，正数表示指定教学周。该 DTO 只表达一个
 * beginLesson + period，因此不会产生多个日期与多个时间段的交叉组合。
 */
internal fun ItineraryDateBean.toScheduleTiming(
  firstMonday: Date? = SchoolCalendar.getFirstMonDay(),
  maxWeek: Int = CourseUtils.maxWeek,
): ItineraryScheduleTiming? {
  return ItineraryScheduleSlot(
    beginLesson = beginLesson,
    day = day,
    period = period,
    week = week,
  ).toScheduleTiming(firstMonday, maxWeek)
}
