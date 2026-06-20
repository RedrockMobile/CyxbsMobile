package com.cyxbs.components.view.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.MutatePriority
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 在底部显示的抽屉组件
 *
 * @author 985892345
 * 2024/4/15 20:43
 */

@Stable
class BottomSheetState(
  var onDismissRequest: suspend BottomSheetState.() -> Unit = { collapseSuspend() },
  val hideable: Boolean = false
) {

  internal val showHeight = mutableFloatStateOf(0F)
  internal val showMaxHeight = mutableFloatStateOf(0F)

  var peekHeight = 0F
    set(value) {
      if (field != value) {
        field = value
        if (stateFlow.value != BottomSheetValueState.Hide && showHeight.floatValue < value) {
          showHeight.floatValue = value
        }
      }
    }

  val stateFlow: StateFlow<BottomSheetValueState> get() = stateFlowInternal
  private val stateFlowInternal = MutableStateFlow(BottomSheetValueState.Collapsed)

  /**
   * 命令通道：承载 expand/collapse/hide 的「一次性触发」语义。
   */
  internal val commandFlow = MutableStateFlow<BottomSheetValueState?>(null)

  var state: BottomSheetValueState by mutableStateOf(stateFlowInternal.value)
    private set

  // 用户是否可滚动
  val userScrollEnabled = mutableStateOf(true)

  // 小于 0 时表示处于 hide 状态
  val fraction by derivedStateOfStructure {
    val showHeight = showHeight.floatValue
    val showMaxHeight = showMaxHeight.floatValue
    if (showMaxHeight == 0F) 0F else (showHeight - peekHeight) / (showMaxHeight - peekHeight)
  }

  internal val scrollableState = ScrollableState {
    val max = showMaxHeight.floatValue
    val now = showHeight.floatValue
    val new = (now - it).coerceIn(0F, max)
    showHeight.floatValue = new
    now - new
  }

  internal val bottomSheetSpring = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 1F  // 当距离目标 < 1px 时认为到达，如果不设置，会导致动画持续较久
  )

  /**
   * 异步触发 expand
   */
  fun expandAsync() {
    // 发命令给 collector（由 BottomSheetCompose 安装），不阻塞调用方
    commandFlow.value = BottomSheetValueState.Expanded
  }

  /**
   * 挂起直到 expand 完成或者被后续操作取消，若不需要等待则使用 [expandAsync]
   *
   * ⚠️外界需要考虑 CancellationException
   *
   * @throws kotlinx.coroutines.CancellationException 被另一次折叠或隐藏操作取消时抛出
   */
  suspend fun expandSuspend() {
    if (state == BottomSheetValueState.Expanded) return
    val now = showHeight.floatValue
    val target = showMaxHeight.floatValue
    if (now != target) {
      setState(BottomSheetValueState.Scrolling)
    }
    // now 即使等于 target 也需要执行 animateScrollBy，将其他正在进行中的协程给取消掉
    scrollableState.animateScrollBy(
      value = now - target,
      animationSpec = bottomSheetSpring,
    )
    setState(BottomSheetValueState.Expanded)
  }

  // 异步触发 collapse
  fun collapseAsync() {
    commandFlow.value = BottomSheetValueState.Collapsed
  }

  /**
   * 挂起直到 collapse 完成或者被后续操作取消，若不需要等待则使用 [collapseAsync]
   *
   * ⚠️外界需要考虑 CancellationException
   *
   * @throws kotlinx.coroutines.CancellationException 被另一次展开或隐藏操作取消时抛出
   */
  suspend fun collapseSuspend() {
    if (state == BottomSheetValueState.Collapsed) return
    val now = showHeight.floatValue
    val target = peekHeight
    if (now != target) {
      setState(BottomSheetValueState.Scrolling)
    }
    scrollableState.animateScrollBy(
      value = now - target,
      animationSpec = bottomSheetSpring,
    )
    setState(BottomSheetValueState.Collapsed)
  }

  // 异步触发 hide
  fun hideAsync() {
    commandFlow.value = BottomSheetValueState.Hide
  }

  /**
   * 挂起直到 hide 完成或者被后续操作取消，若不需要等待则使用 [hideAsync]
   *
   * ⚠️外界需要考虑 CancellationException
   *
   * @throws kotlinx.coroutines.CancellationException 被另一次展开或折叠操作取消时抛出
   */
  suspend fun hideSuspend() {
    if (state == BottomSheetValueState.Hide) return
    val now = showHeight.floatValue
    val target = 0F
    // hide 不触发 Scrolling 状态
    scrollableState.animateScrollBy(
      value = now - target,
      animationSpec = bottomSheetSpring,
    )
    setState(BottomSheetValueState.Hide)
  }

  internal fun setState(value: BottomSheetValueState) {
    stateFlowInternal.tryEmit(value)
    state = value
  }
}

enum class BottomSheetValueState {
  Hide, Collapsed, Scrolling, Expanded
}

@Composable
fun rememberBottomSheetState(
  onDismissRequest: suspend BottomSheetState.() -> Unit = { collapseSuspend() }
): BottomSheetState {
  return remember { BottomSheetState(onDismissRequest) }.also {
    it.onDismissRequest = onDismissRequest
  }
}

@Composable
fun BottomSheetCompose(
  bottomSheetState: BottomSheetState = rememberBottomSheetState(),
  modifier: Modifier = Modifier,
  peekHeight: Dp = 0.dp,
  dismissOnBackPress: Boolean = true,
  dismissOnClickOutside: Boolean = false,
  scrimColor: Color = Color.Transparent.copy(alpha = 0.6F),
  content: @Composable BottomSheetScope.() -> Unit
) {
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
  val density = LocalDensity.current
  DisposableEffect(bottomSheetState, peekHeight, density) {
    bottomSheetState.peekHeight = with(density) { peekHeight.toPx() }
    onDispose { }
  }
  LaunchedEffect(bottomSheetState) {
    bottomSheetState.commandFlow.collectLatest { command ->
      try {
        when (command) {
          BottomSheetValueState.Expanded -> bottomSheetState.expandSuspend()
          BottomSheetValueState.Collapsed -> bottomSheetState.collapseSuspend()
          BottomSheetValueState.Hide -> bottomSheetState.hideSuspend()
          else -> Unit
        }
        bottomSheetState.commandFlow.value = null
      } catch (_: CancellationException) {
        // 被新命令的 animateScrollBy 取消（例如展开动画中触发了折叠）
      }
    }
  }
}

@Composable
private fun BottomSheetBackgroundCompose(
  modifier: Modifier,
  bottomSheetState: BottomSheetState,
  scrimColor: Color,
  dismissOnBackPress: Boolean = true,
  dismissOnClickOutside: Boolean = false,
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
        if (dismissOnBackPress) {
          val enable by rememberDerivedStateOfStructure {
            // 因为 onPostFling 执行的动画比较缓慢，就会导致短时间内不是 Expanded
            // 最好的方式就是判断当前展开是否比较多，大于一定区间后都拦截返回键
            bottomSheetState.fraction > 0.5F
          }
          backHandler(enabled = enable) {
            coroutineScope.launch {
              bottomSheetState.onDismissRequest.invoke(bottomSheetState)
            }
          }
        }
        if (dismissOnClickOutside) {
          clickableNoIndicator { // 这里给背景设置点击事件默认会拦截后面的 XML 布局，所以只有需要时才设置
            coroutineScope.launch {
              bottomSheetState.onDismissRequest.invoke(bottomSheetState)
            }
          }
        }
      }
  ) {
    if (scrimColor != Color.Transparent) {
      Spacer(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            alpha = bottomSheetState.fraction
          }
          .background(scrimColor)
      )
    }
    content()
  }
}

private class BottomSheetSnapLayoutInfoProvider(
  private val bottomSheetState: BottomSheetState,
) : SnapLayoutInfoProvider {
  override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
    if (velocity == 0F) return 0F
    // 返回衰减动画应该需要执行的偏移量，decayOffset 是根据衰减动画计算出来可以执行的最大偏移量
    val min = if (bottomSheetState.hideable) 0F else bottomSheetState.peekHeight
    val max = bottomSheetState.showMaxHeight.floatValue
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
    val max = bottomSheetState.showMaxHeight.floatValue
    val now = bottomSheetState.showHeight.floatValue
    if (bottomSheetState.hideable && now <= min) {
      if (now == 0F) return 0F
      return if (now <= min / 2F) now else now - min
    }
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
      snapAnimationSpec = bottomSheetState.bottomSheetSpring,
    )
  }
  Box(
    modifier = modifier.fillMaxWidth()
      .onSizeChanged {
        if (bottomSheetState.showMaxHeight.floatValue != it.height.toFloat()) {
          bottomSheetState.showMaxHeight.floatValue = it.height.toFloat()
          if (bottomSheetState.stateFlow.value == BottomSheetValueState.Collapsed) {
            bottomSheetState.showHeight.floatValue = bottomSheetState.peekHeight
          } else if (bottomSheetState.stateFlow.value == BottomSheetValueState.Expanded) {
            bottomSheetState.showHeight.floatValue = it.height.toFloat()
          } else if (bottomSheetState.stateFlow.value == BottomSheetValueState.Hide) {
            bottomSheetState.showHeight.floatValue = 0F
          }
        }
      }.graphicsLayer {
        translationY = size.height - bottomSheetState.showHeight.floatValue
      }
      .clickableNoIndicator { /*防止点击穿透*/ }
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

@Stable
interface BottomSheetScope {
  // 这里直接使用 Modifier.bottomSheetDraggable(): Modifier 会出问题，使用处会经历两次 layout，原因未知
  @Stable
  fun bottomSheetDraggable(): Modifier
}

/**
 * 用于把 [BottomSheetScope] 下传到 [BottomSheetCompose] content 之外的地方。
 *
 * 当 BottomSheet 的内容由其他不携带 [BottomSheetScope] receiver 的 @Composable（如
 * navigation3 的 NavEntry.Content()）渲染时，可通过本 CompositionLocal 获取 scope 来调用
 * [BottomSheetScope.bottomSheetDraggable]。
 *
 * 仅在 [BottomSheetCompose] 内部被提供，外部使用前需先 provide。
 */
val LocalBottomSheetScope = staticCompositionLocalOf<BottomSheetScope> {
  error("LocalBottomSheetScope 未被提供，请确认处于 BottomSheetCompose 的 content 作用域内")
}

private class BottomSheetScopeImpl(
  private val coroutineScope: CoroutineScope,
  private val bottomSheetState: BottomSheetState,
  private val flingBehavior: TargetedFlingBehavior,
) : BottomSheetScope {
  override fun bottomSheetDraggable(): Modifier = Modifier.composed {
    draggable(
      enabled = bottomSheetState.userScrollEnabled.value,
      orientation = Orientation.Vertical,
      state = rememberDraggableState {
        val min = bottomSheetState.peekHeight
        val max = bottomSheetState.showMaxHeight.floatValue
        val now = bottomSheetState.showHeight.floatValue
        val new = (now - it).coerceIn(if (bottomSheetState.hideable) 0f else min, max)
        bottomSheetState.scrollableState.dispatchRawDelta(now - new)
      },
      onDragStarted = {
        bottomSheetState.scrollableState.scroll(scrollPriority = MutatePriority.UserInput) {
          // 这里会打断惯性滑动
          bottomSheetState.setState(BottomSheetValueState.Scrolling)
        }
      },
      onDragStopped = { velocity ->
        coroutineScope.launch {
          bottomSheetState.scrollableState.scroll {
            with(flingBehavior) {
              performFling(velocity)
            }
            bottomSheetState.setState(
              if (bottomSheetState.fraction == 0F) {
                BottomSheetValueState.Collapsed
              } else {
                if (bottomSheetState.fraction < 0F) {
                  BottomSheetValueState.Hide
                } else BottomSheetValueState.Expanded
              }
            )
          }
        }
      }
    )
  }
}

private class BottomSheetNestedScrollConnection(
  private val bottomSheetState: BottomSheetState,
  private val flingBehavior: TargetedFlingBehavior,
) : NestedScrollConnection {

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (!bottomSheetState.userScrollEnabled.value) return super.onPreScroll(available, source)
    val min = bottomSheetState.peekHeight
    val max = bottomSheetState.showMaxHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    // 先消耗手指向上的滑动
    if (available.y < 0) {
      bottomSheetState.setState(BottomSheetValueState.Scrolling)
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
    if (!bottomSheetState.userScrollEnabled.value) return super.onPostScroll(consumed, available, source)
    val min = bottomSheetState.peekHeight
    val max = bottomSheetState.showMaxHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    // 再消耗手指向下的滑动，只有 手指拖动 或者 惯性滑动但已经不是完全展开时 才能消耗
    if (available.y > 0 && (source == NestedScrollSource.UserInput || old != max)) {
      bottomSheetState.setState(BottomSheetValueState.Scrolling)
      val new = (old - available.y).coerceIn(min, max)
      val diff = old - new
      bottomSheetState.scrollableState.dispatchRawDelta(diff)
      return Offset(x = 0F, y = diff)
    }
    return super.onPostScroll(consumed, available, source)
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    if (!bottomSheetState.userScrollEnabled.value) return super.onPostFling(consumed, available)
    val max = bottomSheetState.showMaxHeight.floatValue
    val old = bottomSheetState.showHeight.floatValue
    if (old == max) return available.copy(x = 0F) // 完全展开时继续保持展开状态
    var consumeVelocity = available.y
    bottomSheetState.scrollableState.scroll(scrollPriority = MutatePriority.UserInput) {
      with(flingBehavior) {
        consumeVelocity = available.y - performFling(available.y)
      }
      bottomSheetState.setState(
        if (bottomSheetState.fraction == 0F)
          BottomSheetValueState.Collapsed else BottomSheetValueState.Expanded
      )
    }
    return Velocity(x = 0F, y = consumeVelocity)
  }
}