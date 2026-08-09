package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageMetadata
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighter

/**
 * JavaScript 动态语言 npm 包的 Kotlin/JS 入口实现。
 *
 * 语法高亮由 @lezer/javascript 完成，输出 CodeMirror 标准的 `tok-*` 样式标识；补全暂时提供
 * JavaScript 教学常用关键词，后续可在不改变端上协议的前提下扩展为语义补全。
 */
object JavaScriptDynamicLanguageService : DynamicLanguageService {

  override suspend fun metadata(): DynamicLanguageMetadata {
    return DynamicLanguageMetadata(
      languageId = "javascript",
      displayName = "JavaScript",
      protocolVersion = PROTOCOL_VERSION,
    )
  }

  override suspend fun highlight(source: String): List<DynamicHighlightSpan> {
    return LezerSyntaxHighlighter.highlight(parser, source)
  }

  override suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    require(position in 0..source.length) { "position must be inside source." }
    val from = source.findIdentifierStart(position)
    val prefix = source.substring(from, position)
    if (!explicit && prefix.isEmpty()) return null
    val options = KEYWORDS
      .asSequence()
      .filter { it.startsWith(prefix) && it != prefix }
      .map { keyword ->
        DynamicCompletionItem(
          label = keyword,
          type = KEYWORD_STYLE,
          apply = keyword,
        )
      }
      .toList()
    return options.takeIf { it.isNotEmpty() }?.let {
      DynamicCompletionResult(from = from, to = position, options = options)
    }
  }

  /** 从光标向前寻找 JavaScript 标识符起点，偏移保持 UTF-16 语义。 */
  private fun String.findIdentifierStart(position: Int): Int {
    var index = position
    while (index > 0 && this[index - 1].isJavaScriptIdentifierPart()) index--
    return index
  }

  private fun Char.isJavaScriptIdentifierPart(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '$'
  }

  private const val PROTOCOL_VERSION = 1
  private const val KEYWORD_STYLE = "keyword"
  private val KEYWORDS = listOf(
    "async",
    "await",
    "break",
    "class",
    "const",
    "continue",
    "else",
    "export",
    "extends",
    "false",
    "for",
    "function",
    "if",
    "import",
    "let",
    "new",
    "null",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typeof",
    "undefined",
    "var",
    "while",
  )
}
