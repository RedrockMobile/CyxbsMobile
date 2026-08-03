package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.internal.buildNpmPackageDownloadUrl
import com.cyxbs.functions.code.npm.model.NpmLockedPackage
import com.cyxbs.functions.code.npm.model.NpmReleaseSnapshot
import com.cyxbs.functions.code.npm.storage.NpmPackageArchive
import com.cyxbs.functions.code.npm.storage.NpmPackageArchiveStore
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证客户端只信任后端精确快照，并在完整预检后按需下载入口依赖闭包。
 */
class NpmPackageDownloaderTest {

  @Test
  fun backendSnapshotJsonUsesTheLockedGraphContract() {
    val snapshot = Json.decodeFromString<NpmReleaseSnapshot>(
      """
        {
          "releaseTime": "2026.08.03 12:01:10",
          "entries": {
            "@cyxbs/language-javascript": "dist/index.js"
          },
          "urls": ["https://cdn.example/npm"],
          "packages": {
            "@cyxbs/language-javascript": {
              "version": "1.4.0",
              "dependencies": ["@lezer/javascript"],
              "integrity": "sha512-entry"
            },
            "@lezer/javascript": {
              "version": "1.5.4",
              "dependencies": [],
              "integrity": "sha512-dependency"
            }
          }
        }
      """.trimIndent(),
    )

    assertEquals("dist/index.js", snapshot.entries[ENTRY])
    assertEquals(listOf(DOWNLOAD_BASE_URL), snapshot.urls)
    assertEquals(listOf(DEPENDENCY), snapshot.packages.getValue(ENTRY).dependencies)
    assertEquals(DEPENDENCY_VERSION, snapshot.packages.getValue(DEPENDENCY).version)
  }

  @Test
  fun requestedEntryDownloadsOnlyItsDependencyClosure() = runTest {
    val dependencyBytes = "lezer-javascript".encodeToByteArray()
    val entryBytes = "cyxbs-language-javascript".encodeToByteArray()
    val unrelatedBytes = "unrelated".encodeToByteArray()
    val snapshot = snapshot(
      dependencyIntegrity = sri(dependencyBytes),
      entryIntegrity = sri(entryBytes),
      unrelatedIntegrity = sri(unrelatedBytes),
    )
    val transport = FakeTransport(
      mapOf(
        registryUrl(DEPENDENCY, DEPENDENCY_VERSION) to metadata(
          DEPENDENCY,
          DEPENDENCY_VERSION,
          sri(dependencyBytes),
        ),
        registryUrl(ENTRY, ENTRY_VERSION) to metadata(ENTRY, ENTRY_VERSION, sri(entryBytes)),
        DEPENDENCY_URL to dependencyBytes,
        ENTRY_URL to entryBytes,
      ),
    )
    val store = InMemoryArchiveStore()
    val downloader = NpmPackageDownloader(transport, store)

    val prepared = downloader.prepareEntry(snapshot, ENTRY)

    assertEquals("dist/index.js", prepared.entryModule)
    assertEquals(listOf(DEPENDENCY, ENTRY), prepared.archives.map { it.packageName })
    assertEquals(
      listOf(
        registryUrl(DEPENDENCY, DEPENDENCY_VERSION),
        registryUrl(ENTRY, ENTRY_VERSION),
        DEPENDENCY_URL,
        ENTRY_URL,
      ),
      transport.requestedUrls,
    )
    assertTrue(transport.requestedUrls.none { UNRELATED in it })
  }

  @Test
  fun registryMismatchPreventsEveryTarballDownload() = runTest {
    val dependencyBytes = "lezer-javascript".encodeToByteArray()
    val entryBytes = "cyxbs-language-javascript".encodeToByteArray()
    val snapshot = snapshot(
      dependencyIntegrity = sri(dependencyBytes),
      entryIntegrity = sri(entryBytes),
      unrelatedIntegrity = sri("unrelated".encodeToByteArray()),
    )
    val transport = FakeTransport(
      mapOf(
        registryUrl(DEPENDENCY, DEPENDENCY_VERSION) to metadata(
          DEPENDENCY,
          DEPENDENCY_VERSION,
          sri(dependencyBytes),
        ),
        registryUrl(ENTRY, ENTRY_VERSION) to metadata(
          ENTRY,
          "9.9.9",
          sri(entryBytes),
        ),
      ),
    )
    val store = InMemoryArchiveStore()

    assertFailsWith<NpmRegistryMismatchException> {
      NpmPackageDownloader(transport, store).prepareEntry(snapshot, ENTRY)
    }

    assertEquals(
      listOf(
        registryUrl(DEPENDENCY, DEPENDENCY_VERSION),
        registryUrl(ENTRY, ENTRY_VERSION),
      ),
      transport.requestedUrls,
    )
    assertTrue(store.writeCalls.isEmpty())
  }

  @Test
  fun downloadFallsBackToTheNextUrlAndThenUsesCache() = runTest {
    val entryBytes = "entry".encodeToByteArray()
    val integrity = sri(entryBytes)
    val firstBaseUrl = "https://cdn-a.example/npm"
    val secondBaseUrl = "https://cdn-b.example/npm"
    val firstUrl = buildNpmPackageDownloadUrl(firstBaseUrl, ENTRY, ENTRY_VERSION)
    val secondUrl = buildNpmPackageDownloadUrl(secondBaseUrl, ENTRY, ENTRY_VERSION)
    val snapshot = NpmReleaseSnapshot(
      releaseTime = RELEASE_TIME,
      entries = mapOf(ENTRY to "index.js"),
      urls = listOf(firstBaseUrl, secondBaseUrl),
      packages = mapOf(
        ENTRY to NpmLockedPackage(
          version = ENTRY_VERSION,
          integrity = integrity,
        ),
      ),
    )
    val transport = FakeTransport(
      responses = mapOf(
        registryUrl(ENTRY, ENTRY_VERSION) to metadata(ENTRY, ENTRY_VERSION, integrity),
        firstUrl to NpmDownloadException("Primary CDN unavailable."),
        secondUrl to entryBytes,
      ),
    )
    val store = InMemoryArchiveStore()
    val downloader = NpmPackageDownloader(transport, store)

    downloader.prepareEntry(snapshot, ENTRY)
    val requestCountAfterDownload = transport.requestedUrls.size
    downloader.prepareEntry(snapshot, ENTRY)

    assertEquals(
      listOf(registryUrl(ENTRY, ENTRY_VERSION), firstUrl, secondUrl),
      transport.requestedUrls,
    )
    assertEquals(requestCountAfterDownload, transport.requestedUrls.size)
    assertEquals(listOf(ENTRY), store.writeCalls)
  }

  @Test
  fun corruptedTarballsAreRejectedWithoutCaching() = runTest {
    val expectedBytes = "expected".encodeToByteArray()
    val integrity = sri(expectedBytes)
    val firstBaseUrl = "https://cdn-a.example/npm"
    val secondBaseUrl = "https://cdn-b.example/npm"
    val firstUrl = buildNpmPackageDownloadUrl(firstBaseUrl, ENTRY, ENTRY_VERSION)
    val secondUrl = buildNpmPackageDownloadUrl(secondBaseUrl, ENTRY, ENTRY_VERSION)
    val snapshot = NpmReleaseSnapshot(
      releaseTime = RELEASE_TIME,
      entries = mapOf(ENTRY to "index.js"),
      urls = listOf(firstBaseUrl, secondBaseUrl),
      packages = mapOf(
        ENTRY to NpmLockedPackage(
          version = ENTRY_VERSION,
          integrity = integrity,
        ),
      ),
    )
    val transport = FakeTransport(
      responses = mapOf(
        registryUrl(ENTRY, ENTRY_VERSION) to metadata(ENTRY, ENTRY_VERSION, integrity),
        firstUrl to "corrupted-a".encodeToByteArray(),
        secondUrl to "corrupted-b".encodeToByteArray(),
      ),
    )
    val store = InMemoryArchiveStore()

    assertFailsWith<NpmIntegrityException> {
      NpmPackageDownloader(transport, store).prepareEntry(snapshot, ENTRY)
    }

    assertTrue(store.writeCalls.isEmpty())
  }

  @Test
  fun invalidDependencySnapshotFailsBeforeNetworkAccess() = runTest {
    val entryBytes = "entry".encodeToByteArray()
    val snapshot = NpmReleaseSnapshot(
      releaseTime = RELEASE_TIME,
      entries = mapOf(ENTRY to "index.js"),
      urls = listOf(DOWNLOAD_BASE_URL),
      packages = mapOf(
        ENTRY to NpmLockedPackage(
          version = ENTRY_VERSION,
          dependencies = listOf("@lezer/missing"),
          integrity = sri(entryBytes),
        ),
      ),
    )
    val transport = FakeTransport(emptyMap())

    assertFailsWith<NpmSnapshotException> {
      NpmPackageDownloader(transport, InMemoryArchiveStore()).prepareEntry(snapshot, ENTRY)
    }

    assertTrue(transport.requestedUrls.isEmpty())
  }

  /** 构造同时包含入口闭包和无关包的后端快照。 */
  private fun snapshot(
    dependencyIntegrity: String,
    entryIntegrity: String,
    unrelatedIntegrity: String,
  ): NpmReleaseSnapshot {
    return NpmReleaseSnapshot(
      releaseTime = RELEASE_TIME,
      entries = mapOf(ENTRY to "dist/index.js"),
      urls = listOf(DOWNLOAD_BASE_URL),
      packages = mapOf(
        ENTRY to NpmLockedPackage(
          version = ENTRY_VERSION,
          dependencies = listOf(DEPENDENCY),
          integrity = entryIntegrity,
        ),
        DEPENDENCY to NpmLockedPackage(
          version = DEPENDENCY_VERSION,
          integrity = dependencyIntegrity,
        ),
        UNRELATED to NpmLockedPackage(
          version = "1.0.0",
          integrity = unrelatedIntegrity,
        ),
      ),
    )
  }

  /** 生成与 npm registry 返回格式一致的最小精确版本元数据。 */
  private fun metadata(name: String, version: String, integrity: String): ByteArray {
    return """
      {
        "name": "$name",
        "version": "$version",
        "dependencies": {"ignored-by-client": "99.0.0"},
        "dist": {"integrity": "$integrity"}
      }
    """.trimIndent().encodeToByteArray()
  }

  /** 生成 npm 常用的 sha512 SRI。 */
  private fun sri(bytes: ByteArray): String {
    return "sha512-${bytes.toByteString().sha512().base64()}"
  }

  private fun registryUrl(name: String, version: String): String {
    val encodedName = name
      .replace("@", "%40")
      .replace("/", "%2F")
    return "https://registry.npmjs.org/$encodedName/$version"
  }

  private class FakeTransport(
    private val responses: Map<String, Any>,
  ) : NpmHttpTransport {
    val requestedUrls = mutableListOf<String>()

    override suspend fun get(url: String): ByteArray {
      requestedUrls += url
      val response = responses[url]
        ?: throw NpmDownloadException("No fake response for requested URL.")
      if (response is Throwable) throw response
      return response as ByteArray
    }
  }

  private class InMemoryArchiveStore : NpmPackageArchiveStore {
    private val archives = mutableMapOf<String, NpmPackageArchive>()
    val writeCalls = mutableListOf<String>()

    override suspend fun find(
      packageName: String,
      version: String,
      integrity: String,
    ): NpmPackageArchive? {
      return archives[key(packageName, version, integrity)]
    }

    override suspend fun write(
      packageName: String,
      version: String,
      integrity: String,
      bytes: ByteArray,
    ): NpmPackageArchive {
      writeCalls += packageName
      return NpmPackageArchive(
        packageName = packageName,
        version = version,
        integrity = integrity,
        archivePath = "/cache/$packageName-$version.tgz".toPath(),
      ).also { archive ->
        archives[key(packageName, version, integrity)] = archive
      }
    }

    private fun key(packageName: String, version: String, integrity: String): String {
      return "$packageName\u0000$version\u0000$integrity"
    }
  }

  private companion object {
    const val RELEASE_TIME = "2026.08.03 12:01:10"
    const val ENTRY = "@cyxbs/language-javascript"
    const val ENTRY_VERSION = "1.4.0"
    const val DOWNLOAD_BASE_URL = "https://cdn.example/npm"
    const val ENTRY_URL =
      "https://cdn.example/npm/%40cyxbs%2Flanguage-javascript/-/language-javascript-1.4.0.tgz"
    const val DEPENDENCY = "@lezer/javascript"
    const val DEPENDENCY_VERSION = "1.5.4"
    const val DEPENDENCY_URL =
      "https://cdn.example/npm/%40lezer%2Fjavascript/-/javascript-1.5.4.tgz"
    const val UNRELATED = "@cyxbs/language-python"
  }
}
