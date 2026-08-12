package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.javascript.completion.JavaScriptSemanticSession
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession

/**
 * JavaScript 动态语言 npm 包的 Kotlin/JS 入口实现。
 *
 * 语法高亮由 @lezer/javascript 完成，输出 CodeMirror 标准的 `tok-*` 样式标识；补全、定义、
 * 引用与安全重命名复用同一份增量语法树建立轻量作用域索引，不执行用户代码。
 */
object JavaScriptDynamicLanguageService : DynamicLanguageService {
  private val highlighterSessions = mutableMapOf<String, LezerSyntaxHighlighterSession>()
  private val semanticSession = JavaScriptSemanticSession { filePath, source ->
    highlighterSession(filePath).syntaxTree(source)
  }

  /** 每份文件独立缓存 Lezer 语法树，文件切换不会丢失增量分析状态。 */
  override suspend fun highlight(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
  ): DynamicHighlightResult {
    retainWorkspace(workspace)
    return highlighterSession(filePath).highlight(workspace.requireSource(filePath))
  }

  override suspend fun complete(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    retainWorkspace(workspace)
    return semanticSession.complete(workspace, filePath, position, explicit)
  }

  /** 从当前词法索引解析光标符号的定义区间。 */
  override suspend fun definition(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolDefinition? {
    retainWorkspace(workspace)
    return semanticSession.definition(workspace, filePath, position)
  }

  /** 返回光标符号在工作区中的非定义引用。 */
  override suspend fun references(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolReferencesResult? {
    retainWorkspace(workspace)
    return semanticSession.references(workspace, filePath, position)
  }

  /** 校验词法绑定与模块公开名语义后，生成工作区重命名修改。 */
  override suspend fun rename(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    newName: String,
  ): DynamicRenameResult? {
    retainWorkspace(workspace)
    return semanticSession.rename(workspace, filePath, position, newName)
  }

  /** 移除已从工作区删除文件的语法树，避免长时间创建临时文件无界增长。 */
  private fun retainWorkspace(workspace: DynamicLanguageWorkspace) {
    val paths = workspace.files.mapTo(mutableSetOf()) { file -> file.path }
    require(paths.size == workspace.files.size) { "Workspace file paths must be unique." }
    highlighterSessions.keys.retainAll(paths)
    semanticSession.retainFiles(paths)
  }

  /** 按文件路径获取独立的增量高亮会话。 */
  private fun highlighterSession(filePath: String): LezerSyntaxHighlighterSession {
    return highlighterSessions.getOrPut(filePath) { LezerSyntaxHighlighterSession(parser) }
  }

  /** 读取工作区文件，并将路径错误在动态包边界内明确报告。 */
  private fun DynamicLanguageWorkspace.requireSource(filePath: String): String {
    return files.firstOrNull { file -> file.path == filePath }?.source
      ?: throw IllegalArgumentException("Workspace does not contain '$filePath'.")
  }
}
