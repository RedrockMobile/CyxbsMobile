package com.cyxbs.functions.code.editor.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.functions.code.editor.highlight.CodeEditor
import com.cyxbs.functions.code.editor.highlight.DEFAULT_HIGHLIGHT_CACHE_CAPACITY
import com.cyxbs.functions.code.editor.highlight.editorGutterWidth
import com.cyxbs.functions.code.editor.highlight.rememberCodeEditorState
import com.cyxbs.functions.code.editor.preview.workbench.FILES_PANEL_ID
import com.cyxbs.functions.code.editor.preview.workbench.RUN_TOOL_WINDOW_ID
import com.cyxbs.functions.code.editor.preview.workbench.codeEditorTestToolWindows
import com.cyxbs.functions.code.editor.preview.workbench.rememberCodeEditorTestSidePanels
import com.cyxbs.functions.code.editor.workbench.CodeEditorWorkbench
import com.cyxbs.functions.code.editor.workbench.DynamicLanguageFileIcon
import com.cyxbs.functions.code.editor.workbench.rememberCodeEditorWorkbenchState
import com.cyxbs.functions.code.editor.workbench.rememberDynamicLanguageFileIconCache
import com.cyxbs.functions.code.editor.workbench.resolveDynamicLanguageIdForFile
import com.cyxbs.functions.code.js.diagnostic.toJsDiagnostic
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.teaching.JsTeachingCodeResult
import com.cyxbs.functions.code.js.teaching.JsTeachingCodeRunner
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.DynamicLanguageManager
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicTextEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.TimeSource

/** 无参数的代码编辑器手动测试页面路由。 */
@Serializable
data object CodeEditorTestNavArgument : AppNavArgument

/**
 * 多文件 JavaScript 编辑、动态语言分析和本地 QuickJS 运行的手动测试页面。
 *
 * 路由只维护文件、Service 与运行状态，具体布局交给可复用工作台组件；该页面只编译进
 * `noWebMain`，用于 Android、iOS 与 Desktop 的功能体验，不作为最终教学业务页面。
 */
@AppNav(route = "code/editor-test")
class CodeEditorTestNavEntry : AppNavEntry<CodeEditorTestNavArgument>() {

  override fun isNeedLogin(argument: CodeEditorTestNavArgument): Boolean = false

  @Composable
  override fun Content(argument: CodeEditorTestNavArgument) {
    val runner = remember { JsTeachingCodeRunner.create(QuickJsRuntimeFactory) }
    val dynamicLanguageManager = remember { DynamicLanguageManager() }
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var isLoadingLanguage by remember { mutableStateOf(false) }
    var isAnalyzingSymbol by remember { mutableStateOf(false) }
    var highlightCacheCapacity by remember { mutableStateOf(DEFAULT_HIGHLIGHT_CACHE_CAPACITY) }
    var dynamicLanguageService by remember { mutableStateOf<DynamicLanguageService?>(null) }
    var supportedLanguages by remember { mutableStateOf<List<DynamicLanguageInfo>>(emptyList()) }
    val languageIconCache = rememberDynamicLanguageFileIconCache()
    val dynamicDocumentIcon: (@Composable (String, Modifier) -> Unit)? =
      if (supportedLanguages.isEmpty()) {
        null
      } else {
        { filePath, modifier ->
          DynamicLanguageFileIcon(
            imageVector = languageIconCache[
              resolveDynamicLanguageIdForFile(filePath, supportedLanguages)
            ],
            modifier = modifier,
          )
        }
      }
    val sourceFiles = remember {
      mutableStateMapOf<String, String>(
        MAIN_FILE_PATH to DEFAULT_MAIN_CODE,
        STUDENT_FILE_PATH to DEFAULT_STUDENT_CODE,
      ).apply { putAll(DEFAULT_ADDITIONAL_SOURCE_FILES) }
    }
    var activeFilePath by remember { mutableStateOf(MAIN_FILE_PATH) }
    val workspace = DynamicLanguageWorkspace(
      files = sourceFiles.entries
        .sortedBy(Map.Entry<String, String>::key)
        .map { (path, source) -> DynamicSourceFile(path, source) },
    )
    val editorState = rememberCodeEditorState(
      initialCode = DEFAULT_MAIN_CODE,
      activeFilePath = activeFilePath,
      workspace = workspace,
      languageService = dynamicLanguageService,
      highlightCacheCapacity = highlightCacheCapacity,
    )
    val workbenchState = rememberCodeEditorWorkbenchState(initialSidePanelId = FILES_PANEL_ID)
    var output by remember { mutableStateOf("点击右上角运行按钮或底部 Run 查看输出") }
    var languageStatus by remember { mutableStateOf("正在准备 JavaScript 动态语言服务…") }
    var autoHighlightReport by remember { mutableStateOf("动态服务加载后显示实时高亮耗时") }

    /** 用当前编辑器的未保存文本覆盖文件池，供动态服务分析与运行。 */
    fun currentWorkspace(
      filePath: String = activeFilePath,
      currentSource: String = editorState.code,
    ): DynamicLanguageWorkspace {
      return DynamicLanguageWorkspace(
        files = sourceFiles.entries
          .map { (path, source) ->
            DynamicSourceFile(path, if (path == filePath) currentSource else source)
          }
          .sortedBy(DynamicSourceFile::path),
      )
    }

    /** 保存当前文本后切换独立文件会话；普通切换保留光标，跨文件定义则定位到指定区间。 */
    fun openFile(filePath: String, selection: DynamicTextRange? = null) {
      sourceFiles[activeFilePath] = editorState.code
      val targetSource = sourceFiles[filePath] ?: error("文件不存在：$filePath")
      editorState.replaceDocument(filePath, targetSource, selection?.from)
      activeFilePath = filePath
      selection?.let(editorState::selectRange)
    }

    /**
     * 下载目录并创建 JavaScript Service；先构造新实例，成功后再替换旧实例，避免刷新失败使编辑器失能。
     */
    suspend fun loadLanguageService() {
      if (isLoadingLanguage) return
      isLoadingLanguage = true
      languageStatus = "正在加载动态语言目录与 JavaScript 服务…"
      var newService: DynamicLanguageService? = null
      try {
        val startMark = TimeSource.Monotonic.markNow()
        val catalogMark = TimeSource.Monotonic.markNow()
        val languages = dynamicLanguageManager.supportedLanguages()
        supportedLanguages = languages
        // 项目文件列表先恢复上次成功保存的图标，不等待语言 Runtime 创建或远端 latest 检查。
        languageIconCache.updateAll(dynamicLanguageManager.cachedIcons())
        val catalogDuration = catalogMark.elapsedNow()
        val javaScript = languages.firstOrNull { it.languageId == JAVASCRIPT_LANGUAGE_ID }
          ?: error("动态语言目录中未声明 JavaScript。")
        val serviceMark = TimeSource.Monotonic.markNow()
        val loadedService = dynamicLanguageManager.load(javaScript.languageId)
        newService = loadedService
        // Service 代理只在业务读取图标时校验 npm 版本，并透明复用或更新持久缓存。
        languageIconCache.update(javaScript.languageId, loadedService.fileIcon())
        val serviceDuration = serviceMark.elapsedNow()
        editorState.clearHighlightCache()
        dynamicLanguageService = loadedService
        newService = null
        languageStatus = buildLanguageLoadedText(
          packageName = javaScript.npmPackageName,
          totalDuration = startMark.elapsedNow(),
          catalogDuration = catalogDuration,
          serviceDuration = serviceDuration,
        )
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        languageStatus = throwable.toFailureText("动态语言服务加载失败")
      } finally {
        withContext(NonCancellable) {
          newService?.close()
        }
        isLoadingLanguage = false
      }
    }

    // Service 与页面编辑会话同生命周期；即使页面协程被取消，也要完成 Runtime 释放。
    LaunchedEffect(dynamicLanguageService) {
      val service = dynamicLanguageService ?: return@LaunchedEffect
      try {
        awaitCancellation()
      } finally {
        withContext(NonCancellable) {
          service.close()
        }
      }
    }

    // 正规编辑器进入后应直接具备语言能力；失败状态保留在设置栏，用户仍可编辑和手动重试。
    LaunchedEffect(Unit) {
      loadLanguageService()
    }

    // 输入停止后刷新高亮；源码校验防止迟到结果覆盖已切换或继续编辑的文档。
    LaunchedEffect(dynamicLanguageService, editorState, activeFilePath) {
      val service = dynamicLanguageService ?: return@LaunchedEffect
      val requestedFilePath = activeFilePath
      snapshotFlow { editorState.code }.collectLatest { source ->
        if (editorState.hasCachedHighlights(requestedFilePath, source)) {
          autoHighlightReport = "端上文件高亮缓存命中 · $requestedFilePath"
          return@collectLatest
        }
        delay(AUTO_HIGHLIGHT_DELAY_MILLIS)
        try {
          val roundTripMark = TimeSource.Monotonic.markNow()
          val result = service.highlight(
            workspace = currentWorkspace(requestedFilePath, source),
            filePath = requestedFilePath,
          )
          val roundTripDuration = roundTripMark.elapsedNow()
          if (activeFilePath == requestedFilePath && editorState.code == source) {
            val applyMark = TimeSource.Monotonic.markNow()
            editorState.applyHighlights(result.spans)
            autoHighlightReport = buildAutoHighlightText(
              metrics = result.metrics,
              highlightCount = result.spans.size,
              roundTripDuration = roundTripDuration,
              applyDuration = applyMark.elapsedNow(),
            )
          }
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("动态高亮自动刷新失败")
        }
      }
    }

    /** 运行当前内存工作区，并在执行前展开固定底栏上方的 Run Tool Window。 */
    fun runWorkspace() {
      workbenchState.showToolWindow(RUN_TOOL_WINDOW_ID)
      coroutineScope.launch {
        isRunning = true
        output = "正在编译并运行 $MAIN_FILE_PATH…"
        try {
          val files = currentWorkspace().files.associate { file -> file.path to file.source }
          output = runner.executeModule(
            files = files,
            entryFile = MAIN_FILE_PATH,
          ).toDisplayText()
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          val diagnostic = throwable.toJsDiagnostic()
          output = buildString {
            append(diagnostic.kind).append(": ").append(diagnostic.message)
            diagnostic.lineNumber?.let { line -> append("\n位置：第 ").append(line).append(" 行") }
            diagnostic.columnNumber?.let { column -> append("，第 ").append(column).append(" 列") }
          }
        } finally {
          isRunning = false
        }
      }
    }

    /** 校验并创建工作区相对路径文件。 */
    fun createWorkspaceFile(requestedPath: String): Boolean {
      val normalizedPath = requestedPath.trim().replace('\\', '/')
      val error = when {
        normalizedPath.isEmpty() -> "文件路径不能为空。"
        normalizedPath.startsWith('/') || normalizedPath.split('/').any { it == ".." } ->
          "文件必须使用工作区内的相对路径。"
        normalizedPath in sourceFiles -> "文件已存在：$normalizedPath"
        else -> null
      }
      return if (error != null) {
        output = error
        false
      } else {
        sourceFiles[normalizedPath] = DEFAULT_NEW_FILE_CODE
        openFile(normalizedPath)
        output = "已创建并打开 $normalizedPath"
        true
      }
    }

    val displayedSourceFiles = sourceFiles.toMutableMap().apply {
      this[activeFilePath] = editorState.code
    }
    val sidePanels = rememberCodeEditorTestSidePanels(
      activeFilePath = activeFilePath,
      sourceFiles = displayedSourceFiles,
      languageStatus = languageStatus,
      isLanguageReady = dynamicLanguageService != null,
      isLoadingLanguage = isLoadingLanguage,
      isAnalyzingSymbol = isAnalyzingSymbol,
      highlightCacheCapacity = highlightCacheCapacity,
      includeCourse = true,
      fileIcon = dynamicDocumentIcon,
      onOpenFile = ::openFile,
      onCreateFile = ::createWorkspaceFile,
      onLoadLanguage = {
        coroutineScope.launch { loadLanguageService() }
      },
      onHighlightCacheCapacityChange = { capacity ->
        highlightCacheCapacity = capacity
      },
      onFindDefinition = {
        coroutineScope.launch {
          isAnalyzingSymbol = true
          try {
            val mark = TimeSource.Monotonic.markNow()
            val definition = editorState.findDefinitionAtCursor()
            output = if (definition == null) {
              "光标不在可索引的词法符号上。"
            } else {
              val location = definition.definition
              openFile(location.filePath, location.range)
              "已定位 ${definition.name} 的定义 " +
                "${location.filePath}:[${location.range.from}, ${location.range.to})\n" +
                "Service 往返：${mark.elapsedNow().toDisplayMilliseconds()}"
            }
          } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            output = throwable.toFailureText("定义查询失败")
          } finally {
            isAnalyzingSymbol = false
          }
        }
      },
      onFindReferences = {
        coroutineScope.launch {
          isAnalyzingSymbol = true
          try {
            val mark = TimeSource.Monotonic.markNow()
            val references = editorState.findReferencesAtCursor()
            output = if (references == null) {
              "光标不在可索引的词法符号上。"
            } else {
              buildString {
                append(references.symbol.name).append("：")
                  .append(references.references.size).appendLine(" 处引用（不含定义）")
                references.references.take(DISPLAY_RESULT_LIMIT).forEach { location ->
                  append("- ").append(location.filePath).append(":[")
                    .append(location.range.from).append(", ").append(location.range.to).appendLine(")")
                }
                append("Service 往返：").append(mark.elapsedNow().toDisplayMilliseconds())
              }
            }
          } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            output = throwable.toFailureText("引用查询失败")
          } finally {
            isAnalyzingSymbol = false
          }
        }
      },
      onRename = { renameTarget ->
        coroutineScope.launch {
          isAnalyzingSymbol = true
          try {
            val mark = TimeSource.Monotonic.markNow()
            val rename = editorState.renameSymbolAtCursor(renameTarget)
            output = when {
              rename == null -> "光标不在可重命名的词法符号上。"
              !rename.isSuccess -> buildString {
                append("重命名被拒绝：").appendLine(rename.rejectionCode)
                append(rename.rejectionMessage ?: "未提供原因")
              }
              else -> {
                val requestedWorkspace = currentWorkspace()
                val editsByFile = rename.edits.groupBy { edit -> edit.filePath }
                editsByFile.forEach { (filePath, sourceEdits) ->
                  val source = requestedWorkspace.files.first { file -> file.path == filePath }.source
                  val edits = sourceEdits.map { sourceEdit -> sourceEdit.edit }
                  sourceFiles[filePath] = source.applyTextEdits(edits)
                  if (filePath == activeFilePath) editorState.applyTextEdits(edits)
                }
                "已将 ${rename.symbol.name} 重命名为 $renameTarget，" +
                  "修改 ${editsByFile.size} 个文件、${rename.edits.size} 处位置。\n" +
                  "Service 往返与事务应用：${mark.elapsedNow().toDisplayMilliseconds()}"
              }
            }
          } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            output = throwable.toFailureText("重命名失败")
          } finally {
            isAnalyzingSymbol = false
          }
        }
      },
    )
    CodeEditorWorkbench(
      title = "JavaScript 课程",
      activeDocumentLabel = activeFilePath,
      subtitle = "多文件语义分析 · 实验课",
      openDocumentLabels = sourceFiles.keys.sortedWith(
        compareBy<String> { it != MAIN_FILE_PATH }.thenBy { it.lowercase() },
      ),
      breadcrumbs = listOf("src", "lesson-03") + activeFilePath.split('/'),
      onDocumentSelected = ::openFile,
      documentIcon = dynamicDocumentIcon,
      sidePanels = sidePanels,
      toolWindows = codeEditorTestToolWindows(
        activeFilePath = activeFilePath,
        output = output,
        performanceText = autoHighlightReport,
      ),
      state = workbenchState,
      isRunning = isRunning,
      onBack = argument::popBackStack,
      onRun = ::runWorkspace,
      onUndo = { editorState.undo() },
      canUndo = editorState.canUndo,
      onRedo = { editorState.redo() },
      canRedo = editorState.canRedo,
      onSearch = { editorState.openSearch() },
      editorGutterWidth = editorState.editorGutterWidth(),
      editor = {
        CodeEditor(
          state = editorState,
          modifier = Modifier.fillMaxSize(),
        )
      },
    )
  }

  /** 将一次教学执行结果整理成输出面板可直接阅读的文本。 */
  private fun JsTeachingCodeResult.toDisplayText(): String = buildString {
    consoleMessages.forEach { message ->
      append('[').append(message.level).append("] ").appendLine(message.text)
    }
    if (value != null) append("返回值：").append(value)
    else append("运行完成")
  }

  /** 将目录发现和 Service 加载结果整理为设置栏中的紧凑状态。 */
  private fun buildLanguageLoadedText(
    packageName: String,
    totalDuration: Duration,
    catalogDuration: Duration,
    serviceDuration: Duration,
  ): String = buildString {
    appendLine("已加载 $packageName")
    append("目录 ").append(catalogDuration.toDisplayMilliseconds())
      .append(" ｜ Service ").append(serviceDuration.toDisplayMilliseconds())
      .append(" ｜ 合计 ").append(totalDuration.toDisplayMilliseconds())
  }

  /** 将自动高亮一次请求的语言包内部、桥接及编辑器应用耗时整理为实时摘要。 */
  private fun buildAutoHighlightText(
    metrics: DynamicHighlightMetrics,
    highlightCount: Int,
    roundTripDuration: Duration,
    applyDuration: Duration,
  ): String = buildString {
    append("缓存 ").append(metrics.cacheMode.name)
      .append(" ｜ 复用 ").append(metrics.reusableFragmentCount)
      .append(" ｜ 区间 ").append(highlightCount).appendLine()
    metrics.changedRange?.let { range ->
      append("旧 [").append(range.fromBefore).append(", ").append(range.toBefore)
        .append(") → 新 [").append(range.fromAfter).append(", ").append(range.toAfter).appendLine(")")
    }
    append("解析 ").append(metrics.parseMicroseconds.toDisplayMilliseconds())
      .append(" ｜ 收集 ").append(metrics.collectMicroseconds.toDisplayMilliseconds())
      .append(" ｜ 桥接约 ")
      .append(metrics.bridgeOverheadMicroseconds(roundTripDuration).toDisplayMilliseconds())
      .append(" ｜ 往返 ").append(roundTripDuration.toDisplayMilliseconds())
      .append(" ｜ 应用 ").append(applyDuration.toDisplayMilliseconds())
  }

  /** 从往返时间中扣除语言包内部耗时，近似观察 JSON 与 QuickJS 桥接成本。 */
  private fun DynamicHighlightMetrics.bridgeOverheadMicroseconds(roundTripDuration: Duration): Long {
    return (
      roundTripDuration.inWholeMicroseconds - parseMicroseconds - collectMicroseconds
      ).coerceAtLeast(0)
  }

  /** 将异常保留类型和根因，便于输出区域定位协议、下载或运行失败。 */
  private fun Throwable.toFailureText(title: String): String = buildString {
    append(title).append("：").appendLine(this@toFailureText::class.simpleName)
    append(message ?: "未提供错误信息")
    cause?.takeIf { it !== this@toFailureText }?.let { cause ->
      append("\n根因：").append(cause::class.simpleName).append(": ").append(cause.message)
    }
  }

  /** 将微秒精度耗时按三位小数格式化为毫秒。 */
  private fun Duration.toDisplayMilliseconds(): String = inWholeMicroseconds.toDisplayMilliseconds()

  /** 将微秒数按跨平台稳定的纯字符串逻辑格式化。 */
  private fun Long.toDisplayMilliseconds(): String {
    val milliseconds = this / MICROSECONDS_PER_MILLISECOND
    val fraction = (this % MICROSECONDS_PER_MILLISECOND).toString().padStart(3, '0')
    return "$milliseconds.$fraction ms"
  }

  /** 按原始坐标倒序应用同一文件修改，避免前方替换改变后续偏移。 */
  private fun String.applyTextEdits(edits: List<DynamicTextEdit>): String {
    return edits.sortedByDescending(DynamicTextEdit::from).fold(this) { source, edit ->
      source.replaceRange(edit.from, edit.to, edit.replacement)
    }
  }

  private companion object {
    const val JAVASCRIPT_LANGUAGE_ID = "javascript"
    const val DISPLAY_RESULT_LIMIT = 12
    const val MICROSECONDS_PER_MILLISECOND = 1_000
    const val AUTO_HIGHLIGHT_DELAY_MILLIS = 200L
    const val MAIN_FILE_PATH = "main.js"
    const val STUDENT_FILE_PATH = "models/student.js"

    val DEFAULT_MAIN_CODE = """
      import { Student } from "./models/student.js";

      const student = new Student("小邮", [88, 92, 95]);
      console.log(student.name, "平均分", student.average());
      student.average();
    """.trimIndent()

    val DEFAULT_STUDENT_CODE = """
      export class Student {
        constructor(name, scores) {
          this.name = name;
          this.scores = scores;
        }

        average() {
          return this.scores.reduce((sum, score) => sum + score, 0) / this.scores.length;
        }
      }
    """.trimIndent()

    val DEFAULT_NEW_FILE_CODE = """
      // 在这里编写新模块
      export const value = 1;
    """.trimIndent()

    /** 扩充手动测试工作区，验证大量长短标签的横向滑动与选中项自动滚入视口。 */
    val DEFAULT_ADDITIONAL_SOURCE_FILES = mapOf(
      "config/course-settings.js" to """
        export const courseSettings = { passScore: 60, maxScore: 100 };
      """.trimIndent(),
      "data/sample-students.js" to """
        export const sampleStudents = ["小邮", "小红", "小蓝"];
      """.trimIndent(),
      "services/grade-calculation-service.js" to """
        export const calculateAverage = scores =>
          scores.reduce((sum, score) => sum + score, 0) / scores.length;
      """.trimIndent(),
      "services/student-repository.js" to """
        export const findStudent = (students, name) =>
          students.find(student => student.name === name);
      """.trimIndent(),
      "utils/format-score.js" to """
        export const formatScore = score => `${'$'}{score.toFixed(1)} 分`;
      """.trimIndent(),
      "utils/math.js" to """
        export const sum = values => values.reduce((total, value) => total + value, 0);
      """.trimIndent(),
    )
  }
}
