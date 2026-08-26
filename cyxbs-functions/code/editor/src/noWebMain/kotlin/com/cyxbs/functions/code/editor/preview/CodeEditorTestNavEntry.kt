package com.cyxbs.functions.code.editor.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.guided.tour.GuidedTourOverlay
import com.cyxbs.components.guided.tour.GuidedTourTargetRegistry
import com.cyxbs.components.guided.tour.guidedTourTarget
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.functions.code.editor.highlight.CodeEditor
import com.cyxbs.functions.code.editor.highlight.DEFAULT_HIGHLIGHT_CACHE_CAPACITY
import com.cyxbs.functions.code.editor.highlight.editorGutterWidth
import com.cyxbs.functions.code.editor.highlight.rememberCodeEditorState
import com.cyxbs.functions.code.editor.preview.workbench.ActiveCodeEditorTutorial
import com.cyxbs.functions.code.editor.preview.workbench.CompactDropdownMenuItemHeight
import com.cyxbs.functions.code.editor.preview.workbench.FILES_PANEL_ID
import com.cyxbs.functions.code.editor.preview.workbench.RUN_TOOL_WINDOW_ID
import com.cyxbs.functions.code.editor.preview.workbench.TUTORIAL_TOOL_WINDOW_ID
import com.cyxbs.functions.code.editor.preview.workbench.TutorialGuideHint
import com.cyxbs.functions.code.editor.preview.workbench.codeEditorTestToolWindows
import com.cyxbs.functions.code.editor.preview.workbench.codeEditorTutorialToolWindow
import com.cyxbs.functions.code.editor.preview.workbench.rememberCodeEditorTestSidePanels
import com.cyxbs.functions.code.editor.preview.workbench.rememberCodeEditorTutorialSidePanel
import com.cyxbs.functions.code.editor.preview.workbench.removeDefaultDropdownMenuVerticalPadding
import com.cyxbs.functions.code.editor.project.CodeProjectFileRename
import com.cyxbs.functions.code.editor.project.CodeProjectEditorSession
import com.cyxbs.functions.code.editor.project.CodeProjectRepository
import com.cyxbs.functions.code.editor.project.CodeProjectSourceConflictException
import com.cyxbs.functions.code.editor.project.CodeProjectTemplates
import com.cyxbs.functions.code.editor.project.CodeProjectWorkspace
import com.cyxbs.functions.code.editor.project.openProjectDirectory
import com.cyxbs.functions.code.editor.workbench.CodeEditorWorkbench
import com.cyxbs.functions.code.editor.workbench.DynamicLanguageFileIcon
import com.cyxbs.functions.code.editor.workbench.EditorWorkbenchColors
import com.cyxbs.functions.code.editor.workbench.rememberCodeEditorWorkbenchState
import com.cyxbs.functions.code.editor.workbench.rememberDynamicLanguageFileIconCache
import com.cyxbs.functions.code.editor.workbench.resolveDynamicLanguageIdForFile
import com.cyxbs.functions.code.language.DynamicLanguageExecutionException
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.DynamicLanguageManager
import com.cyxbs.functions.code.language.DynamicLanguageSession
import com.cyxbs.functions.code.language.DynamicLanguageUnsupportedCapabilityStatistic
import com.cyxbs.functions.code.language.DynamicProgramRunRequest
import com.cyxbs.functions.code.language.DynamicProgramRunResult
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicTextEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.tutorials.DynamicTutorialCourseState
import com.cyxbs.functions.code.tutorials.DynamicTutorialInfo
import com.cyxbs.functions.code.tutorials.DynamicTutorialLessonWorkspace
import com.cyxbs.functions.code.tutorials.DynamicTutorialManager
import com.cyxbs.functions.code.tutorials.DynamicTutorialProgress
import com.cyxbs.functions.code.tutorials.DynamicTutorialResumeState
import com.cyxbs.functions.code.tutorials.DynamicTutorialSession
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialAnchorIds
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletionKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialGuideTargetKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile
import com.cyxbs.functions.code.tutorials.preferredResumeCourseId
import com.cyxbs.functions.code.tutorials.resolveCourseAvailability
import com.cyxbs.functions.code.tutorials.resolveResumeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * 具体代码工作区路由。
 *
 * [projectId] 与 [tutorialLanguageId] 二选一；都为空只用于兼容开发期直接打开工作区的旧调用。
 */
@Serializable
data class CodeEditorTestNavArgument(
  val projectId: String? = null,
  val tutorialLanguageId: String? = null,
) : AppNavArgument

/**
 * 多语言、多文件编辑、动态语言分析和本地 QuickJS 运行的手动测试页面。
 *
 * 路由只维护文件、Service 与运行状态，具体布局交给可复用工作台组件；该页面只编译进
 * `noWebMain`，用于 Android、iOS 与 Desktop 的功能体验，不作为最终教学业务页面。
 */
@AppNav(route = "code/editor/workspace")
class CodeEditorTestNavEntry : AppNavEntry<CodeEditorTestNavArgument>() {

  override fun isNeedLogin(argument: CodeEditorTestNavArgument): Boolean = false

  @Composable
  override fun Content(argument: CodeEditorTestNavArgument) {
    val projectRepository = remember { CodeProjectRepository() }
    var loadedProjectWorkspace by remember(argument.projectId) {
      mutableStateOf<CodeProjectWorkspace?>(null)
    }
    var loadedProjectSession by remember(argument.projectId) {
      mutableStateOf<CodeProjectEditorSession?>(null)
    }
    var projectLoadError by remember(argument.projectId) { mutableStateOf<String?>(null) }
    var projectLoadGeneration by remember(argument.projectId) { mutableStateOf(0) }

    // 真实项目完成读取前不创建编辑器，避免旧 mock 源码和错误活动文件短暂闪现。
    LaunchedEffect(argument.projectId, projectLoadGeneration) {
      val projectId = argument.projectId ?: return@LaunchedEffect
      projectLoadError = null
      runCatching {
        val workspace = projectRepository.openProject(projectId)
        workspace to projectRepository.loadEditorSession(projectId, workspace.sourceFiles)
      }
        .onSuccess { (workspace, session) ->
          loadedProjectSession = session
          loadedProjectWorkspace = workspace
        }
        .onFailure { projectLoadError = it.message ?: "本地项目读取失败。" }
    }
    if (argument.projectId != null && loadedProjectWorkspace == null) {
      WorkspaceProjectLoading(
        errorMessage = projectLoadError,
        onRetry = { projectLoadGeneration++ },
        onBack = argument::popBackStack,
      )
      return
    }

    val initialTemplate = CodeProjectTemplates.find(argument.tutorialLanguageId.orEmpty())
      ?: CodeProjectTemplates.all.first()
    val initialSourceFiles = loadedProjectWorkspace?.sourceFiles ?: initialTemplate.sourceFiles
    val initialActiveFilePath = loadedProjectSession?.activeFilePath
      ?: loadedProjectWorkspace?.activeFilePath
      ?: initialTemplate.activeFilePath
    // 真实项目只提供项目工具；教程目录、进度和引导仅属于教程或原实验工作区。
    val isTutorialWorkspace = argument.projectId == null
    val dynamicLanguageManager = remember { DynamicLanguageManager() }
    val dynamicTutorialManager = remember { DynamicTutorialManager() }
    val tutorialProgressWriteMutex = remember { Mutex() }
    val guidedTourRegistry = remember { GuidedTourTargetRegistry() }
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var isLoadingLanguage by remember { mutableStateOf(false) }
    var isAnalyzingSymbol by remember { mutableStateOf(false) }
    var highlightCacheCapacity by remember { mutableStateOf(DEFAULT_HIGHLIGHT_CACHE_CAPACITY) }
    var dynamicLanguageService by remember { mutableStateOf<DynamicLanguageSession?>(null) }
    var loadedLanguageId by remember { mutableStateOf<String?>(null) }
    var supportedLanguages by remember { mutableStateOf<List<DynamicLanguageInfo>>(emptyList()) }
    var unsupportedCapabilityStatistics by remember {
      mutableStateOf<List<DynamicLanguageUnsupportedCapabilityStatistic>>(emptyList())
    }
    var tutorialSession by remember { mutableStateOf<DynamicTutorialSession?>(null) }
    var tutorialManifest by remember { mutableStateOf<DynamicTutorialManifest?>(null) }
    var tutorialLanguageChoices by remember { mutableStateOf<List<DynamicTutorialInfo>?>(null) }
    var showTutorialLanguagePicker by remember { mutableStateOf(false) }
    var isLoadingTutorialLanguages by remember { mutableStateOf(false) }
    var tutorialLanguageLoadError by remember { mutableStateOf<String?>(null) }
    var tutorialStatus by remember { mutableStateOf("正在准备动态教程目录…") }
    var isLoadingTutorial by remember { mutableStateOf(false) }
    var canRetryTutorialLoad by remember { mutableStateOf(false) }
    var tutorialLoadGeneration by remember { mutableStateOf(0) }
    var activeTutorial by remember { mutableStateOf<ActiveCodeEditorTutorial?>(null) }
    var resettingTutorialCourseId by remember { mutableStateOf<String?>(null) }
    val savedTutorialProgress = remember { mutableStateMapOf<String, DynamicTutorialProgress>() }
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
    val sourceFiles = remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateMapOf<String, String>().apply { putAll(initialSourceFiles) }
    }
    // 独立保存最近一次确认写入磁盘的基线，不能复用包含未保存编辑的 sourceFiles。
    val persistedSourceFiles = remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateMapOf<String, String>().apply { putAll(initialSourceFiles) }
    }
    val directoryPaths = remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateListOf<String>().apply {
        addAll(loadedProjectWorkspace?.directoryPaths.orEmpty().sorted())
      }
    }
    val openFilePaths = remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateListOf<String>().apply {
        addAll(loadedProjectSession?.openFilePaths.orEmpty().ifEmpty { listOf(initialActiveFilePath) })
      }
    }
    val cursorPositions = remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateMapOf<String, Int>().apply {
        putAll(loadedProjectSession?.cursorPositions.orEmpty())
      }
    }
    var activeFilePath by remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateOf(initialActiveFilePath)
    }
    var runTargets by remember { mutableStateOf<List<DynamicRunTarget>>(emptyList()) }
    var runDiagnostics by remember { mutableStateOf<List<DynamicCompilationDiagnostic>>(emptyList()) }
    var runDiagnosticSources by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingLineRunTarget by remember { mutableStateOf<DynamicRunTarget?>(null) }
    var showRunTargetPicker by remember { mutableStateOf(false) }
    // 教程路由先以用户选择的稳定语言 ID 启动，避免未来新增语言时依赖客户端内置文件扩展名猜测。
    val activeLanguageId = argument.tutorialLanguageId
      ?: resolveDynamicLanguageIdForFile(activeFilePath, supportedLanguages)
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
      initialCode = initialSourceFiles.getValue(initialActiveFilePath),
      activeFilePath = activeFilePath,
      initialCursorPosition = cursorPositions[initialActiveFilePath],
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
    var pendingSourceConflict by remember(argument.projectId) {
      mutableStateOf<ProjectSourceConflict?>(null)
    }
    var isResolvingSourceConflict by remember(argument.projectId) { mutableStateOf(false) }

    /** 将普通保存冲突提升为显式选择，其他异常仍写入 Run 输出区。 */
    fun reportProjectSaveFailure(
      filePath: String,
      title: String,
      throwable: Throwable,
      onConflictResolved: (() -> Unit)? = null,
    ) {
      if (throwable is CodeProjectSourceConflictException && pendingSourceConflict == null) {
        pendingSourceConflict = ProjectSourceConflict(filePath, onConflictResolved)
      }
      output = throwable.toFailureText(title)
    }

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
      if (filePath == activeFilePath) {
        selection?.let(editorState::selectRange)
        return
      }
      val previousFilePath = activeFilePath
      val previousSource = editorState.code
      cursorPositions[previousFilePath] = editorState.cursorPosition
      sourceFiles[previousFilePath] = previousSource
      val targetSource = sourceFiles[filePath] ?: error("文件不存在：$filePath")
      if (filePath !in openFilePaths) openFilePaths += filePath
      editorState.replaceDocument(
        filePath = filePath,
        source = targetSource,
        cursorPosition = selection?.from ?: cursorPositions[filePath],
      )
      activeFilePath = filePath
      selection?.let(editorState::selectRange)
      argument.projectId?.let { projectId ->
        coroutineScope.launch {
          runCatching {
            projectRepository.saveSource(
              projectId = projectId,
              relativePath = previousFilePath,
              source = previousSource,
              expectedSource = persistedSourceFiles[previousFilePath],
            )
            projectRepository.updateActiveFile(projectId, filePath)
          }.onSuccess { persistedSourceFiles[previousFilePath] = previousSource }
            .onFailure { throwable ->
              reportProjectSaveFailure(previousFilePath, "项目文件保存失败", throwable)
            }
        }
      }
    }

    /** 构造可写入 Settings 的当前项目会话，并把活动光标合并进文件级缓存。 */
    fun currentProjectEditorSession(projectId: String): CodeProjectEditorSession {
      val currentCursorPositions = cursorPositions.toMutableMap().apply {
        this[activeFilePath] = editorState.cursorPosition
      }
      return CodeProjectEditorSession(
        projectId = projectId,
        openFilePaths = openFilePaths.toList(),
        activeFilePath = activeFilePath,
        cursorPositions = currentCursorPositions,
      )
    }

    /**
     * 冲刷活动源码和编辑会话；返回、切换项目等会离开页面的操作必须等待它成功。
     * 外部修改冲突由调用方转交统一冲突对话框，不能在这里强制覆盖。
     */
    suspend fun flushActiveProjectState(projectId: String) {
      val filePath = activeFilePath
      val source = editorState.code
      cursorPositions[filePath] = editorState.cursorPosition
      projectRepository.saveSource(
        projectId = projectId,
        relativePath = filePath,
        source = source,
        expectedSource = persistedSourceFiles[filePath],
      )
      sourceFiles[filePath] = source
      persistedSourceFiles[filePath] = source
      projectRepository.updateActiveFile(projectId, filePath)
      projectRepository.saveEditorSession(currentProjectEditorSession(projectId))
    }

    /** 用恢复结果整体替换编辑器工作区，确保课时切换不会混入上一课时文件。 */
    fun applyTutorialResumeState(
      course: DynamicTutorialCourse,
      resumeState: DynamicTutorialResumeState,
    ) {
      val restoredFiles = resumeState.workspace.associate { file -> file.path to file.source }
      val activeSource = restoredFiles[resumeState.activeFilePath]
        ?: error("教程活动文件不存在：${resumeState.activeFilePath}")
      sourceFiles.clear()
      sourceFiles.putAll(restoredFiles)
      persistedSourceFiles.clear()
      persistedSourceFiles.putAll(restoredFiles)
      directoryPaths.clear()
      openFilePaths.clear()
      openFilePaths += resumeState.activeFilePath
      cursorPositions.clear()
      editorState.replaceDocument(resumeState.activeFilePath, activeSource)
      activeFilePath = resumeState.activeFilePath
      activeTutorial = ActiveCodeEditorTutorial(
        course = course,
        lesson = resumeState.lesson,
        stepIndex = resumeState.stepIndex,
        completedSteps = resumeState.completedSteps,
        isCompleted = resumeState.isCourseCompleted,
      )
    }

    /**
     * 进入课程上次停留的课时，并按 npm 版本决定恢复代码现场或使用最新初始文件。
     *
     * 教程源码只在用户明确选择课程后写入内存工作区；下载 Manifest 不会覆盖正在编辑的文件。
     */
    fun openTutorialCourse(courseId: String) {
      coroutineScope.launch {
        try {
          val session = tutorialSession ?: error("当前语言的教程包尚未加载完成。")
          val completedCourseIds = savedTutorialProgress.values
            .filter(DynamicTutorialProgress::isCourseCompleted)
            .mapTo(linkedSetOf(), DynamicTutorialProgress::courseId)
          val availability = tutorialManifest
            ?.resolveCourseAvailability(completedCourseIds, savedTutorialProgress.keys)
            ?.firstOrNull { it.course.courseId == courseId }
            ?: error("教程 Manifest 中不存在课程：$courseId")
          if (availability.state == DynamicTutorialCourseState.LOCKED) {
            val courseTitles = tutorialManifest?.courses.orEmpty().associate { it.courseId to it.title }
            val missingTitles = availability.missingPrerequisiteCourseIds.map { prerequisiteId ->
              courseTitles[prerequisiteId] ?: prerequisiteId
            }
            tutorialStatus = "请先完成 ${missingTitles.joinToString("、")}"
            return@launch
          }
          val course = session.course(courseId).getOrThrow()
            ?: error("教程包中不存在课程：$courseId")
          val resumeState = course.resolveResumeState(
            progress = savedTutorialProgress[courseId],
            npmPackageVersion = session.npmPackageVersion,
          )
          applyTutorialResumeState(course, resumeState)
          workbenchState.showToolWindow(TUTORIAL_TOOL_WINDOW_ID)
          tutorialStatus = buildString {
            append("正在学习 ${course.summary.title} · ${resumeState.lesson.title}")
            if (resumeState.restoredWorkspace) append(" · 已恢复代码")
          }
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          tutorialStatus = throwable.toFailureText("课程加载失败")
        }
      }
    }

    /** 捕获当前课时的未保存文本，供延迟保存与课时切换共用。 */
    fun currentTutorialDraft(tutorial: ActiveCodeEditorTutorial): TutorialProgressDraft {
      return TutorialProgressDraft(
        tutorial = tutorial,
        activeFilePath = activeFilePath,
        workspace = currentWorkspace().files.map { file ->
          DynamicTutorialSourceFile(file.path, file.source)
        },
      )
    }

    /**
     * 把当前工作区和可选运行结果交回教程 npm 包判定，并在通过后推进稳定步骤 ID。
     *
     * 语言相关规则只存在于教程包；客户端不复制输出字符串或源码条件。
     */
    suspend fun evaluateTutorialStep(runResult: DynamicProgramRunResult? = null) {
      val current = activeTutorial ?: return
      val session = tutorialSession ?: return
      val result = session.evaluate(
        DynamicTutorialEvaluationRequest(
          courseId = current.course.summary.courseId,
          lessonId = current.lesson.lessonId,
          stepId = current.step.stepId,
          workspace = currentWorkspace().files.map { file ->
            DynamicTutorialSourceFile(path = file.path, source = file.source)
          },
          runExecuted = runResult?.executed == true,
          standardOutput = runResult?.standardOutput.orEmpty(),
          standardError = runResult?.standardError.orEmpty(),
        ),
      ).getOrThrow()
      if (!result.completed) {
        activeTutorial = current.copy(feedback = result.feedback, isCompleted = false)
        return
      }
      activeTutorial = current.advance()
      workbenchState.showToolWindow(TUTORIAL_TOOL_WINDOW_ID)
    }

    /** 将当前步骤和源码快照写入教程模块；业务页面不直接操作文件格式。 */
    suspend fun persistTutorialProgress(draft: TutorialProgressDraft) {
      try {
        if (resettingTutorialCourseId == draft.tutorial.course.summary.courseId) return
        val session = tutorialSession ?: return
        val previous = savedTutorialProgress[draft.tutorial.course.summary.courseId]
        val reusableLessonWorkspaces = if (previous?.npmPackageVersion == session.npmPackageVersion) {
          previous.lessonWorkspaces.toMutableList().apply {
            if (isEmpty() && previous.workspace.isNotEmpty() && previous.activeFilePath != null) {
              add(
                DynamicTutorialLessonWorkspace(
                  lessonId = previous.lessonId,
                  workspace = previous.workspace,
                  activeFilePath = requireNotNull(previous.activeFilePath),
                ),
              )
            }
          }
        } else {
          mutableListOf()
        }
        reusableLessonWorkspaces.removeAll { it.lessonId == draft.tutorial.lesson.lessonId }
        reusableLessonWorkspaces += DynamicTutorialLessonWorkspace(
          lessonId = draft.tutorial.lesson.lessonId,
          workspace = draft.workspace,
          activeFilePath = draft.activeFilePath,
        )
        val progress = DynamicTutorialProgress(
          languageId = session.tutorial.languageId,
          npmPackageName = session.tutorial.npmPackageName,
          npmPackageVersion = session.npmPackageVersion,
          courseId = draft.tutorial.course.summary.courseId,
          lessonId = draft.tutorial.lesson.lessonId,
          stepId = draft.tutorial.step.stepId,
          completedSteps = draft.tutorial.completedSteps.toList(),
          workspace = draft.workspace,
          activeFilePath = draft.activeFilePath,
          lessonWorkspaces = reusableLessonWorkspaces,
          isCourseCompleted = draft.tutorial.isCompleted,
        )
        tutorialProgressWriteMutex.withLock {
          if (resettingTutorialCourseId == progress.courseId) return@withLock
          session.saveProgress(progress).getOrThrow()
          savedTutorialProgress[progress.courseId] = progress
        }
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        // 持久化失败不能中止高亮、运行或教程校验，仍在侧栏给出可见反馈。
        tutorialStatus = throwable.toFailureText("教程进度保存失败")
      }
    }

    /** 先保存当前课时，再从同一课程恢复目标课时的独立代码现场。 */
    fun openTutorialLesson(lessonId: String) {
      val current = activeTutorial ?: return
      if (current.lesson.lessonId == lessonId) return
      coroutineScope.launch {
        persistTutorialProgress(currentTutorialDraft(current))
        val session = tutorialSession ?: return@launch
        val resumeState = current.course.resolveResumeState(
          progress = savedTutorialProgress[current.course.summary.courseId],
          npmPackageVersion = session.npmPackageVersion,
          requestedLessonId = lessonId,
        )
        applyTutorialResumeState(current.course, resumeState)
        workbenchState.showToolWindow(TUTORIAL_TOOL_WINDOW_ID)
        tutorialStatus = "正在学习 ${current.course.summary.title} · ${resumeState.lesson.title}"
      }
    }

    /** 清除单门课程进度并立即回到当前 npm 包的第一份模板。 */
    fun resetTutorialCourse(courseId: String) {
      coroutineScope.launch {
        resettingTutorialCourseId = courseId
        try {
          val session = tutorialSession ?: return@launch
          tutorialProgressWriteMutex.withLock {
            session.clearCourseProgress(courseId).getOrThrow()
            savedTutorialProgress.remove(courseId)
          }
          val course = session.course(courseId).getOrThrow()
            ?: error("教程包中不存在课程：$courseId")
          val resumeState = course.resolveResumeState(null, session.npmPackageVersion)
          applyTutorialResumeState(course, resumeState)
          workbenchState.showToolWindow(TUTORIAL_TOOL_WINDOW_ID)
          tutorialStatus = "已重置 ${course.summary.title} · ${resumeState.lesson.title}"
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          tutorialStatus = throwable.toFailureText("课程重置失败")
        } finally {
          resettingTutorialCourseId = null
        }
      }
    }

    // 教程内编辑停止后保存现场；切换课程或离开页面时在 NonCancellable 中冲刷最后一次快照。
    LaunchedEffect(activeTutorial?.course?.summary?.courseId, editorState) {
      if (activeTutorial == null) return@LaunchedEffect
      var lastDraft: TutorialProgressDraft? = null
      try {
        snapshotFlow {
          activeTutorial?.let { tutorial ->
            currentTutorialDraft(tutorial)
          }
        }.collectLatest { draft ->
          if (draft == null) return@collectLatest
          lastDraft = draft
          delay(TUTORIAL_PROGRESS_SAVE_DELAY_MILLIS)
          persistTutorialProgress(draft)
        }
      } finally {
        withContext(NonCancellable) {
          lastDraft?.let { draft -> persistTutorialProgress(draft) }
        }
      }
    }

    // 普通项目输入停止后写回真实文件；切换文件时 openFile 还会立即冲刷旧文件，避免丢失尾部编辑。
    LaunchedEffect(argument.projectId, editorState, activeFilePath) {
      val projectId = argument.projectId ?: return@LaunchedEffect
      val requestedFilePath = activeFilePath
      var lastSource: String? = null
      try {
        snapshotFlow { editorState.code }.collectLatest { source ->
          lastSource = source
          delay(PROJECT_SOURCE_SAVE_DELAY_MILLIS)
          try {
            projectRepository.saveSource(
              projectId = projectId,
              relativePath = requestedFilePath,
              source = source,
              expectedSource = persistedSourceFiles[requestedFilePath],
            )
            persistedSourceFiles[requestedFilePath] = source
            if (activeFilePath == requestedFilePath) sourceFiles[requestedFilePath] = source
          } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            reportProjectSaveFailure(requestedFilePath, "项目文件自动保存失败", throwable)
          }
        }
      } finally {
        withContext(NonCancellable) {
          lastSource?.let { source ->
            runCatching {
              projectRepository.saveSource(
                projectId = projectId,
                relativePath = requestedFilePath,
                source = source,
                expectedSource = persistedSourceFiles[requestedFilePath],
              )
            }
          }
        }
      }
    }

    // 项目标签、活动文件和光标独立于源码自动保存；离开页面时在 NonCancellable 中冲刷最后快照。
    LaunchedEffect(argument.projectId, editorState) {
      val projectId = argument.projectId ?: return@LaunchedEffect
      var lastSession: CodeProjectEditorSession? = null
      try {
        snapshotFlow { currentProjectEditorSession(projectId) }.collectLatest { session ->
          lastSession = session
          delay(PROJECT_SESSION_SAVE_DELAY_MILLIS)
          projectRepository.saveEditorSession(session)
        }
      } finally {
        withContext(NonCancellable) {
          lastSession?.let { session -> runCatching { projectRepository.saveEditorSession(session) } }
        }
      }
    }

    // 源码区间目标暂由编辑器原生选区呈现；布局锚点交给 guided-tour 按窗口实时测量。
    LaunchedEffect(activeTutorial?.step?.stepId) {
      val target = activeTutorial?.step?.guideTarget ?: return@LaunchedEffect
      if (target.kind != DynamicTutorialGuideTargetKind.EDITOR_RANGE) return@LaunchedEffect
      val filePath = target.filePath ?: return@LaunchedEffect
      val from = target.from ?: return@LaunchedEffect
      val to = target.to ?: return@LaunchedEffect
      if (filePath in sourceFiles) {
        openFile(filePath, DynamicTextRange(from = from, to = to))
      }
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
        languageIconCache.update(language.languageId, loadedService.fileIcon().getOrThrow())
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
        unsupportedCapabilityStatistics =
          dynamicLanguageManager.unsupportedCapabilityStatistics()
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        languageStatus = throwable.toFailureText("动态语言目录加载失败")
      }
    }

    // 教程包跟随活动语言独立加载；切换语言或离开页面时取消旧会话并释放对应 JavaScript Runtime。
    LaunchedEffect(isTutorialWorkspace, activeLanguageId, tutorialLoadGeneration) {
      if (!isTutorialWorkspace) return@LaunchedEffect
      val languageId = activeLanguageId ?: return@LaunchedEffect
      var loadedSession: DynamicTutorialSession? = null
      isLoadingTutorial = true
      canRetryTutorialLoad = false
      tutorialManifest = null
      activeTutorial = null
      savedTutorialProgress.clear()
      try {
        val supportedTutorial = dynamicTutorialManager.supportedTutorials().firstOrNull { tutorial ->
          tutorial.languageId == languageId || languageId in tutorial.aliases
        }
        if (supportedTutorial == null) {
          tutorialStatus = "当前语言暂未提供动态教程。"
          return@LaunchedEffect
        }
        loadedSession = dynamicTutorialManager.load(languageId)
        tutorialSession = loadedSession
        tutorialManifest = loadedSession.manifest().getOrThrow()
        val storedProgress = loadedSession.savedProgress().getOrThrow()
        savedTutorialProgress.putAll(storedProgress.associateBy { it.courseId })
        val resumeCourseId = storedProgress.preferredResumeCourseId()
        tutorialStatus = buildString {
          append(tutorialManifest?.courses?.size ?: 0).append(" 门课程")
          append(" · npm ").append(loadedSession.npmPackageVersion)
        }
        isLoadingTutorial = false
        // 有历史记录时直接恢复最近课程，不再要求用户通过额外的“继续学习”卡片二次进入。
        resumeCourseId?.let(::openTutorialCourse)
        awaitCancellation()
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        canRetryTutorialLoad = true
        tutorialStatus = throwable.toFailureText("动态教程加载失败")
      } finally {
        isLoadingTutorial = false
        if (tutorialSession === loadedSession) tutorialSession = null
        withContext(NonCancellable) { loadedSession?.close() }
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
          ).getOrThrow()
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
          ).getOrThrow()
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
      if (isRunning) {
        output = "程序正在运行，请等待当前任务结束。"
        return
      }
      workbenchState.showToolWindow(RUN_TOOL_WINDOW_ID)
      coroutineScope.launch {
        isRunning = true
        runDiagnostics = emptyList()
        runDiagnosticSources = emptyMap()
        try {
          val service = activeLanguageService ?: error("当前语言服务尚未加载完成。")
          val requestedWorkspace = currentWorkspace()
          val refreshedTargets = service.runTargets(requestedWorkspace, activeFilePath).getOrThrow()
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
          runDiagnostics = result.diagnostics
          runDiagnosticSources = requestedWorkspace.files.associate { file ->
            file.path to file.source
          }
          unsupportedCapabilityStatistics =
            dynamicLanguageManager.unsupportedCapabilityStatistics()
          output = result.toDisplayText(target)
          val tutorialCompletionKind = activeTutorial?.step?.completion?.kind
          if (tutorialCompletionKind == DynamicTutorialCompletionKind.RUN_SUCCEEDED ||
            tutorialCompletionKind == DynamicTutorialCompletionKind.OUTPUT_CONTAINS
          ) {
            evaluateTutorialStep(result)
          }
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          runDiagnostics = emptyList()
          runDiagnosticSources = emptyMap()
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
          val targets = service.runTargets(currentWorkspace(), activeFilePath).getOrThrow()
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

    /** 用仓库返回的完整快照同步文件池、标签、活动文件与目录；[openPathMapper] 处理改名标签。 */
    fun applyProjectWorkspaceSnapshot(
      refreshed: CodeProjectWorkspace,
      openPathMapper: (String) -> String? = { it },
    ) {
      val mappedOpenPaths = openFilePaths
        .mapNotNull(openPathMapper)
        .filter(refreshed.sourceFiles::containsKey)
        .distinct()
      val mappedCursorPositions = cursorPositions.mapNotNull { (path, position) ->
        val mappedPath = openPathMapper(path) ?: return@mapNotNull null
        val source = refreshed.sourceFiles[mappedPath] ?: return@mapNotNull null
        mappedPath to position.coerceIn(0, source.length)
      }.toMap()
      sourceFiles.clear()
      sourceFiles.putAll(refreshed.sourceFiles)
      persistedSourceFiles.clear()
      persistedSourceFiles.putAll(refreshed.sourceFiles)
      directoryPaths.clear()
      directoryPaths.addAll(refreshed.directoryPaths.sorted())
      openFilePaths.clear()
      openFilePaths.addAll(mappedOpenPaths)
      if (refreshed.activeFilePath !in openFilePaths) openFilePaths += refreshed.activeFilePath
      cursorPositions.clear()
      cursorPositions.putAll(mappedCursorPositions)
      editorState.replaceDocument(
        refreshed.activeFilePath,
        refreshed.sourceFiles.getValue(refreshed.activeFilePath),
        cursorPositions[refreshed.activeFilePath],
      )
      activeFilePath = refreshed.activeFilePath
      loadedProjectWorkspace = refreshed
    }

    /** 返回冲突路径当前仍待保存的编辑器文本，活动文件必须读取编辑器而不是旧文件池。 */
    fun currentSourceForConflict(filePath: String): String? {
      return if (filePath == activeFilePath) editorState.code else sourceFiles[filePath]
    }

    /** 丢弃编辑器版本并读取最新磁盘内容；读取失败时保留对话框供用户选择其他策略。 */
    fun reloadConflictingSource(conflict: ProjectSourceConflict) {
      val projectId = argument.projectId ?: return
      isResolvingSourceConflict = true
      coroutineScope.launch {
        try {
          val diskSource = projectRepository.readSource(projectId, conflict.filePath)
          sourceFiles[conflict.filePath] = diskSource
          persistedSourceFiles[conflict.filePath] = diskSource
          if (activeFilePath == conflict.filePath) {
            editorState.replaceDocument(conflict.filePath, diskSource)
          }
          pendingSourceConflict = null
          output = "已重新加载磁盘版本：${conflict.filePath}"
          conflict.onResolved?.invoke()
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("重新加载磁盘文件失败")
        } finally {
          isResolvingSourceConflict = false
        }
      }
    }

    /** 用户明确确认后覆盖外部版本；成功写盘的文本成为新的冲突检测基线。 */
    fun overwriteConflictingSource(conflict: ProjectSourceConflict) {
      val projectId = argument.projectId ?: return
      val editorSource = currentSourceForConflict(conflict.filePath) ?: return
      isResolvingSourceConflict = true
      coroutineScope.launch {
        try {
          projectRepository.overwriteSource(projectId, conflict.filePath, editorSource)
          sourceFiles[conflict.filePath] = editorSource
          persistedSourceFiles[conflict.filePath] = editorSource
          pendingSourceConflict = null
          output = "已用编辑器内容覆盖磁盘版本：${conflict.filePath}"
          conflict.onResolved?.invoke()
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("覆盖磁盘文件失败")
        } finally {
          isResolvingSourceConflict = false
        }
      }
    }

    /** 保留外部版本，并把编辑器文本写入自动命名的新文件后切换到该副本。 */
    fun saveConflictingSourceAsCopy(conflict: ProjectSourceConflict) {
      val projectId = argument.projectId ?: return
      val editorSource = currentSourceForConflict(conflict.filePath) ?: return
      isResolvingSourceConflict = true
      coroutineScope.launch {
        try {
          val refreshed = projectRepository.saveSourceConflictCopy(
            projectId = projectId,
            relativePath = conflict.filePath,
            source = editorSource,
          )
          applyProjectWorkspaceSnapshot(refreshed)
          pendingSourceConflict = null
          output = "已另存冲突副本：${refreshed.activeFilePath}"
          conflict.onResolved?.invoke()
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("另存冲突副本失败")
        } finally {
          isResolvingSourceConflict = false
        }
      }
    }

    /** 从标签栏移除文件；关闭活动标签时选择右侧优先、左侧兜底的相邻文件。 */
    fun closeDocumentWithoutSaving(filePath: String) {
      if (openFilePaths.size <= 1) {
        output = "至少需要保留一个打开的文件标签。"
        return
      }
      val closingIndex = openFilePaths.indexOf(filePath)
      if (closingIndex < 0) return
      if (filePath != activeFilePath) {
        openFilePaths.removeAt(closingIndex)
        cursorPositions.remove(filePath)
        return
      }

      cursorPositions[filePath] = editorState.cursorPosition
      val nextFilePath = openFilePaths.getOrNull(closingIndex + 1)
        ?: openFilePaths.getOrNull(closingIndex - 1)
        ?: return
      openFilePaths.removeAt(closingIndex)
      cursorPositions.remove(filePath)
      val nextSource = sourceFiles.getValue(nextFilePath)
      editorState.replaceDocument(nextFilePath, nextSource, cursorPositions[nextFilePath])
      activeFilePath = nextFilePath
      argument.projectId?.let { projectId ->
        coroutineScope.launch {
          runCatching { projectRepository.updateActiveFile(projectId, nextFilePath) }
            .onFailure { throwable -> output = throwable.toFailureText("活动文件更新失败") }
        }
      }
    }

    /** 关闭标签前保存活动文件；发生外部冲突时，解决后自动重试关闭。 */
    fun closeDocument(filePath: String) {
      if (filePath !in openFilePaths || openFilePaths.size <= 1) {
        closeDocumentWithoutSaving(filePath)
        return
      }
      if (filePath != activeFilePath || argument.projectId == null) {
        if (filePath == activeFilePath) sourceFiles[filePath] = editorState.code
        closeDocumentWithoutSaving(filePath)
        return
      }
      val projectId = argument.projectId
      val source = editorState.code
      sourceFiles[filePath] = source
      coroutineScope.launch {
        runCatching {
          projectRepository.saveSource(
            projectId = projectId,
            relativePath = filePath,
            source = source,
            expectedSource = persistedSourceFiles[filePath],
          )
        }.onSuccess {
          persistedSourceFiles[filePath] = source
          closeDocumentWithoutSaving(filePath)
        }.onFailure { throwable ->
          reportProjectSaveFailure(
            filePath = filePath,
            title = "关闭文件前保存失败",
            throwable = throwable,
            onConflictResolved = { closeDocument(filePath) },
          )
        }
      }
    }

    /** 离开真实项目之前等待源码与会话落盘；冲突解决后自动重试原导航。 */
    fun leaveWorkspace(onLeave: () -> Unit) {
      val projectId = argument.projectId
      if (projectId == null) {
        onLeave()
        return
      }
      val savingFilePath = activeFilePath
      coroutineScope.launch {
        runCatching { flushActiveProjectState(projectId) }
          .onSuccess { onLeave() }
          .onFailure { throwable ->
            reportProjectSaveFailure(
              filePath = savingFilePath,
              title = "离开项目前保存失败",
              throwable = throwable,
              onConflictResolved = { leaveWorkspace(onLeave) },
            )
          }
      }
    }

    /** 重命名或移动文件树节点；真实项目先保存当前文件，再由仓库执行可恢复磁盘事务。 */
    fun renameWorkspacePath(oldPath: String, requestedNewPath: String, isDirectory: Boolean): Boolean {
      val newPath = requestedNewPath.normalizeWorkspacePath()
      if (
        !newPath.isSafeWorkspacePath() ||
        oldPath == newPath ||
        (isDirectory && newPath.startsWith("$oldPath/"))
      ) {
        return false
      }
      val affectedFiles = sourceFiles.keys.filter { path ->
        path == oldPath || isDirectory && path.startsWith("$oldPath/")
      }
      val affectedDirectories = directoryPaths.filter { path ->
        path == oldPath || isDirectory && path.startsWith("$oldPath/")
      }
      if (affectedFiles.isEmpty() && affectedDirectories.isEmpty()) return false
      val remappedFilePaths = affectedFiles.map { path -> path.remapWorkspacePath(oldPath, newPath) }
      val unaffectedFiles = sourceFiles.keys - affectedFiles.toSet()
      val unaffectedDirectories = directoryPaths - affectedDirectories.toSet()
      if (
        remappedFilePaths.any { it in unaffectedFiles || it in unaffectedDirectories } ||
        newPath in unaffectedFiles || newPath in unaffectedDirectories
      ) {
        return false
      }

      val projectId = argument.projectId
      if (projectId != null) {
        coroutineScope.launch {
          runCatching {
            // 快照应用会整体重载工作区，因此即使移动的是其他节点也必须先冲刷活动文件。
            projectRepository.saveSource(
              projectId = projectId,
              relativePath = activeFilePath,
              source = editorState.code,
              expectedSource = persistedSourceFiles[activeFilePath],
            )
            persistedSourceFiles[activeFilePath] = editorState.code
            projectRepository.renamePath(projectId, oldPath, newPath)
          }.onSuccess { refreshed ->
            applyProjectWorkspaceSnapshot(refreshed) { path ->
              path.remapWorkspacePath(oldPath, newPath)
            }
            output = "已将 $oldPath 移动到 $newPath"
          }.onFailure { throwable ->
            reportProjectSaveFailure(activeFilePath, "重命名或移动失败", throwable)
          }
        }
      } else {
        sourceFiles[activeFilePath] = editorState.code
        val remappedSources = sourceFiles.mapKeys { (path, _) ->
          path.remapWorkspacePath(oldPath, newPath)
        }
        val remappedPersistedSources = persistedSourceFiles.mapKeys { (path, _) ->
          path.remapWorkspacePath(oldPath, newPath)
        }
        val remappedDirectories = directoryPaths.map { path ->
          path.remapWorkspacePath(oldPath, newPath)
        }
        val remappedCursorPositions = cursorPositions.mapKeys { (path, _) ->
          path.remapWorkspacePath(oldPath, newPath)
        }
        val remappedActivePath = activeFilePath.remapWorkspacePath(oldPath, newPath)
        sourceFiles.clear()
        sourceFiles.putAll(remappedSources)
        persistedSourceFiles.clear()
        persistedSourceFiles.putAll(remappedPersistedSources)
        directoryPaths.clear()
        directoryPaths.addAll(remappedDirectories.distinct().sorted())
        cursorPositions.clear()
        cursorPositions.putAll(remappedCursorPositions)
        openFilePaths.indices.forEach { index ->
          openFilePaths[index] = openFilePaths[index].remapWorkspacePath(oldPath, newPath)
        }
        editorState.replaceDocument(remappedActivePath, sourceFiles.getValue(remappedActivePath))
        activeFilePath = remappedActivePath
        output = "已将 $oldPath 移动到 $newPath"
      }
      return true
    }

    /** 删除文件树节点；真实项目由仓库隔离后删除，教程工作区只修改当前会话。 */
    fun deleteWorkspacePath(path: String, isDirectory: Boolean) {
      val isAffected: (String) -> Boolean = { candidate ->
        candidate == path || isDirectory && candidate.startsWith("$path/")
      }
      val remainingFiles = sourceFiles.keys.filterNot(isAffected)
      if (remainingFiles.isEmpty()) {
        output = "项目至少需要保留一个可编辑源码文件。"
        return
      }
      val projectId = argument.projectId
      if (projectId != null) {
        coroutineScope.launch {
          runCatching {
            if (!isAffected(activeFilePath)) {
              projectRepository.saveSource(
                projectId = projectId,
                relativePath = activeFilePath,
                source = editorState.code,
                expectedSource = persistedSourceFiles[activeFilePath],
              )
              persistedSourceFiles[activeFilePath] = editorState.code
            }
            projectRepository.deletePath(projectId, path)
          }.onSuccess { refreshed ->
            applyProjectWorkspaceSnapshot(refreshed) { openPath ->
              openPath.takeUnless(isAffected)
            }
            output = "已删除 $path"
          }.onFailure { throwable ->
            reportProjectSaveFailure(activeFilePath, "删除失败", throwable)
          }
        }
      } else {
        sourceFiles.keys.filter(isAffected).forEach(sourceFiles::remove)
        persistedSourceFiles.keys.filter(isAffected).forEach(persistedSourceFiles::remove)
        cursorPositions.keys.filter(isAffected).forEach(cursorPositions::remove)
        directoryPaths.removeAll(isAffected)
        openFilePaths.removeAll(isAffected)
        val nextActivePath = activeFilePath.takeUnless(isAffected)
          ?: openFilePaths.lastOrNull()
          ?: sourceFiles.keys.first()
        if (nextActivePath !in openFilePaths) openFilePaths += nextActivePath
        editorState.replaceDocument(nextActivePath, sourceFiles.getValue(nextActivePath))
        activeFilePath = nextActivePath
        output = "已删除 $path"
      }
    }

    /** 校验并创建工作区相对路径文件。 */
    fun createWorkspaceFile(requestedPath: String): Boolean {
      val normalizedPath = requestedPath.normalizeWorkspacePath()
      val error = when {
        normalizedPath.isEmpty() -> "文件路径不能为空。"
        !normalizedPath.isSafeWorkspacePath() ->
          "文件必须使用工作区内的相对路径。"
        normalizedPath in sourceFiles -> "文件已存在：$normalizedPath"
        normalizedPath in directoryPaths -> "同名文件夹已存在：$normalizedPath"
        normalizedPath.pathPrefixes().any(sourceFiles::containsKey) ->
          "文件路径中包含同名文件：$normalizedPath"
        else -> null
      }
      return if (error != null) {
        output = error
        false
      } else {
        val applyCreatedFile = {
          normalizedPath.parentDirectoryPaths().forEach { directory ->
            if (directory !in directoryPaths) directoryPaths += directory
          }
          sourceFiles[normalizedPath] = DEFAULT_NEW_FILE_CODE
          persistedSourceFiles[normalizedPath] = DEFAULT_NEW_FILE_CODE
          openFile(normalizedPath)
          output = "已创建并打开 $normalizedPath"
        }
        val projectId = argument.projectId
        if (projectId == null) {
          applyCreatedFile()
        } else {
          coroutineScope.launch {
            runCatching {
              check(
                projectRepository.createFile(
                  projectId = projectId,
                  relativePath = normalizedPath,
                  initialSource = DEFAULT_NEW_FILE_CODE,
                ),
              ) {
                "文件已存在：$normalizedPath"
              }
            }.onSuccess { applyCreatedFile() }
              .onFailure { throwable ->
                output = throwable.toFailureText("创建项目文件失败")
              }
          }
        }
        true
      }
    }

    /** 校验并在真实项目目录中创建文件夹；教程工作区只维护当前会话的虚拟目录。 */
    fun createWorkspaceDirectory(requestedPath: String): Boolean {
      val normalizedPath = requestedPath.normalizeWorkspacePath()
      val error = when {
        normalizedPath.isEmpty() -> "文件夹路径不能为空。"
        !normalizedPath.isSafeWorkspacePath() ->
          "文件夹必须使用工作区内的相对路径。"
        normalizedPath in directoryPaths || normalizedPath in sourceFiles ||
          sourceFiles.keys.any { it.startsWith("$normalizedPath/") } ->
          "文件夹已存在：$normalizedPath"
        normalizedPath.pathPrefixes().any(sourceFiles::containsKey) ->
          "文件夹路径中包含同名文件：$normalizedPath"
        else -> null
      }
      if (error != null) {
        output = error
        return false
      }
      val applyCreatedDirectory = {
        (normalizedPath.parentDirectoryPaths() + normalizedPath).forEach { directory ->
          if (directory !in directoryPaths) directoryPaths += directory
        }
        output = "已创建文件夹 $normalizedPath"
      }
      val projectId = argument.projectId
      if (projectId == null) {
        applyCreatedDirectory()
      } else {
        coroutineScope.launch {
          runCatching {
            check(projectRepository.createDirectory(projectId, normalizedPath)) {
              "文件夹已存在：$normalizedPath"
            }
          }.onSuccess { applyCreatedDirectory() }
            .onFailure { throwable -> output = throwable.toFailureText("创建项目文件夹失败") }
        }
      }
      return true
    }

    /** 重新扫描磁盘并以磁盘内容为准，使外部新增、删除与修改及时反映到文件树和编辑器。 */
    fun refreshProjectFromDisk() {
      val projectId = argument.projectId ?: return
      coroutineScope.launch {
        runCatching { projectRepository.openProject(projectId) }.onSuccess { refreshed ->
          val previousActivePath = activeFilePath
          val nextActivePath = previousActivePath.takeIf(refreshed.sourceFiles::containsKey)
            ?: refreshed.activeFilePath
          sourceFiles.clear()
          sourceFiles.putAll(refreshed.sourceFiles)
          persistedSourceFiles.clear()
          persistedSourceFiles.putAll(refreshed.sourceFiles)
          directoryPaths.clear()
          directoryPaths.addAll(refreshed.directoryPaths.sorted())
          openFilePaths.retainAll(refreshed.sourceFiles.keys)
          if (nextActivePath !in openFilePaths) openFilePaths += nextActivePath
          cursorPositions.keys.retainAll(refreshed.sourceFiles.keys)
          cursorPositions.keys.toList().forEach { path ->
            cursorPositions[path] = cursorPositions.getValue(path)
              .coerceIn(0, refreshed.sourceFiles.getValue(path).length)
          }
          editorState.replaceDocument(
            nextActivePath,
            refreshed.sourceFiles.getValue(nextActivePath),
            cursorPositions[nextActivePath],
          )
          activeFilePath = nextActivePath
          loadedProjectWorkspace = refreshed
          output = "已从磁盘刷新项目，共 ${refreshed.sourceFiles.size} 个源码文件。"
        }.onFailure { throwable ->
          output = throwable.toFailureText("刷新项目失败")
        }
      }
    }

    val displayedSourceFiles = sourceFiles.toMutableMap().apply {
      this[activeFilePath] = editorState.code
    }
    val activeLanguageDisplayName = supportedLanguages
      .firstOrNull { it.languageId == activeLanguageId }
      ?.displayName
      ?: "代码"
    // 即时步骤先覆盖持久化快照，避免异步写盘期间课程卡片的课时数短暂回退。
    val displayedTutorialProgress = savedTutorialProgress.toMutableMap().apply {
      activeTutorial?.let { active ->
        val courseId = active.course.summary.courseId
        val previous = this[courseId]
        val session = tutorialSession
        if (previous != null) {
          this[courseId] = previous.copy(
            lessonId = active.lesson.lessonId,
            stepId = active.step.stepId,
            completedSteps = active.completedSteps.toList(),
            isCourseCompleted = active.isCompleted,
          )
        } else if (session != null) {
          this[courseId] = DynamicTutorialProgress(
            languageId = session.tutorial.languageId,
            npmPackageName = session.tutorial.npmPackageName,
            npmPackageVersion = session.npmPackageVersion,
            courseId = courseId,
            lessonId = active.lesson.lessonId,
            stepId = active.step.stepId,
            completedSteps = active.completedSteps.toList(),
            isCourseCompleted = active.isCompleted,
          )
        }
      }
    }
    // 当前课程完成后按 Manifest 的稳定顺序寻找下一门已解锁课程，避免先退回课程路径再二次选择。
    val nextTutorialCourse = activeTutorial
      ?.takeIf(ActiveCodeEditorTutorial::isCompleted)
      ?.let { active ->
        val completedCourseIds = displayedTutorialProgress.values
          .filter(DynamicTutorialProgress::isCourseCompleted)
          .mapTo(linkedSetOf(), DynamicTutorialProgress::courseId)
        val availability = tutorialManifest?.resolveCourseAvailability(
          completedCourseIds = completedCourseIds,
          startedCourseIds = displayedTutorialProgress.keys,
        ).orEmpty()
        val currentIndex = availability.indexOfFirst { candidate ->
          candidate.course.courseId == active.course.summary.courseId
        }
        if (currentIndex < 0) {
          null
        } else {
          availability
            .drop(currentIndex + 1)
            .firstOrNull { candidate ->
              candidate.state == DynamicTutorialCourseState.AVAILABLE ||
                candidate.state == DynamicTutorialCourseState.IN_PROGRESS
            }
            ?.course
        }
      }
    val tutorialSidePanel = if (isTutorialWorkspace) {
      rememberCodeEditorTutorialSidePanel(
        manifest = tutorialManifest,
        status = tutorialStatus,
        isLoading = isLoadingTutorial,
        canRetryLoad = canRetryTutorialLoad,
        activeCourseId = activeTutorial?.course?.summary?.courseId,
        progressByCourseId = displayedTutorialProgress,
        onRetryLoad = { tutorialLoadGeneration++ },
        onSwitchLanguage = {
          showTutorialLanguagePicker = true
          if (tutorialLanguageChoices == null && !isLoadingTutorialLanguages) {
            isLoadingTutorialLanguages = true
            tutorialLanguageLoadError = null
            coroutineScope.launch {
              runCatching { dynamicTutorialManager.supportedTutorials() }
                .onSuccess { tutorialLanguageChoices = it }
                .onFailure {
                  tutorialLanguageLoadError = it.message ?: "读取教程语言失败。"
                }
              isLoadingTutorialLanguages = false
            }
          }
        },
        onOpenCourse = ::openTutorialCourse,
        onResetCourse = ::resetTutorialCourse,
      )
    } else {
      null
    }
    val sidePanels = rememberCodeEditorTestSidePanels(
      activeFilePath = activeFilePath,
      sourceFiles = displayedSourceFiles,
      directoryPaths = directoryPaths,
      languageStatus = languageStatus,
      isLanguageReady = dynamicLanguageService != null,
      isLoadingLanguage = isLoadingLanguage,
      isAnalyzingSymbol = isAnalyzingSymbol,
      highlightCacheCapacity = highlightCacheCapacity,
      unsupportedCapabilityStatistics = unsupportedCapabilityStatistics,
      leadingPanels = listOfNotNull(tutorialSidePanel),
      projectPath = loadedProjectWorkspace?.directoryDisplayPath
        ?: "/${initialTemplate.defaultProjectName}",
      fileIcon = dynamicDocumentIcon,
      onOpenFile = ::openFile,
      onCreateFile = ::createWorkspaceFile,
      onCreateFolder = ::createWorkspaceDirectory,
      onRenamePath = ::renameWorkspacePath,
      onDeletePath = ::deleteWorkspacePath,
      onRefreshProject = if (argument.projectId != null) ::refreshProjectFromDisk else null,
      onSwitchProject = {
        leaveWorkspace {
          // 入口页已经在进入工作区时出栈；切换项目需要显式重建入口，再移除当前工作区。
          CodeWorkspaceHomeNavArgument.navigate()
          argument.popBackStack()
        }
      },
      onOpenProjectDirectory = {
        val directory = loadedProjectWorkspace?.directory
        if (directory == null) {
          toast("教程工作区没有可在文件管理器中打开的本地目录。")
        } else {
          openProjectDirectory(directory).onFailure {
            toast(it.message ?: "无法打开项目目录。")
          }
        }
      },
      onLoadLanguage = {
        activeLanguageId?.let { languageId ->
          coroutineScope.launch { loadLanguageService(languageId) }
        }
      },
      onHighlightCacheCapacityChange = { capacity ->
        highlightCacheCapacity = capacity
      },
      onClearUnsupportedCapabilityStatistics = {
        coroutineScope.launch {
          dynamicLanguageManager.clearUnsupportedCapabilityStatistics()
          unsupportedCapabilityStatistics = emptyList()
        }
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
                val updatedSources = linkedMapOf<String, String>()
                editsByFile.forEach { (filePath, sourceEdits) ->
                  val source = requestedWorkspace.files.first { file -> file.path == filePath }.source
                  val edits = sourceEdits.map { sourceEdit -> sourceEdit.edit }
                  updatedSources[filePath] = source.applyTextEdits(edits)
                }
                rename.fileRenames.forEach { fileRename ->
                  val renamedSource = updatedSources.remove(fileRename.oldPath)
                    ?: requestedWorkspace.files
                      .firstOrNull { file -> file.path == fileRename.oldPath }
                      ?.source
                    ?: error("待重命名文件不存在：${fileRename.oldPath}")
                  updatedSources[fileRename.newPath] = renamedSource
                }
                argument.projectId?.let { projectId ->
                  val updatedProject = projectRepository.applySourceTransaction(
                    projectId = projectId,
                    updatedSources = updatedSources,
                    fileRenames = rename.fileRenames.map { fileRename ->
                      CodeProjectFileRename(fileRename.oldPath, fileRename.newPath)
                    },
                    expectedSources = buildMap {
                      editsByFile.keys.forEach { path ->
                        persistedSourceFiles[path]?.let { source -> put(path, source) }
                      }
                      rename.fileRenames.forEach { fileRename ->
                        persistedSourceFiles[fileRename.oldPath]?.let { source ->
                          put(fileRename.oldPath, source)
                        }
                      }
                    },
                  )
                  loadedProjectWorkspace = loadedProjectWorkspace?.copy(project = updatedProject)
                }
                editsByFile.forEach { (filePath, sourceEdits) ->
                  val edits = sourceEdits.map { sourceEdit -> sourceEdit.edit }
                  sourceFiles[filePath] = updatedSources[filePath]
                    ?: sourceFiles.getValue(filePath).applyTextEdits(edits)
                  if (filePath == activeFilePath) editorState.applyTextEdits(edits)
                }
                rename.fileRenames.forEach { fileRename ->
                  sourceFiles.remove(fileRename.oldPath)
                  persistedSourceFiles.remove(fileRename.oldPath)
                  val renamedSource = updatedSources.getValue(fileRename.newPath)
                  sourceFiles[fileRename.newPath] = renamedSource
                  persistedSourceFiles[fileRename.newPath] = renamedSource
                  fileRename.newPath.parentDirectoryPaths().forEach { directory ->
                    if (directory !in directoryPaths) directoryPaths += directory
                  }
                  openFilePaths.indexOf(fileRename.oldPath).takeIf { it >= 0 }?.let { index ->
                    openFilePaths[index] = fileRename.newPath
                  }
                  if (activeFilePath == fileRename.oldPath) {
                    activeFilePath = fileRename.newPath
                    editorState.replaceDocument(fileRename.newPath, renamedSource)
                  }
                }
                updatedSources.forEach { (path, source) ->
                  persistedSourceFiles[path] = source
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
    val runToolWindows = codeEditorTestToolWindows(
      activeFilePath = activeFilePath,
      output = output,
      performanceText = autoHighlightReport,
      diagnostics = runDiagnostics,
      diagnosticSources = runDiagnosticSources,
      onDiagnosticSelected = { location ->
        val source = sourceFiles[location.filePath] ?: return@codeEditorTestToolWindows
        val from = location.range.from.coerceIn(0, source.length)
        val to = location.range.to.coerceIn(from, source.length)
        openFile(location.filePath, DynamicTextRange(from, to))
      },
    )
    val toolWindows = activeTutorial?.let { tutorial ->
      listOf(
        codeEditorTutorialToolWindow(
          tutorial = tutorial,
          onLessonSelected = ::openTutorialLesson,
          onNextCourse = nextTutorialCourse?.let { nextCourse ->
            {
              coroutineScope.launch {
                // 先落盘完成状态，保证下一课程的前置条件与恢复现场使用同一份进度。
                persistTutorialProgress(currentTutorialDraft(tutorial))
                openTutorialCourse(nextCourse.courseId)
              }
            }
          },
          onPrevious = {
            activeTutorial = tutorial.previous()
          },
          onCheck = {
            coroutineScope.launch {
              try {
                evaluateTutorialStep()
              } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                activeTutorial = activeTutorial?.copy(
                  feedback = throwable.toFailureText("教程步骤校验失败"),
                )
              }
            }
          },
        ),
      ) + runToolWindows
    } ?: runToolWindows
    val dirtyDocumentLabels = if (argument.projectId == null) {
      emptySet()
    } else {
      openFilePaths.filterTo(linkedSetOf()) { filePath ->
        val currentSource = if (filePath == activeFilePath) editorState.code else sourceFiles[filePath]
        currentSource != null && currentSource != persistedSourceFiles[filePath]
      }
    }
    val layoutGuide = activeTutorial
      ?.takeUnless(ActiveCodeEditorTutorial::isCurrentLessonCompleted)
      ?.step
      ?.guideTarget
      ?.takeIf { it.kind == DynamicTutorialGuideTargetKind.LAYOUT_ANCHOR }

    Box(Modifier.fillMaxSize()) {
      CodeEditorWorkbench(
        title = activeTutorial?.course?.summary?.title ?: "$activeLanguageDisplayName 课程",
        activeDocumentLabel = activeFilePath,
        subtitle = activeTutorial?.lesson?.title ?: "多文件语义分析 · 实验课",
        openDocumentLabels = openFilePaths.toList(),
        dirtyDocumentLabels = dirtyDocumentLabels,
        breadcrumbs = activeFilePath.split('/'),
        onDocumentSelected = ::openFile,
        onDocumentCloseRequested = ::closeDocument,
        documentIcon = dynamicDocumentIcon,
        sidePanels = sidePanels,
        toolWindows = toolWindows,
        state = workbenchState,
        modifier = Modifier.fillMaxSize(),
        isRunning = isRunning,
        onBack = { leaveWorkspace(argument::popBackStack) },
        onRun = ::runWorkspace,
        runButtonModifier = Modifier.guidedTourTarget(
          targetId = DynamicTutorialAnchorIds.RUN_BUTTON,
          registry = guidedTourRegistry,
        ),
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
      val anchorId = layoutGuide?.anchorId
      if (anchorId != null) {
        GuidedTourOverlay(
          registry = guidedTourRegistry,
          targetId = anchorId,
          modifier = Modifier.fillMaxSize(),
        ) { bounds ->
          TutorialGuideHint(
            targetBounds = bounds,
            text = activeTutorial?.step?.content?.firstOrNull()?.text ?: "完成当前教程步骤。",
          )
        }
      }
      if (showTutorialLanguagePicker) {
        WorkspaceLanguageDialog(
          title = "切换教程",
          subtitle = "选择另一门语言的学习路径",
          choices = tutorialLanguageChoices.orEmpty().map { tutorial ->
            WorkspaceLanguageChoice(
              languageId = tutorial.languageId,
              displayName = tutorial.displayName,
              description = "打开 ${tutorial.displayName} 学习路径",
            )
          },
          isLoading = isLoadingTutorialLanguages,
          emptyMessage = tutorialLanguageLoadError,
          onDismiss = { showTutorialLanguagePicker = false },
          onSelect = { choice ->
            showTutorialLanguagePicker = false
            argument.popBackStack()
            CodeEditorTestNavArgument(tutorialLanguageId = choice.languageId).navigate()
          },
        )
      }
      pendingSourceConflict?.let { conflict ->
        ProjectSourceConflictDialog(
          conflict = conflict,
          isResolving = isResolvingSourceConflict,
          onReload = { reloadConflictingSource(conflict) },
          onOverwrite = { overwriteConflictingSource(conflict) },
          onSaveCopy = { saveConflictingSourceAsCopy(conflict) },
        )
      }
    }
  }

  /**
   * 外部修改冲突必须由用户明确选定数据保留策略，因此不允许点击遮罩直接消失。
   * 三个动作分别对应保留磁盘、保留编辑器和同时保留两份内容。
   */
  @Composable
  private fun ProjectSourceConflictDialog(
    conflict: ProjectSourceConflict,
    isResolving: Boolean,
    onReload: () -> Unit,
    onOverwrite: () -> Unit,
    onSaveCopy: () -> Unit,
  ) {
    AlertDialog(
      onDismissRequest = {},
      backgroundColor = EditorWorkbenchColors.PanelBackground,
      contentColor = EditorWorkbenchColors.PrimaryText,
      title = { Text("文件已在外部修改", fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = conflict.filePath,
            color = EditorWorkbenchColors.PrimaryText,
            fontSize = 12.sp,
            maxLines = 1,
          )
          Text(
            text = "重新加载会丢弃编辑器修改；覆盖会替换磁盘版本；另存副本会同时保留两份内容。",
            color = EditorWorkbenchColors.SecondaryText,
            fontSize = 12.sp,
          )
        }
      },
      confirmButton = {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(enabled = !isResolving, onClick = onReload) { Text("重新加载") }
          TextButton(enabled = !isResolving, onClick = onSaveCopy) { Text("另存副本") }
          TextButton(enabled = !isResolving, onClick = onOverwrite) { Text("覆盖磁盘") }
        }
      },
    )
  }

  /** 将统一动态语言运行结果整理成输出面板可直接阅读的文本。 */
  private fun DynamicProgramRunResult.toDisplayText(target: DynamicRunTarget): String = buildString {
    compilationMetrics?.let { metrics ->
      append("编译：").append(metrics.cacheMode)
        .append(" ｜ ").append(metrics.totalMicroseconds / 1_000.0).appendLine(" ms")
    }
    if (standardOutput.isNotEmpty()) append(standardOutput)
    if (standardError.isNotEmpty()) {
      if (isNotEmpty() && last() != '\n') appendLine()
      append(standardError)
    }
    if (outputTruncated) {
      if (isNotEmpty() && last() != '\n') appendLine()
      append("⚠ 输出已截断，丢弃 ").append(droppedOutputBytes).appendLine(" 个 UTF-8 字节。")
    }
    if (isNotEmpty() && last() != '\n') appendLine()
    if (executed) {
      append("运行完成：").append(target.displayName)
      returnValue?.let { value -> append("\n返回值：").append(value) }
    } else if (diagnostics.isNotEmpty()) {
      append("编译失败：共 ").append(diagnostics.size).append(" 条诊断，点击下方条目可定位源码。")
    } else {
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
    (this@toFailureText as? DynamicLanguageExecutionException)?.sourceFrames
      ?.take(MAX_DISPLAYED_SOURCE_FRAMES)
      ?.forEach { frame ->
        append("\n  at ").append(frame.sourceLocation.filePath)
          .append(" [").append(frame.sourceLocation.range.from)
          .append("..").append(frame.sourceLocation.range.to).append(']')
      }
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

  /** 统一用户输入的项目相对路径分隔符；是否越界由 [isSafeWorkspacePath] 单独校验。 */
  private fun String.normalizeWorkspacePath(): String = trim().replace('\\', '/')

  /** 判断路径是否由项目内的普通名称段组成，拒绝绝对路径、空段和目录穿越。 */
  private fun String.isSafeWorkspacePath(): Boolean {
    return isNotEmpty() && !startsWith('/') && split('/').none { segment ->
      segment.isEmpty() || segment == "." || segment == ".."
    }
  }

  /** 返回路径的父目录层级，用于同步空目录状态并检查文件阻断。 */
  private fun String.parentDirectoryPaths(): List<String> {
    val parentSegments = split('/').dropLast(1)
    return parentSegments.indices.map { index -> parentSegments.take(index + 1).joinToString("/") }
  }

  /** 返回文件或目录路径之前的所有层级，不包含路径自身。 */
  private fun String.pathPrefixes(): List<String> = parentDirectoryPaths()

  /** 将文件或目录本身及其后代从旧前缀映射到新前缀。 */
  private fun String.remapWorkspacePath(oldPath: String, newPath: String): String = when {
    this == oldPath -> newPath
    startsWith("$oldPath/") -> newPath + removePrefix(oldPath)
    else -> this
  }

  /** Compose 快照流中的不可变教程保存草稿，避免延迟写入读取到另一门课程的状态。 */
  private data class TutorialProgressDraft(
    val tutorial: ActiveCodeEditorTutorial,
    val activeFilePath: String,
    val workspace: List<DynamicTutorialSourceFile>,
  )

  /** 等待用户选择保留策略的外部文件修改冲突。 */
  private data class ProjectSourceConflict(
    val filePath: String,
    val onResolved: (() -> Unit)? = null,
  )

  private companion object {
    const val DISPLAY_RESULT_LIMIT = 12
    const val MAX_DISPLAYED_SOURCE_FRAMES = 8
    const val MICROSECONDS_PER_MILLISECOND = 1_000
    const val AUTO_HIGHLIGHT_DELAY_MILLIS = 200L
    const val RUN_TARGET_REFRESH_DELAY_MILLIS = 150L
    const val TUTORIAL_PROGRESS_SAVE_DELAY_MILLIS = 600L
    const val PROJECT_SOURCE_SAVE_DELAY_MILLIS = 500L
    const val PROJECT_SESSION_SAVE_DELAY_MILLIS = 350L

    /** 新建文件在识别具体语言前使用的最小占位内容。 */
    val DEFAULT_NEW_FILE_CODE = """
      // 在这里编写新模块
    """.trimIndent()
  }
}
