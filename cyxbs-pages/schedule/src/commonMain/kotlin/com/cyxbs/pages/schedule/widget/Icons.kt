package com.cyxbs.pages.schedule.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.utils.compose.dark


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
      defaultWidth = 20.dp,
      defaultHeight = 21.dp,
      viewportWidth = 20f,
      viewportHeight = 21f,
    ).apply {
      // 底部长横条（fill，圆角矩形）
      path(fill = brush) {
        moveTo(0.75f, 16.25f)
        lineTo(19.25f, 16.25f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, 0.75f)
        lineTo(20f, 17f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, 0.75f)
        lineTo(0.75f, 17.75f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, -0.75f)
        lineTo(0f, 17f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, -0.75f)
        close()
      }
      // 底部短横条（fill，铃铛底座圆角矩形）
      path(fill = brush) {
        moveTo(5.75f, 19.5f)
        lineTo(14.25f, 19.5f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, 0.75f)
        lineTo(15f, 20.25f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, 0.75f)
        lineTo(5.75f, 21f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, -0.75f)
        lineTo(5f, 20.25f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, -0.75f)
        close()
      }
      // 铃铛主体轮廓（stroke）
      path(stroke = brush, strokeLineWidth = 1.5f) {
        moveTo(3.75f, 8.75f)
        curveTo(3.75f, 5.298f, 6.548f, 2.5f, 10f, 2.5f)
        curveTo(13.452f, 2.5f, 16.25f, 5.298f, 16.25f, 8.75f)
        verticalLineTo(17f)
        horizontalLineTo(3.75f)
        verticalLineTo(8.75f)
        close()
      }
      // 顶部小提手（fill，圆角矩形）
      path(fill = brush) {
        moveTo(10f, 0f)
        lineTo(10f, 0f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, 0.75f)
        lineTo(10.75f, 2.25f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, 0.75f)
        lineTo(10f, 3f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, -0.75f)
        lineTo(9.25f, 0.75f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, -0.75f)
        close()
      }
      // 右上铃铛内弧（stroke，圆头端点，alpha 0.8）
      path(
        stroke = brush,
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeAlpha = 0.8f,
        fillAlpha = 0.8f
      ) {
        moveTo(14f, 9f)
        curveTo(14f, 6.791f, 12.209f, 5f, 10f, 5f)
      }
      // 右侧小圆点（fill，alpha 0.8）
      path(fill = brush, fillAlpha = 0.8f, strokeAlpha = 0.8f) {
        moveTo(14f, 11.25f)
        moveToRelative(-0.75f, 0f)
        arcToRelative(0.75f, 0.75f, 0f, true, true, 1.5f, 0f)
        arcToRelative(0.75f, 0.75f, 0f, true, true, -1.5f, 0f)
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
      defaultWidth = 18.dp,
      defaultHeight = 20.dp,
      viewportWidth = 18f,
      viewportHeight = 20f,
    ).apply {
      // 外圈循环箭头（stroke，圆头端点）
      path(stroke = brush, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
        moveTo(17f, 11f)
        curveTo(17f, 15.418f, 13.418f, 19f, 9f, 19f)
        curveTo(4.582f, 19f, 1f, 15.418f, 1f, 11f)
        curveTo(1f, 6.582f, 4.582f, 3f, 9f, 3f)
        curveTo(10.457f, 3f, 11.823f, 3.39f, 13f, 4.07f)
        moveTo(13f, 4.07f)
        lineTo(12.5f, 1.5f)
        moveTo(13f, 4.07f)
        lineTo(11f, 5.5f)
      }
      // 中间数字 1（fill，alpha 0.8）
      path(fill = brush, fillAlpha = 0.8f, strokeAlpha = 0.8f) {
        moveTo(8f, 6f)
        curveTo(7.586f, 6f, 7.25f, 6.336f, 7.25f, 6.75f)
        verticalLineTo(12.25f)
        curveTo(7.25f, 12.664f, 7.586f, 13f, 8f, 13f)
        horizontalLineTo(11.5f)
        curveTo(11.914f, 13f, 12.25f, 12.664f, 12.25f, 12.25f)
        curveTo(12.25f, 11.836f, 11.914f, 11.5f, 11.5f, 11.5f)
        horizontalLineTo(8.75f)
        verticalLineTo(6.75f)
        curveTo(8.75f, 6.336f, 8.414f, 6f, 8f, 6f)
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
      defaultWidth = 20.dp,
      defaultHeight = 20.dp,
      viewportWidth = 20f,
      viewportHeight = 20f,
    ).apply {
      // 日历主体轮廓
      path(stroke = brush, strokeLineWidth = 1.67f) {
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
      path(stroke = brush, strokeLineWidth = 1.67f, strokeLineCap = StrokeCap.Round) {
        moveTo(2.79f, 7.65f)
        horizontalLineTo(17.21f)
      }
      // 顶部装订点
      path(stroke = brush, strokeLineWidth = 1.67f, strokeLineCap = StrokeCap.Round) {
        moveTo(6.12f, 1.27f)
        verticalLineTo(4.60f)
        moveTo(13.88f, 1.27f)
        verticalLineTo(4.60f)
      }
      // 日期网格点
      path(fill = brush, fillAlpha = 0.85f, strokeAlpha = 0.85f) {
        moveTo(5.56f, 10.70f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
        moveTo(10f, 10.70f)
        moveToRelative(-0.83f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, 1.67f, 0f)
        arcToRelative(0.83f, 0.83f, 0f, true, true, -1.67f, 0f)
        moveTo(14.44f, 10.70f)
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
      defaultWidth = 20.dp,
      defaultHeight = 20.dp,
      viewportWidth = 20f,
      viewportHeight = 20f,
    ).apply {
      // 时钟外圈
      path(stroke = brush, strokeLineWidth = 1.51f) {
        moveTo(10f, 2.18f)
        curveTo(5.40f, 2.18f, 1.68f, 5.91f, 1.68f, 10.51f)
        curveTo(1.68f, 15.11f, 5.40f, 18.83f, 10f, 18.83f)
        curveTo(14.60f, 18.83f, 18.32f, 15.11f, 18.32f, 10.51f)
        curveTo(18.32f, 5.91f, 14.60f, 2.18f, 10f, 2.18f)
        close()
      }
      // 时针与分针
      path(stroke = brush, strokeLineWidth = 1.51f, strokeLineCap = StrokeCap.Round) {
        moveTo(10f, 5.97f)
        verticalLineTo(10.51f)
        lineTo(13.12f, 12.48f)
      }
    }.build()
  }
}

/**
 * 由 `drawable/todo_ic_addtodo_category.xml` 翻译而来。
 *
 * 卡片轮廓与底部横条用 #546787（category 色），其中卡片描边用 #294169，
 * 卡片填充用界面背景色 config_common_background_color（日间 #FFFFFF / 夜间 #2D2D2D）。
 *
 * 自动根据 [LocalAppDark] 切换日间/夜间颜色，结果通过 `remember` 缓存。
 *
 * @author 985892345
 * @date 2026/6/25
 */
@Composable
fun rememberIcAddtodoCategory(): ImageVector {
  val categoryColor = 0xFF546787.dark(0xFFA1ADBD)
  val cardStrokeColor = 0xFF294169.dark(0xFFA1ADBD)
  val backgroundColor = 0xFFFFFFFF.dark(0xFF2D2D2D)
  return remember(categoryColor, cardStrokeColor, backgroundColor) {
    val categoryBrush = SolidColor(categoryColor)
    val cardStrokeBrush = SolidColor(cardStrokeColor)
    val backgroundBrush = SolidColor(backgroundColor)
    ImageVector.Builder(
      name = "TodoIcAddtodoCategory",
      defaultWidth = 18.dp,
      defaultHeight = 18.dp,
      viewportWidth = 18f,
      viewportHeight = 18f,
    ).apply {
      // 后层卡片右上角折线（stroke，圆头端点）
      path(stroke = categoryBrush, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
        moveTo(17f, 13f)
        verticalLineTo(4f)
        curveTo(17f, 2.343f, 15.657f, 1f, 14f, 1f)
        horizontalLineTo(10f)
      }
      // 后层卡片顶部小短横（stroke，圆头端点）
      path(stroke = categoryBrush, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
        moveTo(7f, 1f)
        horizontalLineTo(7.5f)
      }
      // 前层卡片主体（fill 背景色 + stroke 描边）
      path(fill = backgroundBrush, stroke = cardStrokeBrush, strokeLineWidth = 1.5f) {
        moveTo(0.75f, 6f)
        curveTo(0.75f, 4.757f, 1.757f, 3.75f, 3f, 3.75f)
        horizontalLineTo(12f)
        curveTo(13.243f, 3.75f, 14.25f, 4.757f, 14.25f, 6f)
        verticalLineTo(15f)
        curveTo(14.25f, 16.243f, 13.243f, 17.25f, 12f, 17.25f)
        horizontalLineTo(3f)
        curveTo(1.757f, 17.25f, 0.75f, 16.243f, 0.75f, 15f)
        verticalLineTo(6f)
        close()
      }
      // 卡片内横条（fill，圆角矩形）
      path(fill = categoryBrush) {
        moveTo(4.75f, 13f)
        lineTo(10.25f, 13f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, 0.75f)
        lineTo(11f, 13.75f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, 0.75f)
        lineTo(4.75f, 14.5f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, -0.75f, -0.75f)
        lineTo(4f, 13.75f)
        arcToRelative(0.75f, 0.75f, 0f, false, true, 0.75f, -0.75f)
        close()
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
