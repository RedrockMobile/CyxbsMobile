package com.cyxbs.components.utils.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize

/**
 * 存放 Compose 触摸事件的配置
 *
 * @author 985892345
 * @date 2025/5/2
 */

/**
 * Compose 事件分发默认是不会传递给兄弟节点的，可以添加该配置已开启
 * 一般用于上层布局拦截了下层布局触摸事件的场景
 */
fun Modifier.sharePointerInput(enable: Boolean): Modifier = this then SharePointerInputElement(enable)

data class SharePointerInputElement(val enable: Boolean) :
  ModifierNodeElement<SharePointerInputModifierNode>() {
  override fun create(): SharePointerInputModifierNode = SharePointerInputModifierNode(enable)
  override fun update(node: SharePointerInputModifierNode) {
    node.enable = enable
  }
}

class SharePointerInputModifierNode(
  var enable: Boolean,
) : Modifier.Node(), PointerInputModifierNode {

  override fun onCancelPointerInput() {
  }

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
  }

  override fun sharePointerInputWithSiblings(): Boolean {
    return enable
  }
}