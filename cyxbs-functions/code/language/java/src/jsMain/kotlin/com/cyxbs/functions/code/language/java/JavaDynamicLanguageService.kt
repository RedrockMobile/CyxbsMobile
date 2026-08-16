package com.cyxbs.functions.code.language.java

import com.cyxbs.functions.code.language.java.semantic.JavaSemanticSession
import com.cyxbs.functions.code.language.java.compiler.JavaCompilerSourceEntryPoint
import com.cyxbs.functions.code.language.java.compiler.JavaToJavaScriptCompiler
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerDiagnostic
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticNote
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticSeverity
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticNote
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationResult
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableModule
import com.cyxbs.functions.code.language.js.bridge.DynamicExecutableProgram
import com.cyxbs.functions.code.language.js.bridge.DynamicGeneratedSourceMapping
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession

/**
 * Java 动态语言 npm 包的 Kotlin/JS 入口。
 *
 * @lezer/java 负责错误恢复语法树和增量高亮；轻量语义索引负责工作区声明、补全、跳转、引用与
 * 安全重命名。编译后的程序交给独立 Runtime 执行，本服务不会加载 JDK、Gradle 或 Maven classpath。
 */
object JavaDynamicLanguageService : DynamicLanguageService {
  private val highlighterSessions = mutableMapOf<String, LezerSyntaxHighlighterSession>()
  private val semanticSession = JavaSemanticSession { filePath, source ->
    highlighterSession(filePath).syntaxTree(source)
  }

  /** 返回不依赖平台资源的 Java 咖啡杯矢量图标。 */
  override suspend fun fileIcon(): DynamicLanguageIcon = JavaLanguageIcon

  /**
   * 发现工作区中的 Java `main`，供顶部运行选择器和行号运行标记共同使用。
   *
   * 查询复用轻量语义索引，不会提前执行完整编译；真正运行时仍由 [compile] 严格校验当前
   * 阶段支持的 Java 语法与语义。
   */
  override suspend fun runTargets(
    workspace: DynamicLanguageWorkspace,
    activeFilePath: String,
  ): List<DynamicRunTarget> {
    require(workspace.files.any { file -> file.path == activeFilePath }) {
      "Workspace does not contain '$activeFilePath'."
    }
    retainWorkspace(workspace)
    return semanticSession.runTargets(workspace)
  }

  /**
   * 将 Java 工作区编译为统一 ES Module 图，入口由文件和可选光标位置选择。
   *
   * 编译只生成代码，不在当前语言分析 Runtime 中执行用户程序。缺少入口、源码错误和当前
   * Stage1 子集尚未支持的 Java 特性都会通过结构化诊断返回。
   */
  override suspend fun compile(request: DynamicCompilationRequest): DynamicCompilationResult {
    val sourceWorkspace = request.workspace.toJavaSourceWorkspace()
    val result = JavaToJavaScriptCompiler.compile(
      workspace = sourceWorkspace,
      entryPoint = JavaCompilerSourceEntryPoint(
        filePath = request.entry.filePath,
        position = request.entry.position,
      ),
    )
    val diagnostics = result.diagnostics.map { diagnostic ->
      diagnostic.toDynamicDiagnostic(sourceWorkspace)
    }
    val artifact = result.value ?: return DynamicCompilationResult(diagnostics = diagnostics)
    return DynamicCompilationResult(
      program = DynamicExecutableProgram(
        entryModuleName = artifact.entryModuleName,
        entryExportName = artifact.entryExportName,
        modules = artifact.modules.map { module ->
          DynamicExecutableModule(
            name = module.moduleName,
            source = module.source,
            sourceMappings = module.sourceMappings.map { mapping ->
              DynamicGeneratedSourceMapping(
                generatedLine = mapping.generatedLine,
                generatedColumn = mapping.generatedColumn,
                sourceLocation = mapping.sourceSpan.toDynamicLocation(sourceWorkspace),
              )
            },
          )
        },
      ),
      diagnostics = diagnostics,
    )
  }

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

  /** 把公共工作区快照转换为当前编译请求内稳定编号的 Java 源码工作区。 */
  private fun DynamicLanguageWorkspace.toJavaSourceWorkspace(): JavaSourceWorkspace {
    return JavaSourceWorkspace(
      files.mapIndexed { index, file ->
        JavaSourceFile(
          id = JavaSourceFileId(index),
          path = file.path,
          source = file.source,
        )
      },
    )
  }

  /** 将 Java 编译器内部诊断映射为公共动态语言协议。 */
  private fun JavaCompilerDiagnostic.toDynamicDiagnostic(
    workspace: JavaSourceWorkspace,
  ): DynamicCompilationDiagnostic {
    return DynamicCompilationDiagnostic(
      code = code,
      message = message,
      severity = when (severity) {
        JavaDiagnosticSeverity.ERROR -> DynamicCompilationDiagnosticSeverity.ERROR
        JavaDiagnosticSeverity.WARNING -> DynamicCompilationDiagnosticSeverity.WARNING
      },
      location = span?.toDynamicLocation(workspace),
      notes = notes.map { note -> note.toDynamicNote(workspace) },
    )
  }

  /** 映射一条 Java 诊断补充信息。 */
  private fun JavaDiagnosticNote.toDynamicNote(
    workspace: JavaSourceWorkspace,
  ): DynamicCompilationDiagnosticNote {
    return DynamicCompilationDiagnosticNote(
      message = message,
      location = span?.toDynamicLocation(workspace),
    )
  }

  /** 将编译请求内文件编号恢复为编辑器工作区路径。 */
  private fun JavaSourceSpan.toDynamicLocation(
    workspace: JavaSourceWorkspace,
  ): DynamicSourceLocation {
    return DynamicSourceLocation(
      filePath = workspace.requireFile(fileId).path,
      range = DynamicTextRange(from = from, to = to),
    )
  }
}
