package com.cyxbs.components.utils.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 在底部显示的抽屉组件
 *
 * @author 985892345
 * 2024/4/15 20:43
 */

@Stable
class BottomSheetState {

  internal val showHeight = mutableFloatStateOf(0F)
  internal val contentHeight = mutableFloatStateOf(0F)

  var peekHeight = 0F
    set(value) {
      field = value
      if (showHeight.floatValue < value) {
        showHeight.floatValue = value
      }
    }

  val fraction by derivedStateOfStructure {
    val showHeight = showHeight.floatValue
    val contentHeight = contentHeight.floatValue
    if (contentHeight == 0F) 0F else (showHeight - peekHeight) / (contentHeight - peekHeight)
  }

  internal val scrollableState = ScrollableState {
    val min = peekHeight
    val max = contentHeight.floatValue
    val now = showHeight.floatValue
    val new = (now - it).coerceIn(min, max)
    showHeight.floatValue = new
    now - new
  }

  suspend fun expand() {
    val now = showHeight.floatValue
    val target = contentHeight.floatValue
    if (now == target) return
    scrollableState.animateScrollBy(
      value = now - target,
    )
  }

  suspend fun collapse() {
    val now = showHeight.floatValue
    val target = peekHeight
    if (now == target) return
    scrollableState.animateScrollBy(
      value = now - target,
    )
  }
}

@Composable
fun rememberBottomSheetState(): BottomSheetState {
  return remember { BottomSheetState() }
}

@Composable
fun BottomSheetCompose(
  bottomSheetState: BottomSheetState,
  modifier: Modifier = Modifier,
  peekHeight: Dp = 0.dp,
  dismissOnBackPress: (() -> Boolean)? = null,
  dismissOnClickOutside: (() -> Boolean)? = null,
  scrimColor: Color = Color.Transparent.copy(alpha = 0.6F),
  content: @Composable BottomSheetScope.() -> Unit
) {
  bottomSheetState.peekHeight = peekHeight.px
  BottomSheetBackgroundCompose(
    modifier = modifier,
    scrimColor = scrimColor,
    bottomSheetState = bottomSheetState,
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
  ) {
    BottomSheetContent(
      modifier = Modifier.align(Alignment.BottomCenter),
      bottomSheetState = bottomSheetState,
      content = content
    )
  }
}

@Composable
private fun BottomSheetBackgroundCompose(
  modifier: Modifier,
  bottomSheetState: BottomSheetState,
  scrimColor: Color,
  dismissOnBackPress: (() -> Boolean)?,
  dismissOnClickOutside: (() -> Boolean)?,
  content: @Composable BoxScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  Box(
    modifier = modifier
      .fillMaxSize()
      .focusRequester(focusRequester)
      .focusable()
      .plusDsl {
        if (dismissOnBackPress != null) {
          onKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && dismissOnBackPress()) {
              // 键盘按下 esc 后 dismiss
              coroutineScope.launch {
                bottomSheetState.collapse()
              }
              true
            } else false
          }
        }
      }
  ) {
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .plusDsl {
          if (dismissOnClickOutside != null) {
            clickableNoIndicator {
              if (dismissOnClickOutside()) {
                // 点击空白区域 dismiss
                coroutineScope.launch {
                  bottomSheetState.collapse()
                }
              }
            }
          }
        }
        .graphicsLayer {
          if (scrimColor != Color.Transparent) {
            alpha = bottomSheetState.fraction
          }
        }
        .background(scrimColor)
    )
    content()
  }
}

private class BottomSheetSnapLayoutInfoProvider(
  private val bottomSheetState: BottomSheetState,
) : SnapLayoutInfoProvider {
  override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
    // 返回衰减动画应该需要执行的偏移量，decayOffset 是根据衰减动画计算出来可以执行的最大偏移量
    val min = bottomSheetState.peekHeight
    val max = bottomSheetState.contentHeight.floatValue
    val now = bottomSheetState.showHeight.floatValue
    val new = now - decayOffset
    if (new < min) return now - min
    if (new > max) return now - max
    return 0F
  }

  override fun calculateSnapOffset(velocity: Float): Float {
    // 衰减动画执行完 calculateApproachOffset 返回的偏移后，开启新动画需要偏移的量
    // 如果衰减动画的起始速度为 0，则就相当于松手后执行动画回到起点或终点
    val min = bottomSheetState.peekHeight
    val max = bottomSheetState.contentHeight.floatValue
    val now = bottomSheetState.showHeight.floatValue
    if (now == min || now == max) return 0F
    val boundary = (min + max) / 2F
    return if (now <= boundary) now - min else now - max
  }
}

@Composable
private fun BottomSheetContent(
  modifier: Modifier,
  bottomSheetState: BottomSheetState,
  content: @Composable BottomSheetScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val decayAnimationSpec = rememberSplineBasedDecay<Float>()
  // 参考 PagerDefaults#flingBehavior
  val flingBehavior = remember(bottomSheetState) {
    snapFlingBehavior(
      snapLayoutInfoProvider = BottomSheetSnapLayoutInfoProvider(bottomSheetState),
      decayAnimationSpec = decayAnimationSpec,
      snapAnimationSpec = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Int.VisibilityThreshold.toFloat()
      )
    )
  }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
          if (bottomSheetState.contentHeight.floatValue == 0F) {
            bottomSheetState.contentHeight.floatValue = placeable.height.toFloat()
            bottomSheetState.showHeight.floatValue = bottomSheetState.peekHeight
          }
          placeable.place(
            x = 0,
            y = (bottomSheetState.contentHeight.floatValue - bottomSheetState.showHeight.floatValue).roundToInt()
          )
        }
      }
      .nestedScroll(remember(bottomSheetState) {
        BottomSheetNestedScrollConnection(
          bottomSheetState = bottomSheetState,
          flingBehavior = flingBehavior,
        )
      })
  ) {
    val scope = remember(bottomSheetState) {
      BottomSheetScopeImpl(
        coroutineScope = coroutineScope,
        bottomSheetState = bottomSheetState,
        flingBehavior = flingBehavior,
      )
    }
    content(scope)
  }
}

interface BottomSheetScope {
  @Composable
  fun Modifier.bottomSheetDraggable(): Modifier
}

private class BottomSheetScopeImpl(
  private val coroutineScope: CoroutineScope,
  private val bottomSheetState: BottomSheetState,
  private val flingBehavior: TargetedFlingBehavior,
) : BottomSheetScope {
  @Composable
  override fun Modifier.bottomSheetDraggable(): Modifier = this then draggable(
    orientation = Orientation.Vertical,
    state = rememberDraggableState {
      bottomSheetState.scrollableState.dispatchRawDelta(it)
    },
    onDragStopped = { velocity ->
      coroutineScope.launch {
        bottomSheetState.scrollableState.scroll {
          with(flingBehavior) {
            performFling(velocity)
          }
        }
      }
    }
  )
}

private class BottomSheetNestedScrollConnection(
  private val bottomSheetState: BottomSheetState,
  private val flingBehavior: TargetedFlingBehavior,
) : NestedScrollConnection {

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    val min = bottomSheetState.peekHeight
    val max = bottomSheetState.contentHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    // 先消耗手指向上的滑动
    if (available.y < 0) {
      val new = (old - available.y).coerceIn(min, max)
      val diff = old - new
      bottomSheetState.scrollableState.dispatchRawDelta(diff)
      return Offset(x = 0F, y = diff)
    }
    return super.onPreScroll(available, source)
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource
  ): Offset {
    val min = bottomSheetState.peekHeight
    val max = bottomSheetState.contentHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    // 再消耗手指向下的滑动
    if (available.y > 0) {
      val new = (old - available.y).coerceIn(min, max)
      val diff = old - new
      bottomSheetState.scrollableState.dispatchRawDelta(diff)
      return Offset(x = 0F, y = diff)
    }
    return super.onPostScroll(consumed, available, source)
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    var remainVelocity = available.y
    bottomSheetState.scrollableState.scroll {
      with(flingBehavior) {
        remainVelocity = available.y - performFling(available.y)
      }
    }
    return Velocity(x = 0F, y = remainVelocity)
  }
}