package com.cyxbs.pages.course.frame.item

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.view.item.extension.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.view.item.impl.CourseScheduleItem
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleAllDayItem
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleCourseItem
import com.cyxbs.pages.course.view.item.impl.PlatformScheduleItemFactory
import com.cyxbs.pages.course.view.item.impl.ScheduleAllDayItem
import com.cyxbs.pages.schedule.api.IScheduleService2

/**
 * 自适应课表的 Schedule Item 平台配置。
 *
 * common 的 Decoration 只负责布局；详情扩展、重叠 BottomSheet 与全天点击均在该平台实现中注入。
 */
object DefaultScheduleItemFactory : PlatformScheduleItemFactory {

  override fun create(item: CourseScheduleItem): PlatformScheduleCourseItem =
    DefaultScheduleCourseItem(item)

  override fun create(item: ScheduleAllDayItem): PlatformScheduleAllDayItem =
    DefaultScheduleAllDayItem(item)
}

private class DefaultScheduleCourseItem(
  private val item: CourseScheduleItem,
  private val scheduleService: IScheduleService2 = IScheduleService2::class.impl(),
) : PlatformScheduleCourseItem {

  init {
    item.extensions.add(object : CourseItemBottomSheetDialogExtension {
      override val itemState: CourseItemState
        get() = item.itemState

      @Composable
      override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
        scheduleService.ScheduleDetailContent(
          occurrence = item.occurrence,
          embeddedInHost = true,
          onDismiss = state::dismissDialog,
          onEditModeChanged = { isEditing ->
            if (isEditing) state.lockCurrentPage()
          },
          onDismissRequestChanged = state::updateDismissRequestGate,
          onWindowOverlayContentChanged = state::updateWindowOverlayContent,
        )
      }
    })
  }

  @Composable
  override fun CourseItemContentWrapper(
    content: @Composable (onClick: ((MinuteTimePair) -> Unit)?) -> Unit,
  ) {
    val dialog = LocalCourseItemBottomSheetDialog.current
    val showStandalone = remember(item) { mutableStateOf(false) }
    content {
      val overlap = item.itemState.overlap
      if (overlap != null) dialog.showDialog(overlap)
      else showStandalone.value = true
    }
    if (showStandalone.value) {
      // Window 由入口持有；返回键交给内部 BottomSheet，以保留未保存内容确认逻辑。
      Window(dismissOnBackPress = null) {
        Box(modifier = Modifier.fillMaxSize()) {
          scheduleService.ScheduleDetailContent(
            occurrence = item.occurrence,
            embeddedInHost = false,
            onDismiss = { showStandalone.value = false },
          )
        }
      }
    }
  }
}

private class DefaultScheduleAllDayItem(
  private val item: ScheduleAllDayItem,
  private val scheduleService: IScheduleService2 = IScheduleService2::class.impl(),
) : PlatformScheduleAllDayItem {

  @Composable
  override fun AllDayItemContentWrapper(
    content: @Composable (onClick: (() -> Unit)?) -> Unit,
  ) {
    val showDetail = remember(item) { mutableStateOf(false) }
    content { showDetail.value = true }
    if (showDetail.value) {
      // 全天日程同样由点击入口提供完整窗口层，详情组件只负责内容与内部状态。
      Window(dismissOnBackPress = null) {
        Box(modifier = Modifier.fillMaxSize()) {
          scheduleService.ScheduleDetailContent(
            occurrence = item.occurrence,
            embeddedInHost = false,
            onDismiss = { showDetail.value = false },
          )
        }
      }
    }
  }
}
