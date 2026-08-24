package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.view.item.extension.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.view.item.impl.CourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItemFactory

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
object MobileCourseCreateItemFactory : PlatformCourseCreateItemFactory {
  override fun create(item: CourseCreateItem): PlatformCourseCreateItem {
    return MobileCourseCreateItem(item)
  }
}

class MobileCourseCreateItem(
  val item: CourseCreateItem,
) : PlatformCourseCreateItem {

  private val bottomSheetExtension = MobileCreateBottomSheetExtension(item)

  init {
    item.extensions.add(bottomSheetExtension)
  }

  @Composable
  override fun CourseItemContentWrapper(content: @Composable ((onClick: (MinuteTimePair) -> Unit) -> Unit)) {
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    content.invoke {
      // 点击事件
      itemBottomSheetDialog.showDialog(bottomSheetExtension)
    }
  }
}


private class MobileCreateBottomSheetExtension(
  val item: CourseCreateItem
) : CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = item.itemState

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    val dateModel = item.dateModelFlow.collectAsState().value ?: return

  }
}