package com.cyxbs.components.guided.tour

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 把当前布局注册为教程引导锚点。
 *
 * ID 应来自业务协议中的稳定常量，不能使用本地化文本或列表下标；布局移动、旋转或调整窗口大小
 * 时会自动上报新边界。
 */
fun Modifier.guidedTourTarget(
  targetId: String,
  registry: GuidedTourTargetRegistry,
): Modifier = composed {
  require(targetId.isNotBlank()) { "Guided tour target id cannot be blank." }
  DisposableEffect(targetId, registry) {
    onDispose { registry.removeLayoutTarget(targetId) }
  }
  onGloballyPositioned { coordinates ->
    registry.updateLayoutTarget(targetId, coordinates.boundsInRoot())
  }
}
