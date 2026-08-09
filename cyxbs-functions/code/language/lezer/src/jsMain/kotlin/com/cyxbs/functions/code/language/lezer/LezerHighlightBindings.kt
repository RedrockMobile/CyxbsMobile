@file:JsModule("@lezer/highlight")

package com.cyxbs.functions.code.language.lezer

/** Lezer 高亮规则的不透明句柄，通常无需由语言模块直接使用。 */
external interface LezerHighlighter

/** CodeMirror 官方的通用 class 映射器，输出稳定的 `tok-*` 样式类。 */
external val classHighlighter: LezerHighlighter

/**
 * 按源码顺序遍历语法树中的高亮区间。
 *
 * 回调区间使用 JavaScript UTF-16 偏移，`classes` 可能包含多个空格分隔的样式类。语言模块
 * 通常应调用 [LezerSyntaxHighlighter]，仅在需要自定义区间转换时直接使用本函数。
 */
external fun highlightTree(
  tree: LezerTree,
  highlighter: LezerHighlighter,
  putStyle: (from: Int, to: Int, classes: String) -> Unit,
)
