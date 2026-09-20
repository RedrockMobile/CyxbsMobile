package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.view.item.extension.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.view.item.extension.LocalCourseItemBottomSheetDialog
import com.cyxbs.pages.course.view.item.impl.CourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItemFactory
import com.cyxbs.pages.schedule.api.IScheduleOccurrenceService
import kotlinx.coroutines.launch

/**
 * 完整课表中的事务创建 Item 配置。
 *
 * 空白处生成的内存占位 Item 点击后复用 Schedule 新建事务弹窗；保存成功前不写入本地数据库。
 */
object DefaultCourseCreateItemFactory : PlatformCourseCreateItemFactory {

  override fun create(item: CourseCreateItem): PlatformCourseCreateItem =
    DefaultCourseCreateItem(item)
}

private class DefaultCourseCreateItem(
  item: CourseCreateItem,
  scheduleService: IScheduleOccurrenceService = IScheduleOccurrenceService::class.impl(),
) : PlatformCourseCreateItem {

  private val bottomSheetExtension = DefaultCreateBottomSheetExtension(item, scheduleService)

  init {
    item.extensions.add(bottomSheetExtension)
  }

  @Composable
  override fun CourseItemContentWrapper(
    content: @Composable ((onClick: (MinuteTimePair) -> Unit) -> Unit),
  ) {
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    content {
      itemBottomSheetDialog.showDialog(bottomSheetExtension)
    }
  }
}

/**
 * 将完整课表的创建占位项接入通用详情弹窗宿主。
 *
 * 新建成功后移除占位项，真实事务由 Schedule Decoration 的数据订阅重新投射。
 */
private class DefaultCreateBottomSheetExtension(
  private val item: CourseCreateItem,
  private val scheduleService: IScheduleOccurrenceService,
) : CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = item.itemState

  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    val initialTiming = item.initialTimingFlow.collectAsState().value ?: return
    val uiCoroutineScope = rememberCoroutineScope()
    scheduleService.ScheduleCreateAffairContent(
      initialTiming = initialTiming,
      embeddedInHost = true,
      onDismiss = state::dismissDialogAnimated,
      onCreated = {
        uiCoroutineScope.launch { item.removeDraft() }
      },
      onEditModeChanged = { isEditing ->
        if (isEditing) state.lockCurrentPage()
      },
      onDismissRequestChanged = state::updateDismissRequestGate,
      onWindowOverlayContentChanged = state::updateWindowOverlayContent,
    )
  }
}
