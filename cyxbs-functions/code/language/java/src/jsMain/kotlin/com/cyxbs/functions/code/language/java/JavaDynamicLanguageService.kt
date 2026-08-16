package com.cyxbs.functions.code.language.java

import com.cyxbs.functions.code.language.java.semantic.JavaSemanticSession
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession

/**
 * Java 动态语言 npm 包的 Kotlin/JS 入口。
 *
 * @lezer/java 负责错误恢复语法树和增量高亮；轻量语义索引负责工作区声明、补全、跳转、引用与
 * 安全重命名。实现不会执行用户源码，也不会加载 JDK、Gradle 或 Maven classpath。
 */
object JavaDynamicLanguageService : DynamicLanguageService {
  private val highlighterSessions = mutableMapOf<String, LezerSyntaxHighlighterSession>()
  private val semanticSession = JavaSemanticSession { filePath, source ->
    highlighterSession(filePath).syntaxTree(source)
  }

  /** 返回不依赖平台资源的 Java 咖啡杯矢量图标。 */
  override suspend fun fileIcon(): DynamicLanguageIcon = JavaLanguageIcon

  /** 高亮指定文件，并复用该文件上一次生成的 Lezer 语法树片段。 */
  override suspend fun highlight(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
  ): DynamicHighlightResult {
    retainWorkspace(workspace)
    return highlighterSession(filePath).highlight(workspace.requireSource(filePath))
  }

  /** 返回当前词法作用域、工作区类型和可确定 receiver 的补全候选。 */
  override suspend fun complete(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    retainWorkspace(workspace)
    return semanticSession.complete(workspace, filePath, position, explicit)
  }

  /** 返回工作区中可唯一解析的定义，外部 classpath 和歧义符号返回 null。 */
  override suspend fun definition(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolDefinition? {
    retainWorkspace(workspace)
    return semanticSession.definition(workspace, filePath, position)
  }

  /** 返回工作区中静态确认的引用，不包含定义区间。 */
  override suspend fun references(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolReferencesResult? {
    retainWorkspace(workspace)
    return semanticSession.references(workspace, filePath, position)
  }

  /** 校验标识符、遮蔽、重载及文件重命名边界后生成一次性修改。 */
  override suspend fun rename(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    newName: String,
  ): DynamicRenameResult? {
    retainWorkspace(workspace)
    return semanticSession.rename(workspace, filePath, position, newName)
  }

  /** 校验工作区路径唯一，并释放已经删除文件的树和语义索引。 */
  private fun retainWorkspace(workspace: DynamicLanguageWorkspace) {
    val paths = workspace.files.mapTo(mutableSetOf()) { file -> file.path }
    require(paths.size == workspace.files.size) { "Workspace file paths must be unique." }
    highlighterSessions.keys.retainAll(paths)
    semanticSession.retainFiles(paths)
  }

  /** 按文件路径获得独立的增量高亮会话。 */
  private fun highlighterSession(filePath: String): LezerSyntaxHighlighterSession {
    return highlighterSessions.getOrPut(filePath) { LezerSyntaxHighlighterSession(parser) }
  }

  /** 读取工作区文件，并在动态包边界内报告路径错误。 */
  private fun DynamicLanguageWorkspace.requireSource(filePath: String): String {
    return files.firstOrNull { file -> file.path == filePath }?.source
      ?: throw IllegalArgumentException("Workspace does not contain '$filePath'.")
  }
}
