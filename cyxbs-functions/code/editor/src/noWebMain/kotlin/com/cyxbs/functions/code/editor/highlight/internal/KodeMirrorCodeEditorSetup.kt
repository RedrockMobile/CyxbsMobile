package com.cyxbs.functions.code.editor.highlight.internal

import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.autocomplete.closeBrackets
import com.monkopedia.kodemirror.autocomplete.closeBracketsKeymap
import com.monkopedia.kodemirror.autocomplete.completionKeymap
import com.monkopedia.kodemirror.basicsetup.minimalSetup
import com.monkopedia.kodemirror.language.bracketMatching
import com.monkopedia.kodemirror.language.foldKeymap
import com.monkopedia.kodemirror.language.indentOnInput
import com.monkopedia.kodemirror.lint.lintKeymap
import com.monkopedia.kodemirror.search.highlightSelectionMatches
import com.monkopedia.kodemirror.search.searchKeymap
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.crosshairCursor
import com.monkopedia.kodemirror.view.dropCursor
import com.monkopedia.kodemirror.view.highlightActiveLine
import com.monkopedia.kodemirror.view.highlightActiveLineGutter
import com.monkopedia.kodemirror.view.keymapOf
import com.monkopedia.kodemirror.view.lineNumbers
import com.monkopedia.kodemirror.view.rectangularSelection

/**
 * 编辑器使用的 KodeMirror 基础能力集合。
 *
 * 功能与上游 `basicSetup` 保持一致，但不注册其独立的折叠 gutter；折叠与运行入口由
 * [kodeMirrorRunOrFoldGutterExtension] 合并到同一列，避免为运行按钮额外占用横向空间。
 */
internal val kodeMirrorCodeEditorSetup: Extension = extensionListOf(
  minimalSetup,
  lineNumbers,
  highlightActiveLineGutter,
  dropCursor,
  allowMultipleSelections.of(true),
  indentOnInput,
  bracketMatching(),
  closeBrackets(),
  autocompletion(),
  rectangularSelection,
  crosshairCursor,
  highlightActiveLine,
  highlightSelectionMatches(),
  // closeBracketsKeymap 放在最后，确保退格时优先删除成对括号。
  keymapOf(
    searchKeymap +
      foldKeymap +
      completionKeymap +
      lintKeymap +
      closeBracketsKeymap,
  ),
)
