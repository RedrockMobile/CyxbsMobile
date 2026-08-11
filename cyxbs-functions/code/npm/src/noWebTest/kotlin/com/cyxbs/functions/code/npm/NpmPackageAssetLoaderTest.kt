package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.model.NpmEntryVersion
import com.cyxbs.functions.code.npm.model.NpmPackageAssetException
import com.cyxbs.functions.code.npm.model.NpmPackageId
import com.cyxbs.functions.code.npm.model.NpmPreparedEntry
import com.cyxbs.functions.code.npm.model.NpmResolvedPackage
import com.cyxbs.functions.code.npm.pool.NpmPreparedEntryLease
import com.cyxbs.functions.code.npm.storage.NpmPackageArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.GzipSink
import okio.buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 验证静态 npm 资源读取只访问根包，并始终释放短生命周期入口租约。 */
class NpmPackageAssetLoaderTest {

  /** 从含依赖的入口图读取根包资源，并透传 latest 请求语义。 */
  @Test
  fun loadsRootPackageAssetAndReleasesLease() = runTest {
    withArchives { dependencyArchive, rootArchive ->
      var releaseCount = 0
      val loader = NpmPackageAssetLoader(
        entryProvider = NpmPackageAssetEntryProvider { request, _, _ ->
          assertEquals("@cyxbs-mobile/language-catalog", request.packageName)
          assertIs<NpmEntryVersion.Latest>(request.version)
          NpmPreparedEntryLease(
            preparedEntry = preparedEntry(dependencyArchive, rootArchive),
            releaseAction = { releaseCount += 1 },
          )
        },
        fileSystem = FileSystem.SYSTEM,
        ioDispatcher = Dispatchers.Default,
      )

      assertEquals(
        "{\"protocolVersion\":1}",
        loader.loadText("@cyxbs-mobile/language-catalog", "catalog.json"),
      )
      assertEquals(1, releaseCount)
    }
  }

  /** 文件不存在时仍释放租约，并报告稳定的资源异常。 */
  @Test
  fun missingAssetReleasesLease() = runTest {
    withArchives { dependencyArchive, rootArchive ->
      var releaseCount = 0
      val loader = NpmPackageAssetLoader(
        entryProvider = NpmPackageAssetEntryProvider { _, _, _ ->
          NpmPreparedEntryLease(
            preparedEntry = preparedEntry(dependencyArchive, rootArchive),
            releaseAction = { releaseCount += 1 },
          )
        },
        fileSystem = FileSystem.SYSTEM,
        ioDispatcher = Dispatchers.Default,
      )

      assertFailsWith<NpmPackageAssetException> {
        loader.load("@cyxbs-mobile/language-catalog", "missing.json")
      }
      assertEquals(1, releaseCount)
    }
  }

  /** 路径穿越在进入包池前失败，不能为非法请求创建入口和网络工作。 */
  @Test
  fun rejectsTraversalBeforeAcquiringEntry() = runTest {
    var acquireCount = 0
    val loader = NpmPackageAssetLoader(
      entryProvider = NpmPackageAssetEntryProvider { _, _, _ ->
        acquireCount += 1
        error("Illegal asset path must not acquire an entry.")
      },
      fileSystem = FileSystem.SYSTEM,
      ioDispatcher = Dispatchers.Default,
    )

    assertFailsWith<NpmPackageAssetException> {
      loader.load("@cyxbs-mobile/language-catalog", "../catalog.json")
    }
    assertEquals(0, acquireCount)
  }

  /** 创建临时根包和依赖包归档，并在测试后回收文件。 */
  private suspend fun withArchives(
    block: suspend (dependency: NpmPackageArchive, root: NpmPackageArchive) -> Unit,
  ) {
    val fileSystem = FileSystem.SYSTEM
    val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-asset-test-${Random.nextLong().toString(16)}"
    fileSystem.createDirectories(directory)
    try {
      val dependency = writeArchive(
        fileSystem,
        directory / "dependency.tgz",
        packageName = "example-dependency",
        version = "2.0.0",
        files = mapOf("catalog.json" to "wrong package"),
      )
      val root = writeArchive(
        fileSystem,
        directory / "catalog.tgz",
        packageName = "@cyxbs-mobile/language-catalog",
        version = "1.0.0",
        files = mapOf("catalog.json" to "{\"protocolVersion\":1}"),
      )
      block(dependency, root)
    } finally {
      fileSystem.deleteRecursively(directory, mustExist = false)
    }
  }

  /** 构造包含根包和依赖包的已准备入口。 */
  private fun preparedEntry(
    dependency: NpmPackageArchive,
    root: NpmPackageArchive,
  ): NpmPreparedEntry {
    val rootId = NpmPackageId(root.packageName, root.version)
    val dependencyId = NpmPackageId(dependency.packageName, dependency.version)
    return NpmPreparedEntry(
      resolvedAtEpochMillis = 0,
      entryPackage = rootId,
      entryModule = null,
      archives = listOf(dependency, root),
      resolvedPackages = listOf(
        NpmResolvedPackage(dependencyId, dependency.integrity, emptyMap()),
        NpmResolvedPackage(rootId, root.integrity, mapOf(dependency.packageName to dependencyId)),
      ),
    )
  }

  /** 写入测试专用的最小 npm ustar+gzip 归档。 */
  private fun writeArchive(
    fileSystem: FileSystem,
    path: okio.Path,
    packageName: String,
    version: String,
    files: Map<String, String>,
  ): NpmPackageArchive {
    val tar = Buffer()
    files.forEach { (name, content) ->
      val bytes = content.encodeToByteArray()
      val header = ByteArray(TAR_BLOCK_SIZE)
      header.writeText(0, 100, "package/$name")
      header.writeText(124, 12, bytes.size.toString(8).padStart(11, '0'))
      header[156] = '0'.code.toByte()
      tar.write(header)
      tar.write(bytes)
      val padding = (TAR_BLOCK_SIZE - bytes.size % TAR_BLOCK_SIZE) % TAR_BLOCK_SIZE
      if (padding > 0) tar.write(ByteArray(padding))
    }
    tar.write(ByteArray(TAR_BLOCK_SIZE * 2))

    val compressed = Buffer()
    GzipSink(compressed).buffer().use { gzip -> gzip.writeAll(tar) }
    fileSystem.write(path) { writeAll(compressed) }
    return NpmPackageArchive(packageName, version, "test-integrity", path)
  }

  /** 向固定长度 tar header 字段写入 UTF-8 文本。 */
  private fun ByteArray.writeText(offset: Int, length: Int, value: String) {
    val bytes = value.encodeToByteArray()
    require(bytes.size < length)
    bytes.copyInto(this, destinationOffset = offset)
  }

  private companion object {
    const val TAR_BLOCK_SIZE = 512
  }
}
