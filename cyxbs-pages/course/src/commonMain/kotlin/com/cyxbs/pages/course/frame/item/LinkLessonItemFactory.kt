package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Stable
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.frame.item.impl.DefaultLinkLessonItem
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import kotlinx.coroutines.CoroutineScope

/**
 * LinkLessonItem 工厂，由具体平台实现
 * - 如果由 commonMain 去实现，则点击事件等无法具体平台定制化
 * - mobileMain 实现了移动端的 LinkMobileLessonItem
 *
 * @author 985892345
 * @date 2025/3/25
 */
interface LinkLessonItemFactory {

  fun createLinkLessonItem(
    whatTime: CourseItemWhatTime,
    coroutineScope: CoroutineScope,
    lesson: LessonByWeeks,
  ): LinkLessonItem

  companion object {
    fun get(): LinkLessonItemFactory {
      return LinkLessonItemFactory::class.implOrNull() ?: DefaultLinkLessonItem
    }
  }
}

@Stable
abstract class LinkLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val lesson: LessonByWeeks,
) : CourseItem(whatTime, coroutineScope) {
}