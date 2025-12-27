package com.cyxbs.pages.course.frame.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.frame.item.LinkLessonItem
import com.cyxbs.pages.course.frame.item.LinkLessonItemFactory
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
class DefaultLinkLessonItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  lesson: LessonByWeeks
) : LinkLessonItem(whatTime, coroutineScope, lesson) {

  companion object Companion : LinkLessonItemFactory {
    override fun createLinkLessonItem(
      whatTime: CourseItemWhatTime,
      coroutineScope: CoroutineScope,
      lesson: LessonByWeeks
    ): LinkLessonItem {
      return DefaultLinkLessonItem(whatTime, coroutineScope, lesson)
    }
  }

  override val extension = DefaultLinkLessonItemExtensionGroup(this)

  @Composable
  override fun CourseItemContent() {
    val itemState = itemState
    CourseDefaultItemContent(
      itemState = itemState,
      topText = lesson.course,
      bottomText = lesson.classroomSimplify,
      textColor = 0xFF06A3FC.dark(0xFFF0F0F2),
      backgroundColor = 0xFFDFF3FC.dark(0x2690DBFB),
    ) {
      toast(lesson.course)
    }
  }
}

class DefaultLinkLessonItemExtensionGroup(
  val itemKeyImpl: DefaultLinkLessonItem
) : IMovableItemExtension by MobileLinkMovableItemExtension(itemKeyImpl)

private class MobileLinkMovableItemExtension(
  val itemKeyImpl: DefaultLinkLessonItem
) : IMovableItemExtension