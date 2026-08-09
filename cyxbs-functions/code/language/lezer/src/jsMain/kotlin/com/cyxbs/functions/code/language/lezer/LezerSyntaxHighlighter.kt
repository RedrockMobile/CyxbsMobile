package com.cyxbs.functions.code.language.lezer

import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan

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
    val spans = mutableListOf<DynamicHighlightSpan>()
    highlightTree(parser.parse(source), classHighlighter) { from, to, classes ->
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
