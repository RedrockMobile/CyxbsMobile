package com.cyxbs.functions.code.editor.project

import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageProjectFile
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageProjectTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 动态语言包项目模板进入本地文件系统前的协议边界回归。 */
class DynamicLanguageProjectTemplateTest {

  @Test
  fun convertsValidTemplateWithoutChangingSourceOrder() {
    val template = template(
      activeFilePath = "src/Main.demo",
      files = listOf(
        DynamicLanguageProjectFile("src/Main.demo", "main"),
        DynamicLanguageProjectFile("src/Helper.demo", "helper"),
      ),
    ).toCodeProjectTemplate(language)

    assertEquals("demo", template.languageId)
    assertEquals("Demo", template.displayName)
    assertEquals(listOf("src/Main.demo", "src/Helper.demo"), template.sourceFiles.keys.toList())
  }

  @Test
  fun rejectsMissingActiveFileAndDuplicatePaths() {
    assertFailsWith<IllegalArgumentException> {
      template(
        activeFilePath = "src/Missing.demo",
        files = listOf(DynamicLanguageProjectFile("src/Main.demo", "main")),
      ).toCodeProjectTemplate(language)
    }
    assertFailsWith<IllegalArgumentException> {
      template(
        activeFilePath = "src/Main.demo",
        files = listOf(
          DynamicLanguageProjectFile("src/Main.demo", "first"),
          DynamicLanguageProjectFile("src/Main.demo", "second"),
        ),
      ).toCodeProjectTemplate(language)
    }
  }

  @Test
  fun rejectsTraversalAndReservedManifestPaths() {
    listOf("../outside.demo", "src/../outside.demo", ".cyxbs-project.json").forEach { path ->
      assertFailsWith<IllegalArgumentException> {
        template(
          activeFilePath = path,
          files = listOf(DynamicLanguageProjectFile(path, "source")),
        ).toCodeProjectTemplate(language)
      }
    }
  }

  /** 创建只改变测试关注字段的协议模板。 */
  private fun template(
    activeFilePath: String,
    files: List<DynamicLanguageProjectFile>,
  ): DynamicLanguageProjectTemplate = DynamicLanguageProjectTemplate(
    defaultProjectName = "DemoProject",
    activeFilePath = activeFilePath,
    sourceFiles = files,
  )

  private companion object {
    val language = DynamicLanguageInfo(
      languageId = "demo",
      displayName = "Demo",
      npmPackageName = "@cyxbs-mobile/language-demo",
      fileExtensions = listOf("demo"),
    )
  }
}
