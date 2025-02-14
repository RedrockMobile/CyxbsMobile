package com.cyxbs.components.view.calendar.month

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import com.cyxbs.components.view.calendar.CalendarDateShowValue
import com.cyxbs.components.view.calendar.scroll.HorizontalScrollState
import com.cyxbs.components.view.calendar.scroll.VerticalScrollState
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.view.calendar.state.CalendarState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/24 19:12
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarState.CalendarMonthCompose(
  modifier: Modifier = Modifier,
  itemContent: @Composable (date: Date, show: CalendarDateShowValue) -> Unit,
) {
  val prefetchState = remember { LazyLayoutPrefetchState() }
  val itemProvider = remember(this) {
    CalendarMonthItemProvider(
      verticalScrollState = verticalScrollState,
      horizontalScrollState = horizontalScrollState,
      startDateState = startDateState,
      endDateState = endDateState,
      clickDateState = clickDateState,
      itemContent = itemContent,
    )
  }
  val measurePolicy = remember(this) {
    CalendarMonthMeasurePolicy(
      verticalScrollState = verticalScrollState,
      horizontalScrollState = horizontalScrollState,
      startDateState = startDateState,
      endDateState = endDateState,
      clickDateState = clickDateState,
      lineHeightState = lineHeightState,
      itemProvider = itemProvider,
    )
  }
  LazyLayout(
    modifier = modifier.clipToBounds()
      .dragPager(this, prefetchState, measurePolicy)
      .onSizeChanged {
        layoutWidth = it.width
      },
    itemProvider = remember<() -> LazyLayoutItemProvider> { { itemProvider } },
    prefetchState = prefetchState,
    measurePolicy = measurePolicy,
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Stable
@Composable
private fun Modifier.dragPager(
  calendar: CalendarState,
  prefetchState: LazyLayoutPrefetchState,
  measurePolicy: CalendarMonthMeasurePolicy,
): Modifier {
  val draggable = remember(calendar) {
    CalendarMonthPagerDraggable(calendar, prefetchState, measurePolicy)
  }
  return draggable(
    state = draggable,
    orientation = Orientation.Horizontal,
    onDragStarted = {
      draggable.onDragStarted()
    },
    onDragStopped = { velocity ->
      draggable.onDragStopped(velocity)
    }
  )
}

@OptIn(ExperimentalFoundationApi::class)
private class CalendarMonthPagerDraggable(
  private val calendarState: CalendarState,
  private val prefetchState: LazyLayoutPrefetchState,
  private val measurePolicy: CalendarMonthMeasurePolicy
) : DraggableState {

  // 当前页面偏移量，在页面拖动越界时与 horizontalScrollState 不一致以实现越界阻尼
  private var realOffset = 0f

  private var animateJob: Job? = null

  private val draggableState = DraggableState {
    with(calendarState) {
      if (verticalScrollState.value is VerticalScrollState.Scrolling) return@DraggableState
      val diffPage = (horizontalScrollState.value.offset / layoutWidth).roundToInt()
      val nowDate =
        if (currentIsCollapsed) clickDateState.value.minusWeeks(diffPage)
        else clickDateState.value.minusMonths(diffPage)
      // 当前中心页面距离左边缘的距离
      val oldOffset = horizontalScrollState.value.offset - diffPage * layoutWidth
      val newOffset = oldOffset + it
      val nowPage = getPage(nowDate)
      if (nowPage == 0 && newOffset >= 0 || nowPage == pageCount - 1 && newOffset <= 0) {
        // 计算实际移动量
        realOffset += it
        // 减去 diffPage * layoutWidth 转换为中心页面距离左边缘的距离
        val pageOffset = (realOffset - diffPage * layoutWidth)
          .coerceIn(-layoutWidth + 4F, layoutWidth - 4F) / 2
        horizontalScrollState.value =
          HorizontalScrollState.Scrolling(pageOffset + diffPage * layoutWidth)
      } else {
        realOffset = horizontalScrollState.value.offset + it
        horizontalScrollState.value = HorizontalScrollState.Scrolling(realOffset)
      }
    }
  }

  fun onDragStarted() {
    animateJob?.cancel()
    animateJob = null
    realOffset = calendarState.horizontalScrollState.value.offset
  }

  fun onDragStopped(velocity: Float) {
    with(calendarState) {
      // clickDataState 对应的页数
      val clickPage = getPage(clickDateState.value)
      // 当前滑到的中心页
      val nowPage = clickPage - (horizontalScrollState.value.offset / layoutWidth).roundToInt()
      // 动画目标页
      val targetPage =
        (if (velocity > 1000) nowPage - 1 else if (velocity < -1000) nowPage + 1 else nowPage)
          .coerceIn(0, pageCount - 1)
      // 动画目标页所需偏移量
      val targetValue = (clickPage - targetPage) * layoutWidth.toFloat()
      var newDate =
        if (currentIsCollapsed) clickDateState.value.plusWeeks(targetPage - clickPage)
        else clickDateState.value.plusMonths(targetPage - clickPage)
      newDate = newDate.coerceIn(startDateState.value, endDateState.value)
      if (horizontalScrollState.value.offset == targetValue) {
        Snapshot.withMutableSnapshot {
          clickDateState.value = newDate
          horizontalScrollState.value = HorizontalScrollState.Idle
          tempClickDate?.let { updateClickDate(it) }
        }
      } else {
        // 这里如果使用 onDragStopped 开启协程则会导致后续触摸事件延迟
        animateJob = coroutineScope.launch {
          animate(
            initialValue = horizontalScrollState.value.offset,
            targetValue = targetValue,
            initialVelocity = velocity,
          ) { value, _ ->
            horizontalScrollState.value = HorizontalScrollState.Scrolling(value)
          }
          Snapshot.withMutableSnapshot {
            clickDateState.value = newDate
            horizontalScrollState.value = HorizontalScrollState.Idle
            tempClickDate?.let { updateClickDate(it) }
          }
        }
      }
    }
  }

  override fun dispatchRawDelta(delta: Float) {
    draggableState.dispatchRawDelta(delta)
  }

  override suspend fun drag(dragPriority: MutatePriority, block: suspend DragScope.() -> Unit) {
    draggableState.drag(dragPriority, block)
  }

}

internal fun Date.monthLineCount(): Int {
  val sum = firstDate.dayOfWeekOrdinal + lengthOfMonth
  return sum / 7 + if (sum % 7 == 0) 0 else 1
}

internal fun Date.indexUntil(date: Date): Int {
  return ((date.year - year) * 12 + date.monthNumber - monthNumber) * 6 +
      (date.firstDate.dayOfWeekOrdinal + date.dayOfMonth - 1) / 7
}

