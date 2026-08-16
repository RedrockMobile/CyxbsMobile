package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationResult
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableModule
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.javascript.completion.JavaScriptSemanticSession
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession
import kotlinx.serialization.json.JsonPrimitive

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

  /** 返回不依赖平台资源的 JavaScript 文件矢量图标。 */
  override suspend fun fileIcon(): DynamicLanguageIcon = JavaScriptLanguageIcon

  /**
   * JavaScript 按当前文件执行，不把工作区中的所有依赖模块都展示为独立运行入口。
   *
   * `.cjs` 仍由 [compile] 返回明确的不支持诊断，因此这里不提供会必然失败的运行目标。
   */
  override suspend fun runTargets(
    workspace: DynamicLanguageWorkspace,
    activeFilePath: String,
  ): List<DynamicRunTarget> {
    val activeFile = workspace.files.firstOrNull { file -> file.path == activeFilePath }
      ?: return emptyList()
    if (activeFile.path.endsWith(".cjs", ignoreCase = true)) return emptyList()
    return listOf(
      DynamicRunTarget(
        displayName = activeFile.path,
        entry = DynamicProgramEntry(filePath = activeFile.path),
      ),
    )
  }

  /**
   * 把 JavaScript 工作区规范化为统一的 ES Module 图。
   *
   * 原始模块不会在语言分析 Runtime 中执行。生成的轻量入口适配器会在独立执行 Runtime 中先
   * 加载入口文件，再调用其 `default` 或 `main` 导出；没有这两个导出时仅执行模块顶层代码。
   */
  override suspend fun compile(request: DynamicCompilationRequest): DynamicCompilationResult {
    val paths = request.workspace.files.map { file -> file.path }
    if (paths.distinct().size != paths.size) {
      return compilationFailure(
        code = "JAVASCRIPT_DUPLICATE_FILE_PATH",
        message = "Workspace file paths must be unique.",
      )
    }
    if (paths.any { path -> !path.isNormalizedModulePath() }) {
      return compilationFailure(
        code = "JAVASCRIPT_INVALID_FILE_PATH",
        message = "Workspace paths must be normalized relative Module names.",
      )
    }
    val entryFile = request.workspace.files.firstOrNull { file ->
      file.path == request.entry.filePath
    } ?: return compilationFailure(
      code = "JAVASCRIPT_ENTRY_FILE_NOT_FOUND",
      message = "Entry file '${request.entry.filePath}' is not part of the workspace.",
    )
    if (entryFile.path.endsWith(".cjs", ignoreCase = true)) {
      return compilationFailure(
        code = "JAVASCRIPT_COMMONJS_UNSUPPORTED",
        message = "CommonJS entry files are not supported by the isolated ES Module runtime.",
      )
    }

    val wrapperName = generateSequence("__cyxbs_dynamic_entry__.mjs") { current ->
      "_" + current
    }.first { candidate -> candidate !in paths }
    val entryExportName = "__cyxbs_dynamic_entry__"
    val entrySpecifier = JsonPrimitive(entryFile.path).toString()
    val wrapperSource = """
      import * as __cyxbs_entry_module__ from $entrySpecifier;

      export function $entryExportName(...args) {
        const candidate = __cyxbs_entry_module__.default ?? __cyxbs_entry_module__.main;
        return typeof candidate === "function" ? candidate(...args) : candidate;
      }
    """.trimIndent()
    return DynamicCompilationResult(
      program = DynamicExecutableProgram(
        entryModuleName = wrapperName,
        entryExportName = entryExportName,
        modules = request.workspace.files.map { file ->
          DynamicExecutableModule(name = file.path, source = file.source)
        } + DynamicExecutableModule(name = wrapperName, source = wrapperSource),
      ),
    )
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

  /** 创建不包含半成品 Module 图的稳定编译失败结果。 */
  private fun compilationFailure(
    code: String,
    message: String,
  ): DynamicCompilationResult {
    return DynamicCompilationResult(
      diagnostics = listOf(
        DynamicCompilationDiagnostic(
          code = code,
          message = message,
          severity = DynamicCompilationDiagnosticSeverity.ERROR,
        ),
      ),
    )
  }

  /** 工作区文件使用与端上内存 Module Loader 一致的正斜杠相对路径。 */
  private fun String.isNormalizedModulePath(): Boolean {
    if (isEmpty() || startsWith('/') || contains('\\')) return false
    return split('/').all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." }
  }
}
