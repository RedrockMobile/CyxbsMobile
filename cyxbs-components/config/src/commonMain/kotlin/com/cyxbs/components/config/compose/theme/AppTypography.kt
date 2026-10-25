package com.cyxbs.components.config.compose.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.Platform
import com.cyxbs.components.config.appPlatform
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.TimeSource

@Composable
internal expect fun getFontFamily(): FontFamily

/** 参与候选验证的字号总数，必须与 [TYPOGRAPHY_FONT_SIZE_MEASUREMENT_ORDER_SP] 的元素数量一致。 */
private const val TYPOGRAPHY_FONT_SIZE_ITEM_COUNT = 5

/** 候选结果完全同分时优先靠近 16sp，使最终值更贴近应用中最常见的正文大小。 */
private const val PREFERRED_REFERENCE_FONT_SIZE_SP = 16

/** 初始最多容忍两个字号不一致；达到该数量便停止验证当前候选，以减少文本测量次数。 */
private const val INITIAL_CANDIDATE_MISMATCH_UPPER_BOUND = 2

/** 最短的英文与数字混合样本，用于触发纯 Latin 字体度量，同时覆盖数字文本场景。 */
private const val LATIN_LINE_HEIGHT_SAMPLE = "A0"

/** 单个中文字符样本，用于触发 CJK 字体或 fallback 字体，并取得中文基准行框高度。 */
private const val CJK_LINE_HEIGHT_SAMPLE = "中"

/** 常用正文区间优先，命中最优值后可以尽早停止；较小字号放在末尾完成覆盖校验。 */
private val TYPOGRAPHY_FONT_SIZE_MEASUREMENT_ORDER_SP = intArrayOf(16, 17, 18, 15, 14)

@Composable
internal fun createAppTypography(): Typography {
  val defaultFontFamily = getFontFamily()
  val sourceTypography = MaterialTheme.typography
  // Web 使用覆盖中英文的固定字体资源，不需要再做启动期系统字体测量。
  val lineHeightEm = if (appPlatform != Platform.Web) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val measurementStyle = sourceTypography.body1.copy(
      fontFamily = defaultFontFamily,
      lineHeight = TextUnit.Unspecified,
    )
    rememberLineHeightEmCandidates(measurementStyle, textMeasurer).preferredCandidate.lineHeightEm
  } else {
    null
  }
  return Typography(
    h1 = sourceTypography.h1.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    h2 = sourceTypography.h2.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    h3 = sourceTypography.h3.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    h4 = sourceTypography.h4.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    h5 = sourceTypography.h5.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    h6 = sourceTypography.h6.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    subtitle1 = sourceTypography.subtitle1.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    subtitle2 = sourceTypography.subtitle2.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    body1 = sourceTypography.body1.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    body2 = sourceTypography.body2.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    button = sourceTypography.button.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    caption = sourceTypography.caption.withAppFontMetrics(defaultFontFamily, lineHeightEm),
    overline = sourceTypography.overline.withAppFontMetrics(defaultFontFamily, lineHeightEm),
  )
}

/**
 * 将平台字体族和测得的相对行高应用到单个 Material 文本样式。
 *
 * 非 Web 使用 em 而不是固定 sp/px，因此 h1、正文、caption 等不同字号会分别按自身
 * [TextStyle.fontSize] 缩放。Web 返回 null，继续沿用此前的未指定行高，避免改变固定
 * 思源黑体在浏览器端的既有排版。
 */
private fun TextStyle.withAppFontMetrics(
  fontFamily: FontFamily,
  lineHeightEm: Float?,
): TextStyle = if (lineHeightEm == null) {
  copy(
    lineHeight = TextUnit.Unspecified,
    fontFamily = fontFamily,
  )
} else {
  copy(
    lineHeight = lineHeightEm.em,
    lineHeightStyle = NormalizedLineHeightStyle,
    fontFamily = fontFamily,
  )
}

private val NormalizedLineHeightStyle = LineHeightStyle(
  alignment = LineHeightStyle.Alignment.Center,
  trim = LineHeightStyle.Trim.None,
  mode = LineHeightStyle.Mode.Fixed,
)

/**
 * 测量当前平台字体栈，并从 14sp～18sp 中选择让纯英文/数字行框最接近中文行框的 em。
 *
 * 为什么需要运行时测量：Android、iOS 与 Desktop 的 [FontFamily.Default] 不是同一个
 * 字体文件，并且纯拉丁字符与中文字符还可能命中不同的 fallback 字体。相同 fontSize
 * 因而不保证得到相同的 ascent、descent 和最终整数像素高度，写死一个 em 无法覆盖所有
 * 平台、设备密度与字体缩放设置。
 *
 * 算法边界：这里只用短文本 [LATIN_LINE_HEIGHT_SAMPLE] 和 [CJK_LINE_HEIGHT_SAMPLE]
 * 测单行最终布局高度；候选范围优先检查 16sp、17sp、18sp，再检查 15sp、14sp。
 * 返回值通过 [remember] 按字体样式、测量器和 density 缓存；这些输入不变时，普通重组
 * 不会重新测量。调用发生在组合阶段，因此方法只做少量同步文本布局，不能放入长文本或
 * 大字号集合。
 *
 * 剪枝规则：误差上限初始为 2 个不一致字号；候选达到当前上限后立即停止剩余验证。
 * 只有完整验证的候选才能收紧上限，避免用一个尚未测完的候选低估真实误差。若完整候选
 * 得到 1 个不一致，后续候选首次不一致就停止；若得到 0，已达到理论最优并停止全部搜索。
 * 同误差时优先已测字号更多者，再比较累计绝对像素差，最后优先接近 16sp 的参考字号。
 *
 * @param baseStyle 必须包含实际使用的字体族，行高会在内部清除后重新设置。
 * @param textMeasurer Compose 文本测量器；调用方应使用 [rememberTextMeasurer] 创建并复用。
 * @return 候选详情、最终选择、实际测量次数和计算耗时，便于测试页验证不同平台表现。
 */
@Composable
fun rememberLineHeightEmCandidates(
  baseStyle: TextStyle,
  textMeasurer: TextMeasurer,
): LineHeightEmCandidateCalculation {
  val density = LocalDensity.current
  return remember(baseStyle, textMeasurer, density) {
    val startMark = TimeSource.Monotonic.markNow()

    // 中文高度按需产生。外层刚进入 16sp 时只测 16sp，不会先 map 完整个字号集合。
    // 后续候选再次需要相同字号时直接命中缓存，确保每个字号的中文基准最多布局一次。
    val cjkSampleCache = mutableMapOf<Int, CjkHeightSample>()
    fun getOrMeasureCjkSample(fontSizeSp: Int): CjkHeightSample {
      return cjkSampleCache.getOrPut(fontSizeSp) {
        val fontSize = fontSizeSp.sp
        val rawStyle = baseStyle.copy(
          fontSize = fontSize,
          lineHeight = TextUnit.Unspecified,
        )
        CjkHeightSample(
          fontSizeSp = fontSizeSp,
          fontSize = fontSize,
          fontSizePx = with(density) { fontSize.toPx() },
          cjkHeightPx = textMeasurer.measureSingleLineHeight(CJK_LINE_HEIGHT_SAMPLE, rawStyle),
        )
      }
    }

    val candidates = mutableListOf<LineHeightEmCandidate>()
    var mismatchUpperBound = INITIAL_CANDIDATE_MISMATCH_UPPER_BOUND
    for (referenceFontSizeSp in TYPOGRAPHY_FONT_SIZE_MEASUREMENT_ORDER_SP) {
      // 先测当前参考字号的中文高度，立即得到候选 em；不等待其他字号基准完成。
      val reference = getOrMeasureCjkSample(referenceFontSizeSp)
      val candidateEm = reference.cjkHeightPx / reference.fontSizePx
      var mismatchCount = 0
      var totalAbsoluteDifferencePx = 0
      var measuredFontSizeCount = 0

      for (sampleFontSizeSp in TYPOGRAPHY_FONT_SIZE_MEASUREMENT_ORDER_SP) {
        // 验证走到某个字号时才产生它的中文基准，实现真正的边测量、边比较、边剪枝。
        val sample = getOrMeasureCjkSample(sampleFontSizeSp)
        val repairedStyle = baseStyle.copy(
          fontSize = sample.fontSize,
          lineHeight = candidateEm.em,
          lineHeightStyle = NormalizedLineHeightStyle,
        )
        val repairedLatinHeightPx =
          textMeasurer.measureSingleLineHeight(LATIN_LINE_HEIGHT_SAMPLE, repairedStyle)
        val differencePx = repairedLatinHeightPx - sample.cjkHeightPx

        measuredFontSizeCount++
        if (differencePx != 0) mismatchCount++
        totalAbsoluteDifferencePx += abs(differencePx)

        // 达到动态上限后继续测量也不能成为更低误差候选，因此立即跳出当前候选验证。
        if (mismatchCount >= mismatchUpperBound) break
      }

      val isFullyMeasured = measuredFontSizeCount == TYPOGRAPHY_FONT_SIZE_ITEM_COUNT
      val candidate = LineHeightEmCandidate(
        referenceFontSizeSp = reference.fontSizeSp,
        lineHeightEm = candidateEm,
        mismatchCount = mismatchCount,
        totalAbsoluteDifferencePx = totalAbsoluteDifferencePx,
        measuredFontSizeCount = measuredFontSizeCount,
        isFullyMeasured = isFullyMeasured,
      )
      candidates += candidate

      // 剪枝候选还有未知字号，只有完整候选的误差才有资格成为后续的新上限。
      if (isFullyMeasured && mismatchCount < mismatchUpperBound) {
        mismatchUpperBound = mismatchCount
      }
      // 完整且零误差已经达到可比较指标的下界，后续候选不可能更好。
      if (isFullyMeasured && mismatchCount == 0) break
    }

    val preferredCandidate = candidates.minWithOrNull(
      compareBy<LineHeightEmCandidate> { it.mismatchCount }
        .thenByDescending { it.measuredFontSizeCount }
        .thenBy { it.totalAbsoluteDifferencePx }
        .thenBy { abs(it.referenceFontSizeSp - PREFERRED_REFERENCE_FONT_SIZE_SP) }
    ) ?: error("字体行高候选不能为空")

    LineHeightEmCandidateCalculation(
      candidates = candidates,
      preferredCandidate = preferredCandidate,
      duration = startMark.elapsedNow(),
      measurementCount = cjkSampleCache.size + candidates.sumOf { it.measuredFontSizeCount },
      finalMismatchUpperBound = mismatchUpperBound,
    )
  }
}

/** 单行、禁止换行测量，返回 Compose 最终取整后的真实布局像素高度。 */
private fun TextMeasurer.measureSingleLineHeight(text: String, style: TextStyle): Int = measure(
  text = text,
  style = style,
  maxLines = 1,
  softWrap = false,
).size.height

/** 单个字号的中文基准高度，只在一次候选计算的按需缓存中使用。 */
private data class CjkHeightSample(
  val fontSizeSp: Int,
  val fontSize: TextUnit,
  val fontSizePx: Float,
  val cjkHeightPx: Int,
)

/** 一个参考字号产生的 em，以及它在剪枝前已经验证到的真实误差。 */
data class LineHeightEmCandidate(
  val referenceFontSizeSp: Int,
  val lineHeightEm: Float,
  val mismatchCount: Int,
  val totalAbsoluteDifferencePx: Int,
  val measuredFontSizeCount: Int,
  val isFullyMeasured: Boolean,
)

/** 一次候选搜索的完整结果；[preferredCandidate] 是主题最终采用的候选。 */
data class LineHeightEmCandidateCalculation(
  val candidates: List<LineHeightEmCandidate>,
  val preferredCandidate: LineHeightEmCandidate,
  val duration: Duration,
  val measurementCount: Int,
  val finalMismatchUpperBound: Int,
)
