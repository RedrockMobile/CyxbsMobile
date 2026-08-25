package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.view.item.extension.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.view.item.impl.CourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItemFactory
import com.cyxbs.pages.schedule.api.IScheduleService2

/**
 * 移动端课表长按创建 Item 的交互配置。
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
  scheduleService: IScheduleService2 = IScheduleService2::class.impl(),
) : PlatformCourseCreateItem {

  private val bottomSheetExtension = MobileCreateBottomSheetExtension(item, scheduleService)

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
  private val item: CourseCreateItem,
  private val scheduleService: IScheduleService2,
) : CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = item.itemState

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    val initialTiming = item.initialTimingFlow.collectAsState().value ?: return
    scheduleService.ScheduleCreateAffairContent(
      initialTiming = initialTiming,
      embeddedInHost = true,
      onDismiss = state::dismissDialog,
      onCreated = item::removeDraft,
      onEditModeChanged = { isEditing ->
        if (isEditing) state.lockCurrentPage()
      },
    )
  }
}
