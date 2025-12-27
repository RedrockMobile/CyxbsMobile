package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Stable
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.impl.DefaultSelfLessonItem
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import kotlinx.coroutines.CoroutineScope

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
    whatTime: CourseItemWhatTime,
    coroutineScope: CoroutineScope,
    lesson: LessonByWeeks
  ): SelfLessonItem

  companion object {
    fun get(): SelfLessonItemFactory {
      return SelfLessonItemFactory::class.implOrNull() ?: DefaultSelfLessonItem
    }
  }
}

@Stable
abstract class SelfLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val lesson: LessonByWeeks
) : CourseItem(whatTime, coroutineScope) {
}