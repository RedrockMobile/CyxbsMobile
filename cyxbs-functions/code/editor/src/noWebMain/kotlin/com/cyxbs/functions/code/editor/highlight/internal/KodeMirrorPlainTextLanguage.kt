package com.cyxbs.functions.code.editor.highlight.internal

import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import com.monkopedia.kodemirror.state.Extension

/**
 * 不携带语法规则的 KodeMirror 语言扩展。
 *
 * KodeMirror 的基础编辑扩展要求会话中存在一个 [Language]，否则会在创建编辑器时直接失败。
 * 这里仅生成覆盖完整文档的空语法树，用于保留输入、光标和搜索等通用编辑能力；真正的高亮、
 * 补全和语义分析仍由按需下载的动态语言服务完成，不会把静态 JavaScript 解析器带回安装包。
 */
internal val kodeMirrorPlainTextLanguageExtension: Extension =
  Language(parser = PlainTextParser, name = "Plain text").extension

/** 为每次解析创建一个立即完成、且不会产生语法节点的纯文本解析任务。 */
private object PlainTextParser : Parser() {

  override fun createParse(
    input: Input,
    fragments: List<TreeFragment>,
    ranges: List<TextRange>,
  ): PartialParse = PlainTextParse(documentLength = input.length)
}

/**
 * 返回覆盖当前文档的空语法树。
 *
 * [stopAt] 由 KodeMirror 在增量解析需要提前停止时调用；由于本解析器不会分批推进，只需让最终
 * 树长与停止位置保持一致，避免编辑器误判仍有未解析内容。
 */
private class PlainTextParse(
  private val documentLength: Int,
) : PartialParse {

  private var stopPosition: Int? = null

  override val parsedPos: Int
    get() = stopPosition ?: documentLength

  override val stoppedAt: Int?
    get() = stopPosition

  override fun stopAt(pos: Int) {
    stopPosition = pos.coerceIn(0, documentLength)
  }

  override fun advance(): Tree {
    val parsedLength = stopPosition ?: documentLength
    return Tree(
      type = NodeType.none,
      children = emptyList(),
      positions = emptyList(),
      length = parsedLength,
    )
  }
}
