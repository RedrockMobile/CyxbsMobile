package com.cyxbs.functions.code.editor.highlight.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.decorations

/**
 * 使用最新动态分析结果替换编辑器中的语法高亮。
 *
 * @param highlights 动态语言服务针对当前完整文档返回的高亮区间。
 * @throws IllegalStateException 编辑会话尚未挂载到包含动态高亮扩展的 KodeMirror 组件。
 */
internal fun EditorSession.replaceDynamicHighlights(
  highlights: List<DynamicHighlightSpan>,
) {
  check(state.field(dynamicHighlightField, require = false) != null) {
    "Dynamic highlight extension is not attached to this editor session."
  }
  val decorationSet = buildDecorations(
    highlights = highlights,
    documentLength = state.doc.length,
  )
  // StateEffect 会生成新的 EditorState，使 KodeMirror 的 remember(state) 行内容缓存可靠失效。
  dispatch(
    TransactionSpec(
      effects = listOf(replaceDynamicHighlightEffect.of(decorationSet)),
    ),
  )
}

private val replaceDynamicHighlightEffect = StateEffect.define<DecorationSet>()

private val dynamicHighlightField: StateField<DecorationSet> = StateField.define(
  StateFieldSpec<DecorationSet>(
    create = { RangeSet.empty<Decoration>() },
    update = { current, transaction ->
      var updated: DecorationSet = if (transaction.docChanged) {
        // 解析结果返回前先随文本变更映射旧区间，避免一次换行让整份文档的高亮闪烁消失。
        current.map(transaction.changes)
      } else {
        current
      }
      transaction.effects.forEach { effect ->
        effect.asType(replaceDynamicHighlightEffect)?.let { replacement ->
          updated = replacement.value
        }
      }
      updated
    },
    provide = { field -> decorations.from(field) },
  ),
)

/**
 * 接收动态语言服务结果的 KodeMirror 高亮扩展。
 *
 * 动态包只返回稳定的样式标识和 UTF-16 区间，端上负责映射为 Compose [SpanStyle]。这样语言解析
 * 规则仍可按需下发，而编辑器主题、颜色及无障碍适配继续由客户端统一控制。
 */
internal val kodeMirrorDynamicHighlightExtension: Extension = dynamicHighlightField

/**
 * 将动态服务区间校验、排序并转换为 KodeMirror 装饰集合。
 *
 * @param highlights 动态语言服务返回的原始区间。
 * @param documentLength 当前文档 UTF-16 长度，越界区间会被收敛，空区间会被忽略。
 * @return 可直接保存进编辑器状态字段的不可变装饰集合。
 */
private fun buildDecorations(
  highlights: List<DynamicHighlightSpan>,
  documentLength: Int,
): DecorationSet {
  val builder = RangeSetBuilder<Decoration>()
  highlights
    .asSequence()
    .mapNotNull { highlight ->
      val from = highlight.from.coerceIn(0, documentLength)
      val to = highlight.to.coerceIn(0, documentLength)
      if (from >= to || highlight.styleIds.isEmpty()) {
        null
      } else {
        ResolvedHighlight(
          from = from,
          to = to,
          style = highlightStyle(highlight.styleIds),
        )
      }
    }
    .sortedWith(compareBy(ResolvedHighlight::from, ResolvedHighlight::to))
    .forEach { highlight ->
      builder.add(
        from = DocPos(highlight.from),
        to = DocPos(highlight.to),
        value = Decoration.mark(style = highlight.style),
      )
    }
  return builder.finish()
}

/** 已完成边界校验和主题映射、可安全写入 KodeMirror 的高亮区间。 */
private data class ResolvedHighlight(
  val from: Int,
  val to: Int,
  val style: SpanStyle,
)

/**
 * 将 CodeMirror/Lezer 的 token 类名映射为 KodeMirror 默认深色编辑器的高对比主题。
 *
 * 一个区间可能同时带有 `tok-variableName`、`tok-definition` 等多个标识；颜色按语义类别
 * 选择，定义和关键字等修饰信息再叠加字重，避免样式标识顺序影响最终显示。
 */
private fun highlightStyle(styleIds: List<String>): SpanStyle {
  val normalized = styleIds.map(String::lowercase)
  val color = when {
    normalized.hasToken("comment") -> Color(0xFF8B949E)
    normalized.hasAnyToken("string", "regexp", "escape") -> Color(0xFFA5D6FF)
    normalized.hasAnyToken("number", "bool", "null", "atom") -> Color(0xFF79C0FF)
    normalized.hasAnyToken("keyword", "controlkeyword", "operatorKeyword") -> Color(0xFFFF7B72)
    normalized.hasAnyToken("typename", "classname", "namespace") -> Color(0xFF7EE787)
    normalized.hasAnyToken("function", "functionname", "labelname") -> Color(0xFFD2A8FF)
    normalized.hasAnyToken("propertyname", "variablename", "localname", "definition") ->
      Color(0xFFFFA657)
    normalized.hasToken("operator") -> Color(0xFFFF7B72)
    else -> Color(0xFFC9D1D9)
  }
  return SpanStyle(
    color = color,
    fontWeight = if (
      normalized.hasAnyToken("keyword", "definition", "typename", "classname")
    ) {
      FontWeight.SemiBold
    } else {
      null
    },
    fontStyle = if (normalized.hasToken("comment")) FontStyle.Italic else null,
  )
}

/** 判断任一样式标识是否以指定 token 名结尾，兼容 `tok-` 前缀和后续命名空间扩展。 */
private fun List<String>.hasToken(token: String): Boolean =
  any { styleId -> styleId.endsWith(token.lowercase()) }

/** 批量匹配语义 token。 */
private fun List<String>.hasAnyToken(vararg tokens: String): Boolean =
  tokens.any(::hasToken)
