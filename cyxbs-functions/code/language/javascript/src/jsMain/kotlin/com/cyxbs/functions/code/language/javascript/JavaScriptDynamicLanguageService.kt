package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.javascript.completion.JavaScriptSemanticCompletionSession
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession

/**
 * JavaScript 动态语言 npm 包的 Kotlin/JS 入口实现。
 *
 * 语法高亮由 @lezer/javascript 完成，输出 CodeMirror 标准的 `tok-*` 样式标识；补全复用同一份
 * 增量语法树建立轻量作用域索引，不执行用户代码。
 */
object JavaScriptDynamicLanguageService : DynamicLanguageService {
  private val highlighterSession = LezerSyntaxHighlighterSession(parser)
  private val completionSession =
    JavaScriptSemanticCompletionSession(highlighterSession::syntaxTree)

  /** 使用当前 JavaScript Runtime 内的 Lezer 会话缓存增量高亮，并返回细粒度耗时。 */
  override suspend fun highlight(source: String): DynamicHighlightResult {
    return highlighterSession.highlight(source)
  }

  override suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    return completionSession.complete(source, position, explicit)
  }
}
