package com.cyxbs.pages.widget.widget.glance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cyxbs.pages.widget.api.CourseWidgetBackgroundPattern
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem

/**
 * 按快照的 light/dark style 绘制通用 item。
 *
 * 圆角底卡与条目内容层之间固定保留 2dp，使同色重叠卡片仍能辨认边缘；事务斜纹只绘制在内容层。
 * [modifier] 只负责卡片的外部尺寸与定位间距，点击节点位于其内部，避免 RemoteViews 宿主把
 * 外部 padding 也绘制成按压阴影。[renderSize] 是扣除调用方外边距后的卡片真实尺寸，用于生成
 * 无需缩放的固定粗细斜纹。
 * [titleTopPadding] 仅在标题置顶模式下增加标题上方留白，不会改变底部内容的下间距。
 * [clipStartEdge] / [clipEndEdge] 表示条目被时间轴边界裁剪，对应边缘使用直角提示仍有内容。
 * [containerColorOverride] 允许布局用自身画布色替换外层底卡，避免非纯白轨道出现突兀白边。
 */
@Composable
internal fun WidgetRenderItemCard(
  item: CourseWidgetRenderItem,
  modifier: GlanceModifier,
  isDark: Boolean,
  renderSize: DpSize,
  titleSizeSp: Int = 9,
  contentSizeSp: Int = 8,
  maxTitleLines: Int = 3,
  maxContentLines: Int = 2,
  showContent: Boolean = true,
  inlineContent: Boolean = false,
  topBottomText: Boolean = false,
  textGap: Dp = 0.dp,
  titleTopPadding: Dp = 0.dp,
  contentPaddingHorizontal: Dp = 2.dp,
  contentPaddingVertical: Dp = 3.dp,
  clipStartEdge: Boolean = false,
  clipEndEdge: Boolean = false,
  containerColorOverride: ColorProvider? = null,
  coverTipColor: ColorProvider? = null,
) {
  val style = if (isDark) item.darkStyle else item.lightStyle
  val foreground = widgetColorProvider(Color(style.contentArgb))
  val cardModifier = GlanceModifier.fillMaxSize().let { base ->
      if (item.action.itemId == null) base else base.clickable(openCourseWidgetItemAction(item.action))
    }
  Box(
    modifier = modifier,
  ) {
    Box(
      modifier = cardModifier,
      contentAlignment = Alignment.Center,
    ) {
      WidgetRenderItemBackground(
        item = item,
        isDark = isDark,
        modifier = GlanceModifier.fillMaxSize(),
        renderSize = renderSize,
        containerColorOverride = containerColorOverride,
        clipStartEdge = clipStartEdge,
        clipEndEdge = clipEndEdge,
      )
      Column(
        modifier = GlanceModifier.fillMaxSize().padding(
          // 背景内容层已相对卡片外沿内缩 2dp，文字还需在内容层内部保留调用方指定的留白。
          horizontal = contentPaddingHorizontal + WidgetItemContainerGap,
          vertical = contentPaddingVertical + WidgetItemContainerGap,
        ),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = if (topBottomText) Alignment.Vertical.Top else Alignment.Vertical.CenterVertically,
      ) {
        val detailText = item.content
        // 矮横条的主体合并成一行，避免内容在有限高度中被 RemoteViews 裁剪。
        val titleText = if (inlineContent) {
          joinWidgetText(item.title, item.content)
        } else {
          item.title
        }
        if (topBottomText && !inlineContent) {
          // 与课表 Item 一致：标题贴近顶部，描述贴近底部，剩余高度全部留在两者之间。
          if (titleTopPadding > 0.dp) {
            Spacer(GlanceModifier.height(titleTopPadding).fillMaxWidth())
          }
          Text(
            text = titleText,
            style = TextStyle(color = foreground, fontSize = titleSizeSp.sp, textAlign = TextAlign.Center),
            maxLines = maxTitleLines,
          )
          if (showContent && detailText.isNotBlank()) {
            Spacer(GlanceModifier.defaultWeight().fillMaxWidth())
            if (textGap > 0.dp) Spacer(GlanceModifier.height(textGap).fillMaxWidth())
            Text(
              text = detailText,
              style = TextStyle(color = foreground, fontSize = contentSizeSp.sp, textAlign = TextAlign.Center),
              maxLines = maxContentLines,
            )
          }
        } else {
          // 外层 Column 已按整个文字区域垂直居中；这里不能再套 defaultWeight 容器，
          // 否则部分 RemoteViews 宿主会把单行标题按权重区域顶部放置。
          Text(
            text = titleText,
            style = TextStyle(color = foreground, fontSize = titleSizeSp.sp, textAlign = TextAlign.Center),
            maxLines = maxTitleLines,
          )
          if (!inlineContent && showContent && detailText.isNotBlank()) {
            Text(
              text = detailText,
              style = TextStyle(color = foreground, fontSize = contentSizeSp.sp, textAlign = TextAlign.Center),
              maxLines = maxContentLines,
            )
          }
        }
      }
      if (coverTipColor != null) {
        // tips 是独立右上角覆盖层，不进入标题/内容的 Column，避免 RemoteViews 宿主把标题向下挤。
        Box(
          modifier = GlanceModifier.fillMaxSize().padding(top = 3.dp, end = 4.dp),
          contentAlignment = Alignment.TopEnd,
        ) {
          Box(
            modifier = GlanceModifier.width(6.dp).height(2.dp)
              .background(coverTipColor).cornerRadius(1.dp),
          ) {}
        }
      }
    }
  }
}

/**
 * 绘制课表 Item 的两层背景：外层圆角底卡，内缩 2dp 后绘制课程色或事务斜纹内容层。
 *
 * 该组件同时用于正文卡片和溢出叠卡，避免两种状态的颜色、圆角与边缘间距不一致。
 * [renderSize] 必须由调用方提供，用实际尺寸和快照颜色生成斜纹位图，避免静态图缩放或丢失分组颜色。
 * [containerColorOverride] 仅覆盖外层和事务斜纹透明间隔，课程内容背景仍使用快照样式。
 */
@Composable
internal fun WidgetRenderItemBackground(
  item: CourseWidgetRenderItem,
  isDark: Boolean,
  modifier: GlanceModifier,
  renderSize: DpSize,
  containerColorOverride: ColorProvider? = null,
  clipStartEdge: Boolean = false,
  clipEndEdge: Boolean = false,
) {
  val style = if (isDark) item.darkStyle else item.lightStyle
  // 兼容升级前没有 containerArgb 的快照；新快照始终由课表侧下发该颜色。
  val containerArgb = style.containerArgb ?: if (isDark) 0xFF2D2D2DL else 0xFFFFFFFFL
  // 不同 Widget 的画布底色可以不同；覆盖色只替换外层与斜纹透明间隔，不修改课程内容色。
  val container = containerColorOverride ?: widgetColorProvider(Color(containerArgb))
  val contentBackground = if (item.backgroundPattern == CourseWidgetBackgroundPattern.DIAGONAL_STRIPE) {
    container
  } else {
    widgetColorProvider(Color(style.backgroundArgb))
  }
  val stripeImageProvider = if (item.backgroundPattern == CourseWidgetBackgroundPattern.DIAGONAL_STRIPE) {
    val stripeColor = Color(style.stripeArgb ?: style.contentArgb)
    val density = LocalContext.current.resources.displayMetrics.density
    // 纹理按内层真实尺寸生成，宿主无需缩放图片，因此任意卡片高度下角度、线宽和间距都固定。
    remember(renderSize, density, stripeColor) {
      ImageProvider(
        createDiagonalStripeBitmap(
          widthDp = (renderSize.width.value - WidgetItemContainerGap.value * 2).coerceAtLeast(1f),
          heightDp = (renderSize.height.value - WidgetItemContainerGap.value * 2).coerceAtLeast(1f),
          density = density,
          color = stripeColor,
        ),
      )
    }
  } else {
    null
  }
  if (!clipStartEdge && !clipEndEdge) {
    Box(
      // Glance 会把同一节点的 padding 与 background 转成一个 RemoteViews：padding 只缩进子内容，
      // 不会缩小该节点自身的背景。间距必须放在纯白外层，才能让课程色子节点真正内缩 2dp。
      modifier = modifier.background(container).cornerRadius(WidgetItemOuterCornerRadius)
        .padding(WidgetItemContainerGap),
    ) {
      WidgetRenderItemContentBackground(
        contentBackground = contentBackground,
        stripeImageProvider = stripeImageProvider,
        modifier = GlanceModifier.fillMaxSize().cornerRadius(WidgetItemInnerCornerRadius),
      )
    }
    return
  }

  val stripeEdgeImageProvider = if (stripeImageProvider != null) {
    val stripeColor = Color(style.stripeArgb ?: style.contentArgb)
    val density = LocalContext.current.resources.displayMetrics.density
    // 被裁边单独生成固定宽度纹理，避免把整张斜纹图压缩后改变线宽和角度。
    remember(renderSize.height, density, stripeColor) {
      ImageProvider(
        createDiagonalStripeBitmap(
          widthDp = WidgetItemInnerCornerRadius.value,
          heightDp = (renderSize.height.value - WidgetItemContainerGap.value * 2).coerceAtLeast(1f),
          density = density,
          color = stripeColor,
        ),
      )
    }
  } else {
    null
  }
  Box(
    modifier = modifier.background(container).cornerRadius(WidgetItemOuterCornerRadius),
  ) {
    // 先用同色矩形补齐被裁侧的圆角区域，再在内缩内容层重复处理课程色或事务斜纹。
    WidgetRenderClippedEdgeFill(
      clipStartEdge = clipStartEdge,
      clipEndEdge = clipEndEdge,
      edgeWidth = WidgetItemOuterCornerRadius,
      background = container,
    )
    Box(modifier = GlanceModifier.fillMaxSize().padding(WidgetItemContainerGap)) {
      WidgetRenderItemContentBackground(
        contentBackground = contentBackground,
        stripeImageProvider = stripeImageProvider,
        modifier = GlanceModifier.fillMaxSize().cornerRadius(WidgetItemInnerCornerRadius),
      )
      WidgetRenderClippedEdgeFill(
        clipStartEdge = clipStartEdge,
        clipEndEdge = clipEndEdge,
        edgeWidth = WidgetItemInnerCornerRadius,
        background = contentBackground,
        imageProvider = stripeEdgeImageProvider,
      )
    }
  }
}

/** 绘制课程色或事务斜纹内容层；调用方负责决定四角形状。 */
@Composable
private fun WidgetRenderItemContentBackground(
  contentBackground: ColorProvider,
  stripeImageProvider: ImageProvider?,
  modifier: GlanceModifier,
) {
  Box(modifier = modifier.background(contentBackground)) {
    if (stripeImageProvider != null) {
      // 透明间隔继续透出内容层的 topBg，而不是直接透出时间轴背景。
      Box(
        modifier = GlanceModifier.fillMaxSize().background(
          imageProvider = stripeImageProvider,
          contentScale = ContentScale.FillBounds,
        ),
      ) {}
    }
  }
}

/** 用无圆角矩形覆盖被时间轴裁掉的一侧，未裁侧继续保留原圆角。 */
@Composable
private fun WidgetRenderClippedEdgeFill(
  clipStartEdge: Boolean,
  clipEndEdge: Boolean,
  edgeWidth: Dp,
  background: ColorProvider,
  imageProvider: ImageProvider? = null,
) {
  if (clipStartEdge) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
      WidgetRenderClippedEdge(edgeWidth, background, imageProvider)
    }
  }
  if (clipEndEdge) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
      WidgetRenderClippedEdge(edgeWidth, background, imageProvider)
    }
  }
}

/** 绘制单侧直角补片；事务条目会在补片上继续绘制固定粗细斜纹。 */
@Composable
private fun WidgetRenderClippedEdge(
  edgeWidth: Dp,
  background: ColorProvider,
  imageProvider: ImageProvider?,
) {
  Box(
    modifier = GlanceModifier.width(edgeWidth).fillMaxHeight().background(background),
  ) {
    if (imageProvider != null) {
      Box(
        modifier = GlanceModifier.fillMaxSize().background(
          imageProvider = imageProvider,
          contentScale = ContentScale.FillBounds,
        ),
      ) {}
    }
  }
}

/**
 * 使用 Glance 官方的日夜颜色实现构造固定 [Color] 提供器。
 *
 * 两个模式传入相同颜色可避开受限的资源 ID 重载；必须使用官方实现，RemoteViews 转换器无法
 * 正确识别任意自定义 [ColorProvider]，否则背景与文字会回落为透明色。
 */
internal fun widgetColorProvider(day: Color, night: Color = day): ColorProvider =
  androidx.glance.color.ColorProvider(day = day, night = night)

/**
 * 生成与卡片内容层等大的透明斜纹位图。
 *
 * 线宽、线距均以 dp 转换为像素，位图与目标区域一一对应，避免 RemoteViews 缩放资源后
 * 让不同高度卡片出现不同粗细。宽高至少为 1px，兼容极窄的时间区间。
 */
private fun createDiagonalStripeBitmap(
  widthDp: Float,
  heightDp: Float,
  density: Float,
  color: Color,
): Bitmap {
  val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
  val widthPx = (widthDp * safeDensity).toInt().coerceAtLeast(1)
  val heightPx = (heightDp * safeDensity).toInt().coerceAtLeast(1)
  val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    strokeWidth = WidgetStripeWidthDp * safeDensity
    strokeCap = Paint.Cap.BUTT
    style = Paint.Style.STROKE
  }
  val pitchPx = WidgetStripePitchDp * safeDensity
  var startX = -heightPx.toFloat()
  while (startX < widthPx + heightPx) {
    canvas.drawLine(startX, heightPx.toFloat(), startX + heightPx, 0f, paint)
    startX += pitchPx
  }
  return bitmap
}

/** Compose 示例与真实 Glance 背景共用的斜纹和圆角尺寸。 */
internal const val WidgetStripeWidthDp = 2f
internal const val WidgetStripePitchDp = 8f
internal val WidgetItemOuterCornerRadius = 6.dp
internal val WidgetItemInnerCornerRadius = 4.dp

/** 条目外底卡与内部内容层的固定间距；文字可用区域计算必须复用同一数值。 */
internal val WidgetItemContainerGap = 2.dp

/** 周课表条目的文字层再留 2dp，与外底卡的 2dp 合计为上下左右各 4dp。 */
internal val WidgetWeekItemInnerPadding = 2.dp

/** 周课表条目在时间方向的外部间隔，所有天数档位保持一致。 */
internal val WidgetWeekItemVerticalPadding = 1.dp

/** 忽略空字段后用中点连接，避免缺少地点时出现多余分隔符。 */
private fun joinWidgetText(vararg values: String?): String =
  values.filterNotNull().filter(String::isNotBlank).joinToString("·")
