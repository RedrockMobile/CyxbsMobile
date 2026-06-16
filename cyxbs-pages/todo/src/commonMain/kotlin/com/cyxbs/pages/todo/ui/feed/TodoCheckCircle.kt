package com.cyxbs.pages.todo.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.utils.compose.clickableNoIndicator
import cyxbsmobile.cyxbs_pages.todo.generated.resources.Res
import cyxbsmobile.cyxbs_pages.todo.generated.resources.todo_ic_feed_check
import org.jetbrains.compose.resources.painterResource

/**
 * 邮子清单 feed 列表项左侧的勾选圈，是旧自定义 View
 * [com.cyxbs.pages.todo.component.CheckLineView] 的 Compose 复刻版。
 *
 * 行为对齐旧版：
 * - 未勾选时画一个完整圆环（[uncheckedColor]）。
 * - 点击后立即回调 [onClick]（旧版点击瞬间即把标题/时间置灰），随后播放约 800ms 的
 *   收拢动画（圆弧从 [startAngle] 起收拢成 320° 弧 + 中心浮现对勾），动画结束再回调
 *   [onAnimEnd]（对齐旧版 `setStatusWithAnime` 的 `doOnEnd`，在此触发数据层删除/更新）。
 *
 * 注：旧 View 在勾选时还会画一条横穿到标题的删除线，因 feed 勾选完成后该项随即被移除、
 * 横线仅一闪而过，这里改用「圆弧收拢 + 对勾图标」表达完成态，省去跨标题层叠布局。
 */
@Composable
fun TodoCheckCircle(
  checked: Boolean,
  uncheckedColor: Color,
  onClick: () -> Unit,
  onAnimEnd: () -> Unit,
  modifier: Modifier = Modifier,
  checkedColor: Color = Color(0xFF8997AD), // todo_inner_checked_line_color
  diameter: Dp = 17.dp,
  lineWidth: Dp = 1.5.dp,
  startAngle: Float = 40f,
) {
  // process: 0..200，对齐旧 View 的动画进度（0~100 收拢圆弧，100~200 完成态）
  val process = remember { Animatable(200f) }
  val currentOnAnimEnd by rememberUpdatedState(onAnimEnd)
  LaunchedEffect(checked) {
    if (checked) {
      process.snapTo(0f)
      process.animateTo(200f, tween(durationMillis = 800))
      currentOnAnimEnd()
    }
  }

  Box(
    modifier = modifier
      .size(diameter)
      .clickableNoIndicator(enabled = !checked, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(diameter)) {
      val sw = lineWidth.toPx()
      val r = (size.minDimension - sw) / 2f
      val sweep = if (checked) (360f - startAngle) * (process.value.coerceAtMost(100f)) / 100f else 360f
      drawArc(
        color = if (checked) checkedColor else uncheckedColor,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = sw, cap = StrokeCap.Round),
      )
    }
    if (checked) {
      Image(
        painter = painterResource(Res.drawable.todo_ic_feed_check),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(width = 12.dp, height = 9.dp),
      )
    }
  }
}
