package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIconPath
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证动态语言图标可以跨缓存实例持久恢复，并按 Catalog 中的包身份隔离旧记录。
 */
class DynamicLanguageIconCacheTest {

  /**
   * 模拟 App 进程重建：新缓存实例应直接读取上次成功保存的图标，不需要加载语言 Service。
   */
  @Test
  fun persistedIconIsRestoredByANewCacheInstance() = runTest {
    val fileSystem = FileSystem.SYSTEM
    val rootDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "dynamic-language-icon-cache-test-${Random.nextLong().toString(16)}"
    val language = javascriptLanguage()

    try {
      DynamicLanguageIconCache(
        OkioDynamicLanguageIconCacheStore(rootDirectory, fileSystem),
      ).update(
        language = language,
        npmPackageVersion = "1.2.3",
        icon = JAVASCRIPT_ICON,
      )

      val restored = DynamicLanguageIconCache(
        OkioDynamicLanguageIconCacheStore(rootDirectory, fileSystem),
      ).restore(listOf(language))

      assertEquals(JAVASCRIPT_ICON, restored[language])
    } finally {
      fileSystem.deleteRecursively(rootDirectory, mustExist = false)
    }
  }

  /**
   * Catalog 将同一语言 ID 改绑到另一 npm 包时，不应继续显示旧实现留下的图标。
   */
  @Test
  fun changedPackageNameInvalidatesPersistedIcon() = runTest {
    val store = InMemoryDynamicLanguageIconCacheStore()
    val cache = DynamicLanguageIconCache(store)
    val language = javascriptLanguage()
    cache.update(language, "1.2.3", JAVASCRIPT_ICON)

    val restored = DynamicLanguageIconCache(store).restore(
      listOf(language.copy(npmPackageName = "@cyxbs-mobile/language-javascript-next")),
    )

    assertTrue(restored.isEmpty())
  }

  private companion object {
    val JAVASCRIPT_ICON = DynamicLanguageIcon(
      viewportWidth = 24F,
      viewportHeight = 24F,
      paths = listOf(
        DynamicLanguageIconPath(
          pathData = "M0 0H24V24H0Z",
          fillColor = "#F7DF1E",
        ),
      ),
    )

    fun javascriptLanguage(): DynamicLanguageInfo {
      return DynamicLanguageInfo(
        languageId = "javascript",
        displayName = "JavaScript",
        npmPackageName = "@cyxbs-mobile/language-javascript",
        aliases = listOf("js"),
        fileExtensions = listOf("js", "mjs", "cjs"),
      )
    }
  }
}
