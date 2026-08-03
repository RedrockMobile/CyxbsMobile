package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.storage.NpmPackageArchive
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.GzipSink
import okio.SYSTEM
import okio.buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** npm tgz 解包、入口、裸包名、子路径与相对 import 的跨平台测试。 */
class NpmModuleGraphTest {

  @Test
  fun resolvePreparedPackagesIntoLoadableModules() = runTest {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-graph-test-${Random.nextLong().toString(16)}"
    fileSystem.createDirectories(root)
    try {
      val dependencyArchive = writeArchive(
        fileSystem = fileSystem,
        path = root / "dependency.tgz",
        files = mapOf(
          "package/package.json" to """
            {
              "name": "example-dependency",
              "version": "1.0.0",
              "type": "module",
              "exports": {
                ".": "./dist/index.js",
                "./features/*": "./dist/features/*.js"
              }
            }
          """.trimIndent(),
          "package/dist/index.js" to "export const dependency = 40;",
          "package/dist/features/offset.js" to "export const offset = 2;",
        ),
      )
      val entryArchive = writeArchive(
        fileSystem = fileSystem,
        path = root / "entry.tgz",
        files = mapOf(
          "package/package.json" to """
            {
              "name": "@cyxbs/language-javascript",
              "version": "1.4.0",
              "type": "module",
              "exports": "./index.js"
            }
          """.trimIndent(),
          "package/index.js" to """
            import { dependency } from "example-dependency";
            import { local } from "./local.js";
            export const value = dependency + local;
          """.trimIndent(),
          "package/local.js" to "export const local = 2;",
        ),
      )
      val graph = NpmModuleGraphFactory(fileSystem = fileSystem).create(
        NpmPreparedEntry(
          releaseTime = "2026.08.03 12:01:10",
          entryPackage = "@cyxbs/language-javascript",
          entryModule = "index.js",
          archives = listOf(dependencyArchive, entryArchive),
        ),
      )

      val entryName = graph.normalize("lesson.js", "@cyxbs/language-javascript")
      assertEquals(graph.entryModuleName, entryName)
      assertNotNull(graph.load(entryName))

      val relativeName = graph.normalize(entryName, "./local.js")
      assertEquals("export const local = 2;", graph.load(relativeName))

      val dependencyName = graph.normalize(entryName, "example-dependency")
      assertEquals("export const dependency = 40;", graph.load(dependencyName))

      val featureName = graph.normalize(entryName, "example-dependency/features/offset")
      assertEquals("export const offset = 2;", graph.load(featureName))
    } finally {
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 写入测试专用的最小 npm ustar+gzip 归档。 */
  private fun writeArchive(
    fileSystem: FileSystem,
    path: okio.Path,
    files: Map<String, String>,
  ): NpmPackageArchive {
    val tar = Buffer()
    files.forEach { (name, content) ->
      val bytes = content.encodeToByteArray()
      val header = ByteArray(TAR_BLOCK_SIZE)
      header.writeText(offset = 0, length = 100, value = name)
      header.writeText(
        offset = 124,
        length = 12,
        value = bytes.size.toString(radix = 8).padStart(11, '0'),
      )
      header[156] = '0'.code.toByte()
      tar.write(header)
      tar.write(bytes)
      val padding = (TAR_BLOCK_SIZE - bytes.size % TAR_BLOCK_SIZE) % TAR_BLOCK_SIZE
      if (padding > 0) tar.write(ByteArray(padding))
    }
    tar.write(ByteArray(TAR_BLOCK_SIZE * 2))

    val compressed = Buffer()
    val gzip = GzipSink(compressed).buffer()
    try {
      gzip.writeAll(tar)
    } finally {
      gzip.close()
    }
    fileSystem.write(path) { writeAll(compressed) }
    val packageJson = files.getValue("package/package.json")
    val name = packageJson.substringAfter("\"name\": \"").substringBefore('"')
    val version = packageJson.substringAfter("\"version\": \"").substringBefore('"')
    return NpmPackageArchive(
      packageName = name,
      version = version,
      integrity = "test-integrity",
      archivePath = path,
    )
  }

  private fun ByteArray.writeText(offset: Int, length: Int, value: String) {
    val bytes = value.encodeToByteArray()
    require(bytes.size < length)
    bytes.copyInto(this, destinationOffset = offset)
  }

  private companion object {
    const val TAR_BLOCK_SIZE = 512
  }
}
