package com.cyxbs.pages.schedule.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 清单页的时间轴切换图标。
 *
 * 左侧竖线表示时间刻度，右侧三段错位线段表示落在不同时刻和不同时长的日程，比日历图标更直接地
 * 表达“按当天时间轴展示”。实际颜色由外层 [androidx.compose.material.Icon] 的 tint 接管。
 */
@Composable
fun rememberScheduleTimelineModeIcon(): ImageVector = remember {
  val brush = SolidColor(Color.Black)
  ImageVector.Builder(
    name = "ScheduleTimelineMode",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(
      stroke = brush,
      strokeLineWidth = 1.8f,
      strokeLineCap = StrokeCap.Round,
    ) {
      moveTo(5f, 3f)
      lineTo(5f, 21f)
      moveTo(5f, 6f)
      lineTo(7f, 6f)
      moveTo(5f, 12f)
      lineTo(7f, 12f)
      moveTo(5f, 18f)
      lineTo(7f, 18f)
    }
    path(
      stroke = brush,
      strokeLineWidth = 3.6f,
      strokeLineCap = StrokeCap.Round,
    ) {
      moveTo(10f, 6f)
      lineTo(19.5f, 6f)
      moveTo(11.5f, 12f)
      lineTo(17f, 12f)
      moveTo(9.5f, 18f)
      lineTo(21f, 18f)
    }
  }.build()
}

/**
 * 清单页的列表切换图标。
 *
 * 三个圆点和三条圆头横线共同表达卡片列表；线宽、画布和时间轴图标保持一致，避免切换后出现原生图标
 * 的直角端点与视觉重量突变。
 */
@Composable
fun rememberScheduleListModeIcon(): ImageVector = remember {
  val brush = SolidColor(Color.Black)
  ImageVector.Builder(
    name = "ScheduleListMode",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(
      stroke = brush,
      strokeLineWidth = 2.6f,
      strokeLineCap = StrokeCap.Round,
    ) {
      moveTo(5f, 6f)
      lineTo(5.1f, 6f)
      moveTo(5f, 12f)
      lineTo(5.1f, 12f)
      moveTo(5f, 18f)
      lineTo(5.1f, 18f)
      moveTo(9f, 6f)
      lineTo(20f, 6f)
      moveTo(9f, 12f)
      lineTo(20f, 12f)
      moveTo(9f, 18f)
      lineTo(20f, 18f)
    }
  }.build()
}

@Preview
@Composable
private fun ScheduleViewModeIconsPreview() {
  Row {
    Image(
      imageVector = rememberScheduleTimelineModeIcon(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberScheduleListModeIcon(),
      contentDescription = null,
    )
  }
}
