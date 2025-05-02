package com.cyxbs.pages.course.view.timeline

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import com.cyxbs.components.utils.compose.reflexScrollableForMouse
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * 课表滚动组件
 *
 * @author 985892345
 * @date 2024/1/27 16:25
 */
@Composable
internal fun CourseScrollCompose(
  timeline: CourseTimeline,
  modifier: Modifier = Modifier,
  verticalScrollState: ScrollState = rememberScrollState(),
  scrollPaddingValues: PaddingValues,
  content: @Composable () -> Unit,
) {
  Layout(
    modifier = modifier
      .reflexScrollableForMouse()
      .verticalScroll(state = verticalScrollState)
      .multiPointerScroll(verticalScrollState),
    content = { content() },
    measurePolicy = remember(timeline) {
      { measurables, constraints ->
        var widthConsume = 0
        var initialWeight = 0F
        var nowWeight = 0F
        timeline.data.fastForEach {
          initialWeight += it.initialWeight
          nowWeight += it.nowWeight
        }
        val startPadding = scrollPaddingValues.calculateStartPadding(layoutDirection).roundToPx()
        val endPadding = scrollPaddingValues.calculateEndPadding(layoutDirection).roundToPx()
        val topPadding = scrollPaddingValues.calculateTopPadding().roundToPx()
        val bottomPadding = scrollPaddingValues.calculateBottomPadding().roundToPx()
        // 因为有 verticalScroll，所以这里 minHeight 就是父布局的高度
        val height =
          ((constraints.minHeight - topPadding - bottomPadding) * (nowWeight / initialWeight))
            .roundToInt()
        val maxWidth = constraints.maxWidth - startPadding - endPadding
        val placeables = measurables.fastMapIndexed { index, measurable ->
          measurable.measure(
            Constraints(
              minWidth = if (index != measurables.lastIndex || index == 0) 0 else maxWidth - widthConsume,
              maxWidth = maxWidth - widthConsume,
              maxHeight = height,
            )
          ).apply { widthConsume += width }
        }
        layout(constraints.maxWidth, height + topPadding + bottomPadding) {
          var start = startPadding
          placeables.fastForEach {
            it.placeRelative(x = start, y = topPadding)
            start += it.width
          }
        }
      }
    }
  )
  LaunchedEffect(timeline, verticalScrollState) {
    val lastTimeline = timeline.data.last()
    if (lastTimeline is MutableTimelineData) {
      // 最后一个展开时需要向上滚动
      lastTimeline.clickAnimateState.collectLatest {
        if (it && verticalScrollState.value == verticalScrollState.maxValue) {
          verticalScrollState.scroll {
            animate(
              0F, 0F,
              animationSpec = infiniteRepeatable(tween(1000))
            ) { _, _ ->
              scrollBy((verticalScrollState.maxValue - verticalScrollState.value).toFloat())
            }
          }
        }
      }
    }
  }
}


// verticalScroll 只支持首个手指的滚动，该方法实现非首个手指的滚动（比如首个手指被子节点消费时，其他手指仍可以进行滚动）
// 位置需要放置在 verticalScroll 之后
@Stable
@Composable
private fun Modifier.multiPointerScroll(verticalScrollState: ScrollState): Modifier {
  val chanel = remember { Channel<Float>(capacity = Channel.UNLIMITED) }
  LaunchedEffect(verticalScrollState) {
    while (true) {
      val value = chanel.receive()
      try {
        verticalScrollState.scroll(MutatePriority.UserInput) {
          scrollBy(-value)
        }
      } catch (e: CancellationException) {
        // 可能因为 scroll 优先级而被取消
      }
    }
  }
  return pointerInput(Unit) {
    awaitEachGesture {
      var main = awaitFirstDown(requireUnconsumed = false)
      while (true) {
        val event = awaitPointerEvent()
        val mainPointer = event.changes.fastFirstOrNull { it.id == main.id }
        if (mainPointer == null) break
        if (mainPointer.changedToUpIgnoreConsumed()) {
          // 这里按照 Scrollable -> DragGestureNode -> detectDragGestures -> awaitPointerSlopOrCancellation 逻辑
          // 如果第一根手指抬起来则会去选择下一根处于 pressed 的手指
          main = event.changes.fastFirstOrNull { it.pressed } ?: break
        }
        // 因为节点在 verticalScroll 之后，所以会先收到事件分发的 Main 流程
        // 在首个 pointer 未消费时，verticalScroll 可能就会触发他的消费然后进行滚动
        // 但 verticalScroll 只支持首个 pointer 的消费，所以我们在首个 pointer 被子节点消费时才允许其他手指的滚动
        // 防止首个 pointer 与其他 pointer 都参与了滚动
        if (!mainPointer.isConsumed) continue
        if (event.changes.size == 1) continue
        val pointer =
          event.changes.fastFirstOrNull { it.id != main.id && !it.isConsumed && it.pressed }
        if (pointer != null) {
          val positionChange = pointer.positionChange()
          pointer.consume()
          chanel.trySend(positionChange.y)
        }
      }
    }
  }
}