package com.cyxbs.pages.course.view.item.extension

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItemState

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

  // 在移动过程中是否允许展开时间轴
  fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean = true
}