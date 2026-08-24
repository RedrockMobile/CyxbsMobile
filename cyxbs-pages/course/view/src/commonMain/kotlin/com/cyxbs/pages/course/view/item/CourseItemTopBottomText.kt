package com.cyxbs.pages.course.view.item

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.utils.extensions.logg

/**
 * 根据当前可见片段的真实高度测量并绘制课表 item 的标题和描述。
 *
 * 展示优先级固定为：原样式标题和描述、原位置标题、去掉上下间距后居中的标题、忽略所有间距
 * 后缩小的标题、完全不展示。描述不会跟随标题缩小，避免短片段为了保留次要信息而让主要标题
 * 过小。文字不创建 Compose 子节点，测量结果直接交给 draw 阶段，减少切割片段动画中的重组开销。
 *
 * @param topText 标题文本。
 * @param bottomText 底部时间或备注文本；空字符串不会占据布局空间。
 * @param textColor 两段文字共同使用的颜色。
 */
@Composable
fun CourseItemTopBottomText(
  modifier: Modifier = Modifier,
  topText: String,
  bottomText: String,
  textColor: Color,
) {
  val textMeasurer = rememberTextMeasurer()
  val drawState = remember { CourseItemTextDrawState() }
  val baseTextStyle = LocalTextStyle.current
  val textStyles = remember(baseTextStyle, textColor) {
    createCourseItemTextStyles(baseTextStyle, textColor)
  }
  val measurePolicy = remember(topText, bottomText, textMeasurer, textStyles) {
    CourseItemTextMeasurePolicy(
      topText = topText,
      bottomText = bottomText,
      textMeasurer = textMeasurer,
      textStyles = textStyles,
      drawState = drawState,
    )
  }
  val accessibilityText = remember(topText, bottomText) {
    if (bottomText.isEmpty()) topText else "$topText，$bottomText"
  }
  Layout(
    content = {},
    modifier = modifier
      .fillMaxSize()
      .semantics { contentDescription = accessibilityText }
      .drawWithContent {
        drawContent()
        // TextLayoutResult 的字号字形可能略超出排版框，必须按当前切割片段裁剪，避免覆盖相邻 item。
        clipRect {
          drawState.topTextLayoutResult?.let { result ->
            drawText(result, topLeft = drawState.topTextOffset)
          }
          drawState.bottomTextLayoutResult?.let { result ->
            drawText(result, topLeft = drawState.bottomTextOffset)
          }
        }
      },
    measurePolicy = measurePolicy,
  )
}

/** measure 阶段写入、draw 阶段读取的绘制结果，不通过 Compose State 触发额外重组。 */
private class CourseItemTextDrawState {
  var topTextLayoutResult: TextLayoutResult? = null
  var topTextOffset: Offset = Offset.Zero
  var bottomTextLayoutResult: TextLayoutResult? = null
  var bottomTextOffset: Offset = Offset.Zero
}

/**
 * 课表 item 文字的分级测量策略。
 *
 * 原样式内容只测量一次；标题和描述无法同时展示时复用同一标题测量结果，依次判断原位置和
 * 去掉上下间距后的居中位置。只有同一结果仍放不下时，才用行高缓存定位零间距下的候选字号，
 * 并在相邻候选中校正。
 */
private class CourseItemTextMeasurePolicy(
  private val topText: String,
  private val bottomText: String,
  private val textMeasurer: TextMeasurer,
  private val textStyles: List<TextStyle>,
  private val drawState: CourseItemTextDrawState,
) : MeasurePolicy {

  /**
   * 按“完整内容 → 原位置标题 → 去掉上下间距并居中 → 缩小标题 → 不展示”的顺序确定结果。
   *
   * 首选配置使用 11sp 字号和 6dp 间距。标题与描述允许按最大行数省略，只有两者的排版高度无法
   * 同时放下时才隐藏描述；若标题自身的排版高度能够放下，
   * 标题仍按原配置靠上绘制而不是居中；原位置放不下时复用同一测量结果，去掉上下间距并判断
   * 居中后能否容纳。仍放不下才忽略上下、左右间距，用行高缓存定位小于 11sp 的候选字号并在
   * 附近查找最大可用字号。
   * 最小字号仍放不下时清空绘制状态，让该片段不显示任何文字。
   *
   * [measurables] 始终为空，文字由 [TextMeasurer] 测量并通过 [drawState] 交给 draw 阶段；本方法
   * 不创建 Placeable，也不会写入 Compose State。
   *
   * @param measurables [Layout] 接口要求的参数，本组件没有子节点，因此始终为空。
   * @param constraints 当前切割片段的固定像素约束。
   * @return 与约束同尺寸且没有子节点放置动作的测量结果。
   */
  override fun MeasureScope.measure(
    measurables: List<Measurable>,
    constraints: Constraints,
  ): MeasureResult {
    val containerWidth = constraints.maxWidth
    val containerHeight = constraints.maxHeight
    val preferredPadding = PREFERRED_CONTENT_PADDING.roundToPx()
    val textGap = TEXT_GAP.roundToPx()
    CourseItemTextLineHeightCache.prepare(
      density = density,
      fontScale = fontScale,
      styleCount = textStyles.size,
    )
    val titleMaxLines = calculateTitleMaxLines(
      availableHeight = (containerHeight - preferredPadding).coerceAtLeast(0),
      preferredLineHeight = CourseItemTextLineHeightCache.get(0),
    )
    val preferredCandidate = measureCandidate(
      styleIndex = 0,
      containerWidth = containerWidth,
      containerHeight = containerHeight,
      horizontalPadding = preferredPadding,
      verticalPadding = preferredPadding,
      titleMaxLines = titleMaxLines,
      measureBottomText = bottomText.isNotEmpty(),
    )

    when {
      preferredCandidate.allContentFits(containerHeight, textGap) -> {
        updateDrawState(
          containerWidth = containerWidth,
          containerHeight = containerHeight,
          candidate = preferredCandidate,
          textGap = textGap,
          centerTitleVertically = false,
        )
      }
      preferredCandidate.titleFitsAtOriginalPosition(containerHeight) -> {
        // 原大小标题仍沿用顶部 6dp 间距，只隐藏描述，不做纵向居中。
        updateDrawState(
          containerWidth = containerWidth,
          containerHeight = containerHeight,
          candidate = preferredCandidate.withoutBottomText(),
          textGap = textGap,
          centerTitleVertically = false,
        )
      }
      preferredCandidate.titleFitsWithoutVerticalPadding(containerHeight) -> {
        // 复用 11sp 标题的原始宽度测量结果，只去掉上下间距并纵向居中，不重复测量文字。
        updateDrawState(
          containerWidth = containerWidth,
          containerHeight = containerHeight,
          candidate = preferredCandidate.withoutBottomText(verticalPadding = 0),
          textGap = textGap,
          centerTitleVertically = true,
        )
      }
      else -> {
        val reducedCandidate = selectReducedTitle(
          containerWidth = containerWidth,
          containerHeight = containerHeight,
          preferredTitleLineCount = preferredCandidate.topResult.lineCount,
          titleMaxLines = titleMaxLines,
        )
        if (reducedCandidate == null) {
          drawState.clear()
        } else {
          updateDrawState(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            candidate = reducedCandidate,
            textGap = textGap,
            centerTitleVertically = true,
          )
        }
      }
    }
    return layout(containerWidth, containerHeight) {}
  }

  /**
   * 先按当前高度估算合适字号，再在相邻候选中校正到最大可用字号。
   *
   * 11sp 已在上层原样式分支测量过，进入本方法就说明标题高度不足。先使用单例缓存中各字号的
   * 一行高度，乘以当前标题行数，得到最可能合适的候选下标并测量一次；候选能放下时逐个尝试
   * 相邻的更大字号，遇到首个放不下的候选立即停止；候选不能放下时逐个尝试相邻的更小字号，
   * 找到首个能放下的候选即停止。所有候选均使用零间距，最终仍以真实排版高度为准。
   */
  private fun selectReducedTitle(
    containerWidth: Int,
    containerHeight: Int,
    preferredTitleLineCount: Int,
    titleMaxLines: Int,
  ): CourseItemTextCandidate? {
    val minimumIndex = textStyles.lastIndex
    val estimatedIndex = estimateTextStyleIndex(
      containerHeight = containerHeight,
      preferredTitleLineCount = preferredTitleLineCount,
      minimumIndex = minimumIndex,
    )
    val estimatedCandidate = measureCandidate(
      styleIndex = estimatedIndex,
      containerWidth = containerWidth,
      containerHeight = containerHeight,
      horizontalPadding = 0,
      verticalPadding = 0,
      titleMaxLines = titleMaxLines,
      measureBottomText = false,
    )
    if (topText.startsWith("时间点")) {
      logg("estimatedCandidate = ${estimatedCandidate.topResult.size}, containerHeight = $containerHeight, lineCount = ${estimatedCandidate.topResult.lineCount}")
    }
    val estimatedFits = estimatedCandidate.titleFitsWithoutVerticalPadding(containerHeight)
    if (estimatedFits) {
      var bestCandidate = estimatedCandidate
      var nearbyIndex = estimatedIndex - 1
      while (nearbyIndex >= 1) {
        val nearbyCandidate = measureCandidate(
          styleIndex = nearbyIndex,
          containerWidth = containerWidth,
          containerHeight = containerHeight,
          horizontalPadding = 0,
          verticalPadding = 0,
          titleMaxLines = titleMaxLines,
          measureBottomText = false,
        )
        if (topText.startsWith("时间点")) {
          logg("111 topResult = ${nearbyCandidate.topResult.size}, lineCount = ${nearbyCandidate.topResult.lineCount}")
        }
        if (!nearbyCandidate.titleFitsWithoutVerticalPadding(containerHeight)) break
        bestCandidate = nearbyCandidate
        nearbyIndex--
      }
      return bestCandidate
    }
    var nearbyIndex = estimatedIndex + 1
    while (nearbyIndex <= minimumIndex) {
      val nearbyCandidate = measureCandidate(
        styleIndex = nearbyIndex,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        horizontalPadding = 0,
        verticalPadding = 0,
        titleMaxLines = titleMaxLines,
        measureBottomText = false,
      )
      if (topText.startsWith("时间点")) {
        logg("111 topResult = ${nearbyCandidate.topResult.size}, lineCount = ${nearbyCandidate.topResult.lineCount}")
      }
      if (nearbyCandidate.titleFitsWithoutVerticalPadding(containerHeight)) {
        return nearbyCandidate
      }
      nearbyIndex++
    }
    return null
  }

  /** 使用指定字号和左右间距测量标题，并记录独立的上下间距用于后续位置判断。 */
  private fun measureCandidate(
    styleIndex: Int,
    containerWidth: Int,
    containerHeight: Int,
    horizontalPadding: Int,
    verticalPadding: Int,
    titleMaxLines: Int,
    measureBottomText: Boolean,
  ): CourseItemTextCandidate {
    val textWidth = (containerWidth - horizontalPadding * 2).coerceAtLeast(0)
    val style = textStyles[styleIndex]
    val topResult = measureText(
      text = topText,
      style = style,
      maxWidth = textWidth,
      maxHeight = containerHeight,
      maxLines = titleMaxLines,
    )
    CourseItemTextLineHeightCache.update(styleIndex, topResult)
    val bottomResult = if (measureBottomText) {
      measureText(
        text = bottomText,
        style = style,
        maxWidth = textWidth,
        maxHeight = containerHeight,
        maxLines = BOTTOM_TEXT_MAX_LINES,
      )
    } else {
      null
    }
    return CourseItemTextCandidate(
      topResult = topResult,
      bottomResult = bottomResult,
      verticalPadding = verticalPadding,
    )
  }

  /** 按统一换行和省略规则测量一段文字。 */
  private fun measureText(
    text: String,
    style: TextStyle,
    maxWidth: Int,
    maxHeight: Int,
    maxLines: Int,
  ): TextLayoutResult {
    return textMeasurer.measure(
      text = text,
      style = style,
      overflow = TextOverflow.Ellipsis,
      maxLines = maxLines,
      constraints = Constraints(maxWidth = maxWidth, maxHeight = maxHeight),
    )
  }

  /** 按候选配置写入标题和描述的靠上、靠下绘制坐标。 */
  private fun updateDrawState(
    containerWidth: Int,
    containerHeight: Int,
    candidate: CourseItemTextCandidate,
    textGap: Int,
    centerTitleVertically: Boolean,
  ) {
    drawState.topTextLayoutResult = candidate.topResult
    drawState.topTextOffset = Offset(
      x = calculateCenteredTextX(containerWidth, candidate.topResult),
      y = if (centerTitleVertically) {
        ((containerHeight - candidate.topResult.size.height) / 2F).coerceAtLeast(0F)
      } else {
        candidate.verticalPadding.toFloat()
      },
    )
    drawState.bottomTextLayoutResult = candidate.bottomResult
    drawState.bottomTextOffset = Offset(
      x = calculateCenteredTextX(containerWidth, candidate.bottomResult),
      y = candidate.bottomResult?.let { result ->
        (containerHeight - candidate.verticalPadding - result.size.height)
          .coerceAtLeast(candidate.verticalPadding + candidate.topResult.size.height + textGap)
          .toFloat()
      } ?: 0F,
    )
  }
}

/** 单一配置下的标题、描述及上下间距；左右间距已体现在 [TextLayoutResult] 的测量宽度中。 */
private class CourseItemTextCandidate(
  val topResult: TextLayoutResult,
  val bottomResult: TextLayoutResult?,
  val verticalPadding: Int,
) {
  /** 标题按原顶部位置绘制时是否能够完整落在容器内。 */
  fun titleFitsAtOriginalPosition(containerHeight: Int): Boolean {
    return verticalPadding * 2 + topResult.requiredTextHeight() <= containerHeight
  }

  /** 去掉上下间距后，标题自身高度是否能够放入容器。 */
  fun titleFitsWithoutVerticalPadding(containerHeight: Int): Boolean {
    return topResult.requiredTextHeight() <= containerHeight
  }

  fun allContentFits(containerHeight: Int, textGap: Int): Boolean {
    val bottom = bottomResult ?: return titleFitsAtOriginalPosition(containerHeight)
    return verticalPadding * 2 +
      topResult.requiredTextHeight() +
      textGap +
      bottom.requiredTextHeight() <= containerHeight
  }

  fun withoutBottomText(verticalPadding: Int = this.verticalPadding): CourseItemTextCandidate {
    return CourseItemTextCandidate(
      topResult = topResult,
      bottomResult = null,
      verticalPadding = verticalPadding,
    )
  }
}

/** 清空上一次测量结果，避免极短片段继续绘制旧文字。 */
private fun CourseItemTextDrawState.clear() {
  topTextLayoutResult = null
  topTextOffset = Offset.Zero
  bottomTextLayoutResult = null
  bottomTextOffset = Offset.Zero
}

/**
 * 返回排版最后一行真实占用到的纵坐标。
 *
 * [TextLayoutResult.size] 会受传入的最大高度截断，不能单独证明字形已经完整放下；而
 * `didOverflowHeight` 也会把横向换行后由 maxLines 产生的正常省略视为溢出。最后一行 bottom
 * 只描述当前实际绘制行的纵向边界，适合用于这里的高度判断。
 */
private fun TextLayoutResult.requiredTextHeight(): Float {
  return if (lineCount == 0) 0F else getLineBottom(lineCount - 1)
}

/** 按文字实际排版宽度计算水平居中坐标。 */
private fun calculateCenteredTextX(
  containerWidth: Int,
  textLayoutResult: TextLayoutResult?,
): Float = textLayoutResult?.let { (containerWidth - it.size.width) / 2F } ?: 0F

/**
 * 使用首选11sp的一行缓存高度，计算当前片段允许的固定标题行数。
 *
 * 行数在字号缩小前确定，后续所有候选复用同一个值；因此时间点按11sp只够一行时，即使6sp能够
 * 在相同像素高度内排下两行，也仍会按一行省略展示。
 */
private fun calculateTitleMaxLines(
  availableHeight: Int,
  preferredLineHeight: Float,
): Int {
  if (preferredLineHeight <= 0F) return 1
  return (availableHeight / preferredLineHeight).toInt().coerceIn(1, TITLE_MAX_LINES)
}

/** 根据缓存的一行高度，估算零间距下最可能放入容器的字号候选下标。 */
private fun estimateTextStyleIndex(
  containerHeight: Int,
  preferredTitleLineCount: Int,
  minimumIndex: Int,
): Int {
  if (minimumIndex <= 1) return minimumIndex.coerceAtLeast(0)
  val lineCount = preferredTitleLineCount.coerceAtLeast(1)
  var styleIndex = 1 // 11sp 已经测量失败，不再作为缩小阶段候选。
  while (styleIndex < minimumIndex) {
    val estimatedHeight = CourseItemTextLineHeightCache.get(styleIndex) * lineCount
    if (estimatedHeight <= containerHeight) return styleIndex
    styleIndex++
  }
  return minimumIndex
}

/**
 * 课表文字候选字号的一行像素高度内存缓存。
 *
 * 首次使用时按字号、密度和字体缩放估算 11sp～9sp 的像素行高；每次 [TextMeasurer] 完成真实
 * 排版后，再用实际总高度除以行数回写对应字号。候选只有七档，缓存仅保存在进程内，不需要落盘。
 * 它只影响首次搜索位置，真实候选仍会再次测量，因此估算误差不会改变最终展示正确性。
 *
 * Compose 测量运行在 UI 线程，本对象不引入锁；即使不同窗口交替重建，也只会降低估算命中率。
 */
private object CourseItemTextLineHeightCache {
  private var cachedDensity = Float.NaN
  private var cachedFontScale = Float.NaN
  private var lineHeights = FloatArray(0)

  /** 为当前显示环境准备各候选字号的预估行高。 */
  fun prepare(
    density: Float,
    fontScale: Float,
    styleCount: Int,
  ) {
    if (
      cachedDensity == density &&
      cachedFontScale == fontScale &&
      lineHeights.size == styleCount
    ) {
      return
    }
    cachedDensity = density
    cachedFontScale = fontScale
    lineHeights = FloatArray(styleCount) { styleIndex ->
      val fontSize = (PREFERRED_TEXT_SIZE.value - styleIndex * TEXT_SIZE_STEP)
        .coerceAtLeast(MINIMUM_TEXT_SIZE.value)
      fontSize * density * fontScale * DEFAULT_LINE_HEIGHT_MULTIPLIER
    }
  }

  /** 返回指定字号候选的一行像素高度。 */
  fun get(styleIndex: Int): Float = lineHeights[styleIndex]

  /** 使用真实排版结果更新指定字号的一行像素高度。 */
  fun update(styleIndex: Int, result: TextLayoutResult) {
    if (styleIndex !in lineHeights.indices || result.lineCount <= 0) return
    val measuredLineHeight = result.requiredTextHeight() / result.lineCount
    if (measuredLineHeight > 0F) {
      lineHeights[styleIndex] = measuredLineHeight
    }
  }
}

/** 创建从首选字号到最小字号的有序候选。 */
private fun createCourseItemTextStyles(
  baseTextStyle: TextStyle,
  color: Color,
): List<TextStyle> {
  val styles = mutableListOf<TextStyle>()
  var fontSize = PREFERRED_TEXT_SIZE.value
  while (fontSize > MINIMUM_TEXT_SIZE.value) {
    styles.add(
      baseTextStyle.copy(
        color = color,
        fontSize = fontSize.sp,
        textAlign = TextAlign.Center,
      )
    )
    fontSize = maxOf(MINIMUM_TEXT_SIZE.value, fontSize - TEXT_SIZE_STEP)
  }
  styles.add(
    baseTextStyle.copy(
      color = color,
      fontSize = MINIMUM_TEXT_SIZE,
      textAlign = TextAlign.Center,
    )
  )
  return styles
}

private val PREFERRED_CONTENT_PADDING = 6.dp
private val TEXT_GAP = 2.dp
private val PREFERRED_TEXT_SIZE = 11.sp
private val MINIMUM_TEXT_SIZE = 9.sp
private const val TEXT_SIZE_STEP = 0.5F
private const val DEFAULT_LINE_HEIGHT_MULTIPLIER = 1.2F
private const val TITLE_MAX_LINES = 3
private const val BOTTOM_TEXT_MAX_LINES = 2
