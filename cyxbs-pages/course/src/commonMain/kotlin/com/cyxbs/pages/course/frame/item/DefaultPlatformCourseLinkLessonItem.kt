package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.view.item.impl.CourseLinkLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLinkLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLinkLessonItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object DefaultPlatformCourseLinkLessonItemFactory : PlatformCourseLinkLessonItemFactory {
  override fun create(item: CourseLinkLessonItem): PlatformCourseLinkLessonItem {
    return DefaultPlatformCourseLinkLessonItem(item)
  }
}

private class DefaultPlatformCourseLinkLessonItem(
  val item: CourseLinkLessonItem,
) : PlatformCourseLinkLessonItem {
  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: ((MinuteTimePair) -> Unit)?) -> Unit)) {
    content.invoke {
      toast(item.lesson.course)
    }
  }
}