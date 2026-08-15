package com.cyxbs.functions.code.editor.workbench

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIconPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 验证动态语言图标协议可以安全转换为 Compose 跨平台矢量图。 */
class DynamicLanguageFileIconTest {

  /** 多路径与八位 ARGB 颜色应保留 viewport 和客户端显示尺寸。 */
  @Test
  fun validIconConvertsToImageVector() {
    val icon = DynamicLanguageIcon(
      viewportWidth = 24F,
      viewportHeight = 24F,
      paths = listOf(
        DynamicLanguageIconPath("M2 2H22V22H2Z", fillColor = "#F7DF1E"),
        DynamicLanguageIconPath("M6 6H18V18H6Z", fillColor = "#CC171717"),
      ),
    )

    val vector = assertNotNull(icon.toImageVectorOrNull(Color.White))

    assertEquals(24F, vector.viewportWidth)
    assertEquals(24F, vector.viewportHeight)
    assertEquals(24.dp, vector.defaultWidth)
    assertEquals(24.dp, vector.defaultHeight)
  }

  /** 损坏颜色不能使文件标签崩溃，应让调用方回退到通用文件标记。 */
  @Test
  fun invalidColorFallsBackWithoutThrowing() {
    val icon = DynamicLanguageIcon(
      viewportWidth = 24F,
      viewportHeight = 24F,
      paths = listOf(DynamicLanguageIconPath("M0 0H24V24Z", fillColor = "yellow")),
    )

    assertNull(icon.toImageVectorOrNull(Color.White))
  }

  /** 不同语言必须保留各自的共享矢量图，新增语言不能覆盖已有缓存。 */
  @Test
  fun cacheStoresIndependentVectorsByLanguageId() {
    val cache = DynamicLanguageFileIconCache()
    cache.update("javascript", singlePathIcon(viewport = 24F, color = "#F7DF1E"))
    val javaScriptVector = assertNotNull(cache["javascript"])

    cache.update("python", singlePathIcon(viewport = 32F, color = "#3776AB"))
    cache.update("javascript", singlePathIcon(viewport = 24F, color = "#F7DF1E"))

    assertSame(javaScriptVector, cache[" JavaScript "])
    assertEquals(32F, assertNotNull(cache["python"]).viewportWidth)
  }

  /** 文件扩展名应映射到 Catalog 的语言 ID，未知文件继续使用兜底图标。 */
  @Test
  fun fileExtensionResolvesLanguageIdFromCatalog() {
    val languages = listOf(
      DynamicLanguageInfo(
        languageId = "javascript",
        displayName = "JavaScript",
        npmPackageName = "@cyxbs-mobile/language-javascript",
        fileExtensions = listOf("js", "mjs", "cjs"),
      ),
      DynamicLanguageInfo(
        languageId = "python",
        displayName = "Python",
        npmPackageName = "@cyxbs-mobile/language-python",
        fileExtensions = listOf("py"),
      ),
    )

    assertEquals("javascript", resolveDynamicLanguageIdForFile("src/MAIN.JS", languages))
    assertEquals("python", resolveDynamicLanguageIdForFile("scripts/main.py", languages))
    assertNull(resolveDynamicLanguageIdForFile("README", languages))
    assertNull(resolveDynamicLanguageIdForFile("src/main.kt", languages))
  }

  /** 创建测试用单路径图标。 */
  private fun singlePathIcon(viewport: Float, color: String): DynamicLanguageIcon {
    return DynamicLanguageIcon(
      viewportWidth = viewport,
      viewportHeight = viewport,
      paths = listOf(DynamicLanguageIconPath("M0 0H${viewport}V${viewport}H0Z", color)),
    )
  }
}
