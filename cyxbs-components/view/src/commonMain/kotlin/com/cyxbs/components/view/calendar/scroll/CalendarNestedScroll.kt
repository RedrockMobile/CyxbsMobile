package com.cyxbs.components.view.calendar.scroll

import androidx.compose.animation.core.animate
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.cyxbs.components.view.calendar.state.CalendarState
import kotlin.math.abs

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/19 19:27
 */
class CalendarNestedScroll(
  private val state: CalendarState,
) : NestedScrollConnection {

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (!state.enableVerticalScroll) return Offset.Zero
    if (state.fraction != 0F) {
      return Offset(x = 0F, y = scrollBy(available.y))
    }
    return Offset.Zero
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource
  ): Offset {
    if (!state.enableVerticalScroll) return Offset.Zero
    if (abs(available.y) > abs(available.x) &&
      (state.fraction == 0F && available.y > 0F || state.fraction == 1F && available.y < 0F)
    ) {
      return Offset(x = 0F, y = scrollBy(available.y))
    }
    return super.onPostScroll(consumed, available, source)
  }

  override suspend fun onPreFling(available: Velocity): Velocity {
    if (!state.enableVerticalScroll) return Velocity.Zero
    if (state.verticalIsScrolling && (available.y != 0F || available == Velocity.Zero)) {
      val target = if (available.y > 1000) state.maxVerticalScrollOffset
      else if (available.y < -1000) 0F
      else if (state.fraction > 0.5F) state.maxVerticalScrollOffset
      else 0F
      animate(
        initialValue = state.verticalScrollOffset,
        targetValue = target,
        initialVelocity = available.y,
      ) { value, _ ->
        scrollBy(value - state.verticalScrollOffset)
      }
      return available
    }
    return super.onPreFling(available)
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    if (!state.enableVerticalScroll) return Velocity.Zero
    val vertical = state.verticalScrollState.value
    if (vertical is VerticalScrollState.Scrolling) {
      Snapshot.withMutableSnapshot {
        state.verticalScrollState.value =
          if (vertical.offset == 0F) VerticalScrollState.Collapsed
          else VerticalScrollState.Expanded(state.maxVerticalScrollOffset)
        state.tempClickDate?.let { state.updateClickDate(it) }
      }
    }
    return super.onPostFling(consumed, available)
  }

  private fun scrollBy(y: Float): Float {
    if (y == 0F) return 0F
    // 翻页中不允许上下滑
    if (state.horizontalIsScrolling) return 0F
    val scrollOffset = state.verticalScrollOffset
    val maxScrollOffset = state.maxVerticalScrollOffset
    if (y > 0) {
      // 向下滑动
      if (scrollOffset == maxScrollOffset) return 0F
      if (scrollOffset + y >= maxScrollOffset) {
        val result = maxScrollOffset - scrollOffset
        state.verticalScrollState.value = VerticalScrollState.Scrolling(maxScrollOffset, 1F)
        return result
      }
    } else {
      if (scrollOffset == 0F) return 0F
      if (scrollOffset + y <= 0) {
        val result = -scrollOffset
        state.verticalScrollState.value = VerticalScrollState.Scrolling(0F, 0F)
        return result
      }
    }
    val newOffset = scrollOffset + y
    state.verticalScrollState.value =
      VerticalScrollState.Scrolling(newOffset, newOffset / maxScrollOffset)
    return y
  }
}