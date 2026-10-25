package com.cyxbs.pages.widget.widget.single

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cyxbs.pages.widget.api.CourseWidgetAction
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.widget.glance.ACTION_WIDGET_FLUSH_LEGACY
import com.cyxbs.pages.widget.widget.glance.ACTION_WIDGET_REFRESH
import com.cyxbs.pages.widget.widget.glance.compactTimeLabel
import com.cyxbs.pages.widget.widget.glance.currentMinute
import com.cyxbs.pages.widget.widget.glance.dispatchRefreshToGlanceReceiver
import com.cyxbs.pages.widget.widget.glance.findCompactItem
import com.cyxbs.pages.widget.widget.glance.widgetColorProvider
import com.cyxbs.pages.widget.widget.glance.mondayBasedDay
import com.cyxbs.pages.widget.widget.glance.observeCourseWidgetSnapshot
import com.cyxbs.pages.widget.widget.glance.openCourseWidgetItemAction
import com.cyxbs.pages.widget.widget.glance.readCourseWidgetPreviewSnapshot
import com.cyxbs.pages.widget.widget.glance.resolveCurrentWeek
import com.cyxbs.pages.widget.widget.glance.weekOrEmpty
import java.util.Calendar
import kotlin.math.roundToInt

/** 最小组件内容两侧留白，与参考实现保持一致。 */
internal val SingleWidgetHorizontalPadding = 10.dp

/** 标题与地点之间的分割线高度，同时参与整体文字高度计算。 */
internal val SingleWidgetDividerHeight = 0.8.dp

/** 单课程字号档位，依次记录标题、时间和内容的 sp 值。 */
private data class SingleWidgetFontTier(
  val titleSizeSp: Float,
  val timeSizeSp: Float,
  val contentSizeSp: Float,
)

/** 从大到小尝试；后四档延续原先 14/13/13sp 开始的降级顺序。 */
private val SingleWidgetFontTiers = listOf(
  SingleWidgetFontTier(16F, 15F, 15F),
  SingleWidgetFontTier(16F, 14F, 14F),
  SingleWidgetFontTier(15F, 14F, 14F),
  SingleWidgetFontTier(15F, 13F, 13F),
  SingleWidgetFontTier(14F, 13F, 13F),
  SingleWidgetFontTier(14F, 12F, 13F),
  SingleWidgetFontTier(14F, 12F, 12F),
  SingleWidgetFontTier(13F, 12F, 12F),
)

/** 最小组件根据标题实测宽度计算出的文字布局。 */
internal data class SingleWidgetTextLayout(
  val timeSizeSp: Float,
  val titleSizeSp: Float,
  val titleMaxLines: Int,
  val contentSizeSp: Float,
)

/**
 * 透明单课程 Glance receiver。显式位于本模块，避免外部依赖移除后 picker 留下失效入口。
 */
class SingleWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = SingleGlanceWidget

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == ACTION_WIDGET_REFRESH || intent.action == ACTION_WIDGET_FLUSH_LEGACY) {
      super.onReceive(context, intent)
      dispatchRefreshToGlanceReceiver(context, javaClass)
      return
    }
    super.onReceive(context, intent)
  }
}

/** 复刻原三行白字、透明背景的 compact 小组件。 */
internal object SingleGlanceWidget : GlanceAppWidget() {
  /** 固定尺寸仍使用宿主实际分配宽度，兼容不同 Launcher 的 2×1 单元格大小。 */
  override val sizeMode = SizeMode.Exact
  override val previewSizeMode = SizeMode.Single

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      // 与完整课表组件一致，活跃会话收到主动更新后必须重新读取快照和当前时间。
      val snapshot = observeCourseWidgetSnapshot()
      val calendar = Calendar.getInstance()
      val nowMinute = currentMinute(calendar)
      val currentWeek = snapshot.resolveCurrentWeek()
      val item = findCompactItem(
        snapshot.weekOrEmpty(currentWeek),
        calendar.mondayBasedDay(),
        nowMinute,
      )
      SingleWidgetContent(
        item = item,
        nowMinute = nowMinute,
        action = item?.action,
      )
    }
  }

  /** Android 15+ widget 选择器使用同一三行布局展示真实下一节或本地示例。 */
  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val calendar = Calendar.getInstance()
    val nowMinute = currentMinute(calendar)
    val snapshot = readCourseWidgetPreviewSnapshot(calendar)
    val item = findCompactItem(
      snapshot.weekOrEmpty(snapshot.resolveCurrentWeek()),
      calendar.mondayBasedDay(),
      nowMinute,
    )
    provideContent {
      SingleWidgetContent(
        item = item,
        nowMinute = nowMinute,
        action = null,
      )
    }
  }
}

/** compact 有条目时直接打开详情 Dialog；空态不注册无效点击。 */
@Composable
private fun SingleWidgetContent(
  item: CourseWidgetRenderItem?,
  nowMinute: Int,
  action: CourseWidgetAction?,
) {
  val title = item?.title ?: "今天没有后续安排~"
  val widgetSize = LocalSize.current
  val contentWidth = (
    widgetSize.width - SingleWidgetHorizontalPadding * 2
  ).coerceAtLeast(1.dp)
  val resources = LocalContext.current.resources
  val displayMetrics = resources.displayMetrics
  // Exact 模式会按宿主尺寸分别组合；宽高、密度和字体缩放变化时必须重新测量。
  val textLayout = remember(
    title,
    contentWidth,
    widgetSize.height,
    displayMetrics.densityDpi,
    resources.configuration.fontScale,
  ) {
    resolveSingleWidgetTextLayout(
      title = title,
      maxWidth = contentWidth,
      maxHeight = widgetSize.height,
      displayMetrics = displayMetrics,
    )
  }
  val contentModifier = GlanceModifier
    .fillMaxWidth()
    .wrapContentHeight()
    .background(widgetColorProvider(Color.Transparent))
    .padding(horizontal = SingleWidgetHorizontalPadding)
    .let { base -> if (action == null) base else base.clickable(openCourseWidgetItemAction(action)) }
  Column(
    modifier = contentModifier,
    horizontalAlignment = Alignment.Horizontal.Start,
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    Text(
      text = item?.let { compactTimeLabel(it, nowMinute) } ?: "今日",
      style = whiteTextStyle(textLayout.timeSizeSp),
      maxLines = 1,
    )
    Text(
      text = title,
      style = whiteTextStyle(textLayout.titleSizeSp),
      maxLines = textLayout.titleMaxLines,
    )
    Spacer(
      GlanceModifier
        .height(SingleWidgetDividerHeight)
        .fillMaxWidth()
        .background(widgetColorProvider(Color.White)),
    )
    Text(
      text = item?.content.orEmpty(),
      style = whiteTextStyle(textLayout.contentSizeSp),
      maxLines = 1,
    )
  }
}

/** 保持原 compact 的纯白字号层级。 */
private fun whiteTextStyle(size: Float): TextStyle = TextStyle(
  color = widgetColorProvider(Color.White),
  fontSize = size.sp,
)

/**
 * 根据宿主实际宽高选择最小组件的字号和标题行数。
 *
 * 从 16/15/15sp 开始按预设档位依次缩小，标题在每一档按实际宽度决定一行或两行。
 * 所有档位仍超出高度时保留最后一档，不再继续缩小字号。
 */
internal fun resolveSingleWidgetTextLayout(
  title: String,
  maxWidth: Dp,
  maxHeight: Dp,
  displayMetrics: DisplayMetrics,
): SingleWidgetTextLayout {
  val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
  val maxWidthPx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    maxWidth.value,
    displayMetrics,
  ).toInt().coerceAtLeast(1)
  val candidates = SingleWidgetFontTiers.map { tier ->
    SingleWidgetTextLayout(
      timeSizeSp = tier.timeSizeSp,
      titleSizeSp = tier.titleSizeSp,
      titleMaxLines = if (fitsSingleWidgetTitleOnOneLine(
          title, tier.titleSizeSp, maxWidthPx, displayMetrics, paint,
        )) 1 else 2,
      contentSizeSp = tier.contentSizeSp,
    )
  }
  return constrainSingleWidgetTextLayoutHeight(
    candidates = candidates,
    maxHeight = maxHeight,
    displayMetrics = displayMetrics,
    paint = paint,
  )
}

/**
 * 按宿主高度从大到小选择第一个能完整展示的字号档位。
 *
 * 字体高度使用 TextView 默认包含 font padding 的 font metrics 计算；全部档位都不合适时返回最后一档，
 * 不再继续缩小，以免文字难以辨认。
 */
private fun constrainSingleWidgetTextLayoutHeight(
  candidates: List<SingleWidgetTextLayout>,
  maxHeight: Dp,
  displayMetrics: DisplayMetrics,
  paint: TextPaint,
): SingleWidgetTextLayout {
  val maxHeightPx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    maxHeight.value,
    displayMetrics,
  ).roundToInt().coerceAtLeast(1)
  val dividerHeightPx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    SingleWidgetDividerHeight.value,
    displayMetrics,
  ).roundToInt()
  fun lineHeightPx(sizeSp: Float): Int {
    paint.textSize = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      sizeSp,
      displayMetrics,
    )
    return paint.fontMetricsInt.let { metrics -> metrics.bottom - metrics.top }
  }

  fun fitsHeight(candidate: SingleWidgetTextLayout): Boolean {
    val totalHeightPx = lineHeightPx(candidate.timeSizeSp) +
      lineHeightPx(candidate.titleSizeSp) * candidate.titleMaxLines +
      dividerHeightPx +
      lineHeightPx(candidate.contentSizeSp)
    return totalHeightPx <= maxHeightPx
  }

  return candidates.firstOrNull(::fitsHeight) ?: candidates.last()
}

/**
 * 验证标题在指定字号下能否被 Android 单行文字布局完整容纳。
 *
 * 使用与 Glance 最终 TextView 更接近的 [StaticLayout]，避免按字符数判断中文与英文混排。
 */
private fun fitsSingleWidgetTitleOnOneLine(
  title: String,
  sizeSp: Float,
  maxWidthPx: Int,
  displayMetrics: DisplayMetrics,
  paint: TextPaint,
): Boolean {
  if ('\n' in title) return false
  paint.textSize = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    sizeSp,
    displayMetrics,
  )
  val layout = StaticLayout.Builder
    .obtain(title, 0, title.length, paint, maxWidthPx)
    .setIncludePad(false)
    .build()
  return layout.lineCount == 1 && layout.getLineEnd(0) == title.length
}
