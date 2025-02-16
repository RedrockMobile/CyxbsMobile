package com.cyxbs.pages.course.view.overlap

import com.cyxbs.pages.course.view.item.CourseItem

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
class CourseItemOverlapHelper {

  /**
   * 筛选重叠后数据
   */
  fun overlay(items: List<CourseItem>): List<OverlayData> {
    return items.map { OverlayData(it, 0, it.beginTime.time, it.finalTime.time) }
  }
}