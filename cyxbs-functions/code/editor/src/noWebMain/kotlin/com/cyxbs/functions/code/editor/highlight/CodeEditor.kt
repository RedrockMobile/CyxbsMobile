package com.cyxbs.functions.code.editor.highlight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.functions.code.editor.theme.CodeEditorConnectedCornerRadius
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorCodeEditorSetup
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorDynamicHighlightExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorDynamicCompletionExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorPlainTextLanguageExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorRunOrFoldGutterExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorSearchExtension
import com.cyxbs.functions.code.editor.highlight.internal.KodeMirrorSearchPanel
import com.cyxbs.functions.code.editor.highlight.internal.RunTargetGutterWidth
import com.cyxbs.functions.code.editor.highlight.internal.codeEditorSearchPanelOpen
import com.cyxbs.functions.code.editor.highlight.internal.EditorSessionCache
import com.cyxbs.functions.code.editor.highlight.internal.replaceDynamicHighlights
import com.cyxbs.functions.code.editor.highlight.internal.toggleCodeEditorSearchPanelVisibility
import com.cyxbs.functions.code.language.js.bridge.DynamicFileRename
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.js.bridge.DynamicTextEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceMethodNotImplementedException
import com.monkopedia.kodemirror.commands.redoDepth
import com.monkopedia.kodemirror.commands.redo as kodeMirrorRedo
import com.monkopedia.kodemirror.commands.undoDepth
import com.monkopedia.kodemirror.commands.undo as kodeMirrorUndo
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorLayout
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.GutterType
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.editorContentStyle
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.gutters
import com.monkopedia.kodemirror.view.rememberEditorSession
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 代码编辑器状态。
 *
 * 状态内部持有 KodeMirror 会话，对外只暴露当前源码，避免业务代码依赖第三方编辑器类型。
 * [code] 会读取编辑器的最新文档，可直接在“运行”操作触发时获取用户输入。
 */
@Stable
class CodeEditorState internal constructor(
  session: EditorSession,
  initialFilePath: String,
  initialSource: String,
  private val createSession: (String) -> EditorSession,
  private val languageService: () -> DynamicLanguageService?,
  private val workspace: () -> DynamicLanguageWorkspace,
  highlightCacheCapacity: Int,
) {
  internal var session: EditorSession by mutableStateOf(session)
    private set
  private var currentFilePath = initialFilePath
  private val sessionCache = EditorSessionCache(
    capacity = highlightCacheCapacity,
    initialFilePath = initialFilePath,
    initialSource = initialSource,
    initialSession = session,
  )

  /**
   * 编辑器最多保留的文件会话数量；默认由 [rememberCodeEditorState] 设置为 20。
   *
   * 调整为更小的值会立即淘汰最久未访问的文件；设为 0 时只保留当前文件，不跨文件缓存。
   */
  var highlightCacheCapacity: Int
    get() = sessionCache.capacity
    set(value) {
      sessionCache.capacity = value
    }

  /** 当前编辑器中的完整源码。 */
  val code: String
    get() = session.state.doc.toString()

  /** 当前主光标的 UTF-16 偏移；存在选区时返回活动端位置。 */
  val cursorPosition: Int
    get() = session.state.selection.main.head.value

  /** 当前编辑文档在工作区中的相对路径。 */
  val filePath: String
    get() = currentFilePath

  /** 当前是否存在可撤销的源码编辑；读取时会随 KodeMirror 会话事务自动触发 Compose 重组。 */
  val canUndo: Boolean
    get() = undoDepth(session.state) > 0

  /** 当前是否存在可重做的源码编辑；新的普通编辑会清空该状态。 */
  val canRedo: Boolean
    get() = redoDepth(session.state) > 0

  /**
   * 撤销最近一次可撤销的编辑事务。
   *
   * @return 成功撤销时为 true；历史栈为空时为 false，源码保持不变。
   */
  fun undo(): Boolean = kodeMirrorUndo(session)

  /**
   * 重做最近一次被撤销的编辑事务。
   *
   * @return 成功重做时为 true；重做栈为空时为 false，源码保持不变。
   */
  fun redo(): Boolean = kodeMirrorRedo(session)

  /**
   * 切换当前文档搜索面板。
   *
   * 搜索状态与匹配算法仍由 KodeMirror 提供，面板使用编辑器模块自有的跨平台 Compose UI；
   * 面板关闭时调用会打开，面板已经打开时调用会关闭，不会触发动态语言服务请求。
   */
  fun openSearch(): Boolean = toggleCodeEditorSearchPanelVisibility(session)

  /**
   * 将动态语言服务针对当前源码返回的高亮区间应用到编辑器。
   *
   * 调用方必须保证 [highlights] 对应当前 [code]；后续文档发生编辑时旧高亮会自动清空，避免
   * 区间错位，待下一次动态分析完成后再调用本方法即可刷新。
   */
  fun applyHighlights(highlights: List<DynamicHighlightSpan>) {
    sessionCache.updateSource(filePath = filePath, source = code)
    sessionCache.markHighlighted(filePath = filePath, source = code)
    session.replaceDynamicHighlights(highlights)
  }

  /**
   * 检查当前文件会话是否已经携带与源码完全匹配的高亮装饰。
   *
   * 文件切换已经恢复完整 KodeMirror 会话，因此命中时无需再次提交装饰事务；未命中时调用方
   * 应请求动态语言服务重新分析。
   *
   * @return 当前会话已带有有效高亮时为 true，否则为 false。
   */
  fun hasCachedHighlights(filePath: String, source: String): Boolean {
    if (this.filePath != filePath || code != source) return false
    sessionCache.updateSource(filePath = filePath, source = source)
    return sessionCache.hasHighlights(filePath = filePath, source = source)
  }

  /** 清空所有文件会话中的高亮装饰；替换动态语言服务后调用以避免规则版本不一致。 */
  fun clearHighlightCache() {
    sessionCache.clearHighlights { cachedSession ->
      cachedSession.replaceDynamicHighlights(emptyList())
    }
  }

  /**
   * 查询主光标所在符号的工作区定义。
   *
   * 返回值可以指向其他文件；调用方应先切换文档，再调用 [selectRange] 选中定义。
   * 动态服务尚未加载或光标不在可索引符号上时返回 null。
   *
   * @throws NpmJsServiceMethodNotImplementedException 旧语言包不支持定义查询。
   * @throws NpmJsServiceInvocationException JavaScript 服务执行失败。
   * @throws SerializationException 动态协议编码或解码失败。
   * @throws CancellationException 调用协程被取消。
   * @throws IllegalStateException 请求期间编辑器源码发生变化。
   * @throws IllegalArgumentException 动态包返回了越界定义区间。
   */
  @Throws(
    NpmJsServiceMethodNotImplementedException::class,
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
    IllegalStateException::class,
    IllegalArgumentException::class,
  )
  suspend fun findDefinitionAtCursor(): DynamicSymbolDefinition? {
    val service = languageService() ?: return null
    val requestedFilePath = filePath
    val source = code
    val requestedWorkspace = currentWorkspace(requestedFilePath, source)
    val definition = service.definition(
      workspace = requestedWorkspace,
      filePath = requestedFilePath,
      position = cursorPosition,
    ) ?: return null
    checkContextUnchanged(requestedFilePath, source)
    definition.definition.requireValidFor(requestedWorkspace)
    return definition
  }

  /**
   * 查询主光标所在符号在工作区中的全部引用，结果不包含定义本身。
   *
   * @throws NpmJsServiceMethodNotImplementedException 旧语言包不支持引用查询。
   * @throws NpmJsServiceInvocationException JavaScript 服务执行失败。
   * @throws SerializationException 动态协议编码或解码失败。
   * @throws CancellationException 调用协程被取消。
   * @throws IllegalStateException 请求期间编辑器源码发生变化。
   * @throws IllegalArgumentException 动态包返回了越界引用区间。
   */
  @Throws(
    NpmJsServiceMethodNotImplementedException::class,
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
    IllegalStateException::class,
    IllegalArgumentException::class,
  )
  suspend fun findReferencesAtCursor(): DynamicSymbolReferencesResult? {
    val service = languageService() ?: return null
    val requestedFilePath = filePath
    val source = code
    val requestedWorkspace = currentWorkspace(requestedFilePath, source)
    val references = service.references(
      workspace = requestedWorkspace,
      filePath = requestedFilePath,
      position = cursorPosition,
    ) ?: return null
    checkContextUnchanged(requestedFilePath, source)
    references.symbol.definition.requireValidFor(requestedWorkspace)
    references.references.forEach { location -> location.requireValidFor(requestedWorkspace) }
    return references
  }

  /**
   * 为主光标所在词法符号计算工作区安全重命名修改。
   *
   * 本方法不直接修改任何文件。调用方应将 [DynamicRenameResult.edits] 与文件路径修改原子应用；
   * 当前文档的文本部分可用 [applyTextEdits] 更新，其他文件与路径则更新自身的工作区存储。
   *
   * @throws NpmJsServiceMethodNotImplementedException 旧语言包不支持重命名。
   * @throws NpmJsServiceInvocationException JavaScript 服务执行失败。
   * @throws SerializationException 动态协议编码或解码失败。
   * @throws CancellationException 调用协程被取消。
   * @throws IllegalStateException 请求期间编辑器源码发生变化。
   * @throws IllegalArgumentException 动态包返回了越界、重叠或乱序文本修改。
   */
  @Throws(
    NpmJsServiceMethodNotImplementedException::class,
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
    IllegalStateException::class,
    IllegalArgumentException::class,
  )
  suspend fun renameSymbolAtCursor(newName: String): DynamicRenameResult? {
    val service = languageService() ?: return null
    val requestedFilePath = filePath
    val source = code
    val requestedWorkspace = currentWorkspace(requestedFilePath, source)
    val result = service.rename(
      workspace = requestedWorkspace,
      filePath = requestedFilePath,
      position = cursorPosition,
      newName = newName,
    ) ?: return null
    checkContextUnchanged(requestedFilePath, source)
    result.symbol.definition.requireValidFor(requestedWorkspace)
    if (result.isSuccess) {
      result.edits.requireValidFor(requestedWorkspace)
      result.fileRenames.requireValidFileRenamesFor(requestedWorkspace)
    }
    return result
  }

  /**
   * 切换到 [filePath] 对应的 [source]，并可将光标放到 [cursorPosition]。
   *
   * 源码未被外部改写时会直接切换到该文件原有的 KodeMirror 会话，使文档、高亮、光标和撤销栈
   * 同时恢复；不会再把旧文件装饰映射到新源码后进行第二次刷新。[cursorPosition] 为 null 时
   * 保留缓存会话原有光标，新创建的会话默认从文档开头开始。
   */
  fun replaceDocument(filePath: String, source: String, cursorPosition: Int? = null) {
    require(cursorPosition == null || cursorPosition in 0..source.length) {
      "cursorPosition must be inside source."
    }

    // 用户可能尚未触发下一轮高亮，切走前仍要保存编辑后的源码以便复用同一个会话。
    sessionCache.updateSource(filePath = currentFilePath, source = code)
    session = sessionCache.activate(filePath = filePath, source = source) {
      createSession(source)
    }
    currentFilePath = filePath
    if (cursorPosition != null) {
      session.dispatch(
        TransactionSpec(
          selection = SelectionSpec.CursorSpec(anchor = DocPos(cursorPosition)),
          scrollIntoView = true,
        ),
      )
    }
  }

  /** 选中当前文档中的 UTF-16 半开区间，并滚动到可见位置。 */
  fun selectRange(range: DynamicTextRange) {
    range.requireValidFor(code)
    session.dispatch(
      TransactionSpec(
        selection = SelectionSpec.CursorSpec(
          anchor = DocPos(range.from),
          head = DocPos(range.to),
        ),
        scrollIntoView = true,
      ),
    )
  }

  /** 将同一份原始文档的有序、不重叠修改作为一个 KodeMirror 事务应用。 */
  fun applyTextEdits(edits: List<DynamicTextEdit>) {
    edits.requireValidFor(code)
    if (edits.isEmpty()) return
    session.dispatch(
      TransactionSpec(
        changes = ChangeSpec.Multi(
          edits.map { edit ->
            ChangeSpec.Single(
              from = DocPos(edit.from),
              to = DocPos(edit.to),
              insert = edit.replacement.asInsert(),
            )
          },
        ),
        userEvent = "input.rename",
      ),
    )
  }

  /** 请求时用编辑器中的未保存文本覆盖工作区快照。 */
  private fun currentWorkspace(filePath: String, source: String): DynamicLanguageWorkspace {
    var replaced = false
    val files = workspace().files.map { file ->
      if (file.path == filePath) {
        replaced = true
        DynamicSourceFile(filePath, source)
      } else {
        file
      }
    }
    require(replaced) { "Workspace does not contain '$filePath'." }
    return DynamicLanguageWorkspace(files)
  }

  /** 阻止异步分析结果覆盖请求期间已切换或继续编辑的文档。 */
  private fun checkContextUnchanged(expectedFilePath: String, expectedSource: String) {
    check(filePath == expectedFilePath && code == expectedSource) {
      "Editor document changed while language analysis was running."
    }
  }

  /** 验证工作区位置指向快照内的真实文件区间。 */
  private fun DynamicSourceLocation.requireValidFor(workspace: DynamicLanguageWorkspace) {
    val source = workspace.files.firstOrNull { file -> file.path == filePath }?.source
      ?: throw IllegalArgumentException("Dynamic language result points to missing '$filePath'.")
    range.requireValidFor(source)
  }

  /** 验证动态包返回的半开区间可安全用于对应源码。 */
  private fun DynamicTextRange.requireValidFor(source: String) {
    require(from in 0..source.length && to in from..source.length) {
      "Dynamic language range [$from, $to) is outside source length ${source.length}."
    }
  }

  /** 文本修改必须按原始源码升序排列且互不重叠。 */
  private fun List<DynamicTextEdit>.requireValidFor(source: String) {
    var previousTo = 0
    forEach { edit ->
      DynamicTextRange(edit.from, edit.to).requireValidFor(source)
      require(edit.from >= previousTo) { "Dynamic language edits must be sorted and non-overlapping." }
      previousTo = edit.to
    }
  }

  /** 按文件分组校验工作区修改，允许不同文件使用各自的原始坐标。 */
  private fun List<DynamicSourceEdit>.requireValidFor(
    workspace: DynamicLanguageWorkspace,
  ) {
    groupBy { sourceEdit -> sourceEdit.filePath }.forEach { (filePath, sourceEdits) ->
      val source = workspace.files.firstOrNull { file -> file.path == filePath }?.source
        ?: throw IllegalArgumentException("Dynamic language edit points to missing '$filePath'.")
      sourceEdits.map { sourceEdit -> sourceEdit.edit }.requireValidFor(source)
    }
  }

  /** 校验文件重命名的源路径、目标路径和同一事务中的唯一性。 */
  private fun List<DynamicFileRename>.requireValidFileRenamesFor(workspace: DynamicLanguageWorkspace) {
    val workspacePaths = workspace.files.mapTo(mutableSetOf(), DynamicSourceFile::path)
    val oldPaths = map(DynamicFileRename::oldPath)
    val newPaths = map(DynamicFileRename::newPath)
    require(oldPaths.distinct().size == size) { "Dynamic language file rename sources must be unique." }
    require(newPaths.distinct().size == size) { "Dynamic language file rename targets must be unique." }
    forEach { rename ->
      require(rename.oldPath in workspacePaths) {
        "Dynamic language file rename points to missing '${rename.oldPath}'."
      }
      require(rename.newPath.isValidWorkspacePath()) {
        "Dynamic language file rename target '${rename.newPath}' is not a normalized relative path."
      }
      require(rename.newPath !in workspacePaths || rename.newPath in oldPaths) {
        "Dynamic language file rename target '${rename.newPath}' already exists."
      }
    }
  }
}

/** 工作区路径必须是使用 `/` 分隔且不包含空段、`.` 或 `..` 的相对路径。 */
private fun String.isValidWorkspacePath(): Boolean {
  if (isEmpty() || startsWith('/') || contains('\\')) return false
  return split('/').all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." }
}

/**
 * 创建并记住一个代码编辑器状态。
 *
 * @param initialCode 首次创建状态时使用的源码；后续重组不会覆盖用户已经编辑的内容。
 * @param activeFilePath 当前编辑文档在工作区中的相对路径；必须由调用方显式提供，避免编辑器
 * 隐式假定源码语言或文件扩展名。
 * @param workspace 当前完整工作区；默认使用 [activeFilePath] 和 [initialCode] 构造单文件工作区。
 * @param languageService 当前已经加载的动态语言服务；可先传 null，加载完成后的重组会让补全源
 * 立即使用新服务，而不会重建或覆盖编辑器文档。
 * @param highlightCacheCapacity 最多保留的文件会话数量，默认 20；运行时变化不会重建当前状态。
 * @param runTargets 当前语言在工作区发现的可运行入口；带源码位置的入口会显示在 gutter。
 * @param onRunTarget 点击 gutter 入口后的回调；为空时不处理运行标记点击。
 * @return 可读取当前源码并在多个组合节点间共享的编辑器状态。
 */
@Composable
fun rememberCodeEditorState(
  initialCode: String = "",
  activeFilePath: String,
  workspace: DynamicLanguageWorkspace = DynamicLanguageWorkspace(
    files = listOf(DynamicSourceFile(activeFilePath, initialCode)),
  ),
  languageService: DynamicLanguageService? = null,
  highlightCacheCapacity: Int = DEFAULT_HIGHLIGHT_CACHE_CAPACITY,
  runTargets: List<DynamicRunTarget> = emptyList(),
  onRunTarget: ((DynamicRunTarget) -> Unit)? = null,
): CodeEditorState {
  val currentLanguageService = rememberUpdatedState(languageService)
  val currentWorkspace = rememberUpdatedState(workspace)
  val currentFilePath = rememberUpdatedState(activeFilePath)
  val currentRunTargets = rememberUpdatedState(runTargets)
  val currentOnRunTarget = rememberUpdatedState(onRunTarget)
  val completionExtension = remember {
    kodeMirrorDynamicCompletionExtension(
      service = { currentLanguageService.value },
      workspace = { currentWorkspace.value },
      filePath = { currentFilePath.value },
    )
  }
  val runGutterExtension = remember {
    kodeMirrorRunOrFoldGutterExtension(
      targets = { currentRunTargets.value },
      activeFilePath = { currentFilePath.value },
      onRunTarget = { currentOnRunTarget.value },
    )
  }
  val editorExtensions = remember(completionExtension, runGutterExtension) {
    extensionListOf(
      kodeMirrorCodeEditorSetup,
      // 合并列放在行号之后，与上游 basicSetup 的折叠列位置保持一致。
      runGutterExtension,
      // 预先安装公开搜索状态，避免 openSearchPanel 注入无法定制的 KodeMirror 默认面板。
      kodeMirrorSearchExtension,
      editorTheme.of(codeEditorTheme),
      editorContentStyle.of(codeEditorTextStyle),
      kodeMirrorPlainTextLanguageExtension,
      kodeMirrorDynamicHighlightExtension,
      completionExtension,
    )
  }
  val session = rememberEditorSession(
    doc = initialCode,
    // basicSetup 强制要求存在 Language；纯文本占位仅维持编辑能力，不承担实际语法解析。
    extensions = editorExtensions,
  )
  val state = remember(session) {
    CodeEditorState(
      session = session,
      initialFilePath = activeFilePath,
      initialSource = initialCode,
      createSession = { source ->
        EditorSession(
          EditorState.create(
            EditorStateConfig(
              doc = source.asDoc(),
              extensions = editorExtensions,
            ),
          ),
        )
      },
      languageService = { currentLanguageService.value },
      workspace = { currentWorkspace.value },
      highlightCacheCapacity = highlightCacheCapacity,
    )
  }
  SideEffect {
    state.highlightCacheCapacity = highlightCacheCapacity
  }
  return state
}

/** 默认保留最近 20 个文件的编辑会话，业务可通过编辑器设置覆盖。 */
const val DEFAULT_HIGHLIGHT_CACHE_CAPACITY = 20

/** 编辑器代码区使用的深黑底色，与工作台代码背景保持一致。 */
private val CodeEditorBackground = Color(0xFF0F131B)

/** 行号栏使用的淡黑底色，与工作台目录面板保持一致。 */
private val CodeEditorGutterBackground = Color(0xFF1C2330)

/**
 * 与代码工作台设计稿一致的 KodeMirror 内部主题。
 *
 * 主题作为 State Extension 注入，避免业务模块接触第三方类型；后续接入正式编辑器设置时可将其
 * 替换为模块内部的通用主题模型，再在此处完成一次转换。
 */
private val codeEditorTheme = EditorTheme(
  // 根层负责绘制全高背景，透明色可避免 KodeMirror 用代码底色覆盖行号栏的空白延伸区域。
  background = Color.Transparent,
  foreground = Color(0xFFD8DFEC),
  cursor = Color(0xFF8E7CFF),
  selection = Color(0x4D6F5CFF),
  activeLineBackground = Color(0xFF181E2A),
  gutterBackground = CodeEditorGutterBackground,
  gutterForeground = Color(0xFF59647A),
  gutterActiveForeground = Color(0xFFD8DFEC),
  // 根层使用统一圆角轮廓绘制边界，因此关闭 KodeMirror 自身只支持直线的 gutter border。
  gutterBorderColor = Color.Transparent,
  panelBackground = Color(0xFF181E28),
  panelBorderColor = Color(0xFF30394B),
  buttonBackground = Color(0xFF232A39),
  buttonBorderColor = Color(0xFF3A465C),
  inputBackground = Color(0xFF121720),
  inputBorderColor = Color(0xFF30394B),
  tooltipBackground = Color(0xFF222938),
  activeLineGutterBackground = CodeEditorGutterBackground,
  // 运行入口和代码折叠复用同一列，不再为运行按钮增加额外横向间距。
  layout = EditorLayout(
    gutterEndPadding = 3.dp,
    customGutterWidth = RunTargetGutterWidth,
  ),
  dark = true,
)

/** 设计稿默认代码排版；字体仍使用跨平台系统等宽字体，不额外增加字体包体积。 */
private val codeEditorTextStyle = TextStyle(
  fontFamily = FontFamily.Monospace,
  fontSize = 13.sp,
  lineHeight = 19.sp,
)

/**
 * 计算当前 KodeMirror gutter 的真实宽度，供外层工作台对齐工具窗口。
 *
 * 宽度会随文档行数位数、字体缩放和已注册 gutter 动态变化；调用方不应再复制固定 dp 值。
 */
@Composable
internal fun CodeEditorState.editorGutterWidth(): Dp {
  val editorState = session.state
  val gutterConfigurations = editorState.facet(gutters)
  if (gutterConfigurations.isEmpty()) return 0.dp

  val density = LocalDensity.current
  return with(density) {
    val maxDigits = editorState.doc.lines.toString().length
    val digitWidth = (codeEditorTextStyle.fontSize.toPx() * 0.65F).toDp()
    val lineNumberWidth = digitWidth * maxDigits +
      codeEditorTheme.layout.gutterStartPadding +
      codeEditorTheme.layout.gutterEndPadding
    val extraGutterCount = gutterConfigurations.count { config ->
      config.type != GutterType.LineNumbers && config.lineMarker != null
    }
    lineNumberWidth + codeEditorTheme.layout.customGutterWidth * extraGutterCount
  }
}

/**
 * 绘制全高编辑器背景，并统一代码内容区左侧的两个圆角。
 *
 * KodeMirror 0.3.5 只会把 gutter 背景绘制到真实代码内容高度。这里在组件根层绘制全高代码
 * 与 gutter 背景，让行号区域自然延伸并包住代码区。深黑代码内容仅在左侧与行号栏衔接处
 * 保留圆角，右侧直接延伸到组件边界，不再绘制圆角或竖向描边。真实行号仍由 KodeMirror
 * 按文档行数绘制，空白延伸区不会产生伪行号。gutter 宽度严格复用其位数、字体、padding
 * 与自定义 gutter 算法。
 */
@Composable
private fun Modifier.roundedCodeAreaBackground(state: CodeEditorState): Modifier {
  val density = LocalDensity.current
  val gutterWidth = state.editorGutterWidth()
  val gutterWidthPx = with(density) { gutterWidth.toPx() }
  val radiusPx = with(density) { CodeEditorConnectedCornerRadius.toPx() }

  return drawWithContent {
    val codeStartX = gutterWidthPx.coerceIn(0F, size.width)
    val cornerRadius = radiusPx
      .coerceAtMost((size.width - codeStartX) / 2F)
      .coerceAtMost(size.height / 2F)
      .coerceAtLeast(0F)
    val codeBounds = Rect(
      Offset(codeStartX, 0F),
      Size(size.width - codeStartX, size.height),
    )
    val codeArea = RoundRect(
      rect = codeBounds,
      topLeft = CornerRadius(cornerRadius),
      bottomLeft = CornerRadius(cornerRadius),
    )
    val codeAreaPath = Path().apply {
      addRoundRect(codeArea)
    }
    val codeCornerMask = Path().apply {
      fillType = PathFillType.EvenOdd
      addRect(codeBounds)
      addRoundRect(codeArea)
    }

    // 先绘制完整淡黑行号栏，再使用仅左侧带圆角的轮廓填充深黑代码内容。
    drawRect(codeEditorTheme.gutterBackground)
    drawPath(codeAreaPath, color = CodeEditorBackground)
    drawContent()

    // KodeMirror 的行背景可能覆盖左侧角落；偶奇遮罩只恢复轮廓以外的淡黑区域。
    drawPath(codeCornerMask, color = codeEditorTheme.gutterBackground)
  }
}

/**
 * 使用纯 Compose KodeMirror 渲染可编辑的代码视图。
 *
 * 组件自身不绑定具体语言解析器，仅提供行号、折叠、搜索等通用编辑能力；语法解析与
 * 高亮、补全由动态语言包加载后接入，避免每种语言的解析实现都进入安装包。
 *
 * 当前组件仅进入 Android、iOS 与 Desktop 的 `noWebMain`。调用方应为组件提供有界高度，
 * KodeMirror 才能在编辑区域内部正确滚动并保持光标可见。
 *
 * @param state 由 [rememberCodeEditorState] 创建的编辑器状态。
 * @param modifier 应用到编辑器根节点的布局修饰符。
 */
@Composable
fun CodeEditor(
  state: CodeEditorState,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    KodeMirror(
      session = state.session,
      modifier = Modifier
        .fillMaxSize()
        .roundedCodeAreaBackground(state),
    )
    CodeEditorSearchPanelSlot(session = state.session)
  }
}

/**
 * 独立观察搜索状态并按需提供搜索面板布局节点。
 *
 * 搜索开关只会使本槽位重新组合；外层 [Box] 随节点增减重新测量，但不会因为读取容器约束
 * 而扩大 KodeMirror 的重组范围。
 */
@Composable
private fun CodeEditorSearchPanelSlot(session: EditorSession) {
  if (codeEditorSearchPanelOpen(session)) {
    KodeMirrorSearchPanel(
      session = session,
      modifier = Modifier.searchPanelLayout(),
    )
  }
}

/**
 * 仅在测量阶段根据编辑器约束摆放搜索面板。
 *
 * 修饰符自身占满父级 [Box]，搜索面板在其内部按实际宽度测量：手机宽度下贴底铺满，宽屏下
 * 限宽并放置在右上角。容器尺寸变化只会重新测量本节点，不会使 KodeMirror 读取布局约束。
 */
private fun Modifier.searchPanelLayout(): Modifier = layout { measurable, constraints ->
  val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
  val layoutHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
  val edgeSpacing = SearchPanelEdgeSpacing.roundToPx()
  val useBottomSearchPanel = layoutWidth < PhoneSearchPanelBreakpoint.roundToPx()
  val searchWidth = if (useBottomSearchPanel) {
    layoutWidth
  } else {
    minOf(
      SearchPanelMaximumWidth.roundToPx(),
      (layoutWidth - edgeSpacing * 2).coerceAtLeast(0),
    )
  }
  val searchMaximumHeight = if (useBottomSearchPanel) {
    layoutHeight
  } else {
    (layoutHeight - edgeSpacing).coerceAtLeast(0)
  }
  val placeable = measurable.measure(
    Constraints(
      minWidth = searchWidth,
      maxWidth = searchWidth,
      minHeight = 0,
      maxHeight = searchMaximumHeight,
    ),
  )

  layout(width = layoutWidth, height = layoutHeight) {
    val x = if (useBottomSearchPanel) 0 else layoutWidth - placeable.width - edgeSpacing
    val y = if (useBottomSearchPanel) layoutHeight - placeable.height else edgeSpacing
    placeable.placeRelative(x = x.coerceAtLeast(0), y = y.coerceAtLeast(0))
  }
}

/** 手机紧凑宽度分界；以当前编辑器容器宽度判断，支持分屏和窗口动态缩放。 */
private val PhoneSearchPanelBreakpoint = 600.dp

/** 宽屏搜索浮层的最大宽度，防止输入框遮挡过多代码。 */
private val SearchPanelMaximumWidth = 330.dp

/** 宽屏搜索浮层与编辑器顶部、末端的间距。 */
private val SearchPanelEdgeSpacing = 6.dp
