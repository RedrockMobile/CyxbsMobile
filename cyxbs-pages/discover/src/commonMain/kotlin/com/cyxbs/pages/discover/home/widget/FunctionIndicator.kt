package com.cyxbs.pages.discover.home.widget

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * 复刻原 [com.cyxbs.pages.discover.widget.IndicatorView] 的进度指示条：
 * - 底色为浅色胶囊，进度条为深色胶囊，进度条宽度占总宽的 [progressFraction]
 * - [progress] ∈ [0, 1]，0 表示进度条贴左，1 表示贴右
 */
@Composable
fun FunctionIndicator(
  progress: Float,
  modifier: Modifier = Modifier,
  underColor: Color = Color(0xFF97B7F0),
  progressColor: Color = Color(0xFF2923D2),
  progressFraction: Float = 0.65f,
) {
  val safeProgress = progress.coerceIn(0f, 1f)
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val radius = h
    // 底色
    drawRoundRect(
      color = underColor,
      topLeft = Offset.Zero,
      size = Size(w, h),
      cornerRadius = CornerRadius(radius, radius),
    )
    // 进度条
    val progressWidth = w * progressFraction
    val travel = w - progressWidth
    val left = travel * safeProgress
    drawRoundRect(
      color = progressColor,
      topLeft = Offset(left, 0f),
      size = Size(progressWidth, h),
      cornerRadius = CornerRadius(radius, radius),
    )
  }
}
