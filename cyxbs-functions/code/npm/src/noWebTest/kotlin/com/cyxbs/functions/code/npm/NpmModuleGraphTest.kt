package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.model.NpmPackageId
import com.cyxbs.functions.code.npm.model.NpmPreparedEntry
import com.cyxbs.functions.code.npm.model.NpmResolvedPackage
import com.cyxbs.functions.code.npm.module.NpmModuleGraphFactory
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
          resolvedAtEpochMillis = 0,
          entryPackage = NpmPackageId("@cyxbs/language-javascript", "1.4.0"),
          entryModule = "index.js",
          archives = listOf(dependencyArchive, entryArchive),
          resolvedPackages = listOf(
            resolvedPackage("example-dependency", "1.0.0"),
            resolvedPackage(
              "@cyxbs/language-javascript",
              "1.4.0",
              "example-dependency" to NpmPackageId("example-dependency", "1.0.0"),
            ),
          ),
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

  @Test
  fun resolveBareImportAgainstOwningPackageVersionGraph() = runTest {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-multiversion-test-${Random.nextLong().toString(16)}"
    fileSystem.createDirectories(root)
    try {
      val sharedV1 = writeArchive(
        fileSystem,
        root / "shared-v1.tgz",
        packageFiles("shared", "1.0.0", "export const version = 'v1';"),
      )
      val sharedV2 = writeArchive(
        fileSystem,
        root / "shared-v2.tgz",
        packageFiles("shared", "2.0.0", "export const version = 'v2';"),
      )
      val bridge = writeArchive(
        fileSystem,
        root / "bridge.tgz",
        packageFiles("bridge", "1.0.0", "export { version } from 'shared';"),
      )
      val entry = writeArchive(
        fileSystem,
        root / "entry.tgz",
        packageFiles("entry", "1.0.0", "export { version } from 'shared';"),
      )
      val graph = NpmModuleGraphFactory(fileSystem = fileSystem).create(
        NpmPreparedEntry(
          resolvedAtEpochMillis = 0,
          entryPackage = NpmPackageId("entry", "1.0.0"),
          entryModule = "index.js",
          archives = listOf(sharedV1, sharedV2, bridge, entry),
          resolvedPackages = listOf(
            resolvedPackage("shared", "1.0.0"),
            resolvedPackage("shared", "2.0.0"),
            resolvedPackage("bridge", "1.0.0", "shared" to NpmPackageId("shared", "2.0.0")),
            resolvedPackage(
              "entry",
              "1.0.0",
              "shared" to NpmPackageId("shared", "1.0.0"),
              "bridge" to NpmPackageId("bridge", "1.0.0"),
            ),
          ),
        ),
      )

      val entryName = graph.entryModuleName
      val entryShared = graph.normalize(entryName, "shared")
      val bridgeName = graph.normalize(entryName, "bridge")
      val bridgeShared = graph.normalize(bridgeName, "shared")
      assertEquals("export const version = 'v1';", graph.load(entryShared))
      assertEquals("export const version = 'v2';", graph.load(bridgeShared))
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

  /** 构造只有默认 ESM 入口的测试包文件。 */
  private fun packageFiles(name: String, version: String, source: String): Map<String, String> {
    return mapOf(
      "package/package.json" to
        """{"name": "$name", "version": "$version", "type": "module", "exports": "./index.js"}""",
      "package/index.js" to source,
    )
  }

  /** 构造固定依赖版本的执行图节点。 */
  private fun resolvedPackage(
    name: String,
    version: String,
    vararg dependencies: Pair<String, NpmPackageId>,
  ): NpmResolvedPackage {
    return NpmResolvedPackage(
      id = NpmPackageId(name, version),
      integrity = "test-integrity",
      dependencies = dependencies.toMap(),
    )
  }

  private companion object {
    const val TAR_BLOCK_SIZE = 512
  }
}
