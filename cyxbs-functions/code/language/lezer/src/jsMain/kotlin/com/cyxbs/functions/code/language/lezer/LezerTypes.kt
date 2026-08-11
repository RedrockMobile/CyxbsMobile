package com.cyxbs.functions.code.language.lezer

/**
 * Lezer 解析器的最小公共视图。
 *
 * 语言模块只需暴露自己的 parser，无需感知 Lezer 语法树及高亮遍历的具体 JavaScript API。
 */
external interface LezerParser {

  /**
   * 解析源码并返回支持错误恢复的语法树。
   *
   * [source] 和语法树节点偏移均沿用 JavaScript UTF-16 索引语义。
   */
  fun parse(
    source: String,
    fragments: Array<LezerTreeFragment> = definedExternally,
  ): LezerTree
}

/** Lezer 语法树的不透明句柄，仅供通用高亮适配层消费。 */
external interface LezerTree
