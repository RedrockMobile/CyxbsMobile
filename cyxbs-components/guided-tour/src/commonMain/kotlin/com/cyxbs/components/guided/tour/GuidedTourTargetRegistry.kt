package com.cyxbs.components.guided.tour

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

/**
 * 保存稳定引导 ID 到当前窗口坐标的映射。
 *
 * 布局尺寸变化后锚点会覆盖旧坐标；编辑器也可把源码区间换算为 [Rect] 后写入同一 Registry，
 * 因此教程协议无需知道手机、横屏或桌面窗口的实际像素位置。
 */
@Stable
class GuidedTourTargetRegistry {
  private val targets = mutableStateMapOf<String, Rect>()

  /** 返回目标当前在根布局中的边界；尚未布局或已离开组合时返回 null。 */
  operator fun get(targetId: String): Rect? = targets[targetId]

  /**
   * 注册由编辑器等非 Compose 布局测量出的目标。
   *
   * [boundsInRoot] 必须使用与 [GuidedTourOverlay] 相同根布局的坐标系。
   */
  fun updateExternalTarget(targetId: String, boundsInRoot: Rect) {
    require(targetId.isNotBlank()) { "Guided tour target id cannot be blank." }
    targets[targetId] = boundsInRoot
  }

  /** 移除不再可见的外部目标，防止窗口变化后继续显示旧位置。 */
  fun removeExternalTarget(targetId: String) {
    targets.remove(targetId)
  }

  /** 由 Compose Modifier 在每次重新布局时更新锚点。 */
  internal fun updateLayoutTarget(targetId: String, boundsInRoot: Rect) {
    targets[targetId] = boundsInRoot
  }

  /** Compose 锚点离开组合时清除对应坐标。 */
  internal fun removeLayoutTarget(targetId: String) {
    targets.remove(targetId)
  }
}
