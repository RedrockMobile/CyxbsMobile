package com.cyxbs.functions.code.language.js

import com.cyxbs.functions.code.language.api.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.api.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.api.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.api.bridge.DynamicLanguageMetadata
import com.cyxbs.functions.code.language.api.bridge.DynamicLanguageService

/**
 * JavaScript 动态语言 npm 包的 Kotlin/JS 入口实现。
 *
 * 当前实现提供可执行的最小关键词高亮与补全，用于验证 npm Service 的生成、打包和调用链路；后续
 * CodeMirror/Lezer 接入只需替换本 object 内部实现，端上接口和加载代码无需变化。
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
    return KEYWORD_REGEX.findAll(source).map { match ->
      DynamicHighlightSpan(
        from = match.range.first,
        to = match.range.last + 1,
        styleIds = listOf(KEYWORD_STYLE),
      )
    }.toList()
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
  private val KEYWORD_REGEX = Regex(
    "\\b(?:${KEYWORDS.joinToString("|")})\\b",
  )
}
