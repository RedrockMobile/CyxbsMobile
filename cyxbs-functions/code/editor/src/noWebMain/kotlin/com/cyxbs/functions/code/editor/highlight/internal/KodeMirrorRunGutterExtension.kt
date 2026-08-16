package com.cyxbs.functions.code.editor.highlight.internal

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.codeFolding
import com.monkopedia.kodemirror.language.foldEffect
import com.monkopedia.kodemirror.language.foldable
import com.monkopedia.kodemirror.language.foldedRanges
import com.monkopedia.kodemirror.language.unfoldEffect
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.GutterConfig
import com.monkopedia.kodemirror.view.GutterMarker
import com.monkopedia.kodemirror.view.GutterType
import com.monkopedia.kodemirror.view.LocalContentTextStyle
import com.monkopedia.kodemirror.view.gutter

/** 行号旁统一使用的运行入口标记。 */
private object RunTargetGutterMarker : GutterMarker() {
  /** 绘制紧凑运行三角，不让语言包或业务页面依赖 KodeMirror 类型。 */
  @Composable
  override fun Content(theme: EditorTheme) {
    Icon(
      imageVector = Icons.Default.PlayArrow,
      contentDescription = "运行此入口",
      tint = RunTargetColor,
      modifier = Modifier.size(14.dp),
    )
  }
}

/** 非运行入口行沿用 KodeMirror 的折叠状态标记。 */
private class FoldGutterMarker(
  private val folded: Boolean,
) : GutterMarker() {

  /** 使用上游折叠 gutter 相同的字符，保持现有编辑器视觉与交互一致。 */
  @Composable
  override fun Content(theme: EditorTheme) {
    BasicText(
      text = if (folded) "\u203A" else "\u2304",
      style = LocalContentTextStyle.current.copy(color = theme.gutterForeground),
    )
  }

  override fun equals(other: Any?): Boolean = other is FoldGutterMarker && folded == other.folded

  override fun hashCode(): Int = folded.hashCode()
}

/**
 * 创建合并运行入口与代码折叠的 KodeMirror gutter。
 *
 * [targets] 和 [activeFilePath] 在 Compose 重组后会返回最新值，因此文件切换或增量入口刷新不需要
 * 重建 EditorSession。入口行优先显示并执行运行按钮，其他行继续显示和执行 KodeMirror 折叠按钮。
 */
internal fun kodeMirrorRunOrFoldGutterExtension(
  targets: () -> List<DynamicRunTarget>,
  activeFilePath: () -> String,
  onRunTarget: () -> ((DynamicRunTarget) -> Unit)?,
): Extension = extensionListOf(
  codeFolding(),
  gutter(
    GutterConfig(
      type = GutterType.Custom(RUN_OR_FOLD_GUTTER_NAME),
      lineMarker = { session, lineFrom ->
        targetAtLine(session, targets(), activeFilePath(), lineFrom)?.let { RunTargetGutterMarker }
          ?: foldMarkerAtLine(session, lineFrom)
      },
      // 外部入口列表变化没有对应的 KodeMirror 事务，因此每次可见行更新都重新读取标记。
      lineMarkerChange = { true },
      lineMarkerClick = click@{ session, lineFrom ->
        val target = targetAtLine(session, targets(), activeFilePath(), lineFrom)
        if (target != null) {
          val callback = onRunTarget() ?: return@click false
          callback(target)
          true
        } else {
          toggleFoldAtLine(session, lineFrom)
        }
      },
    ),
  ),
)

/** 读取当前行的折叠状态；运行入口会在调用方优先覆盖本标记。 */
private fun foldMarkerAtLine(session: EditorSession, lineFrom: Int): GutterMarker? {
  val state = session.state
  val lineFromPosition = DocPos(lineFrom)
  val line = state.doc.lineAt(lineFromPosition)
  var hasFold = false
  foldedRanges(state).between(lineFromPosition, line.to) { from, _, _ ->
    if (from >= lineFrom && DocPos(from) <= line.to) {
      hasFold = true
      false
    } else {
      true
    }
  }
  return when {
    hasFold -> FoldGutterMarker(folded = true)
    foldable(state, lineFromPosition) != null -> FoldGutterMarker(folded = false)
    else -> null
  }
}

/** 展开或折叠指定行；不存在折叠区间时保持文档不变并返回 false。 */
private fun toggleFoldAtLine(session: EditorSession, lineFrom: Int): Boolean {
  val state = session.state
  val lineFromPosition = DocPos(lineFrom)
  val line = state.doc.lineAt(lineFromPosition)
  var wasFolded = false
  foldedRanges(state).between(lineFromPosition, line.to) { from, to, _ ->
    if (from >= lineFrom && DocPos(from) <= line.to) {
      session.dispatch(
        TransactionSpec(
          effects = listOf(unfoldEffect.of(FoldRange(DocPos(from), DocPos(to)))),
        ),
      )
      wasFolded = true
      false
    } else {
      true
    }
  }
  if (wasFolded) return true

  val range = foldable(state, lineFromPosition) ?: return false
  session.dispatch(TransactionSpec(effects = listOf(foldEffect.of(range))))
  return true
}

/** 将 UTF-16 入口位置映射到当前文档行首，并防御异步刷新产生的过期位置。 */
private fun targetAtLine(
  session: EditorSession,
  targets: List<DynamicRunTarget>,
  activeFilePath: String,
  lineFrom: Int,
): DynamicRunTarget? {
  val document = session.state.doc
  return targets.firstOrNull { target ->
    val location = target.location ?: return@firstOrNull false
    val position = location.range.from
    location.filePath == activeFilePath &&
      position in 0..document.length &&
      document.lineAt(DocPos(position)).from.value == lineFrom
  }
}

private const val RUN_OR_FOLD_GUTTER_NAME = "dynamic-run-or-fold"
private val RunTargetColor = Color(0xFF8E7CFF)

/** 运行与折叠共用列的固定宽度；实际宽度由编辑器主题统一应用到自定义 gutter。 */
internal val RunTargetGutterWidth = 16.dp
