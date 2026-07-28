package com.cyxbs.components.view.code

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold

/**
 * 因为官方没有暴露鼠标滚动，所以这里直接把代码 CV 过来了
 *
 * @author 985892345
 * 2024/4/2 21:35
 */
internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig =
  UiKitScrollConfig

internal object UiKitScrollConfig : ScrollConfig {

  /*
   * There are no scroll events produced on iOS,
   * so in reality this function should not be ever called.
   * The implementation is copied from androidMain just for testing purposes.
   */
  override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset =
    event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta } * -64.dp.toPx()
}