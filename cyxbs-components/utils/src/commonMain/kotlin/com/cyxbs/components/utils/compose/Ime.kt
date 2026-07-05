package com.cyxbs.components.utils.compose

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.Constraints
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
        val offset = overlapHeight - imeHeight
        onFraction?.invoke(fraction, offset)
        placeable.place(x = 0, y = (offset * fraction).roundToInt())
      } else {
        // 键盘完全展开
        val imeHeight = abs(sourceBottom - currentBottom)
        val offset = overlapHeight - imeHeight
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

private class BringIntoViewFullBoundsNode : Modifier.Node(), BringIntoViewModifierNode, LayoutModifierNode {

  private val imeIsVisibleFlow = MutableStateFlow(false)

  override fun onAttach() {
    super.onAttach()
    coroutineScope.launch {
      imeIsVisibleFlow.collect {
        if (it) {
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

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      val ime = WindowInsetsRulers.Ime
      val animationProperties = ime.getAnimation(this)
      imeIsVisibleFlow.value = animationProperties.isVisible
      placeable.place(0, 0)
    }
  }
}
