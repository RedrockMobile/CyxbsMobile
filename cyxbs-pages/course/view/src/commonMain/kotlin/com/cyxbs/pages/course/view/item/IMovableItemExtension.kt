package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cyxbs.pages.course.view.page.LocalCoursePageContext

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
    pageContext: LocalCoursePageContext,
    transition: MutableState<Offset>,
    screenTopLeft: Offset,
    size: IntSize,
  ): Offset = Offset.Zero

  // 根据最终目的地的偏移量执行动画
  suspend fun animateMove(
    itemState: CourseItemState,
    pageContext: LocalCoursePageContext,
    transition: MutableState<Offset>,
    destinationOffset: Offset,
  ) {
    animate(
      typeConverter = Offset.VectorConverter,
      initialValue = transition.value,
      targetValue = destinationOffset,
    ) { value, _ ->
      transition.value = value
    }
  }

  // 在移动过程中是否允许展开时间轴
  fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean = false
}