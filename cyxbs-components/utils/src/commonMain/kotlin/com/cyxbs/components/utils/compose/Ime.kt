package com.cyxbs.components.utils.compose

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 键盘 / bringIntoView 相关的 Modifier 工具。
 *
 * @author 985892345
 * @date 2026/6/27
 */

/**
 * 创建一份仅在当前弹窗生命周期内使用的目标 IME 状态。
 *
 * 宿主使用 [imePaddingWithTarget] 执行实际位移，并通过 [LocalImePaddingTargetState] 将状态提供给
 * 子内容；获得焦点后必须保持可见的区域使用 [imePaddingTarget] 标记：
 *
 * ```kotlin
 * val imeState = rememberImePaddingTargetState()           // 使用 rememberImePaddingTargetState()
 * CompositionLocalProvider(LocalImePaddingTargetState provides imeState) {
 *   Box(
 *     modifier = Modifier
 *       .fillMaxSize()
 *       .imePaddingWithTarget(imeState),                   // 父组件设置 imePaddingWithTarget()
 *   ) {
 *     Column(modifier = Modifier.imePaddingTarget()) {     // 子组件设置 imePaddingTarget()
 *       BasicTextField(state = titleState)
 *       InfoRow()
 *       BasicTextField(state = descriptionState)
 *     }
 *     OtherContent()
 *   }
 * }
 * ```
 *
 * 上例中标题或描述获得焦点时，宿主只会上移到整个目标 [Column] 完整露出；[OtherContent] 不会
 * 扩大目标边界，仍允许被键盘覆盖。
 */
@Composable
fun rememberImePaddingTargetState(): ImePaddingTargetState = remember { ImePaddingTargetState() }

/**
 * 当前弹窗提供的目标 IME 状态；为 null 时 [imePaddingTarget] 退化为空操作，普通页面不受影响。
 */
val LocalImePaddingTargetState = staticCompositionLocalOf<ImePaddingTargetState?> { null }

/**
 * 将当前节点作为全尺寸 IME 位移宿主。
 *
 * [imePaddingWithHeight] 放在坐标观察器外层，宿主与目标会一起平移，因此二者的相对边界不会在
 * IME 动画期间发生反馈抖动。
 */
fun Modifier.imePaddingWithTarget(state: ImePaddingTargetState): Modifier =
  imePaddingWithHeight(state.overlapHeight).onGloballyPositioned(state::updateHostCoordinates)

/**
 * 标记获得焦点后必须完整露在键盘上方的内容区域。
 *
 * @param bottomSpacing 目标内容与键盘顶部额外保留的间距。
 */
fun Modifier.imePaddingTarget(bottomSpacing: Dp = 2.dp): Modifier = composed {
  val state = LocalImePaddingTargetState.current ?: return@composed this
  val bottomSpacingPx = with(LocalDensity.current) { bottomSpacing.toPx() }
  onFocusChanged { state.updateTargetFocus(it.hasFocus) }
    .onGloballyPositioned { state.updateTargetCoordinates(it, bottomSpacingPx) }
}

/**
 * @param overlapHeight 键盘最终状态与布局重叠的高度
 */
fun Modifier.imePaddingWithHeight(
  overlapHeight: Int,
  onFraction: ((fraction: Float, imeOffset: Float) -> Unit)? = null
): Modifier = if (overlapHeight == 0) imePadding() else layout { measure, constraints ->
  val placeable = measure.measure(constraints)
  layout(placeable.width, placeable.height) {
    val ime = WindowInsetsRulers.Ime
    val animationProperties = ime.getAnimation(this)
    if (animationProperties.isVisible) {
      // 键盘可见
      val height = placeable.height.toFloat()
      val sourceBottom = animationProperties.source.bottom.current(height)
      val currentBottom = ime.current.bottom.current(height)
      if (animationProperties.isAnimating) {
        // 键盘上升或下降动画中
        val targetBottom = animationProperties.target.bottom.current(height)
        val top = minOf(sourceBottom, targetBottom)
        val bottom = maxOf(sourceBottom, targetBottom)
        val imeHeight = bottom - top
        val fraction = (bottom - currentBottom) / imeHeight
        // 目标区域本来就在键盘上方时不应反向下移。
        val offset = (overlapHeight - imeHeight).coerceAtMost(0F)
        onFraction?.invoke(fraction, offset)
        placeable.place(x = 0, y = (offset * fraction).roundToInt())
      } else {
        // 键盘完全展开
        val imeHeight = abs(sourceBottom - currentBottom)
        // 目标区域本来就在键盘上方时不应反向下移。
        val offset = (overlapHeight - imeHeight).coerceAtMost(0F)
        onFraction?.invoke(1F, offset)
        placeable.place(x = 0, y = offset.roundToInt())
      }
    } else {
      // 键盘不可见
      onFraction?.invoke(0F, 0F)
      placeable.place(x = 0, y = 0)
    }
  }
}

/**
 * 需要由指定内容边界决定 IME 上移距离的状态。
 *
 * 宿主通过 [imePaddingWithTarget] 注册完整窗口坐标，获得焦点的内容通过 [imePaddingTarget]
 * 注册必须露出的底边。最终允许键盘覆盖的高度等于“宿主底部到目标底部”的距离，因此弹窗只会上移
 * 暴露目标区域所需的部分，不会像普通 `imePadding()` 一样整体移动到键盘上方。
 */
@Stable
class ImePaddingTargetState internal constructor() {

  internal var overlapHeight by mutableIntStateOf(0)
    private set

  private var hostCoordinates: LayoutCoordinates? = null
  private var targetCoordinates: LayoutCoordinates? = null
  private var targetBottomSpacingPx = 0F
  private var targetHasFocus = false

  /** 更新承载 IME 位移的全尺寸宿主坐标。 */
  internal fun updateHostCoordinates(coordinates: LayoutCoordinates) {
    hostCoordinates = coordinates
    updateOverlapHeightIfNeeded()
  }

  /** 更新需要在键盘上方完整露出的内容区域。 */
  internal fun updateTargetCoordinates(
    coordinates: LayoutCoordinates,
    bottomSpacingPx: Float,
  ) {
    targetCoordinates = coordinates
    targetBottomSpacingPx = bottomSpacingPx
    updateOverlapHeightIfNeeded()
  }

  /**
   * 只在目标区域拥有焦点时刷新边界；失焦后保留最后一次结果，让键盘收起动画仍按原路径返回。
   */
  internal fun updateTargetFocus(hasFocus: Boolean) {
    targetHasFocus = hasFocus
    updateOverlapHeightIfNeeded()
  }

  /** 使用同一 Compose 坐标树计算边界，避免 Android/iOS 窗口坐标系差异。 */
  private fun updateOverlapHeightIfNeeded() {
    if (!targetHasFocus) return
    val host = hostCoordinates?.takeIf { it.isAttached } ?: return
    val target = targetCoordinates?.takeIf { it.isAttached } ?: return
    val targetBottom = host.localPositionOf(target).y + target.size.height + targetBottomSpacingPx
    overlapHeight = (host.size.height - targetBottom).roundToInt().coerceAtLeast(0)
  }
}





/////////////////////////////////////////////
//
//    Modifier.bringIntoViewFullBounds()
//
/////////////////////////////////////////////


/**
 * 当子节点（如 BasicTextField）请求 `bringIntoView` 时，无视其请求的矩形（如光标矩形），改成把
 * **本节点的完整边界**带入可视区。常用于「输入框外套了带高度/背景的容器」：聚焦弹键盘时让整个容器
 * （而非只露光标/输入框）顶到键盘上方。
 *
 * ```
 * Box(
 *   Modifier
 *     .bringIntoViewFullBounds() // 将 Box 完整撑起来，而不是只撑起 BasicTextField
 *     .padding(10.dp)
 * ) {
 *   BasicTextField(state = textState, ...)
 * }
 * ```
 *
 * 原理：BasicTextField 内部在 `TextFieldCoreModifierNode`（LayoutModifierNode）的 `updateScrollState()`
 * 里——布局 placement 回调中的把「光标矩形」带入可视区。该请求向上
 * 传播时会被本节点（最近的 [BringIntoViewModifierNode] 祖先）截获，改成请求「本节点完整边界」再上抛。
 */
fun Modifier.bringIntoViewFullBounds(): Modifier = then(BringIntoViewFullBoundsElement)

private data object BringIntoViewFullBoundsElement :
  ModifierNodeElement<BringIntoViewFullBoundsNode>() {
  override fun create(): BringIntoViewFullBoundsNode = BringIntoViewFullBoundsNode()
  override fun update(node: BringIntoViewFullBoundsNode) {}
}

private class BringIntoViewFullBoundsNode : Modifier.Node(), BringIntoViewModifierNode, LayoutModifierNode,
  FocusEventModifierNode {

  private val imeIsVisibleFlow = MutableStateFlow(false)
  private val hasFocus = mutableStateOf(false)

  override fun onAttach() {
    super.onAttach()
    coroutineScope.launch {
      imeIsVisibleFlow.collect {
        if (it && hasFocus.value) {
          // ime 首次弹起时需要手动 bringIntoView 一次
          // 因为在安卓上首次点击弹起键盘会直接使用光标的位置
          // 官方还没有完全切换为 BringIntoViewModifierNode
          // 详细可以看 setInsertionMarkerLocation
          bringIntoView()
        }
      }
    }
  }

  override suspend fun bringIntoView(
    childCoordinates: LayoutCoordinates,
    boundsProvider: () -> Rect?,
  ) {
    // 无视子节点请求的矩形（childCoordinates / boundsProvider），改成请求「本节点完整边界」：
    // 无参 bringIntoView() 会用本节点的整块尺寸向上转发（见 androidx.compose.ui.relocation.bringIntoView）。
    bringIntoView()
  }

  override fun onFocusEvent(focusState: FocusState) {
    // onFocusEvent 比 ime 真正弹起要先触发
    // 所以还是需要在 measure 中拿到 ime.getAnimation(this).isVisible 再去触发 bringIntoView()
    hasFocus.value = focusState.hasFocus
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      if (hasFocus.value) {
        val ime = WindowInsetsRulers.Ime
        val animationProperties = ime.getAnimation(this)
        imeIsVisibleFlow.value = animationProperties.isVisible
      }
      placeable.place(0, 0)
    }
  }
}
