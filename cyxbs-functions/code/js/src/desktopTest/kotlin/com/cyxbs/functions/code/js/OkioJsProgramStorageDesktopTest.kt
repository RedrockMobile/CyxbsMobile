package com.cyxbs.functions.code.js

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 使用真实 Desktop 文件系统验证源码和字节码的原子持久化格式。
 */
class OkioJsProgramStorageDesktopTest {

  /**
   * 验证源码可跨存储实例读取，字节码可保存且损坏内容不会交给 QuickJS。
   */
  @Test
  fun persistSourceAndRejectCorruptedBytecode() = runTest {
    val root = Files.createTempDirectory("cyxbs-js-storage").toString().toPath()
    val fileSystem = FileSystem.SYSTEM
    try {
      val sourcePackage = JsSourcePackage.create(
        packageId = "storage.desktop",
        version = "1",
        files = mapOf(QuickJsRuntime.DEFAULT_FILENAME to "40 + 2"),
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

      val key = JsBytecodeCacheKey("a".repeat(64))
      val bytecode = byteArrayOf(1, 2, 3, 4)
      reopenedStorage.writeBytecode(key = key, bytecode = bytecode)
      assertContentEquals(bytecode, reopenedStorage.readBytecode(key))

      val cachePath = root / "bytecode" / "${key.value}.qbc"
      fileSystem.write(cachePath) {
        writeUtf8("corrupted")
      }
      assertNull(reopenedStorage.readBytecode(key))
      assertFalse(fileSystem.exists(cachePath))
    } finally {
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }
}
