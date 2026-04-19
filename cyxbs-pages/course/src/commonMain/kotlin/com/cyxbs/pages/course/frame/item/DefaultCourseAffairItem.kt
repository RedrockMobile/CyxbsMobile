package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.view.item.impl.CourseAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseAffairItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object DefaultCourseAffairItemFactory : PlatformCourseAffairItemFactory {
  override fun create(item: CourseAffairItem): PlatformCourseAffairItem {
    return DefaultCourseAffairItem(item)
  }
}

private class DefaultCourseAffairItem(
  val item: CourseAffairItem
) : PlatformCourseAffairItem {
  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: ((MinuteTimePair) -> Unit)?) -> Unit)) {
    content.invoke {
      toast(item.affairDateModel.idModel.title.value)
    }
  }
}