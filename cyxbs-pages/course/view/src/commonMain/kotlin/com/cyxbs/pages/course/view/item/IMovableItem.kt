package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.animate
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/1
 */
interface IMovableItem : CourseItem {

  fun getMoveToNewLocation(): Offset

  suspend fun moveToNewLocation(
    alphaState: MutableFloatState,
    offsetState: MutableState<Offset>,
    newOffset: Offset,
    isCompleteCover: Boolean,
  ) {
    val beginOffset = offsetState.value
    animate(
      initialValue = 0F,
      targetValue = 1F,
    ) { value, _ ->
      if (isCompleteCover) {
        alphaState.floatValue = 1 - value
      }
      offsetState.value = (newOffset - beginOffset) * value + beginOffset
    }
  }
}