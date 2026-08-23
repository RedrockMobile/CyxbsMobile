package com.cyxbs.functions.code.editor.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import com.cyxbs.functions.code.editor.project.CodeProjectRepository
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
    var projectLoadError by remember(argument.projectId) { mutableStateOf<String?>(null) }
    var projectLoadGeneration by remember(argument.projectId) { mutableStateOf(0) }

    // 真实项目完成读取前不创建编辑器，避免旧 mock 源码和错误活动文件短暂闪现。
    LaunchedEffect(argument.projectId, projectLoadGeneration) {
      val projectId = argument.projectId ?: return@LaunchedEffect
      projectLoadError = null
      runCatching { projectRepository.openProject(projectId) }
        .onSuccess { loadedProjectWorkspace = it }
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
    val initialActiveFilePath = loadedProjectWorkspace?.activeFilePath ?: initialTemplate.activeFilePath
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
    val openFilePaths = remember(argument.projectId, argument.tutorialLanguageId) {
      mutableStateListOf(initialActiveFilePath)
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
      val previousFilePath = activeFilePath
      val previousSource = editorState.code
      sourceFiles[previousFilePath] = previousSource
      val targetSource = sourceFiles[filePath] ?: error("文件不存在：$filePath")
      if (filePath !in openFilePaths) openFilePaths += filePath
      editorState.replaceDocument(filePath, targetSource, selection?.from)
      activeFilePath = filePath
      selection?.let(editorState::selectRange)
      argument.projectId?.let { projectId ->
        coroutineScope.launch {
          runCatching {
            projectRepository.saveSource(projectId, previousFilePath, previousSource)
            projectRepository.updateActiveFile(projectId, filePath)
          }.onFailure { throwable ->
            output = throwable.toFailureText("项目文件保存失败")
          }
        }
      }
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
      openFilePaths.clear()
      openFilePaths += resumeState.activeFilePath
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
      snapshotFlow { editorState.code }.collectLatest { source ->
        delay(PROJECT_SOURCE_SAVE_DELAY_MILLIS)
        try {
          projectRepository.saveSource(projectId, requestedFilePath, source)
          if (activeFilePath == requestedFilePath) sourceFiles[requestedFilePath] = source
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("项目文件自动保存失败")
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
        argument.projectId?.let { projectId ->
          coroutineScope.launch {
            runCatching {
              check(projectRepository.createFile(projectId, normalizedPath)) {
                "文件已存在：$normalizedPath"
              }
              projectRepository.saveSource(projectId, normalizedPath, DEFAULT_NEW_FILE_CODE)
            }.onFailure { throwable ->
              output = throwable.toFailureText("创建项目文件失败")
            }
          }
        }
        output = "已创建并打开 $normalizedPath"
        true
      }
    }

    /** 校验并在真实项目目录中创建文件夹；教程工作区只维护当前会话的虚拟目录。 */
    fun createWorkspaceDirectory(requestedPath: String): Boolean {
      val normalizedPath = requestedPath.trim().replace('\\', '/')
      val error = when {
        normalizedPath.isEmpty() -> "文件夹路径不能为空。"
        normalizedPath.startsWith('/') || normalizedPath.split('/').any { it == ".." } ->
          "文件夹必须使用工作区内的相对路径。"
        sourceFiles.keys.any { it == normalizedPath || it.startsWith("$normalizedPath/") } ->
          "文件夹已存在：$normalizedPath"
        else -> null
      }
      if (error != null) {
        output = error
        return false
      }
      argument.projectId?.let { projectId ->
        coroutineScope.launch {
          runCatching { projectRepository.createDirectory(projectId, normalizedPath) }
            .onFailure { throwable -> output = throwable.toFailureText("创建项目文件夹失败") }
        }
      }
      output = "已创建文件夹 $normalizedPath"
      return true
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
      onSwitchProject = {
        // 入口页已经在进入工作区时出栈；切换项目需要显式重建入口，再移除当前工作区。
        CodeWorkspaceHomeNavArgument.navigate()
        argument.popBackStack()
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
        breadcrumbs = activeFilePath.split('/'),
        onDocumentSelected = ::openFile,
        documentIcon = dynamicDocumentIcon,
        sidePanels = sidePanels,
        toolWindows = toolWindows,
        state = workbenchState,
        modifier = Modifier.fillMaxSize(),
        isRunning = isRunning,
        onBack = argument::popBackStack,
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
    }
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

  /** Compose 快照流中的不可变教程保存草稿，避免延迟写入读取到另一门课程的状态。 */
  private data class TutorialProgressDraft(
    val tutorial: ActiveCodeEditorTutorial,
    val activeFilePath: String,
    val workspace: List<DynamicTutorialSourceFile>,
  )

  private companion object {
    const val DISPLAY_RESULT_LIMIT = 12
    const val MAX_DISPLAYED_SOURCE_FRAMES = 8
    const val MICROSECONDS_PER_MILLISECOND = 1_000
    const val AUTO_HIGHLIGHT_DELAY_MILLIS = 200L
    const val RUN_TARGET_REFRESH_DELAY_MILLIS = 150L
    const val TUTORIAL_PROGRESS_SAVE_DELAY_MILLIS = 600L
    const val PROJECT_SOURCE_SAVE_DELAY_MILLIS = 500L

    /** 新建文件在识别具体语言前使用的最小占位内容。 */
    val DEFAULT_NEW_FILE_CODE = """
      // 在这里编写新模块
    """.trimIndent()
  }
}
