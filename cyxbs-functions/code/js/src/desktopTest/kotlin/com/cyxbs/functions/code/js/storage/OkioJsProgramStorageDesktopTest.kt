package com.cyxbs.functions.code.js.storage

import com.cyxbs.functions.code.js.program.JsSourcePackage
import com.cyxbs.functions.code.js.runtime.JsRuntime
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 使用真实 Desktop 文件系统验证源码的原子持久化格式。
 */
class OkioJsProgramStorageDesktopTest {

  /**
   * 验证源码可以跨存储实例读取。
   */
  @Test
  fun persistAndReopenSourcePackage() = runTest {
    val root = Files.createTempDirectory("cyxbs-js-storage").toString().toPath()
    val fileSystem = FileSystem.SYSTEM
    try {
      val sourcePackage = JsSourcePackage.create(
        packageId = "storage.desktop",
        version = "1",
        files = mapOf(JsRuntime.DEFAULT_FILENAME to "40 + 2"),
      )
      val firstStorage = OkioJsProgramStorage(
        rootDirectory = root,
        fileSystem = fileSystem,
      )
      firstStorage.writeSource(sourcePackage)

      val reopenedStorage = OkioJsProgramStorage(
        rootDirectory = root,
        fileSystem = fileSystem,
      )
      assertEquals(sourcePackage, reopenedStorage.readSource(sourcePackage.reference))
    } finally {
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }
}
