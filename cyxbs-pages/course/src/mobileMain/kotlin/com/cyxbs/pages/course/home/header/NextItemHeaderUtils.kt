package com.cyxbs.pages.course.home.header

import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.course.home.compose.MobileHomeCourseFrame
import com.cyxbs.pages.course.view.data.CourseDayDataPool
import com.cyxbs.pages.course.view.item.CourseItemWrapper

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
object NextItemHeaderUtils {

  fun find(
    nowTime: MinuteTimeDate,
    frame: MobileHomeCourseFrame,
  ): CourseBottomSheetHeaderExtension? {
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
    frame: MobileHomeCourseFrame,
  ): CourseBottomSheetHeaderExtension? {
    val dayOfWeek = nowTime.date.dayOfWeek
//    return findMinDayItemFromDayDataPool(
//      nowTime,
//      frame.providerGroup.getWeekDataPool(frame.getPage(nowTime.date)).get(dayOfWeek)
//    )
    // todo 待实现寻找下一节课程
    return null
  }

  private fun findMinDayItemFromDayDataPool(
    nowTime: MinuteTimeDate,
    dayDataPool: CourseDayDataPool,
  ): CourseBottomSheetHeaderExtension? {
    var minWrapper: CourseItemWrapper<*>? = null
    for (itemOverlap in dayDataPool.state.value.asReversed()) {
      val wrapper = itemOverlap.wrapper
      val item = wrapper.item
      if (item !is CourseBottomSheetHeaderExtension) continue
      if (nowTime.time in wrapper.beginTime..wrapper.finalTime) {
        return item
      }
      if (wrapper.beginTime < nowTime.time) continue // 略过小于的时间段
      if (minWrapper == null || wrapper.beginTime < minWrapper.beginTime) {
        minWrapper = wrapper
        continue
      }
    }
    return minWrapper?.item as CourseBottomSheetHeaderExtension?
  }
}