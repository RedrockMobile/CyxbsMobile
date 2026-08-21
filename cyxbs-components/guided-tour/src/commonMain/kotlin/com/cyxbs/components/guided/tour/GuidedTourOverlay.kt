package com.cyxbs.components.guided.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 在任意已注册目标周围绘制镂空引导层。
 *
 * 本组件只负责锚点高亮和内容承载，不拦截目标本身的点击；业务可在 [content] 中放置提示卡片、
 * 上一步/下一步按钮，并根据当前教程步骤决定是否允许手动关闭。
 */
@Composable
fun GuidedTourOverlay(
  registry: GuidedTourTargetRegistry,
  targetId: String,
  modifier: Modifier = Modifier,
  targetPadding: Dp = 6.dp,
  targetCornerRadius: Dp = 10.dp,
  scrimColor: Color = Color.Black.copy(alpha = 0.68f),
  content: @Composable BoxScope.(targetBounds: Rect?) -> Unit = {},
) {
  val density = LocalDensity.current
  val bounds = registry[targetId]
  val paddingPx = with(density) { targetPadding.toPx() }
  val cornerRadiusPx = with(density) { targetCornerRadius.toPx() }
  val highlightedBounds = bounds?.let { target ->
    Rect(
      left = target.left - paddingPx,
      top = target.top - paddingPx,
      right = target.right + paddingPx,
      bottom = target.bottom + paddingPx,
    )
  }

  Box(modifier = modifier) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
      drawRect(scrimColor)
      highlightedBounds?.let { target ->
        drawRoundRect(
          color = Color.Transparent,
          topLeft = Offset(target.left, target.top),
          size = Size(target.width, target.height),
          cornerRadius = CornerRadius(cornerRadiusPx),
          blendMode = BlendMode.Clear,
        )
      }
    }
    content(highlightedBounds)
  }
}
