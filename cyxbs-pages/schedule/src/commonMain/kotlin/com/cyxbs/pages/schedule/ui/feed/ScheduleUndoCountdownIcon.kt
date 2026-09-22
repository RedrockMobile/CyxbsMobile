package com.cyxbs.pages.schedule.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 绘制清单完成撤销期的圆环倒计时图标。
 *
 * [durationMillis] 决定圆环从完整到消失的时长；动画只属于清单业务，不侵入通用 Snackbar。
 */
@Composable
internal fun ScheduleUndoCountdownIcon(
  durationMillis: Int,
  color: Color,
  modifier: Modifier = Modifier,
) {
  val progress = remember { Animatable(1f) }
  LaunchedEffect(durationMillis) {
    progress.snapTo(1f)
    progress.animateTo(
      targetValue = 0f,
      animationSpec = tween(
        durationMillis = durationMillis,
        easing = LinearEasing,
      ),
    )
  }
  Canvas(modifier = modifier) {
    drawArc(
      color = color,
      startAngle = -90f,
      sweepAngle = progress.value * 360f,
      useCenter = false,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )
  }
}
