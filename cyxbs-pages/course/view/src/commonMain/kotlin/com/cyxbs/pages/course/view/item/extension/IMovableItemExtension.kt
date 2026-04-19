package com.cyxbs.pages.course.view.item.extension

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItemState
import kotlinx.datetime.DayOfWeek

/**
 * 支持长按移动的 item
 *
 * @author 985892345
 * @date 2025/5/1
 */
interface IMovableItemExtension : CourseItemExtension {

  // 得到抬手时目的地的偏移量
  fun getMoveDestinationOffset(
    upOrCancel: Boolean,
    itemState: CourseItemState,
    transition: MutableState<Offset>,
    screenTopLeft: Offset,
    size: IntSize,
    newBeginTime: MinuteTime,
  ): Offset = Offset.Zero

  // 修改 item 时间段
  suspend fun changeWhatTime(
    itemState: CourseItemState,
    newBeginTime: MinuteTime,
    newDayOfWeek: DayOfWeek,
  ) {
    val now = itemState.item.whatTime.now.value
    // 修改 item 时间段至对应时间
    itemState.item.whatTime.now.value = now.copy(
      dayOfWeek = newDayOfWeek,
      beginTime = newBeginTime,
      finalTime = newBeginTime + (now.finalTime - now.beginTime)
    )
  }

  // 在移动过程中是否允许展开时间轴
  fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean = true
}