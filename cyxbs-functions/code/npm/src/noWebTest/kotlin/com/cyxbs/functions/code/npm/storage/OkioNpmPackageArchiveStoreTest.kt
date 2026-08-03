package com.cyxbs.functions.code.npm.storage

import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 验证真实文件缓存会重新校验 SRI，且损坏内容不会继续暴露给后续解包流程。
 */
class OkioNpmPackageArchiveStoreTest {

  @Test
  fun corruptedArchiveIsRemovedAfterSuccessfulAtomicWrite() = runTest {
    val fileSystem = FileSystem.SYSTEM
    val rootDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-store-test-${Random.nextLong().toString(16)}"
    val bytes = "valid npm archive".encodeToByteArray()
    val integrity = "sha512-${bytes.toByteString().sha512().base64()}"
    val store = OkioNpmPackageArchiveStore(
      rootDirectory = rootDirectory,
      fileSystem = fileSystem,
    )

    try {
      val written = store.write(
        packageName = "@cyxbs/language-javascript",
        version = "1.4.0",
        integrity = integrity,
        bytes = bytes,
      )

      assertEquals(
        written,
        store.find(
          packageName = written.packageName,
          version = written.version,
          integrity = written.integrity,
        ),
      )

      fileSystem.write(written.archivePath) {
        write("tampered".encodeToByteArray())
      }

      assertNull(
        store.find(
          packageName = written.packageName,
          version = written.version,
          integrity = written.integrity,
        ),
      )
      assertFalse(fileSystem.exists(written.archivePath))
    } finally {
      fileSystem.deleteRecursively(rootDirectory, mustExist = false)
    }
  }
}
