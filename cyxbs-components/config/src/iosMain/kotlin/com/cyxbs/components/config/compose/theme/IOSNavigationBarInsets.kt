@file:OptIn(InternalComposeUiApi::class)

package com.cyxbs.components.config.compose.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.platform.PlatformWindowInsetsProviderNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 从 Insets 数据源统一限制 iOS CMP 页面看到的底部导航栏安全区高度。
 *
 * [LocalPlatformWindowInsets] 负责直接读取 `WindowInsets.navigationBars` 和显式传入
 * `windowInsetsPadding(WindowInsets.navigationBars)` 的场景；根布局上的旧节点兼容层负责
 * Compose 1.11/1.12 中仍通过独立节点树读取 Insets 的 `navigationBarsPadding()` 等快捷方法。
 *
 * 这里只限制导航栏与系统栏的底部值，手势安全区仍保留系统原值；需要完整避让
 * Home Indicator 手势区域的组件仍可显式读取并消费该 Insets。使用上限而不是固定高度，
 * 原始安全区不足 [maxBottom] 或为 0 时不会额外制造空白。
 *
 * [LocalPlatformWindowInsets] 属于 Compose Multiplatform 内部 API，升级 Compose 后需要
 * 复查该入口是否仍然存在。
 *
 * @param maxBottom iOS 页面可读取到的导航栏和系统栏底部 Insets 上限。
 * @param content 应用该 Insets 数据源的 Compose 子树。
 */
@Composable
fun IOSNavigationBarInsets(
  maxBottom: Dp = 12.dp,
  content: @Composable () -> Unit,
) {
  val source = LocalPlatformWindowInsets.current
  val maxBottomPx = with(LocalDensity.current) { maxBottom.roundToPx() }
  val adjustedInsets = remember(source, maxBottomPx) {
    source.limitNavigationBarBottom(maxBottomPx)
  }

  CompositionLocalProvider(LocalPlatformWindowInsets provides adjustedInsets) {
    Box(
      modifier = Modifier.legacyNavigationBarInsets(maxBottomPx),
      content = { content() },
    )
  }
}

/**
 * 将导航栏和系统栏底部 Insets 限制在 [maxBottomPx] 内。
 *
 * 返回对象保留源 Insets 的动态 getter，因此旋转、窗口变化或键盘状态更新时仍能读取最新值；
 * 未覆盖的状态栏、显示缺口和手势区域完全沿用系统数据。
 */
private fun PlatformWindowInsets.limitNavigationBarBottom(
  maxBottomPx: Int,
): PlatformWindowInsets {
  val source = this
  val sourceNavigationBars = navigationBars
  val sourceSystemBars = systemBars
  return object : PlatformWindowInsets by source {
    override val navigationBars: PlatformInsets = PlatformInsets(
      getLeft = { sourceNavigationBars.left },
      getTop = { sourceNavigationBars.top },
      getRight = { sourceNavigationBars.right },
      getBottom = { minOf(sourceNavigationBars.bottom, maxBottomPx) },
    )

    override val systemBars: PlatformInsets = PlatformInsets(
      getLeft = { sourceSystemBars.left },
      getTop = { sourceSystemBars.top },
      getRight = { sourceSystemBars.right },
      getBottom = { minOf(sourceSystemBars.bottom, maxBottomPx) },
    )
  }
}

/**
 * Compose 1.11/1.12 的临时兼容入口，让快捷 Insets Modifier 读取受限后的数据。
 *
 * CMP-9379 在 Compose 1.13.0-alpha01 删除了 [PlatformWindowInsetsProviderNode]。升级后该符号
 * 会产生编译错误，届时必须删除本方法、[LegacyNavigationBarInsetsElement] 和
 * [LegacyNavigationBarInsetsNode]；保留 [IOSNavigationBarInsets] 中的 CompositionLocal
 * 覆盖即可，新的快捷 Modifier 会直接读取 [LocalPlatformWindowInsets]。
 */
private fun Modifier.legacyNavigationBarInsets(maxBottomPx: Int): Modifier {
  return then(LegacyNavigationBarInsetsElement(maxBottomPx))
}

/**
 * 把底部 Insets 上限传递给旧版 Modifier 节点，并在上限发生变化时刷新后代。
 */
private data class LegacyNavigationBarInsetsElement(
  val maxBottomPx: Int,
) : ModifierNodeElement<LegacyNavigationBarInsetsNode>() {

  override fun create(): LegacyNavigationBarInsetsNode {
    return LegacyNavigationBarInsetsNode(maxBottomPx)
  }

  override fun update(node: LegacyNavigationBarInsetsNode) {
    node.updateMaxBottom(maxBottomPx)
  }
}

/**
 * Compose 1.11/1.12 专用的旧 Insets 节点桥接。
 *
 * 节点以祖先提供的原始 Insets 为基础应用同一套底部限制，确保
 * `navigationBarsPadding()`、`systemBarsPadding()` 与 CompositionLocal 读取结果一致。
 * 该父类在 Compose 1.13 起被删除，以编译失败强制提醒调用方移除这层兼容代码。
 */
private class LegacyNavigationBarInsetsNode(
  private var maxBottomPx: Int,
) : PlatformWindowInsetsProviderNode() {

  override fun calculatePlatformInsets(
    ancestorWindowInsets: PlatformWindowInsets,
  ): PlatformWindowInsets {
    return ancestorWindowInsets.limitNavigationBarBottom(maxBottomPx)
  }

  /**
   * 更新底部上限，并让旧节点树重新计算和分发 Insets。
   */
  fun updateMaxBottom(maxBottomPx: Int) {
    if (this.maxBottomPx == maxBottomPx) return
    this.maxBottomPx = maxBottomPx
    windowInsetsInvalidated()
  }
}
