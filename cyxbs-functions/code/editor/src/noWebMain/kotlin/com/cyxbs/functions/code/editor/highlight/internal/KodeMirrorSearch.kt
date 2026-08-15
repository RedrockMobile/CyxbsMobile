package com.cyxbs.functions.code.editor.highlight.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.functions.code.editor.theme.CodeEditorConnectedCornerRadius
import com.monkopedia.kodemirror.search.SearchQuery
import com.monkopedia.kodemirror.search.findNext
import com.monkopedia.kodemirror.search.findPrevious
import com.monkopedia.kodemirror.search.getSearchQuery
import com.monkopedia.kodemirror.search.searchPanelOpen
import com.monkopedia.kodemirror.search.searchQueryField
import com.monkopedia.kodemirror.search.setSearchQuery
import com.monkopedia.kodemirror.search.toggleSearchPanel
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.keymap

/** 仅控制项目自有搜索浮层，避免与 KodeMirror 固定面板共享显示状态。 */
private val toggleCodeEditorSearchPanel: StateEffectType<Boolean> = StateEffect.define()

/** 项目自有搜索浮层的打开状态。 */
private val codeEditorSearchPanelOpenField: StateField<Boolean> = StateField.define(
  StateFieldSpec(
    create = { false },
    update = { value, transaction ->
      var result = value
      for (effect in transaction.effects) {
        val panelEffect = effect.asType(toggleCodeEditorSearchPanel)
        if (panelEffect != null) result = panelEffect.value
      }
      result
    },
  ),
)

/**
 * 为项目自有搜索面板提供 KodeMirror 搜索状态与匹配高亮。
 *
 * KodeMirror 0.3.5 的 [com.monkopedia.kodemirror.search.search] 会固定创建三方搜索面板，无法替换
 * 其布局。这里注册公开查询字段和独立的浮层状态，并覆盖 Mod+F/Escape 快捷键；查找、替换
 * 算法仍复用 KodeMirror 的公开命令，但不会再触发默认面板的按需安装。
 */
internal val kodeMirrorSearchExtension: Extension = extensionListOf(
  searchQueryField,
  codeEditorSearchPanelOpenField,
  ViewPlugin.define(
    create = { view -> CodeEditorSearchHighlightPlugin(view.state) },
    configure = {
      copy(
        decorations = { plugin ->
          plugin.decorations
        },
      )
    },
  ).asExtension(),
  keymap.of(
    listOf(
      KeyBinding(key = "Mod-f", run = ::openCodeEditorSearchPanel),
      KeyBinding(key = "Escape", run = ::closeCodeEditorSearchPanel),
    ),
  ),
)

/**
 * 打开项目自有搜索浮层。
 *
 * 对热更新前已经安装默认搜索扩展的会话，会在同一事务中关闭其面板，避免两套 UI 重叠。
 */
internal fun openCodeEditorSearchPanel(session: EditorSession): Boolean {
  val hasCustomSearch = session.state.field(codeEditorSearchPanelOpenField, require = false) != null
  val effects = buildList {
    if (!hasCustomSearch) add(StateEffect.appendConfig.of(kodeMirrorSearchExtension))
    add(toggleCodeEditorSearchPanel.of(true))
    if (searchPanelOpen(session.state)) add(toggleSearchPanel.of(false))
  }
  session.dispatch(TransactionSpec(effects = effects))
  return true
}

/**
 * 切换项目自有搜索浮层，供顶部搜索按钮使用。
 *
 * 与只负责打开的 Mod+F 不同，重复点击按钮会关闭已经显示的搜索栏。
 */
internal fun toggleCodeEditorSearchPanelVisibility(session: EditorSession): Boolean {
  return if (codeEditorSearchPanelOpen(session)) {
    closeCodeEditorSearchPanel(session)
  } else {
    openCodeEditorSearchPanel(session)
  }
}

/** 关闭项目自有搜索浮层，并兼容关闭会话中可能残留的 KodeMirror 默认面板。 */
internal fun closeCodeEditorSearchPanel(session: EditorSession): Boolean {
  val customPanelOpen = codeEditorSearchPanelOpen(session)
  val defaultPanelOpen = searchPanelOpen(session.state)
  if (!customPanelOpen && !defaultPanelOpen) return false

  val effects = buildList {
    if (customPanelOpen) add(toggleCodeEditorSearchPanel.of(false))
    if (defaultPanelOpen) add(toggleSearchPanel.of(false))
  }
  session.dispatch(TransactionSpec(effects = effects))
  return true
}

/** 返回项目自有搜索浮层是否打开；尚未安装扩展的旧会话视为关闭。 */
internal fun codeEditorSearchPanelOpen(session: EditorSession): Boolean =
  session.state.field(codeEditorSearchPanelOpenField, require = false) ?: false

/** 根据文档、选区或查询变化重建搜索匹配装饰。 */
private class CodeEditorSearchHighlightPlugin(
  state: EditorState,
) : PluginValue {
  var decorations: DecorationSet = buildSearchDecorations(state)
    private set

  override fun update(update: ViewUpdate) {
    val searchStateChanged = update.transactions.any { transaction ->
      transaction.effects.any { effect ->
        effect.asType(setSearchQuery) != null ||
          effect.asType(toggleCodeEditorSearchPanel) != null
      }
    }
    if (update.docChanged || update.selectionSet || searchStateChanged) {
      decorations = buildSearchDecorations(update.state)
    }
  }
}

/** 仅在搜索状态打开且查询有效时绘制全部匹配，并区分当前选中项。 */
private fun buildSearchDecorations(state: EditorState): DecorationSet {
  val panelOpen = state.field(codeEditorSearchPanelOpenField, require = false) ?: false
  if (!panelOpen) return RangeSet.empty()

  val query = state.field(searchQueryField, require = false) ?: SearchQuery()
  if (!query.valid) return RangeSet.empty()

  val theme = state.facet(editorTheme)
  val matchDecoration = Decoration.mark(
    MarkDecorationSpec(
      style = SpanStyle(background = theme.searchMatchBackground),
      cssClass = "cm-searchMatch",
    ),
  )
  val selectedDecoration = Decoration.mark(
    MarkDecorationSpec(
      style = SpanStyle(background = theme.searchMatchSelectedBackground),
      cssClass = "cm-searchMatch-selected",
    ),
  )
  val selection = state.selection.main
  val builder = RangeSetBuilder<Decoration>()
  for (match in query.getCursor(state)) {
    builder.add(
      match.from,
      match.to,
      if (match.from == selection.from && match.to == selection.to) {
        selectedDecoration
      } else {
        matchDecoration
      },
    )
  }
  return builder.finish()
}

/**
 * 绘制 IDEA 风格的紧凑文内搜索面板。
 *
 * 当前只提供查找能力；替换交互将在后续结合选中文本单独设计。Enter/Shift+Enter 分别跳到
 * 下一个/上一个结果，Escape 或右侧关闭按钮会关闭面板并清除匹配装饰。
 */
@Composable
internal fun KodeMirrorSearchPanel(
  session: EditorSession,
  modifier: Modifier = Modifier,
) {
  val currentQuery = getSearchQuery(session.state)
  var searchText by remember(session) { mutableStateOf(currentQuery.search) }
  var caseSensitive by remember(session) { mutableStateOf(currentQuery.caseSensitive) }
  var regexp by remember(session) { mutableStateOf(currentQuery.regexp) }
  var wholeWord by remember(session) { mutableStateOf(currentQuery.wholeWord) }
  val focusRequester = remember(session) { FocusRequester() }

  fun updateQuery() {
    session.dispatch(
      TransactionSpec(
        effects = listOf(
          setSearchQuery.of(
            SearchQuery(
              search = searchText,
              caseSensitive = caseSensitive,
              regexp = regexp,
              replace = "",
              wholeWord = wholeWord,
            ),
          ),
        ),
      ),
    )
  }

  Column(
    modifier = modifier
      .clip(SearchPanelShape)
      .background(SearchPanelBackground)
      .border(1.dp, SearchPanelBorder, SearchPanelShape)
      .padding(4.dp)
      .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
          Key.Escape -> closeCodeEditorSearchPanel(session)
          Key.Enter -> if (event.isShiftPressed) findPrevious(session) else findNext(session)
          else -> false
        }
      },
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      SearchInput(
        value = searchText,
        onValueChange = {
          searchText = it
          updateQuery()
        },
        modifier = Modifier
          .weight(1F)
          .focusRequester(focusRequester),
        trailingContent = {
          SearchOptionButton(
            label = "Aa",
            selected = caseSensitive,
            contentDescription = "Match case",
            onClick = {
              caseSensitive = !caseSensitive
              updateQuery()
            },
          )
          SearchOptionButton(
            label = "W",
            selected = wholeWord,
            contentDescription = "Whole words",
            onClick = {
              wholeWord = !wholeWord
              updateQuery()
            },
          )
          SearchOptionButton(
            label = ".*",
            selected = regexp,
            contentDescription = "Regular expression",
            onClick = {
              regexp = !regexp
              updateQuery()
            },
          )
        },
      )
      SearchIconButton(contentDescription = "Previous match", onClick = { findPrevious(session) }) {
        Icon(
          imageVector = Icons.Default.KeyboardArrowUp,
          contentDescription = null,
          tint = SearchForeground,
          modifier = Modifier.size(17.dp),
        )
      }
      SearchIconButton(contentDescription = "Next match", onClick = { findNext(session) }) {
        Icon(
          imageVector = Icons.Default.KeyboardArrowDown,
          contentDescription = null,
          tint = SearchForeground,
          modifier = Modifier.size(17.dp),
        )
      }
      SearchIconButton(
        contentDescription = "Close search",
        onClick = { closeCodeEditorSearchPanel(session) },
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = null,
          tint = SearchSecondaryForeground,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }

  LaunchedEffect(session) {
    focusRequester.requestFocus()
  }
}

/** 单行搜索输入框，可在右侧嵌入 IDEA 风格的查询选项图标。 */
@Composable
private fun SearchInput(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  trailingContent: @Composable (() -> Unit)? = null,
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    textStyle = SearchTextStyle,
    cursorBrush = SolidColor(SearchAccent),
    singleLine = true,
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(SearchActionSize)
          .clip(SearchInputShape)
          .background(SearchInputBackground)
          .border(1.dp, SearchInputBorder, SearchInputShape)
          .padding(start = 6.dp, end = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1F)) {
          if (value.isEmpty()) {
            BasicText(
              text = "Find",
              style = SearchTextStyle.copy(color = SearchSecondaryForeground),
            )
          }
          innerTextField()
        }
        trailingContent?.invoke()
      }
    },
  )
}

/** 紧凑搜索动作按钮，不使用 Material 默认的 48dp 最小尺寸。 */
@Composable
private fun SearchIconButton(
  contentDescription: String,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier
      .size(SearchActionSize)
      .clip(SearchActionShape)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      )
      .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** 以 `Aa`、`W`、`.*` 字形承载查询选项，选中后用主题强调色提示状态。 */
@Composable
private fun SearchOptionButton(
  label: String,
  selected: Boolean,
  contentDescription: String,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier
      .size(width = 24.dp, height = 22.dp)
      .clip(SearchActionShape)
      .then(
        if (selected) Modifier.background(SearchAccent.copy(alpha = 0.18F)) else Modifier,
      )
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Checkbox,
        onClick = onClick,
      )
      .semantics {
        this.contentDescription = contentDescription
        this.role = Role.Checkbox
        this.selected = selected
      },
    contentAlignment = Alignment.Center,
  ) {
    BasicText(
      text = label,
      style = SearchTextStyle.copy(
        color = if (selected) SearchAccent else SearchSecondaryForeground,
        fontSize = if (label == ".*") 12.sp else 11.sp,
      ),
    )
  }
}

// 与编辑器、文件标签和底部工具栏使用同一圆角，贴近底部时保持连续的视觉语言。
private val SearchPanelShape = RoundedCornerShape(CodeEditorConnectedCornerRadius)
private val SearchInputShape = RoundedCornerShape(3.dp)
private val SearchActionShape = RoundedCornerShape(3.dp)
private val SearchActionSize = 28.dp
private val SearchPanelBackground = Color(0xFF1C2330)
private val SearchPanelBorder = Color(0xFF30394B)
private val SearchInputBackground = Color(0xFF121720)
private val SearchInputBorder = Color(0xFF3A465C)
private val SearchForeground = Color(0xFFD8DFEC)
private val SearchSecondaryForeground = Color(0xFF8792A6)
private val SearchAccent = Color(0xFF8E7CFF)
private val SearchTextStyle = TextStyle(
  color = SearchForeground,
  fontFamily = FontFamily.Monospace,
  fontSize = 12.sp,
  lineHeight = 16.sp,
)
