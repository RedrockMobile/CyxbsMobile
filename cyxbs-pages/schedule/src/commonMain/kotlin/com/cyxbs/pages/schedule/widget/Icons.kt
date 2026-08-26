package com.cyxbs.pages.schedule.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.utils.compose.dark

// 信息栏图标统一在 20×20 坐标系内绘制，再以相同倍率缩放到 13dp，保证最终描边一致。
private val ScheduleInfoIconSize = 13.dp
private const val ScheduleInfoIconViewport = 20f
private const val ScheduleInfoIconStrokeWidth = 2f


@Preview
@Composable
private fun PreviewCompose() {
  Row {
    Image(
      imageVector = rememberIcPicManage(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcPicManageChange(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcAddtodoNotice(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcAddtodoRepeat(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcAddtodoCalendar(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcAddtodoTime(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcAddtodoCategory(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcAddtodoRelation(),
      contentDescription = null,
    )
    Image(
      imageVector = rememberIcDetailClassifyMore(),
      contentDescription = null,
    )
  }
}

/**
 * 由 `drawable/todo_ic_pic_manage.xml`（日间 #536177）与
 * `drawable-night/todo_ic_pic_manage.xml`（夜间 #1293AA）合并而来。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author 985892345
 * @date 2026/6/20
 */
@Composable
fun rememberIcPicManage(): ImageVector {
  val color = 0xFF536177.dark(0xFF1293AA)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcPicManage",
      defaultWidth = 12.dp,
      defaultHeight = 11.dp,
      viewportWidth = 12f,
      viewportHeight = 11f,
    ).apply {
      // 底部文件夹/托盘轮廓（stroke）
      path(stroke = brush, strokeLineWidth = 1f) {
        moveTo(8.488f, 7.808f)
        verticalLineTo(9.226f)
        curveTo(8.488f, 9.724f, 8.084f, 10.128f, 7.586f, 10.128f)
        horizontalLineTo(4.27f)
        curveTo(2.613f, 10.128f, 1.27f, 8.785f, 1.27f, 7.128f)
        verticalLineTo(3.812f)
        curveTo(1.27f, 3.314f, 1.674f, 2.91f, 2.172f, 2.91f)
        horizontalLineTo(3.59f)
      }
      // 上方卡片轮廓（stroke，圆头端点）
      path(stroke = brush, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
        moveTo(6.907f, 0.884f)
        horizontalLineTo(4.798f)
        curveTo(3.97f, 0.884f, 3.298f, 1.556f, 3.298f, 2.384f)
        verticalLineTo(6.602f)
        curveTo(3.298f, 7.431f, 3.97f, 8.102f, 4.798f, 8.102f)
        horizontalLineTo(9.016f)
        curveTo(9.845f, 8.102f, 10.516f, 7.431f, 10.516f, 6.602f)
        verticalLineTo(6.298f)
        verticalLineTo(4.493f)
      }
      // 右上角编辑笔（fill）
      path(fill = brush) {
        moveTo(9.8f, 0.554f)
        curveTo(9.995f, 0.358f, 10.312f, 0.358f, 10.507f, 0.554f)
        lineTo(10.685f, 0.731f)
        curveTo(10.88f, 0.926f, 10.88f, 1.243f, 10.685f, 1.438f)
        lineTo(7.506f, 4.617f)
        curveTo(7.19f, 4.933f, 6.805f, 5.171f, 6.381f, 5.312f)
        curveTo(6.1f, 5.406f, 5.833f, 5.138f, 5.926f, 4.857f)
        curveTo(6.068f, 4.433f, 6.306f, 4.048f, 6.622f, 3.732f)
        lineTo(9.8f, 0.554f)
        close()
      }
    }.build()
  }
}

/**
 * 由 `drawable/todo_ic_pic_manage_change.xml`（日间 #4A44E4）与
 * `drawable-night/todo_ic_pic_manage_change.xml`（夜间 #1293AA）合并而来。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author 985892345
 * @date 2026/6/20
 */
@Composable
fun rememberIcPicManageChange(): ImageVector {
  val color = 0xFF4A44E4.dark(0xFF1293AA)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcPicManageChange",
      defaultWidth = 11.dp,
      defaultHeight = 11.dp,
      viewportWidth = 11f,
      viewportHeight = 11f,
    ).apply {
      // 左侧括号轮廓（stroke，圆头端点）
      path(stroke = brush, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
        moveTo(5.5f, 1f)
        horizontalLineTo(2.5f)
        curveTo(1.672f, 1f, 1f, 1.672f, 1f, 2.5f)
        verticalLineTo(8.5f)
        curveTo(1f, 9.328f, 1.672f, 10f, 2.5f, 10f)
        horizontalLineTo(5.5f)
      }
      // 中间横条（fill，圆角矩形）
      path(fill = brush) {
        moveTo(4.5f, 5f)
        horizontalLineTo(9.5f)
        arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 5.5f)
        arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.5f, 6f)
        horizontalLineTo(4.5f)
        arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 5.5f)
        arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.5f, 5f)
        close()
      }
      // 右侧箭头（stroke，圆头端点）
      path(stroke = brush, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
        moveTo(7.828f, 2.672f)
        lineTo(9.95f, 4.793f)
        curveTo(10.34f, 5.183f, 10.34f, 5.817f, 9.95f, 6.207f)
        lineTo(7.828f, 8.328f)
      }
    }.build()
  }
}

/**
 * 由 `drawable/todo_ic_addtodo_notice.xml`（日间 #294169）翻译而来。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author 985892345
 * @date 2026/6/25
 */
@Composable
fun rememberIcAddtodoNotice(): ImageVector {
  val color = 0xFF294169.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcAddtodoNotice",
      defaultWidth = ScheduleInfoIconSize,
      defaultHeight = ScheduleInfoIconSize,
      viewportWidth = ScheduleInfoIconViewport,
      viewportHeight = ScheduleInfoIconViewport,
    ).apply {
      // 底部长横条（fill，圆角矩形）
      path(fill = brush) {
        moveTo(1.18f, 15.48f)
        lineTo(18.82f, 15.48f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, 0.71f, 0.71f)
        lineTo(19.53f, 16.19f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -0.71f, 0.71f)
        lineTo(1.18f, 16.9f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -0.71f, -0.71f)
        lineTo(0.47f, 16.19f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, 0.71f, -0.71f)
        close()
      }
      // 底部短横条（fill，铃铛底座圆角矩形）
      path(fill = brush) {
        moveTo(5.95f, 18.57f)
        lineTo(14.05f, 18.57f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, 0.71f, 0.71f)
        lineTo(14.76f, 19.28f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -0.71f, 0.71f)
        lineTo(5.95f, 19.99f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -0.71f, -0.71f)
        lineTo(5.24f, 19.28f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, 0.71f, -0.71f)
        close()
      }
      // 铃铛主体轮廓（stroke）
      path(stroke = brush, strokeLineWidth = ScheduleInfoIconStrokeWidth) {
        moveTo(4.05f, 8.34f)
        curveTo(4.05f, 5.05f, 6.71f, 2.38f, 10f, 2.38f)
        curveTo(13.29f, 2.38f, 15.95f, 5.05f, 15.95f, 8.34f)
        verticalLineTo(16.19f)
        horizontalLineTo(4.05f)
        verticalLineTo(8.34f)
        close()
      }
      // 顶部小提手（fill，圆角矩形）
      path(fill = brush) {
        moveTo(10f, 0f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, 0.71f, 0.71f)
        lineTo(10.71f, 2.14f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -0.71f, 0.71f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -0.71f, -0.71f)
        lineTo(9.29f, 0.71f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, 0.71f, -0.71f)
        close()
      }
      // 右上铃铛内弧（stroke，圆头端点，alpha 0.8）
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeAlpha = 0.8f,
        fillAlpha = 0.8f
      ) {
        moveTo(13.82f, 8.57f)
        curveTo(13.82f, 6.46f, 12.11f, 4.77f, 10f, 4.77f)
      }
      // 右侧小圆点（fill，alpha 0.8）
      path(fill = brush, fillAlpha = 0.8f, strokeAlpha = 0.8f) {
        moveTo(13.82f, 10.71f)
        moveToRelative(-0.71f, 0f)
        arcToRelative(0.71f, 0.71f, 0f, true, true, 1.42f, 0f)
        arcToRelative(0.71f, 0.71f, 0f, true, true, -1.42f, 0f)
      }
    }.build()
  }
}

/**
 * 由 `drawable/todo_ic_addtodo_repeat.xml`（日间 #294169）翻译而来。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author 985892345
 * @date 2026/6/25
 */
@Composable
fun rememberIcAddtodoRepeat(): ImageVector {
  val color = 0xFF294169.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcAddtodoRepeat",
      defaultWidth = ScheduleInfoIconSize,
      defaultHeight = ScheduleInfoIconSize,
      viewportWidth = ScheduleInfoIconViewport,
      viewportHeight = ScheduleInfoIconViewport,
    ).apply {
      // 外圈循环箭头（stroke，圆头端点）
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineCap = StrokeCap.Round,
      ) {
        moveTo(18f, 11f)
        curveTo(18f, 15.42f, 14.42f, 19f, 10f, 19f)
        curveTo(5.58f, 19f, 2f, 15.42f, 2f, 11f)
        curveTo(2f, 6.58f, 5.58f, 3f, 10f, 3f)
        curveTo(11.46f, 3f, 12.82f, 3.39f, 14f, 4.07f)
        moveTo(14f, 4.07f)
        lineTo(13.5f, 1.5f)
        moveTo(14f, 4.07f)
        lineTo(12f, 5.5f)
      }
      // 中间数字 1（fill，alpha 0.8）
      path(fill = brush, fillAlpha = 0.8f, strokeAlpha = 0.8f) {
        moveTo(9f, 6f)
        curveTo(8.59f, 6f, 8.25f, 6.34f, 8.25f, 6.75f)
        verticalLineTo(12.25f)
        curveTo(8.25f, 12.66f, 8.59f, 13f, 9f, 13f)
        horizontalLineTo(12.5f)
        curveTo(12.91f, 13f, 13.25f, 12.66f, 13.25f, 12.25f)
        curveTo(13.25f, 11.84f, 12.91f, 11.5f, 12.5f, 11.5f)
        horizontalLineTo(9.75f)
        verticalLineTo(6.75f)
        curveTo(9.75f, 6.34f, 9.41f, 6f, 9f, 6f)
        close()
      }
    }.build()
  }
}

/**
 * 日程日期入口图标：日历外框 + 顶部装订点 + 日期网格。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author Codex
 * @date 2026/7/4
 */
@Composable
fun rememberIcAddtodoCalendar(): ImageVector {
  val color = 0xFF294169.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcAddtodoCalendar",
      defaultWidth = ScheduleInfoIconSize,
      defaultHeight = ScheduleInfoIconSize,
      viewportWidth = ScheduleInfoIconViewport,
      viewportHeight = ScheduleInfoIconViewport,
    ).apply {
      // 日历主体轮廓
      path(stroke = brush, strokeLineWidth = ScheduleInfoIconStrokeWidth) {
        moveTo(1.95f, 4.87f)
        curveTo(1.95f, 3.49f, 3.07f, 2.38f, 4.45f, 2.38f)
        horizontalLineTo(15.55f)
        curveTo(16.93f, 2.38f, 18.05f, 3.49f, 18.05f, 4.87f)
        verticalLineTo(16.25f)
        curveTo(18.05f, 17.63f, 16.93f, 18.75f, 15.55f, 18.75f)
        horizontalLineTo(4.45f)
        curveTo(3.07f, 18.75f, 1.95f, 17.63f, 1.95f, 16.25f)
        verticalLineTo(4.87f)
        close()
      }
      // 顶部分隔线
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineCap = StrokeCap.Round,
      ) {
        moveTo(2.79f, 7.65f)
        horizontalLineTo(17.21f)
      }
      // 顶部装订点
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineCap = StrokeCap.Round,
      ) {
        moveTo(6.12f, 1.27f)
        verticalLineTo(4.6f)
        moveTo(13.88f, 1.27f)
        verticalLineTo(4.6f)
      }
      // 日期网格点
      path(fill = brush, fillAlpha = 0.85f, strokeAlpha = 0.85f) {
        moveTo(5.56f, 10.7f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
        moveTo(10f, 10.7f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
        moveTo(14.44f, 10.7f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
        moveTo(5.56f, 14.03f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
        moveTo(10f, 14.03f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
      }
    }.build()
  }
}

/**
 * 日程时间段入口图标：时钟。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author Codex
 * @date 2026/7/4
 */
@Composable
fun rememberIcAddtodoTime(): ImageVector {
  val color = 0xFF294169.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcAddtodoTimeRange",
      defaultWidth = ScheduleInfoIconSize,
      defaultHeight = ScheduleInfoIconSize,
      viewportWidth = ScheduleInfoIconViewport,
      viewportHeight = ScheduleInfoIconViewport,
    ).apply {
      // 时钟外圈
      path(stroke = brush, strokeLineWidth = ScheduleInfoIconStrokeWidth) {
        moveTo(10f, 2.18f)
        curveTo(5.4f, 2.18f, 1.68f, 5.91f, 1.68f, 10.51f)
        curveTo(1.68f, 15.11f, 5.4f, 18.83f, 10f, 18.83f)
        curveTo(14.6f, 18.83f, 18.32f, 15.11f, 18.32f, 10.51f)
        curveTo(18.32f, 5.91f, 14.6f, 2.18f, 10f, 2.18f)
        close()
      }
      // 时针与分针
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineCap = StrokeCap.Round,
      ) {
        moveTo(10f, 5.97f)
        verticalLineTo(10.51f)
        lineTo(13.12f, 12.48f)
      }
    }.build()
  }
}

/**
 * 分组标签图标，造型参考 🏷️：用斜向标签轮廓和圆孔与日期、时间等信息图标区分。
 *
 * 图标只绘制描边、不填充背景，避免深色模式下在信息栏中形成突兀的色块；
 * 自动根据 [LocalAppDark] 切换描边颜色，结果通过 `remember` 缓存。
 */
@Composable
fun rememberIcAddtodoCategory(): ImageVector {
  val color = 0xFF294169.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcAddtodoCategory",
      defaultWidth = ScheduleInfoIconSize,
      defaultHeight = ScheduleInfoIconSize,
      viewportWidth = ScheduleInfoIconViewport,
      viewportHeight = ScheduleInfoIconViewport,
    ).apply {
      // 标签主体保持纯描边；圆角连接让小尺寸下仍接近 emoji 标签的柔和轮廓。
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        // 顶部横线从圆角结束点起笔，避免闭合点在左侧形成“T”形出头。
        moveTo(2f, 1f)
        horizontalLineTo(9f)
        curveTo(9.5f, 1f, 9.9f, 1.2f, 10.25f, 1.55f)
        lineTo(18.4f, 9.7f)
        curveTo(19.15f, 10.45f, 19.15f, 11.55f, 18.4f, 12.3f)
        lineTo(12.3f, 18.4f)
        curveTo(11.55f, 19.15f, 10.45f, 19.15f, 9.7f, 18.4f)
        lineTo(1.55f, 10.25f)
        curveTo(1.2f, 9.9f, 1f, 9.5f, 1f, 9f)
        verticalLineTo(2f)
        curveTo(1f, 1.45f, 1.45f, 1f, 2f, 1f)
        close()
      }

      // 标签孔独立描边，缩放后不会与主体轮廓粘连。
      path(stroke = brush, strokeLineWidth = ScheduleInfoIconStrokeWidth) {
        moveTo(5.8f, 4.8f)
        arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
        arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
        close()
      }
    }.build()
  }
}

/**
 * 清单与课表关联图标，复用设计稿中蓝色方块内部的开口链节线稿。
 *
 * 与其余信息栏图标共用 20×20 坐标系和 2px 描边，避免小尺寸下出现线宽或视觉重心不一致。
 */
@Composable
fun rememberIcAddtodoRelation(): ImageVector {
  val color = 0xFF294169.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcAddtodoRelation",
      defaultWidth = ScheduleInfoIconSize,
      defaultHeight = ScheduleInfoIconSize,
      viewportWidth = ScheduleInfoIconViewport,
      viewportHeight = ScheduleInfoIconViewport,
    ).apply {
      // 仅保留设计稿方块内部的链条，不绘制其蓝色圆角背景。
      path(
        stroke = brush,
        strokeLineWidth = ScheduleInfoIconStrokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        // 左上链节。
        moveTo(11.67f, 10.83f)
        arcToRelative(4.17f, 4.17f, 0f, false, true, -6.28f, 0.45f)
        lineToRelative(-2.5f, -2.5f)
        arcToRelative(4.17f, 4.17f, 0f, false, true, 5.89f, -5.89f)
        lineToRelative(1.43f, 1.43f)

        // 右下链节。
        moveTo(8.33f, 9.17f)
        arcToRelative(4.17f, 4.17f, 0f, false, true, 6.28f, -0.45f)
        lineToRelative(2.5f, 2.5f)
        arcToRelative(4.17f, 4.17f, 0f, false, true, -5.89f, 5.89f)
        lineToRelative(-1.43f, -1.43f)
      }
    }.build()
  }
}

/**
 * 由 `drawable/todo_ic_detail_classify_more.xml`（日间 #514DEB）翻译而来，
 * 是一个描边的右箭头（›）。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author 985892345
 * @date 2026/6/25
 */
@Composable
fun rememberIcDetailClassifyMore(): ImageVector {
  val color = 0xFF514DEB.dark(0xFFA1ADBD)
  return remember(color) {
    val brush = SolidColor(color)
    ImageVector.Builder(
      name = "TodoIcDetailClassifyMore",
      defaultWidth = 7.dp,
      defaultHeight = 13.dp,
      viewportWidth = 7f,
      viewportHeight = 13f,
    ).apply {
      // 右箭头 ›（stroke，圆头端点）
      path(stroke = brush, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
        moveTo(0.565f, 0.935f)
        lineTo(5.423f, 5.793f)
        curveTo(5.813f, 6.183f, 5.813f, 6.817f, 5.423f, 7.207f)
        lineTo(0.565f, 12.065f)
      }
    }.build()
  }
}
