package com.cyxbs.pages.course.home.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.LinkLessonItem
import com.cyxbs.pages.course.home.item.LinkLessonItemFactory
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.IMovableItemExtension

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
@Stable
data class DefaultLinkLessonItem(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
  override val lesson: LessonByWeeks,
) : LinkLessonItem {
  companion object Companion : LinkLessonItemFactory {
    override fun createLinkLessonItem(page: Int, lesson: LessonByWeeks): LinkLessonItem {
      return DefaultLinkLessonItem(page, lesson)
    }
  }

  override val whatTime = CourseItemWhatTime.Fixed(
    page = page,
    dayOfWeek = lesson.dayOfWeek,
    beginTime = lesson.beginTime,
    finalTime = lesson.finalTime,
  )

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