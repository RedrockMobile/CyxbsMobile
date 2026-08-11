package com.cyxbs.functions.code.language.lezer

import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightChangedRange
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import kotlin.time.TimeSource

/**
 * 将任意 Lezer parser 的结果转换为端上通用高亮协议。
 *
 * 该层统一复用 CodeMirror 的 `classHighlighter`，因此输出的 [DynamicHighlightSpan.styleIds]
 * 是 `tok-keyword`、`tok-string` 等稳定样式标识。Lezer 具备错误恢复能力，即使教学代码尚未
 * 输入完整，也会尽可能返回可解析区域的高亮结果。
 */
object LezerSyntaxHighlighter {

  /**
   * 解析 [source] 并返回有序高亮区间。
   *
   * 返回区间沿用 Kotlin/JS 与 JavaScript 共同的 UTF-16 偏移，不进行码点转换；空区间和没有
   * 有效样式的区间会被忽略。
   */
  fun highlight(
    parser: LezerParser,
    source: String,
  ): List<DynamicHighlightSpan> {
    return collect(parser.parse(source))
  }

  /** 遍历已经生成的 [tree]，将 CodeMirror 样式转换为端上通用高亮区间。 */
  internal fun collect(tree: LezerTree): List<DynamicHighlightSpan> {
    val spans = mutableListOf<DynamicHighlightSpan>()
    highlightTree(tree, classHighlighter) { from, to, classes ->
      val styleIds = classes
        .splitToSequence(' ')
        .filter(String::isNotBlank)
        .toList()
      if (from < to && styleIds.isNotEmpty()) {
        spans += DynamicHighlightSpan(
          from = from,
          to = to,
          styleIds = styleIds,
        )
      }
    }
    return spans
  }
}

/**
 * 面向单个编辑会话的 Lezer 增量高亮器。
 *
 * 本类只缓存上一份源码、语法树片段和高亮结果。首次调用完整解析；后续调用通过最长公共前后缀
 * 计算一个最小变更区间，并让 Lezer 复用未受影响的 [LezerTreeFragment]。完全相同的源码直接
 * 返回缓存结果。实例不保证并发安全，应由同一个 JavaScript Runtime 串行调用。
 */
class LezerSyntaxHighlighterSession(
  private val parser: LezerParser,
) {
  private var previousSource: String? = null
  private var previousTree: LezerTree? = null
  private var fragments: Array<LezerTreeFragment> = emptyArray()
  private var previousSpans: List<DynamicHighlightSpan> = emptyList()

  /**
   * 高亮 [source] 并返回缓存路径及解析、区间收集耗时。
   *
   * 时间单位为微秒；增量范围沿用 JavaScript UTF-16 偏移，适合直接传给端上编辑器。
   */
  fun highlight(source: String): DynamicHighlightResult {
    val oldSource = previousSource
    if (oldSource == source) {
      return DynamicHighlightResult(
        spans = previousSpans,
        metrics = DynamicHighlightMetrics(
          cacheMode = DynamicHighlightCacheMode.EXACT,
          sourceLength = source.length,
          reusableFragmentCount = fragments.size,
          parseMicroseconds = 0,
          collectMicroseconds = 0,
        ),
      )
    }

    val changedRange = oldSource?.findChangedRange(source)
    val reusableFragments = if (changedRange == null || fragments.isEmpty()) {
      emptyArray()
    } else {
      LezerTreeFragment.applyChanges(
        fragments = fragments,
        changes = arrayOf(changedRange.toLezerChangedRange()),
      )
    }
    val cacheMode = if (reusableFragments.isEmpty()) {
      DynamicHighlightCacheMode.FULL
    } else {
      DynamicHighlightCacheMode.INCREMENTAL
    }

    val parseMark = TimeSource.Monotonic.markNow()
    val tree = if (reusableFragments.isEmpty()) {
      parser.parse(source)
    } else {
      parser.parse(source, reusableFragments)
    }
    val parseMicroseconds = parseMark.elapsedNow().inWholeMicroseconds

    val collectMark = TimeSource.Monotonic.markNow()
    val spans = LezerSyntaxHighlighter.collect(tree)
    val collectMicroseconds = collectMark.elapsedNow().inWholeMicroseconds

    // 只保留当前树的片段，避免编辑会话增长时旧树仍被缓存引用。
    previousSource = source
    previousTree = tree
    fragments = LezerTreeFragment.addTree(tree)
    previousSpans = spans
    return DynamicHighlightResult(
      spans = spans,
      metrics = DynamicHighlightMetrics(
        cacheMode = cacheMode,
        sourceLength = source.length,
        changedRange = changedRange,
        reusableFragmentCount = reusableFragments.size,
        parseMicroseconds = parseMicroseconds,
        collectMicroseconds = collectMicroseconds,
      ),
    )
  }

  /**
   * 返回 [source] 对应的语法树，并与 [highlight] 共用同一份增量解析缓存。
   *
   * 若当前源码尚未高亮，本方法会先完成一次高亮分析；调用方随后请求同一源码高亮时会直接命中
   * `EXACT` 缓存。该行为让补全与高亮共享解析成本，同时避免向业务协议暴露 Lezer 类型。
   */
  fun syntaxTree(source: String): LezerTree {
    if (previousSource != source || previousTree == null) highlight(source)
    return checkNotNull(previousTree)
  }

  /** 通过最长公共前后缀生成 Lezer 所需的单一最小变更范围。 */
  private fun String.findChangedRange(newSource: String): DynamicHighlightChangedRange {
    val oldSource = this
    val sharedLimit = minOf(oldSource.length, newSource.length)
    var prefixLength = 0
    while (prefixLength < sharedLimit && oldSource[prefixLength] == newSource[prefixLength]) {
      prefixLength += 1
    }

    var oldSuffixStart = oldSource.length
    var newSuffixStart = newSource.length
    while (
      oldSuffixStart > prefixLength &&
      newSuffixStart > prefixLength &&
      oldSource[oldSuffixStart - 1] == newSource[newSuffixStart - 1]
    ) {
      oldSuffixStart -= 1
      newSuffixStart -= 1
    }
    return DynamicHighlightChangedRange(
      fromBefore = prefixLength,
      toBefore = oldSuffixStart,
      fromAfter = prefixLength,
      toAfter = newSuffixStart,
    )
  }

  /** 将可序列化协议模型转换为 @lezer/common 接受的结构对象。 */
  @Suppress("UnsafeCastFromDynamic")
  private fun DynamicHighlightChangedRange.toLezerChangedRange(): LezerChangedRange {
    val range = js("({})").unsafeCast<LezerChangedRange>()
    range.fromA = fromBefore
    range.toA = toBefore
    range.fromB = fromAfter
    range.toB = toAfter
    return range
  }
}
