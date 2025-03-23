package com.cyxbs.pages.course.utils

import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.view.data.CourseDayDataPool
import com.cyxbs.pages.course.view.frame.CourseBottomSheetFrame
import com.cyxbs.pages.course.view.item.BottomSheetItemHeader
import com.cyxbs.pages.course.view.item.CourseItem

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
object NextItemUtils {

  fun find(
    nowTime: MinuteTimeDate,
    frame: CourseBottomSheetFrame,
  ): BottomSheetItemHeader? {
    return findDayItem(nowTime, frame) ?: findDayItem(
      MinuteTimeDate(
        date = nowTime.date.plusDays(1),
        time = MinuteTime(0, 0)
      ),
      frame
    )
  }

  // 寻找正在进行的或者下一节的 BottomSheetItemHeader
  private fun findDayItem(
    nowTime: MinuteTimeDate,
    frame: CourseBottomSheetFrame,
  ): BottomSheetItemHeader? {
    val dayOfWeek = nowTime.date.dayOfWeek
    return findMinDayItemFromDayDataPool(
      nowTime,
      frame.providerGroup.getWeekDataPool(frame.getPage(nowTime.date)).get(dayOfWeek)
    )
  }

  private fun findMinDayItemFromDayDataPool(
    nowTime: MinuteTimeDate,
    dayDataPool: CourseDayDataPool,
  ): CourseItem? {
    var minItem: CourseItem? = null
    for (itemContent in dayDataPool.state.value.asReversed()) {
      val item = itemContent.item
      if (nowTime.time in item.beginTime..item.finalTime) {
        return item
      }
      if (item.beginTime < nowTime.time) continue // 略过小于的时间段
      if (minItem == null || item.beginTime < minItem.beginTime) {
        minItem = item
        continue
      }
    }
    return minItem
  }
}