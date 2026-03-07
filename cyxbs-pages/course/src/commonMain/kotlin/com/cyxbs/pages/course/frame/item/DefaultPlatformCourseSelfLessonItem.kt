package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.view.item.impl.CourseSelfLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseSelfLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseSelfLessonItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object DefaultPlatformCourseSelfLessonItemFactory : PlatformCourseSelfLessonItemFactory {
  override fun create(item: CourseSelfLessonItem): PlatformCourseSelfLessonItem {
    return DefaultPlatformCourseSelfLessonItem(item)
  }
}

private class DefaultPlatformCourseSelfLessonItem(
  val item: CourseSelfLessonItem,
) : PlatformCourseSelfLessonItem {
  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: ((MinuteTimePair) -> Unit)?) -> Unit)) {
    content.invoke {
      toast(item.lesson.course)
    }
  }
}