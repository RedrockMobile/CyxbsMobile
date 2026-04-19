package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.view.item.impl.CourseLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLessonItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object DefaultCourseLessonItemFactory : PlatformCourseLessonItemFactory {
  override fun create(item: CourseLessonItem): PlatformCourseLessonItem {
    return DefaultCourseLessonItem(item)
  }
}

private class DefaultCourseLessonItem(
  val item: CourseLessonItem,
) : PlatformCourseLessonItem {
  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: ((MinuteTimePair) -> Unit)?) -> Unit)) {
    content.invoke {
      toast(item.lesson.course)
    }
  }
}