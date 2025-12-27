package com.cyxbs.pages.course.frame.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.frame.item.SelfLessonItem
import com.cyxbs.pages.course.frame.item.SelfLessonItemFactory
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import kotlinx.coroutines.CoroutineScope

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
class DefaultSelfLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  lesson: LessonByWeeks
) : SelfLessonItem(whatTime, coroutineScope, lesson) {

  companion object Companion : SelfLessonItemFactory {
    override fun createSelfLessonItem(
      whatTime: CourseItemWhatTime,
      coroutineScope: CoroutineScope,
      lesson: LessonByWeeks
    ): SelfLessonItem {
      return DefaultSelfLessonItem(whatTime, coroutineScope, lesson)
    }
  }

  override val extension = DefaultSelfLessonItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = when {
        lesson.beginTime < MinuteTime(12, 0) -> 0xFFFF8015.dark(0xFFF0F0F2)
        lesson.beginTime < MinuteTime(18, 0) -> 0xFFFF6262.dark(0xFFF0F0F2)
        else -> 0xFF4066EA.dark(0xFFF0F0F2)
      },
      backgroundColor = when {
        lesson.beginTime < MinuteTime(12, 0) -> 0xFFF9E7D8.dark(0x26FFCCA1)
        lesson.beginTime < MinuteTime(18, 0) -> 0xFFF9E3E4.dark(0x26FF979B)
        else -> 0xFFDDE3F8.dark(0x269BB2FF)
      },
    ) {
      toast(lesson.course)
    }
  }
}

class DefaultSelfLessonItemExtensionGroup(
  val itemKeyImpl: DefaultSelfLessonItem
) : IMovableItemExtension by MobileSelfMovableItemExtension(itemKeyImpl)

private class MobileSelfMovableItemExtension(
  val itemKeyImpl: DefaultSelfLessonItem
) : IMovableItemExtension

