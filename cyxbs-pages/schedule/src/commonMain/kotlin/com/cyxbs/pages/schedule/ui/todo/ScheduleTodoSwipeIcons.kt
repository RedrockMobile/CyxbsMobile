package com.cyxbs.pages.schedule.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** 左滑动作按钮使用设计稿给出的带透明度底色，Preview 与实际页面共用，避免两处样式再次偏离。 */
internal val ScheduleTodoPinActionBackgroundColor = 0xB23852DA.color()
internal val ScheduleTodoPinActionTintColor = 0xB23852DA.color()
internal val ScheduleTodoDeleteActionBackgroundColor = 0xB2E2554E.color()
internal val ScheduleTodoDeleteActionTintColor = 0xB2E2554E.color()
internal val ScheduleTodoRestoreActionBackgroundColor = 0xB26BC166.color()
internal val ScheduleTodoRestoreActionTintColor = 0xB249AB43.color()

/** 预览 ConfigRes 暴露的置顶、删除和恢复矢量资源，确保资源与实际清单页面一致。 */
@Preview
@Composable
private fun PreviewScheduleTodoSwipeIcons() {
  AppTheme {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      ScheduleTodoSwipeIconPreview(
        icon = ConfigRes.configIcPin(),
        backgroundColor = ScheduleTodoPinActionBackgroundColor,
        tint = ScheduleTodoPinActionTintColor,
      )
      ScheduleTodoSwipeIconPreview(
        icon = ConfigRes.configIcPin(),
        backgroundColor = ScheduleTodoPinActionBackgroundColor,
        tint = ScheduleTodoPinActionTintColor,
        showCancelMark = true,
      )
      ScheduleTodoSwipeIconPreview(
        icon = ConfigRes.configIcDelete(),
        backgroundColor = ScheduleTodoDeleteActionBackgroundColor,
        tint = ScheduleTodoDeleteActionTintColor,
      )
      ScheduleTodoSwipeIconPreview(
        icon = ConfigRes.configIcRestore(),
        backgroundColor = ScheduleTodoRestoreActionBackgroundColor,
        tint = ScheduleTodoRestoreActionTintColor,
      )
    }
  }
}

/** Preview 专用的 28dp 动作按钮容器，与清单页面的按钮尺寸和圆角保持一致。 */
@Composable
private fun ScheduleTodoSwipeIconPreview(
  icon: DrawableResource,
  backgroundColor: Color,
  tint: Color,
  showCancelMark: Boolean = false,
) {
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.size(28.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier
          .size(28.dp)
          .scheduleTodoCancelPinMark(showCancelMark, tint),
        tint = tint,
      )
    }
  }
}

/**
 * 清单页与首页 Feed 共用的 28dp 左滑动作按钮。
 *
 * [icon] 来自 ConfigRes 的设计稿矢量资源；[backgroundColor] 已包含目标透明度，不再二次覆盖 alpha。
 * [showCancelMark] 仅用于“取消置顶”，会在 pin 图标上叠加斜线。
 */
@Composable
internal fun ScheduleTodoSwipeAction(
  icon: DrawableResource,
  contentDescription: String,
  backgroundColor: Color,
  tint: Color,
  showCancelMark: Boolean = false,
  onClick: () -> Unit,
) {
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.size(28.dp).clickableNoIndicator(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
          .size(28.dp)
          .scheduleTodoCancelPinMark(showCancelMark, tint),
      )
    }
  }
}

/** 在现有 pin 资源上叠加反向斜线，供“取消置顶”动作与资源 Preview 共用。 */
internal fun Modifier.scheduleTodoCancelPinMark(show: Boolean, color: Color): Modifier =
  if (!show) this else drawWithContent {
    drawContent()
    drawLine(
      color = color,
      start = Offset(size.width * 0.28f, size.height * 0.28f),
      end = Offset(size.width * 0.72f, size.height * 0.72f),
      strokeWidth = 1.5.dp.toPx(),
    )
  }
