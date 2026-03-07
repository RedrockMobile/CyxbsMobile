package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.view.frame.decoration.CreateAffairDecorationViewModel
import com.cyxbs.pages.course.view.item.impl.CourseCreateAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateAffairItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object MobilePlatformCourseCreateAffairItemFactory : PlatformCourseCreateAffairItemFactory {
  override fun create(item: CourseCreateAffairItem): PlatformCourseCreateAffairItem {
    return MobilePlatformCourseCreateAffairItem(item)
  }
}

class MobilePlatformCourseCreateAffairItem(
  val item: CourseCreateAffairItem,
) : PlatformCourseCreateAffairItem {
  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: () -> Unit) -> Unit)) {
    val viewModel = viewModel<CreateAffairDecorationViewModel>()
    content.invoke {
      toast(viewModel.hierarchy.getAllWhatTime().size.toString())
    }
  }
}