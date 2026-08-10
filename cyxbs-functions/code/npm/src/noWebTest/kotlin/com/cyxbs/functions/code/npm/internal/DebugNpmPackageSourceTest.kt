package com.cyxbs.functions.code.npm.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.FileSystem
import okio.GzipSink
import okio.Path
import okio.SYSTEM
import okio.buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 本地 debug tgz 的按需发现、metadata 合并和虚拟下载地址测试。 */
class DebugNpmPackageSourceTest {

  @Test
  fun localDebugPackageMergesWithRegistryAndStableVersionCanOverrideIt() {
    withSource { source, root, fileSystem ->
      val packageName = "@cyxbs-mobile/language-javascript"
      val debugVersion = "0.1.1-debug.20260809153042"
      val archivePath = root / "@cyxbs-mobile" / "language-javascript.tgz"
      fileSystem.createDirectories(requireNotNull(archivePath.parent))
      val archiveBytes = writeArchive(
        fileSystem = fileSystem,
        path = archivePath,
        packageJson = """{
          "name": "$packageName",
          "version": "$debugVersion",
          "dependencies": {"@cyxbs-mobile/kotlin-js-runtime": "0.1.0"}
        }""".trimIndent(),
      )

      val localPackage = assertNotNull(source.read(packageName))
      val mergedWithOlderRemote = source.mergeMetadata(
        localPackage,
        registryMetadata(packageName, "0.1.0"),
      ).decodeToString().let(Json::parseToJsonElement).jsonObject
      assertEquals(
        debugVersion,
        mergedWithOlderRemote.getValue("dist-tags").jsonObject
          .getValue("latest").jsonPrimitive.content,
      )
      val localMetadata = mergedWithOlderRemote.getValue("versions").jsonObject
        .getValue(debugVersion).jsonObject
      val localTarball = localMetadata.getValue("dist").jsonObject
        .getValue("tarball").jsonPrimitive.content
      assertEquals(packageName, source.packageNameFromLocalTarballUrl(localTarball))
      assertContentEquals(archiveBytes, source.read(packageName)?.archiveBytes)

      // 同基线正式版高于 debug 预发布版，下一次 latest 刷新会自然回到正式版本。
      val mergedWithStable = source.mergeMetadata(
        localPackage,
        registryMetadata(packageName, "0.1.1"),
      ).decodeToString().let(Json::parseToJsonElement).jsonObject
      assertEquals(
        "0.1.1",
        mergedWithStable.getValue("dist-tags").jsonObject
          .getValue("latest").jsonPrimitive.content,
      )
    }
  }

  @Test
  fun metadataCanBeSynthesizedWhenRegistryIsUnavailable() {
    withSource { source, root, fileSystem ->
      val packageName = "lesson-runtime"
      val debugVersion = "2.0.0-debug.20260809153100"
      fileSystem.createDirectories(root)
      writeArchive(
        fileSystem,
        root / "lesson-runtime.tgz",
        """{"name":"$packageName","version":"$debugVersion"}""",
      )

      val localPackage = assertNotNull(source.read(packageName))
      val metadata = source.mergeMetadata(localPackage, null)
        .decodeToString()
        .let(Json::parseToJsonElement)
        .jsonObject
      assertEquals(packageName, metadata.getValue("name").jsonPrimitive.content)
      assertEquals(
        debugVersion,
        metadata.getValue("dist-tags").jsonObject.getValue("latest").jsonPrimitive.content,
      )
      assertEquals(
        packageName,
        source.packageNameFromMetadataUrl(
          "https://registry.npmjs.org/${packageName.encodeNpmPathSegment()}",
        ),
      )
    }
  }

  /** 为每个用例创建独立的固定 debug 根目录并在结束后清理。 */
  private fun withSource(block: (DebugNpmPackageSource, Path, FileSystem) -> Unit) {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-debug-npm-test-${Random.nextLong().toString(16)}"
    try {
      block(DebugNpmPackageSource(fileSystem, root), root, fileSystem)
    } finally {
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 构造一个只包含单版本的 abbreviated registry metadata。 */
  private fun registryMetadata(packageName: String, version: String): ByteArray {
    return """{
      "name": "$packageName",
      "dist-tags": {"latest": "$version"},
      "versions": {
        "$version": {
          "name": "$packageName",
          "version": "$version",
          "dist": {
            "integrity": "sha512-dGVzdA==",
            "tarball": "https://registry.test/package.tgz"
          }
        }
      }
    }""".trimIndent().encodeToByteArray()
  }

  /** 写入足以覆盖 package.json 读取链路的最小 npm ustar+gzip 归档。 */
  private fun writeArchive(
    fileSystem: FileSystem,
    path: Path,
    packageJson: String,
  ): ByteArray {
    val payload = packageJson.encodeToByteArray()
    val header = ByteArray(TAR_BLOCK_SIZE)
    header.writeText(0, 100, "package/package.json")
    header.writeText(124, 12, payload.size.toString(radix = 8).padStart(11, '0'))
    header[156] = '0'.code.toByte()
    val tar = Buffer()
    tar.write(header)
    tar.write(payload)
    val padding = (TAR_BLOCK_SIZE - payload.size % TAR_BLOCK_SIZE) % TAR_BLOCK_SIZE
    if (padding > 0) tar.write(ByteArray(padding))
    tar.write(ByteArray(TAR_BLOCK_SIZE * 2))

    val compressed = Buffer()
    val gzip = GzipSink(compressed).buffer()
    try {
      gzip.writeAll(tar)
    } finally {
      gzip.close()
    }
    val bytes = compressed.readByteArray()
    fileSystem.write(path) { write(bytes) }
    return bytes
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
