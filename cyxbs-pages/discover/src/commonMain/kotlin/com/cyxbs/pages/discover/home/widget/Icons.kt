package com.cyxbs.pages.discover.home.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 使用 claude code 转化的 svg 图片
 *
 * @author 985892345
 * @date 2026/6/7
 */


/**
 * 签到入口的矢量图标，由 `drawable/discover_ic_check_in.xml` +
 * `drawable-dark/discover_ic_check_in.xml` 合并为单个 ImageVector。
 *
 * 路径里写死的 `SolidColor(Color.Black)` 只是占位 —— 实际着色交给 `Icon` 的
 * `tint` 参数，这样矢量本身可以
 * `by lazy` 复用，不必跟主题绑定。22.5×22.5 viewport，`PathFillType.EvenOdd`
 * 保留挖洞效果。
 */
val CheckInImageVector: ImageVector by lazy {
  ImageVector.Builder(
    name = "DiscoverCheckIn",
    defaultWidth = 22.5.dp,
    defaultHeight = 22.5.dp,
    viewportWidth = 22.5f,
    viewportHeight = 22.5f,
  ).apply {
    path(
      fill = SolidColor(Color.Black),
      pathFillType = PathFillType.EvenOdd,
    ) {
      // 笔尖（小斜杠）
      moveTo(12.066f, 12.011f)
      arcToRelative(1.012f, 1.012f, 0f, false, true, -0.724f, 0.32f)
      arcToRelative(1.1f, 1.1f, 0f, false, true, -0.812f, -1.855f)
      lineToRelative(9.7f, -9.7f)
      arcToRelative(1.011f, 1.011f, 0f, false, true, 0.724f, -0.32f)
      arcToRelative(1.1f, 1.1f, 0f, false, true, 0.812f, 1.855f)
      close()
      // 笔尾小圆点
      moveTo(8.354f, 14.704f)
      lineToRelative(0f, 0f)
      arcToRelative(1.086f, 1.086f, 0f, true, true, 1.1f, -1.875f)
      lineToRelative(0f, 0f)
      arcToRelative(1.086f, 1.086f, 0f, false, true, -1.1f, 1.875f)
      close()
      // 外圈日历样轮廓
      moveTo(15.364f, 3.044f)
      curveTo(6.273f, -1.154f, -2.413f, 10.453f, 4.817f, 17.681f)
      arcTo(9.229f, 9.229f, 0f, false, false, 16.249f, 18.895f)
      arcToRelative(9.275f, 9.275f, 0f, false, false, 3.17f, -11.831f)
      curveToRelative(-0.593f, -1.258f, 1.28f, -2.359f, 1.876f, -1.1f)
      arcTo(11.482f, 11.482f, 0f, false, true, 17.605f, 20.595f)
      arcTo(11.41f, 11.41f, 0f, false, true, 3.282f, 19.217f)
      arcToRelative(11.409f, 11.409f, 0f, false, true, -1.2f, -14.581f)
      arcTo(11.5f, 11.5f, 0f, false, true, 16.461f, 1.169f)
      curveTo(17.729f, 1.755f, 16.625f, 3.626f, 15.364f, 3.044f)
      close()
    }
  }.build()
}


/**
 * 消息中心的矢量图标，由 `drawable/discover_ic_home_msg.xml` +
 * `drawable-dark/discover_ic_home_msg.xml` 合并为单个 ImageVector。
 *
 * 24×20 viewport，`PathFillType.EvenOdd` 保留信封外圈+内圈挖洞。
 */
val MsgImageVector: ImageVector by lazy {
  ImageVector.Builder(
    name = "DiscoverHomeMsg",
    defaultWidth = 24.dp,
    defaultHeight = 20.dp,
    viewportWidth = 24f,
    viewportHeight = 20f,
  ).apply {
    path(
      fill = SolidColor(Color.Black),
      pathFillType = PathFillType.EvenOdd,
    ) {
      // 内层圆角矩形（被外层 evenodd 挖空）
      moveTo(4f, 2f)
      horizontalLineTo(20f)
      curveTo(21.1046f, 2f, 22f, 2.89543f, 22f, 4f)
      verticalLineTo(16f)
      curveTo(22f, 17.1046f, 21.1046f, 18f, 20f, 18f)
      horizontalLineTo(4f)
      curveTo(2.89543f, 18f, 2f, 17.1046f, 2f, 16f)
      verticalLineTo(4f)
      curveTo(2f, 2.89543f, 2.89543f, 2f, 4f, 2f)
      close()
      // 外层圆角矩形
      moveTo(0f, 4f)
      curveTo(0f, 1.79086f, 1.79086f, 0f, 4f, 0f)
      horizontalLineTo(20f)
      curveTo(22.2091f, 0f, 24f, 1.79086f, 24f, 4f)
      verticalLineTo(16f)
      curveTo(24f, 18.2091f, 22.2091f, 20f, 20f, 20f)
      horizontalLineTo(4f)
      curveTo(1.79086f, 20f, 0f, 18.2091f, 0f, 16f)
      verticalLineTo(4f)
      close()
      // 信封折痕（V 形）
      moveTo(3.11757f, 4.50753f)
      curveTo(3.37319f, 4.01796f, 3.96264f, 3.85421f, 4.43415f, 4.14178f)
      lineTo(12.1028f, 8.81872f)
      lineTo(19.7683f, 4.14363f)
      curveTo(20.2398f, 3.85607f, 20.8293f, 4.01982f, 21.0849f, 4.50939f)
      curveTo(21.3405f, 4.99896f, 21.1655f, 5.62896f, 20.694f, 5.91652f)
      lineTo(12.623f, 10.8389f)
      curveTo(12.4523f, 10.943f, 12.2662f, 10.9879f, 12.0861f, 10.9797f)
      curveTo(11.916f, 10.9815f, 11.7417f, 10.9359f, 11.581f, 10.838f)
      lineTo(3.50849f, 5.91466f)
      curveTo(3.03697f, 5.6271f, 2.86196f, 4.9971f, 3.11757f, 4.50753f)
      close()
    }
  }.build()
}


/**
 * 图钉矢量图标，复用 Material Symbols `push_pin` 的 path 数据，用 Kotlin DSL 重写，
 * 不依赖 XML drawable，跨平台一致。
 *
 * 24×24 viewport，对应的 SVG path：
 * `M16,9V4h1c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1h1v5`
 * `c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2C17.34,12 16,10.66 16,9z`
 */
val PinImageVector: ImageVector by lazy {
  ImageVector.Builder(
    name = "DiscoverPin",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black)) {
      moveTo(16f, 9f)
      verticalLineTo(4f)
      horizontalLineToRelative(1f)
      curveTo(17.55f, 4f, 18f, 3.55f, 18f, 3f)
      reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
      horizontalLineTo(7f)
      curveTo(6.45f, 2f, 6f, 2.45f, 6f, 3f)
      reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
      horizontalLineToRelative(1f)
      verticalLineToRelative(5f)
      curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
      verticalLineToRelative(2f)
      horizontalLineToRelative(5.97f)
      verticalLineToRelative(7f)
      lineToRelative(1f, 1f)
      lineToRelative(1f, -1f)
      verticalLineToRelative(-7f)
      horizontalLineTo(19f)
      verticalLineToRelative(-2f)
      curveTo(17.34f, 12f, 16f, 10.66f, 16f, 9f)
      close()
    }
  }.build()
}
