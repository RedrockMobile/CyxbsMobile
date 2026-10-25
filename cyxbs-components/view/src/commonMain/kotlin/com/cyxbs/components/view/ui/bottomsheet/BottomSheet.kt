package com.cyxbs.components.view.ui.bottomsheet

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.isImeVisible
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 在底部显示的抽屉组件
 *
 * @author 985892345
 * 2024/4/15 20:43
 */

@Composable
fun rememberBottomSheetState(
  onDismissRequest: suspend BottomSheetState.() -> Unit = { collapseSuspend() }
): BottomSheetState {
  return remember { BottomSheetState(onDismissRequest) }.also {
    it.onDismissRequest = onDismissRequest
  }
}

/**
 * 显示可拖拽的底部抽屉。
 *
 * @param bottomSheetState 抽屉状态，可用于展开、折叠和隐藏。
 * @param modifier 应用于抽屉外层容器；其中位于本组件之前的 Insets 消费会参与剩余高度计算。
 * @param peekHeight 业务折叠高度，不需要手动包含 navBar 高度。
 * @param navigationBarContent 父级剩余 navBar 区域的占位内容，会在折叠和展开状态时进行 navBar 的占位。
 * 默认使用 `LocalAppColors.topBg`，传入 {} 可保留高度但保持透明，传 null 则关闭导航栏适配，由调用方自行兼容。
 * @param navigationBarPaddingInContent 是否对展开的内容应用 `navigationBarsPadding()`，
 * 默认在 [navigationBarContent] 非 null 时开启；业务已自行处理时传 false。
 * @param dismissImeOnClickOutsideFirst 点击外部区域时是否优先清除焦点并收起软键盘。为 true 且键盘
 * 可见时，本次点击只处理键盘；键盘完全收起后再次点击才会触发 [BottomSheetState.onDismissRequest]。
 */
@Composable
fun BottomSheetCompose(
  bottomSheetState: BottomSheetState = rememberBottomSheetState(),
  modifier: Modifier = Modifier,
  peekHeight: Dp = 0.dp,
  navigationBarContent: (@Composable BoxScope.() -> Unit)? = { DefaultBottomSheetNavigationBarContent() },
  navigationBarPaddingInContent: Boolean = navigationBarContent != null,
  dismissOnBackPress: Boolean = true,
  dismissOnClickOutside: Boolean = false,
  dismissImeOnClickOutsideFirst: Boolean = true,
  scrimColor: Color = Color.Transparent.copy(alpha = 0.6F),
  content: @Composable BottomSheetScope.() -> Unit
) {
  val density = LocalDensity.current
  val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
  val peekHeightUpdater = bottomSheetState.peekHeightUpdater
  val navigationBarEnabled = navigationBarContent != null
  BottomSheetBackgroundCompose(
    // 回调位于调用方 modifier 之后，可以拿到祖先和调用方已经消费的 Insets 总量。
    modifier = modifier.onConsumedWindowInsetsChanged { consumedInsets ->
      peekHeightUpdater.updateConsumedInsets(consumedInsets)
    },
    scrimColor = scrimColor,
    bottomSheetState = bottomSheetState,
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    dismissImeOnClickOutsideFirst = dismissImeOnClickOutsideFirst,
  ) {
    BottomSheetContent(
      modifier = Modifier.align(Alignment.BottomCenter),
      bottomSheetState = bottomSheetState,
      navigationBarContent = navigationBarContent,
      navigationBarPaddingInContent = navigationBarPaddingInContent,
      content = content
    )
  }
  DisposableEffect(
    peekHeightUpdater,
    peekHeight,
    density,
    navigationBarBottomPx,
    navigationBarEnabled,
  ) {
    peekHeightUpdater.updateConfiguration(
      basePeekHeightPx = with(density) { peekHeight.toPx() },
      navigationBarBottomPx = navigationBarBottomPx,
      includeNavigationBar = navigationBarEnabled,
      density = density,
    )
    onDispose { }
  }
  LaunchedEffect(bottomSheetState) {
    bottomSheetState.commandFlow.collectLatest { command ->
      try {
        when (command) {
          BottomSheetAnchor.Expanded -> bottomSheetState.expandSuspend()
          BottomSheetAnchor.Collapsed -> bottomSheetState.collapseSuspend()
          BottomSheetAnchor.Hidden -> bottomSheetState.hideSuspend()
          null -> Unit
        }
        bottomSheetState.commandFlow.value = null
      } catch (_: CancellationException) {
        // 被新命令的吸附动画取消（例如展开动画中触发了折叠），最后一帧速度会由新动画继承。
      }
    }
  }
}

/** 默认导航栏占位，跟随主题使用与课表、校车和地图 Sheet 一致的顶部背景色。 */
@Composable
internal fun DefaultBottomSheetNavigationBarContent() {
  Spacer(
    modifier = Modifier
      .fillMaxSize()
      .background(LocalAppColors.current.topBg)
  )
}

@Composable
private fun BottomSheetBackgroundCompose(
  modifier: Modifier,
  bottomSheetState: BottomSheetState,
  scrimColor: Color,
  dismissOnBackPress: Boolean = true,
  dismissOnClickOutside: Boolean = false,
  dismissImeOnClickOutsideFirst: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val dismissInteractionEnabled by rememberDerivedStateOfStructure {
    // 关闭目标建立后立即撤销内部关闭入口，避免动画期间再次点击导致旧动画被取消并重新开始。
    // Window 仍会拦截底层内容，真正解除平台级触摸隔离要等宿主在动画完成后移除 Window。
    when (val motion = bottomSheetState.motionState) {
      is BottomSheetMotionState.Idle -> motion.anchor == BottomSheetAnchor.Expanded
      is BottomSheetMotionState.Dragging -> motion.originAnchor == BottomSheetAnchor.Expanded
      is BottomSheetMotionState.Settling -> motion.targetAnchor == BottomSheetAnchor.Expanded
    }
  }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  Box(
    modifier = modifier
      .fillMaxSize()
      .focusRequester(focusRequester)
      .focusable()
      .plusDsl {
        if (dismissOnBackPress) {
          backHandler(enabled = dismissInteractionEnabled) {
            coroutineScope.launch {
              bottomSheetState.onDismissRequest.invoke(bottomSheetState)
            }
          }
        }
        // 全屏 Window 会保留到收起动画结束；这里只控制组件内部是否仍可重复发起关闭。
        if (dismissOnClickOutside && dismissInteractionEnabled) {
          clickableNoIndicator { // 这里给背景设置点击事件默认会拦截后面的 XML 布局，所以只有需要时才设置
            if (dismissImeOnClickOutsideFirst && isImeVisible()) {
              // iOS 隐藏键盘后仍可能保留输入焦点；先强制清焦点，再显式请求收起软键盘。
              focusManager.clearFocus(force = true)
              keyboardController?.hide()
            } else {
              coroutineScope.launch {
                bottomSheetState.onDismissRequest.invoke(bottomSheetState)
              }
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
            alpha = bottomSheetState.expansionFraction
          }
          .background(scrimColor)
      )
    }
    content()
  }
}

@Composable
private fun BottomSheetContent(
  modifier: Modifier,
  bottomSheetState: BottomSheetState,
  navigationBarContent: (@Composable BoxScope.() -> Unit)?,
  navigationBarPaddingInContent: Boolean,
  content: @Composable BottomSheetScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val flingBehavior = remember(bottomSheetState) {
    BottomSheetFlingBehavior(bottomSheetState)
  }
  val scope = remember(bottomSheetState) {
    BottomSheetScopeImpl(
      coroutineScope = coroutineScope,
      bottomSheetState = bottomSheetState,
      flingBehavior = flingBehavior,
    )
  }
  Box(
    modifier = modifier.fillMaxWidth()
      .onSizeChanged {
        if (bottomSheetState.showMaxHeight.floatValue != it.height.toFloat()) {
          bottomSheetState.showMaxHeight.floatValue = it.height.toFloat()
          when ((bottomSheetState.motionState as? BottomSheetMotionState.Idle)?.anchor) {
            BottomSheetAnchor.Collapsed -> {
              bottomSheetState.showHeight.floatValue = bottomSheetState.peekHeightUpdater.peekHeightPx
            }
            BottomSheetAnchor.Expanded -> bottomSheetState.showHeight.floatValue = it.height.toFloat()
            BottomSheetAnchor.Hidden -> bottomSheetState.showHeight.floatValue = 0F
            null -> Unit
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
    val contentWrapperModifier = if (navigationBarPaddingInContent) {
      Modifier.navigationBarsPadding()
    } else {
      Modifier
    }
    Box(modifier = contentWrapperModifier) {
      content(scope)
    }
    if (navigationBarContent != null) {
      with(scope) {
        NavigationBarContent(navigationBarContent)
      }
    }
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
  private val flingBehavior: BottomSheetFlingBehavior,
) : BottomSheetScope {
  override fun bottomSheetDraggable(): Modifier = Modifier.composed {
    draggable(
      enabled = bottomSheetState.userScrollEnabled.value,
      orientation = Orientation.Vertical,
      state = rememberDraggableState {
        val min = bottomSheetState.peekHeightUpdater.peekHeightPx
        val max = bottomSheetState.showMaxHeight.floatValue
        val now = bottomSheetState.showHeight.floatValue
        val new = (now - it).coerceIn(if (bottomSheetState.hideable) 0f else min, max)
        bottomSheetState.scrollableState.dispatchRawDelta(now - new)
      },
      onDragStarted = {
        bottomSheetState.scrollableState.scroll(scrollPriority = MutatePriority.UserInput) {
          // 这里会打断惯性滑动
          bottomSheetState.beginDrag(BottomSheetDragSource.DragHandle)
        }
      },
      onDragStopped = { velocity ->
        coroutineScope.launch {
          var targetAnchor = BottomSheetAnchor.Expanded
          bottomSheetState.scrollableState.scroll {
            with(flingBehavior) {
              targetAnchor = settle(
                initialVelocity = velocity,
                source = BottomSheetSettleSource.DragRelease,
              ).targetAnchor
            }
          }
          if (targetAnchor != BottomSheetAnchor.Expanded && bottomSheetState.requestDismissOnDrag) {
            // 离开 scroll mutation 后再请求关闭，回弹/收起动画才能安全重新取得 ScrollableState。
            bottomSheetState.onDismissRequest.invoke(bottomSheetState)
          }
        }
      }
    )
  }

  /**
   * 在尚未消费的导航栏区域上叠加业务内容。
   *
   * peekHeight 为 0 时，占位直接跟随整个 Sheet 的显示和隐藏动画；peekHeight 大于 0 时，占位在
   * 展开到折叠区间固定覆盖屏幕底部，从折叠继续进入 Hide 区间后再锁定于业务 peekHeight 下方，
   * 随整个 Sheet 一起下移。默认铺满 BottomSheet；地图横屏等特殊宽度由业务通过传入的 [content]
   * 自行约束。
   */
  @Composable
  fun BoxScope.NavigationBarContent(content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    val remainingHeightPx = bottomSheetState.peekHeightUpdater.remainingNavigationBarHeightPx
    if (remainingHeightPx <= 0F) return
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .offset {
          val showHeightPx = bottomSheetState.showHeight.floatValue
          val showMaxHeightPx = bottomSheetState.showMaxHeight.floatValue
          val basePeekHeightPx = bottomSheetState.peekHeightUpdater.basePeekHeightPx
          val offsetYPx = if (basePeekHeightPx <= 0F) {
            // 模态 Sheet 没有常驻折叠区，导航栏占位直接留在内容底部并随整个 Sheet 下移。
            0F
          } else {
            // 折叠以上抵消 Sheet 位移并固定在屏幕底部；折叠以下固定到业务 peek 底部一起下移。
            val topPx = maxOf(basePeekHeightPx, showHeightPx - remainingHeightPx)
            val bottomAlignedTopPx = showMaxHeightPx - remainingHeightPx
            topPx - bottomAlignedTopPx
          }
          IntOffset(
            x = 0,
            y = offsetYPx.roundToInt(),
          )
        }
        .fillMaxWidth()
        .height(with(density) { (remainingHeightPx + 2).toDp() }), // 添加 2px 防止衔接处出现虚线
      content = content,
    )
  }
}
