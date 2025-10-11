package com.cyxbs.components.view.calendar.layout

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import com.cyxbs.components.view.calendar.state.CalendarState
import kotlin.math.roundToInt

/**
 * 根据 Calendar 上下滑动进行偏移而不缩减高度的 MeasurePolicy
 *
 * @author 985892345
 * 2024/3/11 15:39
 */
fun CalendarState.createCalendarContentOffsetMeasurePolicy(): (MeasureScope, Measurable, Constraints) -> MeasureResult {
  return { scope, measurable, constraints ->
    var height = 0
    if (verticalIsCollapsed) {
      height = constraints.maxHeight
    }
    if (height == 0) {
      // 防止第一次初始化时不是折叠状态
      height = Snapshot.withoutReadObservation {
        (constraints.maxHeight + verticalScrollOffset).roundToInt()
      }
    }
    val placeable = measurable.measure(constraints.copy(maxHeight = height))
    scope.layout(placeable.width, constraints.maxHeight) {
      placeable.placeRelative(x = 0, y = 0)
    }
  }
}