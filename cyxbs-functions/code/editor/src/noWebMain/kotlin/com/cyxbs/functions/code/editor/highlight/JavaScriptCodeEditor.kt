package com.cyxbs.functions.code.editor.highlight

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.functions.code.editor.theme.CodeEditorConnectedCornerRadius
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorDynamicHighlightExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorDynamicCompletionExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorPlainTextLanguageExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorSearchExtension
import com.cyxbs.functions.code.editor.highlight.internal.KodeMirrorSearchPanel
import com.cyxbs.functions.code.editor.highlight.internal.codeEditorSearchPanelOpen
import com.cyxbs.functions.code.editor.highlight.internal.replaceDynamicHighlights
import com.cyxbs.functions.code.editor.highlight.internal.toggleCodeEditorSearchPanelVisibility
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.js.bridge.DynamicTextEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceMethodNotImplementedException
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.commands.redoDepth
import com.monkopedia.kodemirror.commands.redo as kodeMirrorRedo
import com.monkopedia.kodemirror.commands.undoDepth
import com.monkopedia.kodemirror.commands.undo as kodeMirrorUndo
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
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
 * JavaScript 编辑器状态。
 *
 * 状态内部持有 KodeMirror 会话，对外只暴露当前源码，避免业务代码依赖第三方编辑器类型。
 * [code] 会读取编辑器的最新文档，可直接在“运行”操作触发时获取用户输入。
 */
@Stable
class JavaScriptCodeEditorState internal constructor(
  internal val session: EditorSession,
  private val languageService: () -> DynamicLanguageService?,
  private val workspace: () -> DynamicLanguageWorkspace,
  private val activeFilePath: () -> String,
) {

  /** 当前编辑器中的完整 JavaScript 源码。 */
  val code: String
    get() = session.state.doc.toString()

  /** 当前主光标的 UTF-16 偏移；存在选区时返回活动端位置。 */
  val cursorPosition: Int
    get() = session.state.selection.main.head.value

  /** 当前编辑文档在工作区中的相对路径。 */
  val filePath: String
    get() = activeFilePath()

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
    session.replaceDynamicHighlights(highlights)
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
   * 本方法不直接修改任何文件。调用方应按文件分组 [DynamicRenameResult.edits]，当前文档可用
   * [applyTextEdits] 原子应用，其他文件则更新自身的文档存储。
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
    if (result.isSuccess) result.edits.requireValidFor(requestedWorkspace)
    return result
  }

  /** 用新源码替换当前文档，并将光标放到 [cursorPosition] 。 */
  fun replaceDocument(source: String, cursorPosition: Int = 0) {
    require(cursorPosition in 0..source.length) { "cursorPosition must be inside source." }
    session.dispatch(
      TransactionSpec(
        changes = ChangeSpec.Single(
          from = DocPos(0),
          to = DocPos(code.length),
          insert = source.asInsert(),
        ),
        selection = SelectionSpec.CursorSpec(anchor = DocPos(cursorPosition)),
        userEvent = "input.openFile",
        scrollIntoView = true,
      ),
    )
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
}

/**
 * 创建并记住一个 JavaScript 编辑器状态。
 *
 * @param initialCode 首次创建状态时使用的源码；后续重组不会覆盖用户已经编辑的内容。
 * @param languageService 当前已经加载的动态语言服务；可先传 null，加载完成后的重组会让补全源
 * 立即使用新服务，而不会重建或覆盖编辑器文档。
 * @return 可读取当前源码并在多个组合节点间共享的编辑器状态。
 */
@Composable
fun rememberJavaScriptCodeEditorState(
  initialCode: String = "",
  activeFilePath: String = DEFAULT_EDITOR_FILE_PATH,
  workspace: DynamicLanguageWorkspace = DynamicLanguageWorkspace(
    files = listOf(DynamicSourceFile(activeFilePath, initialCode)),
  ),
  languageService: DynamicLanguageService? = null,
): JavaScriptCodeEditorState {
  val currentLanguageService = rememberUpdatedState(languageService)
  val currentWorkspace = rememberUpdatedState(workspace)
  val currentFilePath = rememberUpdatedState(activeFilePath)
  val completionExtension = remember {
    kodeMirrorDynamicCompletionExtension(
      service = { currentLanguageService.value },
      workspace = { currentWorkspace.value },
      filePath = { currentFilePath.value },
    )
  }
  val session = rememberEditorSession(
    doc = initialCode,
    // basicSetup 强制要求存在 Language；纯文本占位仅维持编辑能力，不承担实际语法解析。
    extensions = extensionListOf(
      basicSetup,
      // 预先安装公开搜索状态，避免 openSearchPanel 注入无法定制的 KodeMirror 默认面板。
      kodeMirrorSearchExtension,
      editorTheme.of(codeEditorTheme),
      editorContentStyle.of(codeEditorTextStyle),
      kodeMirrorPlainTextLanguageExtension,
      kodeMirrorDynamicHighlightExtension,
      completionExtension,
    ),
  )
  return remember(session) {
    JavaScriptCodeEditorState(
      session = session,
      languageService = { currentLanguageService.value },
      workspace = { currentWorkspace.value },
      activeFilePath = { currentFilePath.value },
    )
  }
}

/** 未显式创建工作区时使用的单文件路径。 */
private const val DEFAULT_EDITOR_FILE_PATH = "main.js"

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
  // 保持行号与代码区紧凑衔接，同时留出最小间距避免文字直接贴边。
  // basicSetup 会注册折叠 gutter，但当前动态语言尚未向 KodeMirror 提供折叠区间，因此取消空列占位。
  layout = EditorLayout(
    gutterEndPadding = 3.dp,
    customGutterWidth = 0.dp,
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
internal fun JavaScriptCodeEditorState.editorGutterWidth(): Dp {
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
private fun Modifier.roundedCodeAreaBackground(state: JavaScriptCodeEditorState): Modifier {
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
 * 使用纯 Compose KodeMirror 渲染可编辑的 JavaScript 代码视图。
 *
 * 组件自身不再绑定 JavaScript 解析器，仅提供行号、折叠、搜索等通用编辑能力；语法解析与
 * 高亮、补全由动态语言包加载后接入，避免每种语言的解析实现都进入安装包。
 *
 * 当前组件仅进入 Android、iOS 与 Desktop 的 `noWebMain`。调用方应为组件提供有界高度，
 * KodeMirror 才能在编辑区域内部正确滚动并保持光标可见。
 *
 * @param state 由 [rememberJavaScriptCodeEditorState] 创建的编辑器状态。
 * @param modifier 应用到编辑器根节点的布局修饰符。
 */
@Composable
fun JavaScriptCodeEditor(
  state: JavaScriptCodeEditorState,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier) {
    KodeMirror(
      session = state.session,
      modifier = Modifier
        .fillMaxSize()
        .roundedCodeAreaBackground(state),
    )
    if (codeEditorSearchPanelOpen(state.session)) {
      val useBottomSearchPanel = maxWidth < PhoneSearchPanelBreakpoint
      // 手机宽度下使用铺满编辑器的底部搜索栏；更宽窗口继续使用右上角紧凑浮层。
      val searchPanelWidth = (maxWidth - 12.dp)
        .coerceAtLeast(0.dp)
        .coerceAtMost(330.dp)
      val searchPanelModifier = if (useBottomSearchPanel) {
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
      } else {
        Modifier
          .align(Alignment.TopEnd)
          .padding(top = 6.dp, end = 6.dp)
          .width(searchPanelWidth)
      }
      KodeMirrorSearchPanel(
        session = state.session,
        modifier = searchPanelModifier,
      )
    }
  }
}

/** 手机紧凑宽度分界；以当前编辑器容器宽度判断，支持分屏和窗口动态缩放。 */
private val PhoneSearchPanelBreakpoint = 600.dp
