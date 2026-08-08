package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.internal.encodeNpmPathSegment
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** 全局包池的远端首次解析、本地代际重解析、租约与可达性 GC 测试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NpmPackagePoolTest {

  @Test
  fun firstLoadUsesRemoteAndOtherEntryChangeTriggersLocalOnlyReresolution() = runTest {
    withPool { registry, pool, _ ->
      registry.publish(
        packageVersion(SHARED, "1.0.0"),
        packageVersion(TARGET, "1.0.0", dependencies = mapOf(SHARED to "^1.0.0")),
      )

      val targetV1 = pool.acquireEntry(NpmEntryRequest(TARGET))
      assertEquals("1.0.0", targetV1.preparedEntry.dependencyVersion(TARGET, SHARED))
      targetV1.release()

      // 全局池已有 shared@1，但新入口首次加载仍按远端 metadata 选择 shared@1.1。
      registry.publish(
        packageVersion(SHARED, "1.0.0"),
        packageVersion(SHARED, "1.1.0"),
        packageVersion(OTHER, "1.0.0", dependencies = mapOf(SHARED to "^1.0.0")),
      )
      val other = pool.acquireEntry(NpmEntryRequest(OTHER))
      assertEquals("1.1.0", other.preparedEntry.dependencyVersion(OTHER, SHARED))
      other.release()
      assertTrue(
        registry.metadataRequestHeaders.any {
          it["Accept"] == "application/vnd.npm.install-v1+json"
        },
      )

      registry.clearRequestHistory()

      // other 扩充了池代际；target 下次加载只用本地池重解析，不请求 metadata。
      val targetV2 = pool.acquireEntry(NpmEntryRequest(TARGET))
      assertEquals("1.1.0", targetV2.preparedEntry.dependencyVersion(TARGET, SHARED))
      targetV2.release()
      val targetAgain = pool.acquireEntry(NpmEntryRequest(TARGET))
      targetAgain.release()

      assertTrue(registry.metadataRequests.isEmpty())
      assertTrue(registry.archiveRequests.isEmpty())
    }
  }

  @Test
  fun garbageCollectionKeepsSharedDependencyAndCollectsUnreachableCycle() = runTest {
    withPool { registry, pool, clock ->
      registry.publish(
        packageVersion(SHARED, "1.0.0"),
        packageVersion(CYCLE_B, "1.0.0", dependencies = mapOf(CYCLE_C to "1.0.0")),
        packageVersion(CYCLE_C, "1.0.0", dependencies = mapOf(CYCLE_B to "1.0.0")),
        packageVersion(
          ENTRY_A,
          "1.0.0",
          dependencies = mapOf(SHARED to "1.0.0", CYCLE_B to "1.0.0"),
        ),
        packageVersion(ENTRY_X, "1.0.0", dependencies = mapOf(SHARED to "1.0.0")),
      )

      pool.acquireEntry(NpmEntryRequest(ENTRY_A)).release()
      pool.acquireEntry(NpmEntryRequest(ENTRY_X)).release()

      clock.now = 13.days.inWholeMilliseconds
      pool.acquireEntry(NpmEntryRequest(ENTRY_X)).release()
      clock.now = 15.days.inWholeMilliseconds
      pool.collectGarbage()
      registry.clearRequestHistory()

      // X 仍在有效期，完整命中本地；shared 因 X 可达而保留。
      pool.acquireEntry(NpmEntryRequest(ENTRY_X)).release()
      assertTrue(registry.metadataRequests.isEmpty())
      assertTrue(registry.archiveRequests.isEmpty())

      // A 已被 GC，重新加载会远端解析；其独占的 A/B/C 需下载，shared 不会重复下载。
      pool.acquireEntry(NpmEntryRequest(ENTRY_A)).release()
      assertTrue(ENTRY_A in registry.metadataRequests)
      assertTrue(CYCLE_B in registry.metadataRequests)
      assertTrue(CYCLE_C in registry.metadataRequests)
      assertTrue(SHARED in registry.metadataRequests)
      assertFalse(registry.archiveRequests.any { it.contains("shared-1.0.0") })
      assertTrue(registry.archiveRequests.any { it.contains("entry-a-1.0.0") })
      assertTrue(registry.archiveRequests.any { it.contains("cycle-b-1.0.0") })
      assertTrue(registry.archiveRequests.any { it.contains("cycle-c-1.0.0") })
    }
  }

  @Test
  fun activeLeasePreventsExpiredEntryCollection() = runTest {
    withPool { registry, pool, clock ->
      registry.publish(packageVersion(TARGET, "1.0.0"))
      val active = pool.acquireEntry(NpmEntryRequest(TARGET))
      clock.now = 15.days.inWholeMilliseconds
      pool.collectGarbage()
      registry.clearRequestHistory()

      val reused = pool.acquireEntry(NpmEntryRequest(TARGET))
      reused.release()
      assertTrue(registry.metadataRequests.isEmpty())
      assertTrue(registry.archiveRequests.isEmpty())

      active.release()
    }
  }

  @Test
  fun changingExactEntryVersionTriggersRemoteResolution() = runTest {
    withPool { registry, pool, _ ->
      registry.publish(
        packageVersion(TARGET, "1.0.0"),
        packageVersion(TARGET, "2.0.0"),
      )
      pool.acquireEntry(
        NpmEntryRequest(TARGET, version = NpmEntryVersion.Exact("1.0.0")),
      ).release()
      registry.clearRequestHistory()

      val changed = pool.acquireEntry(
        NpmEntryRequest(TARGET, version = NpmEntryVersion.Exact("2.0.0")),
      )
      assertEquals("2.0.0", changed.preparedEntry.entryPackage.version)
      changed.release()
      assertTrue(TARGET in registry.metadataRequests)
      assertTrue(registry.archiveRequests.any { it.contains("target-2.0.0") })
    }
  }

  @Test
  fun exactVersionReusesPersistedResolutionAcrossPoolInstances() = runTest {
    withPoolStorage { registry, root, fileSystem, clock, backgroundScope ->
      registry.publish(packageVersion(TARGET, "1.0.0"))
      val request = NpmEntryRequest(TARGET, version = NpmEntryVersion.Exact("1.0.0"))
      createPool(registry, root, fileSystem, clock, backgroundScope).acquireEntry(request).release()
      advanceUntilIdle()

      registry.publish(
        packageVersion(TARGET, "1.0.0"),
        packageVersion(TARGET, "2.0.0"),
      )
      registry.clearRequestHistory()

      val reused = createPool(registry, root, fileSystem, clock, backgroundScope).acquireEntry(request)
      assertEquals("1.0.0", reused.preparedEntry.entryPackage.version)
      reused.release()
      assertTrue(registry.metadataRequests.isEmpty())
      assertTrue(registry.archiveRequests.isEmpty())
    }
  }

  @Test
  fun latestRefreshesBeforeFirstRunOfEachPoolInstanceAndDeletesOldVersion() = runTest {
    withPoolStorage { registry, root, fileSystem, clock, backgroundScope ->
      registry.publish(packageVersion(TARGET, "1.0.0"))
      val firstPool = createPool(registry, root, fileSystem, clock, backgroundScope)
      val first = firstPool.acquireEntry(NpmEntryRequest(TARGET))
      val oldArchive = first.preparedEntry.archives.single().archivePath
      first.release()
      advanceUntilIdle()

      registry.publish(
        packageVersion(TARGET, "1.0.0"),
        packageVersion(TARGET, "2.0.0"),
      )
      registry.clearRequestHistory()

      val restartedPool = createPool(registry, root, fileSystem, clock, backgroundScope)
      val refreshed = restartedPool.acquireEntry(NpmEntryRequest(TARGET))
      assertEquals("2.0.0", refreshed.preparedEntry.entryPackage.version)
      assertTrue(TARGET in registry.metadataRequests)
      assertTrue(registry.archiveRequests.any { it.contains("target-2.0.0") })
      // 新图已可运行后才异步清理旧图，下载链路不会等待缓存删除。
      assertTrue(fileSystem.exists(oldArchive))
      advanceUntilIdle()
      assertFalse(fileSystem.exists(oldArchive))
      refreshed.release()

      registry.clearRequestHistory()
      restartedPool.acquireEntry(NpmEntryRequest(TARGET)).release()
      assertTrue(registry.metadataRequests.isEmpty())
      assertTrue(registry.archiveRequests.isEmpty())
    }
  }

  @Test
  fun latestRefreshAlsoUpdatesTransitiveDependencyBeforeRun() = runTest {
    withPoolStorage { registry, root, fileSystem, clock, backgroundScope ->
      registry.publish(
        packageVersion(SHARED, "1.0.0"),
        packageVersion(TARGET, "1.0.0", dependencies = mapOf(SHARED to "^1.0.0")),
      )
      val first = createPool(registry, root, fileSystem, clock, backgroundScope)
        .acquireEntry(NpmEntryRequest(TARGET))
      val oldSharedArchive = first.preparedEntry.archives
        .single { it.packageName == SHARED }
        .archivePath
      first.release()
      advanceUntilIdle()

      registry.publish(
        packageVersion(SHARED, "1.0.0"),
        packageVersion(SHARED, "1.1.0"),
        packageVersion(TARGET, "1.0.0", dependencies = mapOf(SHARED to "^1.0.0")),
      )
      val refreshed = createPool(registry, root, fileSystem, clock, backgroundScope)
        .acquireEntry(NpmEntryRequest(TARGET))

      assertEquals("1.0.0", refreshed.preparedEntry.entryPackage.version)
      assertEquals("1.1.0", refreshed.preparedEntry.dependencyVersion(TARGET, SHARED))
      assertTrue(fileSystem.exists(oldSharedArchive))
      advanceUntilIdle()
      assertFalse(fileSystem.exists(oldSharedArchive))
      refreshed.release()
    }
  }

  @Test
  fun replacedVersionIsDeletedOnlyAfterOldRuntimeLeaseReleases() = runTest {
    withPool { registry, pool, _ ->
      registry.publish(
        packageVersion(TARGET, "1.0.0"),
        packageVersion(TARGET, "2.0.0"),
      )
      val oldRuntime = pool.acquireEntry(
        NpmEntryRequest(TARGET, version = NpmEntryVersion.Exact("1.0.0")),
      )
      val oldArchive = oldRuntime.preparedEntry.archives.single().archivePath

      val newRuntime = pool.acquireEntry(
        NpmEntryRequest(TARGET, version = NpmEntryVersion.Exact("2.0.0")),
      )
      advanceUntilIdle()
      assertTrue(FileSystem.SYSTEM.exists(oldArchive))
      newRuntime.release()
      assertTrue(FileSystem.SYSTEM.exists(oldArchive))

      oldRuntime.release()
      // release 只解除运行保护，不在调用链上执行磁盘清理。
      assertTrue(FileSystem.SYSTEM.exists(oldArchive))
      pool.collectGarbage()
      assertFalse(FileSystem.SYSTEM.exists(oldArchive))
    }
  }

  @Test
  fun latestRefreshFailureFallsBackBeforeRuntimeCreation() = runTest {
    withPoolStorage { registry, root, fileSystem, clock, backgroundScope ->
      registry.publish(packageVersion(TARGET, "1.0.0"))
      createPool(registry, root, fileSystem, clock, backgroundScope)
        .acquireEntry(NpmEntryRequest(TARGET))
        .release()
      advanceUntilIdle()

      registry.clearRequestHistory()
      registry.failMetadata(TARGET)
      val fallback = createPool(registry, root, fileSystem, clock, backgroundScope)
        .acquireEntry(NpmEntryRequest(TARGET))

      assertEquals("1.0.0", fallback.preparedEntry.entryPackage.version)
      fallback.release()
      assertTrue(TARGET in registry.metadataRequests)
      assertTrue(registry.archiveRequests.isEmpty())
    }
  }

  /** 为每个测试建立独立的真实 Okio 目录，并保证结束后清理。 */
  private suspend fun TestScope.withPool(
    block: suspend (FakeNpmRegistry, NpmPackagePool, MutableClock) -> Unit,
  ) {
    withPoolStorage { registry, root, fileSystem, clock, backgroundScope ->
      block(registry, createPool(registry, root, fileSystem, clock, backgroundScope), clock)
    }
  }

  /** 为需要模拟进程重启的测试暴露同一个持久化目录。 */
  private suspend fun TestScope.withPoolStorage(
    block: suspend (FakeNpmRegistry, Path, FileSystem, MutableClock, TestScope) -> Unit,
  ) {
    val fileSystem = FileSystem.SYSTEM
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-npm-pool-test-${Random.nextLong().toString(16)}"
    val registry = FakeNpmRegistry()
    val clock = MutableClock()
    try {
      block(registry, root, fileSystem, clock, this)
    } finally {
      // 先完成包池调度的异步 GC，再删除测试目录，避免后台任务与清理目录竞争。
      advanceUntilIdle()
      fileSystem.deleteRecursively(root, mustExist = false)
    }
  }

  /** 使用同一持久化目录创建一个新的进程内包池实例。 */
  private fun createPool(
    registry: FakeNpmRegistry,
    root: Path,
    fileSystem: FileSystem,
    clock: MutableClock,
    backgroundScope: TestScope,
  ): NpmPackagePool {
    return NpmPackagePool(
      transport = registry,
      rootDirectory = root,
      fileSystem = fileSystem,
      ioDispatcher = StandardTestDispatcher(backgroundScope.testScheduler),
      backgroundScope = backgroundScope,
      clock = { clock.now },
    )
  }

  private companion object {
    const val SHARED = "shared"
    const val TARGET = "target"
    const val OTHER = "other"
    const val ENTRY_A = "entry-a"
    const val ENTRY_X = "entry-x"
    const val CYCLE_B = "cycle-b"
    const val CYCLE_C = "cycle-c"
  }
}

/** 从入口精确图读取一个父包最终锁定的依赖版本。 */
private fun NpmPreparedEntry.dependencyVersion(parent: String, dependency: String): String {
  return resolvedPackages
    .single { it.id.name == parent }
    .dependencies
    .getValue(dependency)
    .version
}

private class MutableClock(var now: Long = 0)

/** 一个版本的 registry 元数据和对应 tgz 测试载荷。 */
private class FakePackageVersion(
  val name: String,
  val version: String,
  val dependencies: Map<String, String>,
  val archiveUrl: String,
  val archiveBytes: ByteArray,
  val integrity: String,
)

private fun packageVersion(
  name: String,
  version: String,
  dependencies: Map<String, String> = emptyMap(),
): FakePackageVersion {
  val bytes = "archive:$name@$version".encodeToByteArray()
  val fileName = name.substringAfterLast('/')
  return FakePackageVersion(
    name = name,
    version = version,
    dependencies = dependencies,
    archiveUrl = "https://cdn.test/$fileName-$version.tgz",
    archiveBytes = bytes,
    integrity = "sha512-${bytes.toByteString().sha512().base64()}",
  )
}

/**
 * 可在测试中更新版本目录的 npm registry。
 *
 * metadata 请求始终返回当前 publish 的完整版本集合，tgz 则按精确 URL 返回不可变内容。
 */
private class FakeNpmRegistry : NpmHttpTransport {
  private val metadata = mutableMapOf<String, ByteArray>()
  private val metadataFailures = mutableMapOf<String, NpmDownloadException>()
  private val archives = mutableMapOf<String, ByteArray>()
  val metadataRequests = mutableListOf<String>()
  val archiveRequests = mutableListOf<String>()
  val metadataRequestHeaders = mutableListOf<Map<String, String>>()

  fun publish(vararg versions: FakePackageVersion) {
    versions.groupBy(FakePackageVersion::name).forEach { (name, packageVersions) ->
      val latest = packageVersions.maxBy { it.version }.version
      val response = buildJsonObject {
        put("name", name)
        put("dist-tags", buildJsonObject { put("latest", latest) })
        put("versions", buildJsonObject {
          packageVersions.forEach { item ->
            put(item.version, buildJsonObject {
              put("name", item.name)
              put("version", item.version)
              put("dependencies", buildJsonObject {
                item.dependencies.forEach { (dependency, range) -> put(dependency, range) }
              })
              put("dist", buildJsonObject {
                put("integrity", item.integrity)
                put("tarball", item.archiveUrl)
              })
            })
            archives[item.archiveUrl] = item.archiveBytes
          }
        })
      }
      metadata[metadataUrl(name)] = response.toString().encodeToByteArray()
      metadataFailures.remove(metadataUrl(name))
    }
  }

  /** 让指定包的 metadata 请求模拟网络失败。 */
  fun failMetadata(packageName: String) {
    metadataFailures[metadataUrl(packageName)] = NpmDownloadException("Registry unavailable.")
  }

  fun clearRequestHistory() {
    metadataRequests.clear()
    archiveRequests.clear()
    metadataRequestHeaders.clear()
  }

  override suspend fun get(url: String): ByteArray {
    archives[url]?.let {
      archiveRequests += url
      return it
    }
    metadata[url]?.let {
      val packageName = metadata.entries.single { entry -> entry.key == url }
        .key.substringAfterLast('/')
      metadataRequests += packageName
      metadataFailures[url]?.let { throw it }
      return it
    }
    error("Unexpected npm test request: $url")
  }

  override suspend fun get(url: String, headers: Map<String, String>): ByteArray {
    if (url in metadata) metadataRequestHeaders += headers
    return get(url)
  }

  private fun metadataUrl(name: String): String {
    return "https://registry.npmjs.org/${name.encodeNpmPathSegment()}"
  }
}
