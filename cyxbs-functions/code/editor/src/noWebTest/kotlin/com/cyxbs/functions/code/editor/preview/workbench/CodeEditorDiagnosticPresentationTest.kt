package com.cyxbs.functions.code.editor.preview.workbench

import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import kotlin.test.Test
import kotlin.test.assertEquals

/** 编辑器诊断位置展示的跨平台回归测试。 */
class CodeEditorDiagnosticPresentationTest {

  /** UTF-16 偏移必须与协议和 CodeMirror 一致，不能把 supplementary 字符误算成单列。 */
  @Test
  fun convertsUtf16OffsetToOneBasedLineAndColumn() {
    val source = "class Main {\n  String emoji = \"🐶\"; int value;\n}"
    val offset = source.indexOf("value")
    val location = DynamicSourceLocation(
      filePath = "src/lesson/Main.java",
      range = DynamicTextRange(offset, offset + "value".length),
    )

    assertEquals(
      "src/lesson/Main.java:2:28",
      location.toEditorLocationText(source),
    )
  }

  /** 源码快照缺失时仍保留可识别的工作区文件路径。 */
  @Test
  fun fallsBackToFilePathWithoutSourceSnapshot() {
    val location = DynamicSourceLocation(
      filePath = "src/lesson/Other.java",
      range = DynamicTextRange(10, 12),
    )

    assertEquals("src/lesson/Other.java", location.toEditorLocationText(null))
  }
}
