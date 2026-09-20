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
private val SingleWidgetHorizontalPadding = 10.dp

/** 标题与地点之间的分割线高度，同时参与整体文字高度计算。 */
private val SingleWidgetDividerHeight = 0.8.dp

private const val SingleWidgetTimeSizeSp = 14F
private const val SingleWidgetDefaultTitleSizeSp = 18F
private const val SingleWidgetMinimumTitleSizeSp = 14F
private const val SingleWidgetDefaultContentSizeSp = 16F
private const val SingleWidgetTwoLineMinimumWidthRatio = 1.3F
private const val SingleWidgetTwoLineMaximumWidthRatio = 1.9F

/** 最小组件根据标题实测宽度计算出的文字布局。 */
private data class SingleWidgetTextLayout(
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
      style = whiteTextStyle(SingleWidgetTimeSizeSp),
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
 * 根据标题在原始 18sp 样式下的实测宽度计算行数和字号。
 *
 * 使用系统字形实测像素宽度而不是字符数，因此中文、英文、数字和混排标题都按当前字体缩放准确判断；
 * 优先在 18sp 到 14sp 之间选择能完整放下一行的最大字号；只有 14sp 仍无法单行容纳时才计算双行。
 * 双行候选字号的总占宽必须位于 [1.3, 1.9] 行；若 14sp 时仍超过上限，则保留最小字号并交给
 * maxLines 截断。最后按宿主实际高度再次约束标题和内容字号，确保内容行不会被挤出边界。
 */
private fun resolveSingleWidgetTextLayout(
  title: String,
  maxWidth: Dp,
  maxHeight: Dp,
  displayMetrics: DisplayMetrics,
): SingleWidgetTextLayout {
  val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      SingleWidgetDefaultTitleSizeSp,
      displayMetrics,
    )
  }
  val maxWidthPx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    maxWidth.value,
    displayMetrics,
  ).toInt().coerceAtLeast(1)
  val titleWidthPx = title.lineSequence().fold(0F) { width, line ->
    width + paint.measureText(line)
  }
  val widthRatio = titleWidthPx / maxWidthPx
  val oneLineTitleSizeSp = findLargestSingleLineTitleSizeSp(
    title = title,
    maxWidthPx = maxWidthPx,
    displayMetrics = displayMetrics,
    paint = paint,
  )
  val widthConstrainedLayout = if (oneLineTitleSizeSp != null) {
    SingleWidgetTextLayout(
      titleSizeSp = oneLineTitleSizeSp,
      titleMaxLines = 1,
      contentSizeSp = minOf(SingleWidgetDefaultContentSizeSp, oneLineTitleSizeSp - 1F),
    )
  } else {
    val twoLineTitleSizeSp = when {
      widthRatio < SingleWidgetTwoLineMaximumWidthRatio -> SingleWidgetDefaultTitleSizeSp
      else -> (
        SingleWidgetDefaultTitleSizeSp * SingleWidgetTwoLineMaximumWidthRatio / widthRatio
      ).coerceAtLeast(SingleWidgetMinimumTitleSizeSp)
    }
    val twoLineWidthRatio = widthRatio * twoLineTitleSizeSp / SingleWidgetDefaultTitleSizeSp
    val reachesMinimumTitleSize = twoLineTitleSizeSp == SingleWidgetMinimumTitleSizeSp
    // 非最小字号来自“按 1.9 行宽反推字号”的公式，理论上已满足上限；避免 Float 回算为
    // 1.9000001 后被误判为超限。只有字号被 14sp 下限截住时，才需要重新校验实际占宽。
    val fitsTwoLineMaximumWidth = !reachesMinimumTitleSize ||
      twoLineWidthRatio <= SingleWidgetTwoLineMaximumWidthRatio
    val fitsBalancedTwoLines = twoLineWidthRatio >= SingleWidgetTwoLineMinimumWidthRatio &&
      fitsTwoLineMaximumWidth
    val exceedsTwoLinesAtMinimumSize = reachesMinimumTitleSize &&
      twoLineWidthRatio >= SingleWidgetTwoLineMaximumWidthRatio
    val useTwoLines = '\n' in title || fitsBalancedTwoLines || exceedsTwoLinesAtMinimumSize
    val titleSizeSp = if (useTwoLines) twoLineTitleSizeSp else SingleWidgetMinimumTitleSizeSp
    SingleWidgetTextLayout(
      titleSizeSp = titleSizeSp,
      titleMaxLines = if (useTwoLines) 2 else 1,
      contentSizeSp = minOf(SingleWidgetDefaultContentSizeSp, titleSizeSp - 1F),
    )
  }
  return constrainSingleWidgetTextLayoutHeight(
    layout = widthConstrainedLayout,
    widthRatio = widthRatio,
    maxHeight = maxHeight,
    displayMetrics = displayMetrics,
    paint = paint,
  )
}

/**
 * 按宿主实际高度压缩标题与内容字号，避免两行标题把最后一行内容推出 RemoteViews 边界。
 *
 * 标题不会低于 14sp；双行标题同时维持至少 1.3 行的总占宽。内容字号随标题同步变化，且始终
 * 小于标题字号。字体高度使用 TextView 默认包含 font padding 的 font metrics 计算。
 */
private fun constrainSingleWidgetTextLayoutHeight(
  layout: SingleWidgetTextLayout,
  widthRatio: Float,
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
  val minimumTitleSizeSp = if (layout.titleMaxLines == 2) {
    maxOf(
      SingleWidgetMinimumTitleSizeSp,
      SingleWidgetDefaultTitleSizeSp * SingleWidgetTwoLineMinimumWidthRatio / widthRatio,
    ).coerceAtMost(layout.titleSizeSp)
  } else {
    SingleWidgetMinimumTitleSizeSp
  }

  fun createLayout(titleSizeSp: Float): SingleWidgetTextLayout = layout.copy(
    titleSizeSp = titleSizeSp,
    contentSizeSp = minOf(SingleWidgetDefaultContentSizeSp, titleSizeSp - 1F),
  )

  fun lineHeightPx(sizeSp: Float): Int {
    paint.textSize = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      sizeSp,
      displayMetrics,
    )
    return paint.fontMetricsInt.let { metrics -> metrics.bottom - metrics.top }
  }

  fun fitsHeight(candidate: SingleWidgetTextLayout): Boolean {
    val totalHeightPx = lineHeightPx(SingleWidgetTimeSizeSp) +
      lineHeightPx(candidate.titleSizeSp) * candidate.titleMaxLines +
      dividerHeightPx +
      lineHeightPx(candidate.contentSizeSp)
    return totalHeightPx <= maxHeightPx
  }

  val preferredLayout = createLayout(layout.titleSizeSp)
  if (fitsHeight(preferredLayout)) return preferredLayout

  val minimumLayout = createLayout(minimumTitleSizeSp)
  if (!fitsHeight(minimumLayout)) return minimumLayout

  var fittingSizeSp = minimumTitleSizeSp
  var overflowingSizeSp = layout.titleSizeSp
  repeat(10) {
    val candidateSizeSp = (fittingSizeSp + overflowingSizeSp) / 2F
    if (fitsHeight(createLayout(candidateSizeSp))) {
      fittingSizeSp = candidateSizeSp
    } else {
      overflowingSizeSp = candidateSizeSp
    }
  }
  return createLayout(fittingSizeSp)
}

/**
 * 在允许的标题字号区间内查找能够被 Android 单行文字布局完整容纳的最大字号。
 *
 * 比例换算无法覆盖字形 hinting 和像素取整；这里使用与 Glance 最终 TextView 更接近的
 * [StaticLayout] 逐次验证，返回 null 表示 14sp 仍需换行。
 */
private fun findLargestSingleLineTitleSizeSp(
  title: String,
  maxWidthPx: Int,
  displayMetrics: DisplayMetrics,
  paint: TextPaint,
): Float? {
  if ('\n' in title) return null

  fun fitsSingleLine(sizeSp: Float): Boolean {
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

  if (!fitsSingleLine(SingleWidgetMinimumTitleSizeSp)) return null
  if (fitsSingleLine(SingleWidgetDefaultTitleSizeSp)) return SingleWidgetDefaultTitleSizeSp

  var fittingSizeSp = SingleWidgetMinimumTitleSizeSp
  var overflowingSizeSp = SingleWidgetDefaultTitleSizeSp
  repeat(10) {
    val candidateSizeSp = (fittingSizeSp + overflowingSizeSp) / 2F
    if (fitsSingleLine(candidateSizeSp)) {
      fittingSizeSp = candidateSizeSp
    } else {
      overflowingSizeSp = candidateSizeSp
    }
  }
  return fittingSizeSp
}
