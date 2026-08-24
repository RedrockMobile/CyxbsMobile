package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.course.frame.header.CourseBottomSheetHeaderExtension
import com.cyxbs.pages.course.frame.header.CourseItemBottomSheetHeader
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 移动端主页课表的 Schedule Item 平台配置。
 *
 * common 的 Decoration 只负责布局；详情扩展、重叠 BottomSheet 与全天点击均在该平台实现中注入。
 */
object MobileScheduleItemFactory : PlatformScheduleItemFactory {

  override fun create(item: CourseScheduleItem): PlatformScheduleCourseItem =
    MobileScheduleCourseItem(item)

  override fun create(item: ScheduleAllDayItem): PlatformScheduleAllDayItem =
    MobileScheduleAllDayItem(item)
}

private class MobileScheduleCourseItem(
  private val item: CourseScheduleItem,
  private val scheduleService: IScheduleService2 = IScheduleService2::class.impl(),
) : PlatformScheduleCourseItem {

  init {
    item.extensions.add(MobileScheduleBottomSheetExtension(item, scheduleService))
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
      Window(dismissOnBackPress = { showStandalone.value = false }) {
        scheduleService.ScheduleDetailContent(
          occurrence = item.occurrence,
          embeddedInHost = false,
          onDismiss = { showStandalone.value = false },
        )
      }
    }
  }
}

/**
 * 日程课表项的移动端详情扩展。
 *
 * 与事务使用同一套 Header 和重叠 BottomSheet 协议：正文交给 Schedule 服务渲染，Header 展示
 * 当前日程的标题、描述和时间，并根据课表日期更新“下个日程/进行中/明天”状态。
 */
private class MobileScheduleBottomSheetExtension(
  private val item: CourseScheduleItem,
  private val scheduleService: IScheduleService2,
) : CourseBottomSheetHeaderExtension, CourseItemBottomSheetDialogExtension {

  override val itemState: CourseItemState
    get() = item.itemState

  /** 在课表通用 BottomSheet 宿主中显示日程详情。 */
  @Composable
  override fun CourseBottomSheetDialogContent(state: CourseItemBottomSheetDialogState) {
    scheduleService.ScheduleDetailContent(
      occurrence = item.occurrence,
      embeddedInHost = true,
      onDismiss = state::dismissDialog,
      onEditModeChanged = { isEditing ->
        if (isEditing) state.lockCurrentPage()
      },
    )
  }

  /** 绘制与事务一致的课表 Header，并在标题点击时重新打开当前日程详情。 */
  @Composable
  override fun CourseBottomSheetHeaderContent(modifier: Modifier) {
    val state = remember(this) { mutableStateOf("") }
    val itemBottomSheetDialog = LocalCourseItemBottomSheetDialog.current
    val fixed by item.whatTime.now.collectAsState()
    CourseItemBottomSheetHeader(
      modifier = modifier,
      state = state,
      title = item.occurrence.title,
      content = item.occurrence.description,
      beginTime = fixed.beginTime,
      finalTime = fixed.finalTime,
      onClickTitle = {
        itemBottomSheetDialog.showDialog(this)
      },
    )
    LaunchedEffect(this) {
      item.whatTime.now.collectLatest { current ->
        val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val now = localDateTime.toMinuteTimeDate()
        if (now.date.dayOfWeek == current.dayOfWeek) {
          if (now.time < current.beginTime) {
            state.value = "下个日程"
            delay(
              (current.beginTime.minuteOfDay - now.minuteOfDay).minutes +
                  localDateTime.second.seconds,
            )
          }
          state.value = "进行中..."
        } else {
          // Header 只会选择今天或明天的下一个课表项。
          state.value = "明天"
        }
      }
    }
  }
}

private class MobileScheduleAllDayItem(
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
      Window(dismissOnBackPress = { showDetail.value = false }) {
        scheduleService.ScheduleDetailContent(
          occurrence = item.occurrence,
          embeddedInHost = false,
          onDismiss = { showDetail.value = false },
        )
      }
    }
  }
}
