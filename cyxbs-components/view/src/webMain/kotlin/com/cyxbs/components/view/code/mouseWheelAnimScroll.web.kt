package com.cyxbs.components.view.code

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold

internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig =
  WebScrollConfig

internal object WebScrollConfig : ScrollConfig {

  /*
   * The implementation is copied from androidMain.
   */
  override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset =
    event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta } * -64.dp.toPx()
}