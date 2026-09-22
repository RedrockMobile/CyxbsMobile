package com.cyxbs.pages.schedule.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_feed_check
import org.jetbrains.compose.resources.painterResource

/**
 * 邮子清单 feed 列表项左侧的勾选圈，是旧自定义 View
 * [com.cyxbs.pages.schedule.component.CheckLineView] 的 Compose 复刻版。
 *
 * 行为对齐旧版：
 * - 未勾选时画一个完整圆环（[uncheckedColor]），完成态颜色来自全局主题正文色。
 * - 点击后立即回调 [onClick]，随后播放约 800ms 的收拢动画（圆弧从 [startAngle] 起收拢成
 *   320° 弧 + 中心浮现对勾）。数据提交由外层两秒撤销窗口统一调度，不再与图标动画结束时机耦合。
 *
 * 标题删除线由外层 Text 根据同一个 [checked] 状态绘制，避免该图标组件与文字测量互相耦合。
 */
@Composable
fun ScheduleCheckCircle(
  checked: Boolean,
  uncheckedColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  diameter: Dp = 17.dp,
  lineWidth: Dp = 1.5.dp,
  startAngle: Float = 40f,
) {
  val checkedBaseColor = LocalAppColors.current.tvLv3
  val checkedColor = checkedBaseColor.copy(alpha = 0.65f)
  // 动画进度为 0..200：0~100 收拢圆弧，100~200 进入完成态，以保持旧 View 的节奏。
  val process = remember { Animatable(200f) }
  LaunchedEffect(checked) {
    if (checked) {
      process.snapTo(0f)
      process.animateTo(200f, tween(durationMillis = 800))
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
        painter = painterResource(Res.drawable.schedule_ic_feed_check),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // 矢量资源原本写死为浅色主题的深蓝色；运行时着色以保证深色主题下仍清晰可见。
        colorFilter = ColorFilter.tint(checkedBaseColor),
        modifier = Modifier.size(width = 12.dp, height = 9.dp),
      )
    }
  }
}
