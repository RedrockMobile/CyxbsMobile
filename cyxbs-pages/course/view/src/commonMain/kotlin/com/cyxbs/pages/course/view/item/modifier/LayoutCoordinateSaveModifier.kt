package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 保存 item 的 layoutCoordinates
 *
 * 但需要注意的是：如果 item 被重叠完全遮挡，则可能并不存在 layoutCoordinates，或者 layoutCoordinates.isAttached = false
 *
 * @author 985892345
 * @date 2026/1/11
 */
object LayoutCoordinateSaveModifier : CourseItemModifier {
  @Composable
  override fun createModifier(): Modifier {
    val itemState = itemState
    DisposableEffect(itemState) {
      onDispose { itemState.layoutCoordinatesFlow.tryEmit(null) }
    }
    return Modifier.onGloballyPositioned {
      itemState.layoutCoordinatesFlow.tryEmit(it)
    }
  }
}