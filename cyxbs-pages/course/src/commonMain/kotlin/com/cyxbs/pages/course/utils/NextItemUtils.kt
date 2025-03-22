package com.cyxbs.pages.course.utils

import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.home.data.HomeLinkLessonDataProvider
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
    var selfItem: CourseItem? = null
    var linkMinItem: CourseItem? = null
    for (weekPool in frame.providerGroup.getWeekDataPool(frame.initialPage)) {
      if (weekPool.provider === HomeLinkLessonDataProvider) {
        // 关联人的课程单独查找，优先显示自己的课程
        linkMinItem = findMinDayItemFromDayDataPool(nowTime, weekPool.get(dayOfWeek))
      } else {
        val item = findMinDayItemFromDayDataPool(nowTime, weekPool.get(dayOfWeek))
        if (item != null) {
          if (nowTime.time in item.beginTime..item.finalTime) {
            // 如果提前找到了正在进行中的 item 则直接 return
            return item as BottomSheetItemHeader
          }
          if (selfItem == null || item.beginTime < selfItem.beginTime) {
            selfItem = item
          }
        }
      }
    }
    if (selfItem == null) {
      // 如果与自己相关的 item 都已经上完了，则显示关联人的 item
      return linkMinItem as BottomSheetItemHeader?
    }
    return selfItem as BottomSheetItemHeader?
  }

  private fun findMinDayItemFromDayDataPool(
    nowTime: MinuteTimeDate,
    dayDataPool: CourseDayDataPool,
  ): CourseItem? {
    var minItem: CourseItem? = null
    for (itemContent in dayDataPool.state.value) {
      val item = itemContent.item
      if (item is BottomSheetItemHeader) {
        if (nowTime.time in item.beginTime..item.finalTime) {
          return item
        }
        if (item.beginTime < nowTime.time) continue // 略过小于的时间段
        if (minItem == null || item.beginTime < minItem.beginTime) {
          minItem = item
          continue
        }
      }
    }
    return minItem
  }
}