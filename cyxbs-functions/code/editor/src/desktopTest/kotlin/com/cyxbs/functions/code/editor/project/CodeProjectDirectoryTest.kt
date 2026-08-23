package com.cyxbs.functions.code.editor.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** 项目路径末尾省略规则的桌面回归；规则本身为公共 Kotlin，可覆盖所有 noWeb 平台。 */
class CodeProjectDirectoryTest {

  @Test
  fun keepsOnlyTrailingSegmentsForLongPaths() {
    assertEquals(
      "…/cyxbs-code/projects/java-123",
      projectDirectoryLabel("/Users/demo/Application Support/cyxbs-code/projects/java-123"),
    )
  }

  @Test
  fun normalizesWindowsSeparatorsWhenPathIsAbbreviated() {
    assertEquals(
      "…/cyxbs-code/projects/js-456",
      projectDirectoryLabel("C:\\Users\\demo\\cyxbs-code\\projects\\js-456"),
    )
  }

  @Test
  fun keepsShortPathsUnchanged() {
    assertEquals("/projects/java-123", projectDirectoryLabel("/projects/java-123"))
  }
}
