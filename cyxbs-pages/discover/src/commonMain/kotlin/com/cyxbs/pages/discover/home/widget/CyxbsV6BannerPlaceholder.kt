package com.cyxbs.pages.discover.home.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 「掌上重邮 v6」Banner 兜底图，纯 Compose 绘制，替代 discover_ic_cyxbsv6.webp。
 *
 * 由蓝紫渐变背景 + 文案 + 简化手机插画 + 星点 / 虚线 / 纸飞机 / 行星轨道
 * 等装饰组成。原图右侧人物在此版中省略，仅保留手机。
 *
 * 以 [Painter] 形式提供（[rememberCyxbsV6BannerPainter]），便于直接交给 Coil `AsyncImage`
 * 当 placeholder / error，也可直接用 `Image(painter = ..., contentScale = FillBounds)` 铺到布局里。
 */
@Preview
@Composable
fun CyxbsV6BannerPlaceholderPreview() {
  Image(
    painter = rememberCyxbsV6BannerPainter(),
    contentDescription = null,
    modifier = Modifier.aspectRatio(2.56f),
    contentScale = ContentScale.FillBounds,
  )
}

/**
 * 记忆一个 [CyxbsV6BannerPainter]，可直接用作 Coil `AsyncImage` 的 placeholder / error。
 *
 * 由于 [TextMeasurer] 依赖 `LocalDensity` / `LocalFontFamilyResolver`，必须在 Composition
 * 中创建并带入 Painter；其余尺寸单位（dp / sp）都在 `DrawScope.onDraw()` 里转换，
 * 因为 `DrawScope` 本身就实现了 `Density`。
 */
@Composable
fun rememberCyxbsV6BannerPainter(
  cornerRadius: Dp = 8.dp,
): Painter {
  val textMeasurer = rememberTextMeasurer()
  return remember(textMeasurer, cornerRadius) {
    CyxbsV6BannerPainter(textMeasurer = textMeasurer, cornerRadius = cornerRadius)
  }
}

/**
 * 「掌上重邮 v6」Banner 兜底图的 [Painter] 实现。
 *
 * - 圆角通过自绘 clipPath 完成，所以使用方无需再 `Modifier.clip(...)`。
 * - 文字由外部传入的 [TextMeasurer] 在 `onDraw` 里现场测量；动画帧间的测量开销
 *   可以忽略（仅两段静态文本）。
 * - [intrinsicSize] 故意保持 [Size.Unspecified]，让画面完全跟随布局尺寸，
 *   作为 `AsyncImage` 的 placeholder 时能自然铺满。
 */
class CyxbsV6BannerPainter(
  private val textMeasurer: TextMeasurer,
  private val cornerRadius: Dp = 8.dp,
) : Painter() {

  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val cornerPx = cornerRadius.toPx()
    val clipPath = Path().apply {
      addRoundRect(
        RoundRect(
          rect = Rect(Offset.Zero, size),
          cornerRadius = CornerRadius(cornerPx, cornerPx),
        ),
      )
    }

    clipPath(clipPath) {
      drawRect(brush = backgroundGradient)
      drawDecorations()
      drawTexts(w, h)
      drawPhoneIllustration(w, h)
    }
  }

  private val backgroundGradient: Brush = Brush.linearGradient(
    colors = listOf(
      Color(0xFF3A34D2),
      Color(0xFF3847D6),
      Color(0xFF375DDC),
    ),
  )

  private fun DrawScope.drawTexts(w: Float, h: Float) {
    val titleStyle = TextStyle(
      color = Color(0xFF6FD5FF),
      fontSize = 26.sp,
      fontWeight = FontWeight.Bold,
    )
    val subStyle = TextStyle(
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
    )
    val title = textMeasurer.measure(AnnotatedString("掌上重邮v6"), titleStyle)
    val sub = textMeasurer.measure(AnnotatedString("完美整合校园生活的APP"), subStyle)

    val gap = 6.dp.toPx()
    val totalTextH = title.size.height + gap + sub.size.height
    val textTop = (h - totalTextH) / 2f
    val textLeft = 24.dp.toPx()

    drawText(
      textLayoutResult = title,
      topLeft = Offset(textLeft, textTop),
    )
    drawText(
      textLayoutResult = sub,
      topLeft = Offset(textLeft, textTop + title.size.height + gap),
    )
  }

  private fun DrawScope.drawPhoneIllustration(w: Float, h: Float) {
    // 还原原 Canvas：fillMaxHeight(0.86f) + aspectRatio(0.50f)，BiasAlignment(0.55f, 0f)
    val phoneH = h * 0.86f
    val phoneW = phoneH * 0.50f
    val phoneX = (w - phoneW) * ((1f + 0.55f) / 2f) // BiasAlignment 横向公式
    val phoneY = (h - phoneH) / 2f
    translate(left = phoneX, top = phoneY) {
      drawPhone(Size(phoneW, phoneH))
    }
  }
}

/* ------------------------------ 背景装饰 ------------------------------ */

private fun DrawScope.drawDecorations() {
  val w = size.width
  val h = size.height

  // 顶部柔光斑（两个 radial gradient 模拟原图紫粉光晕）
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        Color(0xFF7A8AE8).copy(alpha = 0.55f),
        Color.Transparent,
      ),
    ),
    radius = h * 0.50f,
    center = Offset(w * 0.25f, -h * 0.15f),
  )
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        Color(0xFF8E9DEC).copy(alpha = 0.40f),
        Color.Transparent,
      ),
    ),
    radius = h * 0.60f,
    center = Offset(w * 0.55f, -h * 0.20f),
  )

  // 散落圆点
  val dot = Color.White.copy(alpha = 0.55f)
  val pinkDot = Color(0xFFFF8DB0).copy(alpha = 0.80f)
  drawCircle(color = dot, radius = 2.dp.toPx(), center = Offset(w * 0.40f, h * 0.20f))
  drawCircle(color = dot, radius = 1.5.dp.toPx(), center = Offset(w * 0.36f, h * 0.55f))
  drawCircle(color = dot, radius = 1.5.dp.toPx(), center = Offset(w * 0.48f, h * 0.80f))
  drawCircle(color = pinkDot, radius = 2.dp.toPx(), center = Offset(w * 0.93f, h * 0.86f))
  drawCircle(color = pinkDot, radius = 1.5.dp.toPx(), center = Offset(w * 0.97f, h * 0.55f))

  // 4 角星
  drawStar(
    center = Offset(w * 0.50f, h * 0.13f),
    radius = 7.dp.toPx(),
    color = Color(0xFFFF7AA8),
  )
  drawStar(
    center = Offset(w * 0.48f, h * 0.42f),
    radius = 4.dp.toPx(),
    color = Color(0xFFFF7AA8).copy(alpha = 0.85f),
  )

  // 手机大概的中心（与 Canvas 的 BiasAlignment 0.55f 对应：约 73% 宽度）
  val phoneCx = w * 0.73f
  val phoneCy = h * 0.50f

  // 轨道环（白色虚线，扁平横向，土星环视角，右上左下倾斜）
  rotate(degrees = -25f, pivot = Offset(phoneCx, phoneCy)) {
    drawOval(
      color = Color.White.copy(alpha = 0.55f),
      topLeft = Offset(phoneCx - w * 0.22f, phoneCy - h * 0.10f),
      size = Size(w * 0.44f, h * 0.20f),
      style = Stroke(
        width = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
      ),
    )
  }

  drawCircle(color = dot, radius = 4.dp.toPx(), center = Offset(w * 0.58f, h * 0.60f))
  drawCircle(color = pinkDot, radius = 3.dp.toPx(), center = Offset(w * 0.88f, h * 0.40f))
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
  val path = Path()
  val points = 4
  for (i in 0 until points * 2) {
    val angle = (PI / points) * i - PI / 2
    val r = if (i % 2 == 0) radius else radius * 0.35f
    val x = (center.x + r * cos(angle)).toFloat()
    val y = (center.y + r * sin(angle)).toFloat()
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  path.close()
  drawPath(path = path, color = color)
}

/* ------------------------------ 手机插画 ------------------------------ */

/**
 * 屏幕内容仿照 `DiscoverPage` 自顶向下：
 * 「问候语 + 标题 + 右上 icon」→ Banner → 教务在线行 → 功能按钮行 → Feed 容器；
 * 屏幕底部再叠 `HomePageContent` 的课表 BottomSheet peek 头与底部导航栏。
 * 全部用纯几何形状抽象表示，保持原 placeholder 的简约风。
 */
private fun DrawScope.drawPhone(phoneSize: Size) {
  val w = phoneSize.width
  val h = phoneSize.height
  val cornerR = w * 0.18f
  val frame = Color(0xFFA3B0F2)
  val screenBg = Color(0xFF4F62C8)
  // Feed / 课表头 / 底导 共用同一种容器底色，仅靠间距区分
  val containerBg = Color(0xFFB8C5F0).copy(alpha = 0.25f)
  val accent = Color(0xFF7BB3FF)
  val accentPink = Color(0xFFFFA0BE)
  val textLight = Color.White.copy(alpha = 0.6f)
  val textDim = Color.White.copy(alpha = 0.5f)
  val textDark = Color(0xFF2A3A75)
  val textDarkDim = Color(0xFF6C7CB8)

  // 外壳描边
  drawRoundRect(
    color = frame,
    topLeft = Offset(0f, 0f),
    size = Size(w, h),
    cornerRadius = CornerRadius(cornerR),
    style = Stroke(width = 1.dp.toPx()),
  )
  // 屏幕底色：去掉听筒和 Home 指示后，四周边框收窄让内容贴合外壳
  val padH = w * 0.01f
  val padTop = w * 0.02f
  val padBottom = w * 0.01f
  val sX = padH
  val sY = padTop
  val sW = w - padH * 2
  val sH = h - padTop - padBottom
  val screenCorner = cornerR * 0.9f
  val screenPath = Path().apply {
    addRoundRect(
      RoundRect(
        rect = Rect(Offset(sX, sY), Size(sW, sH)),
        cornerRadius = CornerRadius(screenCorner),
      ),
    )
  }
  drawPath(path = screenPath, color = screenBg)

  // 屏幕内所有内容 clip 在屏幕圆角内，避免课表头/底导超出
  clipPath(screenPath) {
    val cx = sX + sW * 0.025f
    val cw = sW * 0.95f

    // —— 课表头与底部导航合并卡的总高度，课表头部分压低
    val bottomBarH = sH * 0.16f
    val sheetSectionH = sH * 0.062f

    var y = sY + sH * 0.04f

    // 1. Header：问候语 + 「发现」 + 右上两个 icon（消息 / 签到）
    val headerH = sH * 0.10f
    drawRoundRect(
      color = textDim,
      topLeft = Offset(cx, y + sH * 0.010f),
      size = Size(cw * 0.30f, sH * 0.014f),
      cornerRadius = CornerRadius(sH * 0.007f),
    )
    drawRoundRect(
      color = textLight,
      topLeft = Offset(cx, y + sH * 0.034f),
      size = Size(cw * 0.25f, sH * 0.042f),
      cornerRadius = CornerRadius(sH * 0.012f),
    )
    val iconCy = y + sH * 0.058f
    drawCircle(color = textLight, radius = sH * 0.018f,
      center = Offset(cx + cw - sH * 0.018f, iconCy))
    drawCircle(color = textLight, radius = sH * 0.018f,
      center = Offset(cx + cw - sH * 0.075f, iconCy))
    y += headerH

    // 2. Banner（圆角矩形 + 内部文字线 + 装饰圆）
    y += sH * 0.010f
    val bannerH = sH * 0.135f
    drawRoundRect(
      color = accent.copy(alpha = 0.6f),
      topLeft = Offset(cx, y),
      size = Size(cw, bannerH),
      cornerRadius = CornerRadius(sH * 0.014f),
    )
    drawRoundRect(
      color = Color.White.copy(alpha = 0.4f),
      topLeft = Offset(cx + cw * 0.08f, y + bannerH * 0.28f),
      size = Size(cw * 0.42f, bannerH * 0.14f),
      cornerRadius = CornerRadius(sH * 0.005f),
    )
    drawRoundRect(
      color = Color.White.copy(alpha = 0.3f),
      topLeft = Offset(cx + cw * 0.08f, y + bannerH * 0.52f),
      size = Size(cw * 0.30f, bannerH * 0.10f),
      cornerRadius = CornerRadius(sH * 0.005f),
    )
    drawCircle(
      color = Color.White.copy(alpha = 0.4f),
      radius = bannerH * 0.20f,
      center = Offset(cx + cw * 0.85f, y + bannerH * 0.50f),
    )
    y += bannerH

    // 3. 教务在线行：左下/右上圆角的小标签 + 跑马灯线
    y += sH * 0.022f
    val jwH = sH * 0.034f
    val jwLabelW = cw * 0.22f
    val jwLabelR = jwH * 0.45f
    drawPath(
      path = Path().apply {
        addRoundRect(
          RoundRect(
            rect = Rect(Offset(cx, y), Size(jwLabelW, jwH)),
            topLeft = CornerRadius.Zero,
            topRight = CornerRadius(jwLabelR),
            bottomRight = CornerRadius.Zero,
            bottomLeft = CornerRadius(jwLabelR),
          ),
        )
      },
      color = accent.copy(alpha = 0.85f),
    )
    drawRoundRect(
      color = textDim,
      topLeft = Offset(cx + jwLabelW + sW * 0.025f, y + jwH * 0.34f),
      size = Size(cw - jwLabelW - sW * 0.045f, jwH * 0.32f),
      cornerRadius = CornerRadius(jwH * 0.16f),
    )
    y += jwH

    // 4. 功能按钮：5 个小圆 + 名称短线 + 底部指示器
    y += sH * 0.024f
    val funcH = sH * 0.10f
    val funcCount = 4
    val funcSpacing = cw / funcCount
    val funcR = funcH * 0.26f
    for (i in 0 until funcCount) {
      val ccx = cx + funcSpacing * (i + 0.5f)
      val ccy = y + funcH * 0.28f
      drawCircle(
        color = if (i % 2 == 0) accent.copy(alpha = 0.6f) else accentPink.copy(alpha = 0.6f),
        radius = funcR,
        center = Offset(ccx, ccy),
      )
      drawRoundRect(
        color = textDim,
        topLeft = Offset(ccx - funcR * 0.95f, ccy + funcR + sH * 0.012f),
        size = Size(funcR * 1.9f, sH * 0.011f),
        cornerRadius = CornerRadius(sH * 0.0055f),
      )
    }
    drawRoundRect(
      color = textDim,
      topLeft = Offset(cx + cw * 0.45f, y + funcH * 0.92f),
      size = Size(cw * 0.10f, sH * 0.007f),
      cornerRadius = CornerRadius(sH * 0.0035f),
    )
    y += funcH

    // 5. Feed 区：4 角圆角浮卡，留出底部 bar 的空间
    val feedTop = y + sH * 0.025f
    // 底部 bar 直接贴到屏幕底，下两角由屏幕 clipPath 自然剪出，跟手机外壳贴合
    val feedBottom = sY + sH - bottomBarH
    val feedCorner = CornerRadius(sH * 0.022f)
    val bottomCornerRadius = CornerRadius(screenCorner)
    drawPath(
      path = Path().apply {
        addRoundRect(
          RoundRect(
            rect = Rect(Offset(sX, feedTop), Size(sW, feedBottom - feedTop + bottomBarH)),
            topLeft = feedCorner,
            topRight = feedCorner,
            bottomRight = bottomCornerRadius,
            bottomLeft = bottomCornerRadius,
          ),
        )
      },
      color = containerBg,
    )
    // Feed 内部 2 段：每段一个标题短线 + 一张内容卡
    val feedItemH = (feedBottom - feedTop - sH * 0.024f) * 0.5f
    for (i in 0 until 2) {
      val ty = feedTop + sH * 0.012f + i * (feedItemH + sH * 0.012f)
      drawRoundRect(
        color = textLight.copy(alpha = 0.50f),
        topLeft = Offset(cx, ty + sH * 0.008f),
        size = Size(cw * 0.28f, sH * 0.013f),
        cornerRadius = CornerRadius(sH * 0.0065f),
      )
      drawRoundRect(
        color = if (i == 0) accent.copy(alpha = 0.40f) else accentPink.copy(alpha = 0.40f),
        topLeft = Offset(cx, ty + sH * 0.028f),
        size = Size(cw, feedItemH - sH * 0.032f),
        cornerRadius = CornerRadius(sH * 0.014f),
      )
    }

    // 6. 课表 BottomSheet 头 + 底部导航：合并为一张 4 角圆角浮卡，content 宽度内
    val barTop = feedBottom
    val barCornerR = CornerRadius(sH * 0.022f)
    drawPath(
      path = Path().apply {
        addRoundRect(
          RoundRect(
            rect = Rect(Offset(sX, barTop), Size(sW, bottomBarH)),
            topLeft = barCornerR,
            topRight = barCornerR,
            bottomRight = bottomCornerRadius,
            bottomLeft = bottomCornerRadius,
          ),
        )
      },
      color = containerBg,
    )
    // 6a. 课表头：drag handle + 单行（课程名 + 时间 + 地点）
    drawRoundRect(
      color = textDark,
      topLeft = Offset(cx + cw * 0.43f, barTop + sH * 0.015f),
      size = Size(cw * 0.14f, sH * 0.005f),
      cornerRadius = CornerRadius(sH * 0.0025f),
    )
    val sheetRowY = barTop + sH * 0.04f
    // 左：课程名
    drawRoundRect(
      color = textDark.copy(alpha = 0.6f),
      topLeft = Offset(cx, sheetRowY),
      size = Size(cw * 0.22f, sH * 0.024f),
      cornerRadius = CornerRadius(sH * 0.010f),
    )
    // 中：时间块
    drawRoundRect(
      color = textDark.copy(alpha = 0.5f),
      topLeft = Offset(cx + cw * 0.40f, sheetRowY + sH * 0.005f),
      size = Size(cw * 0.20f, sH * 0.014f),
      cornerRadius = CornerRadius(sH * 0.007f),
    )
    // 右：地点块
    drawRoundRect(
      color = textDark.copy(alpha = 0.5f),
      topLeft = Offset(cx + cw * 0.85f, sheetRowY + sH * 0.005f),
      size = Size(cw * 0.15f, sH * 0.014f),
      cornerRadius = CornerRadius(sH * 0.007f),
    )

    // 6b. 底部导航：紧贴课表头下方，3 个 tab，第一个选中态深一些
    val navTop = barTop + sheetSectionH
    val navH = bottomBarH - sheetSectionH
    val navCount = 3
    val navSpacing = cw / navCount
    for (i in 0 until navCount) {
      val ccx = cx + navSpacing * (i + 0.5f)
      val ccy = navTop + navH * 0.42f
      val isSel = i == 0
      val color = if (isSel) textDark.copy(alpha = 0.6f) else textDarkDim
      // icon: 圆角方块
      drawRoundRect(
        color = color,
        topLeft = Offset(ccx - cw * 0.045f, ccy - sH * 0.005f),
        size = Size(cw * 0.09f, sH * 0.036f),
        cornerRadius = CornerRadius(sH * 0.010f),
      )
      // 名称短线
      drawRoundRect(
        color = color.copy(alpha = 0.7f),
        topLeft = Offset(ccx - cw * 0.035f, ccy + sH * 0.04f),
        size = Size(cw * 0.07f, sH * 0.008f),
        cornerRadius = CornerRadius(sH * 0.004f),
      )
    }
  }
}
