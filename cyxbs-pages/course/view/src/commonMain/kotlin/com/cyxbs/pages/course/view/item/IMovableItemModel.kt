package com.cyxbs.pages.course.view.item

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/1
 */
interface IMovableItemModel : CourseItemModel {

  fun getMoveToNewLocation(): Offset

  suspend fun moveToNewLocation(
    offsetState: MutableState<Offset>,
    newOffset: Offset,
  ) {
    animate(
      typeConverter = Offset.VectorConverter,
      initialValue = offsetState.value,
      targetValue = newOffset,
    ) { value, _ ->
      offsetState.value = value
    }
  }
}