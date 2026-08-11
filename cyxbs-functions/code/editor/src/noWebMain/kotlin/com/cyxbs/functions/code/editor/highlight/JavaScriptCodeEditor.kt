package com.cyxbs.functions.code.editor.highlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorDynamicHighlightExtension
import com.cyxbs.functions.code.editor.highlight.internal.kodeMirrorPlainTextLanguageExtension
import com.cyxbs.functions.code.editor.highlight.internal.replaceDynamicHighlights
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

/**
 * JavaScript 编辑器状态。
 *
 * 状态内部持有 KodeMirror 会话，对外只暴露当前源码，避免业务代码依赖第三方编辑器类型。
 * [code] 会读取编辑器的最新文档，可直接在“运行”操作触发时获取用户输入。
 */
@Stable
class JavaScriptCodeEditorState internal constructor(
  internal val session: EditorSession,
) {

  /** 当前编辑器中的完整 JavaScript 源码。 */
  val code: String
    get() = session.state.doc.toString()

  /**
   * 将动态语言服务针对当前源码返回的高亮区间应用到编辑器。
   *
   * 调用方必须保证 [highlights] 对应当前 [code]；后续文档发生编辑时旧高亮会自动清空，避免
   * 区间错位，待下一次动态分析完成后再调用本方法即可刷新。
   */
  fun applyHighlights(highlights: List<DynamicHighlightSpan>) {
    session.replaceDynamicHighlights(highlights)
  }
}

/**
 * 创建并记住一个 JavaScript 编辑器状态。
 *
 * @param initialCode 首次创建状态时使用的源码；后续重组不会覆盖用户已经编辑的内容。
 * @return 可读取当前源码并在多个组合节点间共享的编辑器状态。
 */
@Composable
fun rememberJavaScriptCodeEditorState(
  initialCode: String = "",
): JavaScriptCodeEditorState {
  val session = rememberEditorSession(
    doc = initialCode,
    // basicSetup 强制要求存在 Language；纯文本占位仅维持编辑能力，不承担实际语法解析。
    extensions = extensionListOf(
      basicSetup,
      kodeMirrorPlainTextLanguageExtension,
      kodeMirrorDynamicHighlightExtension,
    ),
  )
  return remember(session) { JavaScriptCodeEditorState(session) }
}

/**
 * 使用纯 Compose KodeMirror 渲染可编辑的 JavaScript 代码视图。
 *
 * 组件自身不再绑定 JavaScript 解析器，仅提供行号、折叠、搜索等通用编辑能力；语法解析与
 * 高亮由动态语言包加载后接入，避免每种语言的解析实现都进入安装包。
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
  KodeMirror(
    session = state.session,
    modifier = modifier,
  )
}
