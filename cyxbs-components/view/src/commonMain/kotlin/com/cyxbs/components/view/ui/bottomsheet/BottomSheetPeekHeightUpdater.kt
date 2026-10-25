package com.cyxbs.components.view.ui.bottomsheet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density

/**
 * 汇总业务 peekHeight、系统导航栏和父级 Insets 消费量，并维护最终有效折叠高度。
 *
 * Insets 消费回调发生在布局阶段，因此这里直接保存最近配置并同步计算结果，避免调用方再维护
 * 一套高度状态。配置或消费量任一变化时都会重新计算，旋转屏幕和动态安全区也能生效。
 * 调用方可通过 [BottomSheetState.peekHeightUpdater] 取得本对象，并读取 [peekHeightPx]；更新入口
 * 仅供 [BottomSheetCompose] 使用，以保证业务高度和 Insets 始终经过同一条计算链。
 */
@Stable
class BottomSheetPeekHeightUpdater internal constructor(
  private val bottomSheetState: BottomSheetState,
) {
  /** 当前最终有效折叠高度，单位为 px；包含配置允许时父级尚未消费的导航栏底部高度。 */
  var peekHeightPx by mutableFloatStateOf(0F)
    private set

  /** 业务传入的原始折叠高度，单位为 px，不包含导航栏。 */
  var basePeekHeightPx by mutableFloatStateOf(0F)
    private set

  /** 父级尚未消费的导航栏底部高度，单位为 px；即使业务 peekHeight 为 0 也会保留用于绘制占位。 */
  var remainingNavigationBarHeightPx by mutableFloatStateOf(0F)
    private set

  private var navigationBarBottomPx = 0
  private var includeNavigationBar = true
  private var density: Density? = null
  private var consumedInsets: WindowInsets? = null

  /** 更新组合阶段可得的业务高度与系统配置。 */
  internal fun updateConfiguration(
    basePeekHeightPx: Float,
    navigationBarBottomPx: Int,
    includeNavigationBar: Boolean,
    density: Density,
  ) {
    this.basePeekHeightPx = basePeekHeightPx
    this.navigationBarBottomPx = navigationBarBottomPx
    this.includeNavigationBar = includeNavigationBar
    this.density = density
    updatePeekHeight()
  }

  /** 更新当前节点之前已经消费的 Insets；其底部值会从导航栏总高度中扣除。 */
  internal fun updateConsumedInsets(consumedInsets: WindowInsets) {
    this.consumedInsets = consumedInsets
    updatePeekHeight()
  }

  /**
   * 计算导航栏剩余高度与最终折叠高度。
   *
   * 导航栏占位在 peekHeight 为 0 的模态 Sheet 中也需要绘制，但不能因此改变其完全隐藏的目标；
   * 所以剩余高度始终计算，仅在业务传入正数 peekHeight 时才加入最终折叠高度。
   */
  private fun updatePeekHeight() {
    val density = density ?: return
    val remainingNavigationBarPx = if (includeNavigationBar) {
      val consumedBottomPx = consumedInsets?.getBottom(density) ?: 0
      (navigationBarBottomPx - consumedBottomPx).coerceAtLeast(0)
    } else {
      0
    }
    remainingNavigationBarHeightPx = remainingNavigationBarPx.toFloat()
    val navigationBarPeekHeightPx = if (basePeekHeightPx > 0F) {
      remainingNavigationBarHeightPx
    } else {
      0F
    }
    val value = basePeekHeightPx + navigationBarPeekHeightPx
    if (peekHeightPx != value) {
      peekHeightPx = value
      bottomSheetState.onPeekHeightChanged(value)
    }
  }
}
