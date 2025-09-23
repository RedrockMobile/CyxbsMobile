package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Stable
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.impl.DefaultAffairItemModel
import com.cyxbs.pages.course.home.item.impl.DefaultLinkLessonItemModel
import com.cyxbs.pages.course.view.item.CourseItemModel

/**
 * LinkLessonItem 工厂，由具体平台实现
 * - 如果由 commonMain 去实现，则点击事件等无法具体平台定制化
 * - mobileMain 实现了移动端的 LinkMobileLessonItem
 *
 * @author 985892345
 * @date 2025/3/25
 */
interface LinkLessonItemFactory {

  fun createLinkLessonItemModel(
    page: Int,
    lesson: LessonByWeeks,
  ): LinkLessonItemModel


  companion object {
    fun get(): LinkLessonItemFactory {
      return LinkLessonItemFactory::class.implOrNull() ?: DefaultLinkLessonItemModel
    }
  }
}

@Stable
interface LinkLessonItemModel : CourseItemModel {
  val lesson: LessonByWeeks
}