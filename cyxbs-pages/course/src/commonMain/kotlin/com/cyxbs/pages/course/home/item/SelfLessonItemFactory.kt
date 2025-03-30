package com.cyxbs.pages.course.home.item

import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.item.CourseItem

/**
 * SelfLessonItem 工厂，由具体平台实现
 * - 如果由 commonMain 去实现，则点击事件等无法具体平台定制化
 * - mobileMain 实现了移动端的 SelfMobileLessonItem
 *
 *
 * @author 985892345
 * @date 2025/3/25
 */
interface SelfLessonItemFactory {

  fun createSelfLessonItem(
    page: Int,
    lesson: LessonByWeeks,
  ): SelfLessonItem
}

interface SelfLessonItem : CourseItem {
  val lesson: LessonByWeeks
}