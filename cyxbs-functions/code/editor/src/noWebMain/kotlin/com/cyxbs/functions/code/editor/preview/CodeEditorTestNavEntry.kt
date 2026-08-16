package com.cyxbs.functions.code.editor.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.functions.code.editor.highlight.CodeEditor
import com.cyxbs.functions.code.editor.highlight.DEFAULT_HIGHLIGHT_CACHE_CAPACITY
import com.cyxbs.functions.code.editor.highlight.editorGutterWidth
import com.cyxbs.functions.code.editor.highlight.rememberCodeEditorState
import com.cyxbs.functions.code.editor.preview.workbench.FILES_PANEL_ID
import com.cyxbs.functions.code.editor.preview.workbench.RUN_TOOL_WINDOW_ID
import com.cyxbs.functions.code.editor.preview.workbench.CompactDropdownMenuItemHeight
import com.cyxbs.functions.code.editor.preview.workbench.codeEditorTestToolWindows
import com.cyxbs.functions.code.editor.preview.workbench.rememberCodeEditorTestSidePanels
import com.cyxbs.functions.code.editor.preview.workbench.removeDefaultDropdownMenuVerticalPadding
import com.cyxbs.functions.code.editor.workbench.CodeEditorWorkbench
import com.cyxbs.functions.code.editor.workbench.DynamicLanguageFileIcon
import com.cyxbs.functions.code.editor.workbench.EditorWorkbenchColors
import com.cyxbs.functions.code.editor.workbench.rememberCodeEditorWorkbenchState
import com.cyxbs.functions.code.editor.workbench.rememberDynamicLanguageFileIconCache
import com.cyxbs.functions.code.editor.workbench.resolveDynamicLanguageIdForFile
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.DynamicLanguageManager
import com.cyxbs.functions.code.language.DynamicLanguageSession
import com.cyxbs.functions.code.language.DynamicProgramRunRequest
import com.cyxbs.functions.code.language.DynamicProgramRunResult
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
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
 * 多语言、多文件编辑、动态语言分析和本地 QuickJS 运行的手动测试页面。
 *
 * 路由只维护文件、Service 与运行状态，具体布局交给可复用工作台组件；该页面只编译进
 * `noWebMain`，用于 Android、iOS 与 Desktop 的功能体验，不作为最终教学业务页面。
 */
@AppNav(route = "code/editor-test")
class CodeEditorTestNavEntry : AppNavEntry<CodeEditorTestNavArgument>() {

  override fun isNeedLogin(argument: CodeEditorTestNavArgument): Boolean = false

  @Composable
  override fun Content(argument: CodeEditorTestNavArgument) {
    val dynamicLanguageManager = remember { DynamicLanguageManager() }
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var isLoadingLanguage by remember { mutableStateOf(false) }
    var isAnalyzingSymbol by remember { mutableStateOf(false) }
    var highlightCacheCapacity by remember { mutableStateOf(DEFAULT_HIGHLIGHT_CACHE_CAPACITY) }
    var dynamicLanguageService by remember { mutableStateOf<DynamicLanguageSession?>(null) }
    var loadedLanguageId by remember { mutableStateOf<String?>(null) }
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
      mutableStateMapOf<String, String>().apply { putAll(DEFAULT_SOURCE_FILES) }
    }
    val openFilePaths = remember { mutableStateListOf(JAVA_MAIN_FILE_PATH) }
    var activeFilePath by remember { mutableStateOf(JAVA_MAIN_FILE_PATH) }
    var runTargets by remember { mutableStateOf<List<DynamicRunTarget>>(emptyList()) }
    var pendingLineRunTarget by remember { mutableStateOf<DynamicRunTarget?>(null) }
    var showRunTargetPicker by remember { mutableStateOf(false) }
    val activeLanguageId = resolveDynamicLanguageIdForFile(activeFilePath, supportedLanguages)
    val activeLanguageService = dynamicLanguageService.takeIf {
      loadedLanguageId == activeLanguageId
    }
    val workspace = DynamicLanguageWorkspace(
      files = sourceFiles.entries
        .filter { (path, _) ->
          activeLanguageId == null ||
            resolveDynamicLanguageIdForFile(path, supportedLanguages) == activeLanguageId
        }
        .sortedBy(Map.Entry<String, String>::key)
        .map { (path, source) -> DynamicSourceFile(path, source) },
    )
    val workbenchState = rememberCodeEditorWorkbenchState(initialSidePanelId = FILES_PANEL_ID)
    val editorState = rememberCodeEditorState(
      initialCode = DEFAULT_MAIN_CODE,
      activeFilePath = activeFilePath,
      workspace = workspace,
      languageService = activeLanguageService,
      highlightCacheCapacity = highlightCacheCapacity,
      runTargets = runTargets,
      onRunTarget = { target ->
        // gutter 点击应立即给出界面反馈，入口复核与编译继续由页面协程异步完成。
        workbenchState.showToolWindow(RUN_TOOL_WINDOW_ID)
        pendingLineRunTarget = target
      },
    )
    var output by remember { mutableStateOf("点击右上角运行按钮或底部 Run 查看输出") }
    var languageStatus by remember { mutableStateOf("正在准备动态语言目录…") }
    var autoHighlightReport by remember { mutableStateOf("动态服务加载后显示实时高亮耗时") }

    /** 用当前编辑器的未保存文本覆盖文件池，供动态服务分析与运行。 */
    fun currentWorkspace(
      filePath: String = activeFilePath,
      currentSource: String = editorState.code,
    ): DynamicLanguageWorkspace {
      val languageId = resolveDynamicLanguageIdForFile(filePath, supportedLanguages)
      return DynamicLanguageWorkspace(
        files = sourceFiles.entries
          .filter { (path, _) ->
            languageId == null ||
              resolveDynamicLanguageIdForFile(path, supportedLanguages) == languageId
          }
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
      if (filePath !in openFilePaths) openFilePaths += filePath
      editorState.replaceDocument(filePath, targetSource, selection?.from)
      activeFilePath = filePath
      selection?.let(editorState::selectRange)
    }

    /**
     * 按语言 ID 创建对应 Service；先构造新实例，成功后再替换，失败时不污染当前语言会话。
     */
    suspend fun loadLanguageService(languageId: String) {
      if (loadedLanguageId == languageId && dynamicLanguageService != null) return
      isLoadingLanguage = true
      languageStatus = "正在加载 $languageId 动态语言服务…"
      var newService: DynamicLanguageSession? = null
      try {
        val startMark = TimeSource.Monotonic.markNow()
        val catalogMark = TimeSource.Monotonic.markNow()
        val languages = dynamicLanguageManager.supportedLanguages()
        supportedLanguages = languages
        // 项目文件列表先恢复上次成功保存的图标，不等待语言 Runtime 创建或远端 latest 检查。
        languageIconCache.updateAll(dynamicLanguageManager.cachedIcons())
        val catalogDuration = catalogMark.elapsedNow()
        val language = languages.firstOrNull { it.languageId == languageId }
          ?: error("动态语言目录中未声明 $languageId。")
        val serviceMark = TimeSource.Monotonic.markNow()
        val loadedService = dynamicLanguageManager.load(language.languageId)
        newService = loadedService
        // Service 代理只在业务读取图标时校验 npm 版本，并透明复用或更新持久缓存。
        languageIconCache.update(language.languageId, loadedService.fileIcon())
        val serviceDuration = serviceMark.elapsedNow()
        editorState.clearHighlightCache()
        dynamicLanguageService = loadedService
        loadedLanguageId = language.languageId
        newService = null
        languageStatus = buildLanguageLoadedText(
          packageName = language.npmPackageName,
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

    // 先发现 Catalog 和持久图标；具体语言 Runtime 由活动文件触发，未打开的语言不会下载。
    LaunchedEffect(Unit) {
      try {
        supportedLanguages = dynamicLanguageManager.supportedLanguages()
        languageIconCache.updateAll(dynamicLanguageManager.cachedIcons())
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        languageStatus = throwable.toFailureText("动态语言目录加载失败")
      }
    }

    // 文件语言变化时先停止使用旧 Service，再在任何分析请求发生前加载新语言包。
    LaunchedEffect(activeLanguageId) {
      val languageId = activeLanguageId ?: return@LaunchedEffect
      if (loadedLanguageId != languageId) {
        dynamicLanguageService = null
        loadedLanguageId = null
      }
      loadLanguageService(languageId)
    }

    // 输入停止后刷新高亮；源码校验防止迟到结果覆盖已切换或继续编辑的文档。
    LaunchedEffect(activeLanguageService, editorState, activeFilePath) {
      val service = activeLanguageService ?: return@LaunchedEffect
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

    // 入口发现与高亮一样跟随未保存文本，并复用语言包内部增量语法树；迟到结果不能覆盖文件切换。
    LaunchedEffect(activeLanguageService, editorState, activeFilePath) {
      val service = activeLanguageService ?: run {
        runTargets = emptyList()
        return@LaunchedEffect
      }
      val requestedFilePath = activeFilePath
      snapshotFlow { editorState.code }.collectLatest { source ->
        delay(RUN_TARGET_REFRESH_DELAY_MILLIS)
        try {
          val targets = service.runTargets(
            workspace = currentWorkspace(requestedFilePath, source),
            activeFilePath = requestedFilePath,
          )
          if (activeFilePath == requestedFilePath && editorState.code == source) {
            runTargets = targets
          }
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          if (activeFilePath == requestedFilePath && editorState.code == source) {
            runTargets = emptyList()
            output = throwable.toFailureText("运行入口发现失败")
          }
        }
      }
    }

    /**
     * 重新确认入口位置后编译并运行，避免用户编辑源码后点击到异步刷新前的旧位置。
     */
    fun runTarget(requestedTarget: DynamicRunTarget) {
      workbenchState.showToolWindow(RUN_TOOL_WINDOW_ID)
      coroutineScope.launch {
        isRunning = true
        try {
          val service = activeLanguageService ?: error("当前语言服务尚未加载完成。")
          val requestedWorkspace = currentWorkspace()
          val refreshedTargets = service.runTargets(requestedWorkspace, activeFilePath)
          runTargets = refreshedTargets
          val target = refreshedTargets.firstOrNull { candidate ->
            candidate.displayName == requestedTarget.displayName &&
              candidate.entry.filePath == requestedTarget.entry.filePath
          } ?: error("运行入口已经随源码变化失效，请重新选择。")
          output = "正在编译并运行 ${target.displayName}…"
          val result = service.run(
            DynamicProgramRunRequest(
              compilation = DynamicCompilationRequest(
                workspace = requestedWorkspace,
                entry = target.entry,
              ),
            ),
          )
          output = result.toDisplayText(target)
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("程序运行失败")
        } finally {
          isRunning = false
        }
      }
    }

    /** 顶部运行按钮按目标数量直接运行、提示无入口或打开入口选择器。 */
    fun runWorkspace() {
      coroutineScope.launch {
        try {
          val service = activeLanguageService ?: error("当前语言服务尚未加载完成。")
          val targets = service.runTargets(currentWorkspace(), activeFilePath)
          runTargets = targets
          when (targets.size) {
            0 -> {
              workbenchState.showToolWindow(RUN_TOOL_WINDOW_ID)
              output = "当前工作区没有可运行入口。"
            }
            1 -> runTarget(targets.single())
            else -> showRunTargetPicker = true
          }
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          workbenchState.showToolWindow(RUN_TOOL_WINDOW_ID)
          output = throwable.toFailureText("运行入口发现失败")
        }
      }
    }

    // gutter 点击只写入 Compose 状态，实际协程从页面作用域启动并使用刷新后的入口。
    LaunchedEffect(pendingLineRunTarget) {
      val target = pendingLineRunTarget ?: return@LaunchedEffect
      pendingLineRunTarget = null
      runTarget(target)
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
    val activeLanguageDisplayName = supportedLanguages
      .firstOrNull { it.languageId == activeLanguageId }
      ?.displayName
      ?: "代码"
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
        activeLanguageId?.let { languageId ->
          coroutineScope.launch { loadLanguageService(languageId) }
        }
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
                rename.fileRenames.forEach { fileRename ->
                  val renamedSource = sourceFiles.remove(fileRename.oldPath)
                    ?: error("待重命名文件不存在：${fileRename.oldPath}")
                  sourceFiles[fileRename.newPath] = renamedSource
                  openFilePaths.indexOf(fileRename.oldPath).takeIf { it >= 0 }?.let { index ->
                    openFilePaths[index] = fileRename.newPath
                  }
                  if (activeFilePath == fileRename.oldPath) {
                    activeFilePath = fileRename.newPath
                    editorState.replaceDocument(fileRename.newPath, renamedSource)
                  }
                }
                "已将 ${rename.symbol.name} 重命名为 $renameTarget，" +
                  "修改 ${editsByFile.size} 个文件、${rename.edits.size} 处位置，" +
                  "重命名 ${rename.fileRenames.size} 个文件。\n" +
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
      title = "$activeLanguageDisplayName 课程",
      activeDocumentLabel = activeFilePath,
      subtitle = "多文件语义分析 · 实验课",
      openDocumentLabels = openFilePaths.toList(),
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
      runPopupContent = {
        MaterialTheme(
          colors = MaterialTheme.colors.copy(
            surface = EditorWorkbenchColors.PanelBackground,
            onSurface = EditorWorkbenchColors.PrimaryText,
          ),
        ) {
          DropdownMenu(
            expanded = showRunTargetPicker,
            onDismissRequest = { showRunTargetPicker = false },
            modifier = Modifier.removeDefaultDropdownMenuVerticalPadding(),
          ) {
            runTargets.forEach { target ->
              DropdownMenuItem(
                modifier = Modifier.height(CompactDropdownMenuItemHeight),
                contentPadding = PaddingValues(horizontal = 12.dp),
                onClick = {
                  showRunTargetPicker = false
                  runTarget(target)
                },
              ) {
                Text(
                  text = target.displayName,
                  color = EditorWorkbenchColors.PrimaryText,
                  fontSize = 12.sp,
                  maxLines = 1,
                )
              }
            }
          }
        }
      },
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

  /** 将统一动态语言运行结果整理成输出面板可直接阅读的文本。 */
  private fun DynamicProgramRunResult.toDisplayText(target: DynamicRunTarget): String = buildString {
    if (standardOutput.isNotEmpty()) append(standardOutput)
    if (standardError.isNotEmpty()) {
      if (isNotEmpty() && last() != '\n') appendLine()
      append(standardError)
    }
    diagnostics.forEach { diagnostic ->
      if (isNotEmpty() && last() != '\n') appendLine()
      append('[').append(diagnostic.severity).append("] ")
        .append(diagnostic.code).append(": ").appendLine(diagnostic.message)
    }
    if (isNotEmpty() && last() != '\n') appendLine()
    if (executed) {
      append("运行完成：").append(target.displayName)
      returnValue?.let { value -> append("\n返回值：").append(value) }
    } else if (diagnostics.isEmpty()) {
      append("编译未生成可执行程序。")
    }
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
    const val DISPLAY_RESULT_LIMIT = 12
    const val MICROSECONDS_PER_MILLISECOND = 1_000
    const val AUTO_HIGHLIGHT_DELAY_MILLIS = 200L
    const val RUN_TARGET_REFRESH_DELAY_MILLIS = 150L
    const val JAVA_MAIN_FILE_PATH = "java/Main.java"
    const val JAVASCRIPT_MAIN_FILE_PATH = "javascript/main.js"

    val DEFAULT_MAIN_CODE = """
      package course;

      public class Main {
        public static int main() {
          return ScoreMath.sumTo(10);
        }
      }
    """.trimIndent()

    val DEFAULT_SOURCE_FILES = mapOf(
      JAVA_MAIN_FILE_PATH to DEFAULT_MAIN_CODE,
      "java/ScoreMath.java" to """
        package course;

        public class ScoreMath {
          public static int sumTo(int limit) {
            int total = 0;
            for (int value = 1; value <= limit; value++) {
              total += value;
            }
            return total;
          }
        }
      """.trimIndent(),
      "java/CounterMain.java" to """
        package course;

        public class CounterMain {
          public static int main() {
            int counter = 0;
            int remaining = 5;
            while (remaining > 0) {
              counter++;
              remaining--;
            }
            return counter;
          }
        }
      """.trimIndent(),
      JAVASCRIPT_MAIN_FILE_PATH to """
        import { Student } from "./models/student.js";

        const student = new Student("小邮", [88, 92, 95]);
        console.log(student.name, "平均分", student.average());
        student.average();
      """.trimIndent(),
      "javascript/models/student.js" to """
        export class Student {
          constructor(name, scores) {
            this.name = name;
            this.scores = scores;
          }

          average() {
            return this.scores.reduce((sum, score) => sum + score, 0) / this.scores.length;
          }
        }
      """.trimIndent(),
    )

    val DEFAULT_NEW_FILE_CODE = """
      // 在这里编写新模块
      export const value = 1;
    """.trimIndent()

  }
}
