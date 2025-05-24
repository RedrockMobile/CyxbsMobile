package com.cyxbs.pages.course.view.timeline

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import com.cyxbs.components.utils.compose.reflexScrollableForMouse
import com.cyxbs.pages.course.view.timeline.data.CourseTimelineData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * 课表滚动组件
 *
 * @author 985892345
 * @date 2024/1/27 16:25
 */

val LocalCourseScroll = staticCompositionLocalOf<LocalCourseScrollContext> { error("未实现") }

class LocalCourseScrollContext(
  timeline: CourseTimeline,
  scrollState: ScrollState,
  val outerCoordinatesState: State<LayoutCoordinates?>,
  val innerCoordinatesState: State<LayoutCoordinates?>,
) {
  var timeline by mutableStateOf(timeline)
    private set

  var scrollState by mutableStateOf(scrollState)
    private set

  // scroll 外层布局
  val outerCoordinates by outerCoordinatesState

  // scroll 内层布局
  val innerCoordinates by innerCoordinatesState

  // 时间轴上单个 data 的坐标系，用于快速得到组件的高度占比
  val timelineCoordinatesMap = mutableMapOf<CourseTimelineData, LayoutCoordinates?>()

  fun update(
    timeline: CourseTimeline,
    scrollState: ScrollState
  ) {
    this.timeline = timeline
    this.scrollState = scrollState
  }
}

@Composable
internal fun CourseScrollCompose(
  timeline: CourseTimeline,
  modifier: Modifier = Modifier,
  verticalScrollState: ScrollState = rememberScrollState(),
  scrollPaddingValues: PaddingValues,
  content: @Composable () -> Unit,
) {
  val outerCoordinatesState = remember { mutableStateOf<LayoutCoordinates?>(null) }
  val innerCoordinatesState = remember { mutableStateOf<LayoutCoordinates?>(null) }
  Layout(
    modifier = modifier
      .onGloballyPositioned {
        outerCoordinatesState.value = it
      }
      .reflexScrollableForMouse()
      .verticalScroll(state = verticalScrollState)
      .multiPointerScroll(verticalScrollState)
      .onGloballyPositioned {
        innerCoordinatesState.value = it
      },
    content = {
      val context = remember {
        LocalCourseScrollContext(
          timeline = timeline,
          scrollState = verticalScrollState,
          outerCoordinatesState = outerCoordinatesState,
          innerCoordinatesState = innerCoordinatesState,
        )
      }.apply {
        update(
          timeline = timeline,
          scrollState = verticalScrollState,
        )
      }
      CompositionLocalProvider(
        LocalCourseScroll provides context
      ) {
        content()
      }
    },
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
          coordinates
          placeables.fastForEach {
            it.placeRelative(x = start, y = topPadding)
            start += it.width
          }
        }
      }
    }
  )
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
      var totalPositionChange = 0F
      var lastPointerId: PointerId? = null
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
          if (pointer.id != lastPointerId) {
            // 说明之前用于滑动的手指已抬起或者已被消费，此时就重新计算 totalPositionChange
            totalPositionChange = 0F
            lastPointerId = pointer.id
          }
          val positionChange = pointer.positionChange()
          if (totalPositionChange.absoluteValue > viewConfiguration.touchSlop) {
            pointer.consume()
            chanel.trySend(positionChange.y)
          } else {
            totalPositionChange += positionChange.y
          }
        }
      }
    }
  }
}