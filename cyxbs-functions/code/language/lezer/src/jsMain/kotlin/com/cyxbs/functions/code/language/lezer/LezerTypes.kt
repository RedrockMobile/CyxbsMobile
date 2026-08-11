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

/**
 * Lezer 语法树句柄。
 *
 * 除高亮遍历外，动态语言包可通过 [topNode] 和 [resolveInner] 构建轻量语义索引；节点位置继续
 * 使用 JavaScript UTF-16 偏移。这里只暴露只读遍历所需的最小 API，避免绑定整个 @lezer/common。
 */
external interface LezerTree {

  /** 整棵语法树的根节点。 */
  val topNode: LezerSyntaxNode

  /** 返回覆盖 [position] 的最内层节点，[side] 控制边界位置偏向。 */
  fun resolveInner(position: Int, side: Int = definedExternally): LezerSyntaxNode
}

/** 动态语言语义分析所需的最小 Lezer 节点视图。 */
external interface LezerSyntaxNode {
  val name: String
  val from: Int
  val to: Int
  val parent: LezerSyntaxNode?
  val firstChild: LezerSyntaxNode?
  val nextSibling: LezerSyntaxNode?
}
