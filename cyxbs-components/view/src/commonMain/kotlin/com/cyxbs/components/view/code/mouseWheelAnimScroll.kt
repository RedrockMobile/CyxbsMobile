package com.cyxbs.components.view.code

import androidx.compose.animation.core.*
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.node.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.withSign

/**
 * 因为官方没有暴露鼠标滚动，所以这里直接把代码 CV 过来了，
 * CV 的是 1.6.1 的版本，该版本未做页面甩尾，1.6.2 版本支持了页面甩尾代码变得较大
 *
 * @author 985892345
 * 2024/4/2 21:35
 */

/**
 * 带有过度动画的滚轮滑动
 */
@Stable
fun Modifier.mouseWheelAnimScroll(
  key: Any?,
  consume: (PointerEvent) -> Boolean = { event -> event.changes.fastAll { !it.isConsumed } },
  scrollBy: (Offset) -> Unit,
): Modifier = this then MouseWheelScrollElement(key, true, consume, scrollBy)

/**
 * 不带有过度动画的滚轮滑动
 */
@Stable
fun Modifier.mouseWheelRawScroll(
  key: Any?,
  consume: (PointerEvent) -> Boolean = { event -> event.changes.fastAll { !it.isConsumed } },
  scrollBy: (Offset) -> Unit,
): Modifier = this then MouseWheelScrollElement(key, false, consume, scrollBy)

private class MouseWheelScrollElement(
  val key: Any?,
  val isSmoothScrollingEnabled: Boolean,
  val consume: (PointerEvent) -> Boolean,
  val scrollBy: (Offset) -> Unit,
) : ModifierNodeElement<MouseWheelScrollNode>() {
  override fun create(): MouseWheelScrollNode =
    MouseWheelScrollNode(isSmoothScrollingEnabled, consume, scrollBy)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MouseWheelScrollElement) return false
    if (key != other.key) return false
    return true
  }

  override fun hashCode(): Int {
    return key?.hashCode() ?: 0
  }

  override fun update(node: MouseWheelScrollNode) {
    node.update(consume, scrollBy)
  }
}

internal class MouseWheelScrollNode(
  private val isSmoothScrollingEnabled: Boolean,
  private var consume: (PointerEvent) -> Boolean,
  private var scrollBy: (Offset) -> Unit,
) : DelegatingNode(), CompositionLocalConsumerModifierNode, ObserverModifierNode {

  private lateinit var mouseWheelScrollConfig: ScrollConfig
  private lateinit var physics: ScrollPhysics

  override fun onAttach() {
    mouseWheelScrollConfig = platformScrollConfig()
    physics = if (mouseWheelScrollConfig.isSmoothScrollingEnabled && isSmoothScrollingEnabled) {
      AnimatedMouseWheelScrollPhysics(
        mouseWheelScrollConfig,
        scrollBy,
        density = { currentValueOf(LocalDensity) }
      )
    } else {
      RawMouseWheelScrollPhysics(
        mouseWheelScrollConfig,
        scrollBy
      )
    }
    coroutineScope.launch {
      physics.launch()
    }
  }

  // TODO(https://youtrack.jetbrains.com/issue/COMPOSE-731/Scrollable-doesnt-react-on-density-changes)
  //  it isn't called, because LocalDensity is staticCompositionLocalOf
  override fun onObservedReadsChanged() {
    physics.mouseWheelScrollConfig = mouseWheelScrollConfig
    physics.scrollBy = scrollBy
  }

  private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
    mouseWheelInput()
  })

  fun update(consume: (PointerEvent) -> Boolean, scrollBy: (Offset) -> Unit) {
    pointerInputNode.resetPointerInputHandler()
    this.consume = consume
    this.scrollBy = scrollBy
    physics.scrollBy = scrollBy
  }

  private suspend fun PointerInputScope.mouseWheelInput() = awaitPointerEventScope {
    while (true) {
      val event = awaitScrollEvent()
      if (consume.invoke(event)) {
        with(physics) { onMouseWheel(event) }
        event.consume()
      }
    }
  }

  private suspend fun AwaitPointerEventScope.awaitScrollEvent(): PointerEvent {
    var event: PointerEvent
    do {
      event = awaitPointerEvent()
    } while (event.type != PointerEventType.Scroll)
    return event
  }

  private inline val PointerEvent.isConsumed: Boolean get() = changes.fastAny { it.isConsumed }
  private fun PointerEvent.consume() = changes.fastForEach { it.consume() }
}

private abstract class ScrollPhysics {
  abstract var mouseWheelScrollConfig: ScrollConfig
  abstract var scrollBy: (Offset) -> Unit

  open suspend fun launch() = Unit
  abstract fun PointerInputScope.onMouseWheel(pointerEvent: PointerEvent)
}

private class RawMouseWheelScrollPhysics(
  override var mouseWheelScrollConfig: ScrollConfig,
  override var scrollBy: (Offset) -> Unit,
) : ScrollPhysics() {
  override fun PointerInputScope.onMouseWheel(pointerEvent: PointerEvent) {
    val delta = with(mouseWheelScrollConfig) {
      calculateMouseWheelScroll(pointerEvent, size)
    }
    scrollBy.invoke(delta)
  }
}

private class AnimatedMouseWheelScrollPhysics(
  override var mouseWheelScrollConfig: ScrollConfig,
  override var scrollBy: (Offset) -> Unit,
  val density: () -> Density,
) : ScrollPhysics() {
  private var isAnimationRunning = false
  private val channel = Channel<Offset>(capacity = Channel.UNLIMITED)

  override suspend fun launch() {
    while (coroutineContext.isActive) {
      val event = channel.receive()
      isAnimationRunning = true
      try {
        animatedDispatchScroll(event, speed = 1f * density().density) {
          // Sum delta from all pending events to avoid multiple animation restarts.
          channel.sumOrNull()
        }
      } finally {
        isAnimationRunning = false
      }
    }
  }

  override fun PointerInputScope.onMouseWheel(pointerEvent: PointerEvent) {
    val scrollDelta = with(mouseWheelScrollConfig) {
      calculateMouseWheelScroll(pointerEvent, size)
    }
    if (mouseWheelScrollConfig.isPreciseWheelScroll(pointerEvent)) {
      // In case of high-resolution wheel, such as a freely rotating wheel with no notches
      // or trackpads, delta should apply directly without any delays.
      scrollBy.invoke(scrollDelta)

      /*
       * TODO Set isScrollInProgress to true in case of touchpad.
       *  Dispatching raw delta doesn't cause a progress indication even with wrapping in
       *  `scrollableState.scroll` block, since it applies the change within single frame.
       *  Touchpads emit just multiple mouse wheel events, so detecting start and end of this
       *  "gesture" is not straight forward.
       *  Ideally it should be resolved by catching real touches from input device instead of
       *  introducing a timeout (after each event before resetting progress flag).
       */
    } else {
      if (isAnimationRunning) {
        channel.trySend(scrollDelta)
      } else {
        // Try to apply small delta immediately to conditionally consume
        // an input event and to avoid useless animation.
        tryToScrollBySmallDelta(scrollDelta, threshold = 4.dp.toPx()) {
          channel.trySend(it)
        }
      }
    }
  }

  private fun Channel<Offset>.sumOrNull(): Offset? {
    var delta = tryReceive().getOrNull() ?: return null
    while (true) {
      val element = tryReceive().getOrNull()
      delta += element ?: return delta
    }
  }

  private fun tryToScrollBySmallDelta(
    delta: Offset,
    threshold: Float = 4f,
    fallback: (Offset) -> Unit
  ) {
    val newDelta = Offset(
      x = minOf(abs(delta.x), threshold).withSign(delta.x),
      y = minOf(abs(delta.y), threshold).withSign(delta.y),
    )
    scrollBy.invoke(newDelta)
    val diffDelta = delta - newDelta
    if (diffDelta.getDistance() != 0F) {
      fallback.invoke(diffDelta) // 将剩余的移动量放到下一次滚动事件中（官方是这么实现的）
    }
  }

  private suspend fun animatedDispatchScroll(
    eventDelta: Offset,
    speed: Float = 1f,
    maxDurationMillis: Int = 100,
    tryReceiveNext: () -> Offset?
  ) {
    var target = eventDelta
    tryReceiveNext()?.let {
      target += it
    }
    if (target.isLowScrollingDelta()) {
      return
    }
    var requiredAnimation = true
    var lastValue = Offset.Zero
    val anim = AnimationState(
      typeConverter = Offset.VectorConverter,
      initialValue = Offset.Zero
    )
    while (requiredAnimation && coroutineContext.isActive) {
      requiredAnimation = false
      val durationMillis = (abs((target - anim.value).getDistance()) / speed)
        .roundToInt()
        .coerceAtMost(maxDurationMillis)
      try {
        anim.animateTo(
          target,
          animationSpec = tween(
            durationMillis = durationMillis,
            easing = LinearEasing
          ),
          sequentialAnimation = true
        ) {
          val delta = value - lastValue
          if (!delta.isLowScrollingDelta()) {
            scrollBy(delta)
            lastValue += delta
          }
          tryReceiveNext()?.let {
            target += it
            requiredAnimation = !(target - lastValue).isLowScrollingDelta()
            cancelAnimation()
          }
        }
      } catch (ignore: CancellationException) {
        requiredAnimation = true
      }
    }
  }
}

/**
 * Returns true, if the value is too low for visible change in scroll (consumed delta, animation-based change, etc),
 * false otherwise
 */
private fun Offset.isLowScrollingDelta(): Boolean = abs(x) < 0.5F && abs(y) < 0.5F

internal interface ScrollConfig {

  /**
   * Enables animated transition of scroll on mouse wheel events.
   */
  val isSmoothScrollingEnabled: Boolean
    get() = true

  fun isPreciseWheelScroll(event: PointerEvent): Boolean = false

  fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset
}

internal expect fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig