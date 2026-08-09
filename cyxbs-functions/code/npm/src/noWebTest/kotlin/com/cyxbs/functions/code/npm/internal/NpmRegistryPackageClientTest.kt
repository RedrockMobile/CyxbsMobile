package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.model.NpmDownloadException
import com.cyxbs.functions.code.npm.diagnostic.NpmPackagePoolMetrics
import com.cyxbs.functions.code.npm.diagnostic.NpmRegistrySourceOutcome
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/** npm 多 registry 元数据竞速、校验回退与取消传播测试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NpmRegistryPackageClientTest {

  /** 较快的完整有效响应直接获胜，较慢请求随父调用结束而取消。 */
  @Test
  fun fastestValidRegistryWinsAndCancelsSlowerRequest() = runTest {
    val slowRequestCancelled = CompletableDeferred<Unit>()
    val metrics = NpmPackagePoolMetrics()
    val client = clientWith { url, _ ->
      when {
        url.startsWith(FAST_REGISTRY) -> {
          delay(10.milliseconds)
          metadata("2.0.0")
        }
        else -> try {
          delay(1_000.milliseconds)
          metadata("1.0.0")
        } finally {
          if (!currentCoroutineContext().isActive) slowRequestCancelled.complete(Unit)
        }
      }
    }

    val result = client.fetch(PACKAGE_NAME, metrics)

    assertEquals("2.0.0", result.select("latest").id.version)
    assertEquals(10L, currentTime)
    assertTrue(slowRequestCancelled.isCompleted)
    assertEquals(1, metrics.metadataTimings.size)
    assertEquals(2, metrics.registrySourceTimings.size)
    assertTrue(
      metrics.registrySourceTimings.any {
        it.selected && it.outcome == NpmRegistrySourceOutcome.SUCCEEDED
      },
    )
    assertTrue(
      metrics.registrySourceTimings.any {
        !it.selected && it.outcome == NpmRegistrySourceOutcome.CANCELLED
      },
    )
  }

  /** 最快响应若未通过 JSON 与元数据校验，则继续等待下一份有效响应。 */
  @Test
  fun invalidFastResponseDoesNotWin() = runTest {
    val client = clientWith { url, _ ->
      if (url.startsWith(FAST_REGISTRY)) {
        delay(10.milliseconds)
        "not-json".encodeToByteArray()
      } else {
        delay(20.milliseconds)
        metadata("1.5.0")
      }
    }

    val result = client.fetch(PACKAGE_NAME)

    assertEquals("1.5.0", result.select("latest").id.version)
    assertEquals(20L, currentTime)
  }

  /** 所有源均完成失败后，主异常仍按配置顺序稳定选择，而不是按网络完成顺序选择。 */
  @Test
  fun allFailuresKeepConfiguredPrimaryException() = runTest {
    val attemptedUrls = mutableSetOf<String>()
    val client = clientWith { url, _ ->
      attemptedUrls += url
      if (url.startsWith(FAST_REGISTRY)) {
        delay(20.milliseconds)
        throw NpmDownloadException("first registry failed")
      } else {
        delay(10.milliseconds)
        throw NpmDownloadException("second registry failed")
      }
    }

    val exception = assertFailsWith<NpmDownloadException> { client.fetch(PACKAGE_NAME) }

    assertEquals("first registry failed", exception.message)
    assertTrue(attemptedUrls.any { it.startsWith(FAST_REGISTRY) })
    assertTrue(attemptedUrls.any { it.startsWith(SLOW_REGISTRY) })
  }

  /** 调用协程取消时，仍在等待的所有 registry 请求都必须被取消。 */
  @Test
  fun callerCancellationCancelsEveryRegistryRequest() = runTest {
    val started = List(2) { CompletableDeferred<Unit>() }
    val cancelled = List(2) { CompletableDeferred<Unit>() }
    val client = clientWith { url, _ ->
      val index = if (url.startsWith(FAST_REGISTRY)) 0 else 1
      started[index].complete(Unit)
      try {
        awaitCancellation()
      } finally {
        if (!currentCoroutineContext().isActive) cancelled[index].complete(Unit)
      }
    }
    val job = launch { client.fetch(PACKAGE_NAME) }
    started.forEach { it.await() }

    job.cancelAndJoin()

    assertTrue(cancelled.all { it.isCompleted })
  }

  /** 完成三次探测后，新客户端应复用持久化结果，健康主源场景不再请求备用源。 */
  @Test
  fun probeResultIsReusedByNewClient() = runTest {
    val store = InMemoryNpmRegistrySelectionStore()
    repeat(3) {
      clientWith(registrySelectionStore = store) { url, _ ->
        if (url.startsWith(FAST_REGISTRY)) {
          delay(10.milliseconds)
          metadata("2.0.0")
        } else {
          delay(20.milliseconds)
          metadata("1.0.0")
        }
      }.fetch(PACKAGE_NAME)
    }
    val requestedRegistries = mutableListOf<String>()
    val restoredClient = clientWith(registrySelectionStore = store) { url, _ ->
      requestedRegistries += registryOf(url)
      metadata("2.0.0")
    }

    restoredClient.fetch(PACKAGE_NAME)

    assertEquals(listOf(FAST_REGISTRY), requestedRegistries)
  }

  /** 固定主源失败时才顺序请求备用源，备用源成功仍可完成本次元数据加载。 */
  @Test
  fun preferredRegistryFailureFallsBackSequentially() = runTest {
    val store = InMemoryNpmRegistrySelectionStore()
    repeat(3) {
      clientWith(registrySelectionStore = store) { url, _ ->
        if (url.startsWith(FAST_REGISTRY)) {
          delay(10.milliseconds)
          metadata("2.0.0")
        } else {
          delay(20.milliseconds)
          metadata("1.0.0")
        }
      }.fetch(PACKAGE_NAME)
    }
    val requestedRegistries = mutableListOf<String>()
    val metrics = NpmPackagePoolMetrics()
    val client = clientWith(registrySelectionStore = store) { url, _ ->
      requestedRegistries += registryOf(url)
      if (url.startsWith(FAST_REGISTRY)) {
        throw NpmDownloadException("preferred registry failed")
      }
      metadata("1.5.0")
    }

    val result = client.fetch(PACKAGE_NAME, metrics)

    assertEquals("1.5.0", result.select("latest").id.version)
    assertEquals(listOf(FAST_REGISTRY, SLOW_REGISTRY), requestedRegistries)
    assertEquals(
      listOf(NpmRegistrySourceOutcome.FAILED, NpmRegistrySourceOutcome.SUCCEEDED),
      metrics.registrySourceTimings.map { it.outcome },
    )
  }

  /** registry 配置发生变化时不得沿用旧排序，必须用新列表重新探测。 */
  @Test
  fun registryConfigurationChangeDiscardsPreference() = runTest {
    val store = InMemoryNpmRegistrySelectionStore()
    repeat(3) {
      clientWith(registrySelectionStore = store) { url, _ ->
        if (url.startsWith(FAST_REGISTRY)) {
          delay(10.milliseconds)
          metadata("2.0.0")
        } else {
          delay(20.milliseconds)
          metadata("1.0.0")
        }
      }.fetch(PACKAGE_NAME)
    }
    val requestedRegistries = mutableSetOf<String>()
    val changedClient = clientWith(
      registrySelectionStore = store,
      registryBaseUrls = listOf(SLOW_REGISTRY, THIRD_REGISTRY),
    ) { url, _ ->
      requestedRegistries += registryOf(url)
      if (url.startsWith(SLOW_REGISTRY)) {
        delay(10.milliseconds)
        metadata("1.5.0")
      } else {
        delay(20.milliseconds)
        metadata("1.0.0")
      }
    }

    changedClient.fetch(PACKAGE_NAME)

    assertEquals(setOf(SLOW_REGISTRY, THIRD_REGISTRY), requestedRegistries)
  }

  /** 依赖并发数超过探测样本数时，仅前三个请求竞速，等待者随后直接使用固定主源。 */
  @Test
  fun concurrentMetadataRequestsDoNotAmplifyRegistryProbes() = runTest {
    val requestedRegistries = mutableListOf<String>()
    val client = clientWith { url, _ ->
      requestedRegistries += registryOf(url)
      if (url.startsWith(FAST_REGISTRY)) {
        delay(10.milliseconds)
        metadata("2.0.0")
      } else {
        delay(20.milliseconds)
        metadata("1.0.0")
      }
    }

    List(5) { async { client.fetch(PACKAGE_NAME) } }.awaitAll()

    assertEquals(5, requestedRegistries.count { it == FAST_REGISTRY })
    assertEquals(3, requestedRegistries.count { it == SLOW_REGISTRY })
  }

  /** 固定主源连续失败两次后，下一次请求重新竞速全部源，而不是永久黏在备用源上。 */
  @Test
  fun repeatedPrimaryFailuresTriggerNewProbe() = runTest {
    val store = InMemoryNpmRegistrySelectionStore()
    repeat(3) {
      clientWith(registrySelectionStore = store) { url, _ ->
        if (url.startsWith(FAST_REGISTRY)) {
          delay(10.milliseconds)
          metadata("2.0.0")
        } else {
          delay(20.milliseconds)
          metadata("1.0.0")
        }
      }.fetch(PACKAGE_NAME)
    }
    val requestedRegistries = mutableListOf<String>()
    val client = clientWith(registrySelectionStore = store) { url, _ ->
      requestedRegistries += registryOf(url)
      if (url.startsWith(FAST_REGISTRY)) {
        throw NpmDownloadException("preferred registry failed")
      }
      metadata("1.5.0")
    }
    repeat(2) { client.fetch(PACKAGE_NAME) }
    requestedRegistries.clear()

    client.fetch(PACKAGE_NAME)

    assertEquals(setOf(FAST_REGISTRY, SLOW_REGISTRY), requestedRegistries.toSet())
  }

  /** 主源成功不会续期；选优满七天后，下一次真实 metadata 请求仍会重新并发探测。 */
  @Test
  fun expiredPreferenceTriggersNewProbe() = runTest {
    var now = 1_000L
    val store = InMemoryNpmRegistrySelectionStore()
    repeat(3) {
      clientWith(registrySelectionStore = store, clock = { now }) { url, _ ->
        if (url.startsWith(FAST_REGISTRY)) {
          delay(10.milliseconds)
          metadata("2.0.0")
        } else {
          delay(20.milliseconds)
          metadata("1.0.0")
        }
      }.fetch(PACKAGE_NAME)
    }
    now += 6.days.inWholeMilliseconds
    val requestedRegistries = mutableSetOf<String>()
    val restoredClient = clientWith(registrySelectionStore = store, clock = { now }) { url, _ ->
      requestedRegistries += registryOf(url)
      metadata("2.0.0")
    }
    restoredClient.fetch(PACKAGE_NAME)
    assertEquals(setOf(FAST_REGISTRY), requestedRegistries)

    now += 2.days.inWholeMilliseconds
    requestedRegistries.clear()
    restoredClient.fetch(PACKAGE_NAME)

    assertEquals(setOf(FAST_REGISTRY, SLOW_REGISTRY), requestedRegistries)
  }

  /** 系统时间早于选优时间时无法证明缓存年龄，必须按过期状态重新探测。 */
  @Test
  fun clockRollbackInvalidatesPreference() = runTest {
    var now = 10_000L
    val store = InMemoryNpmRegistrySelectionStore()
    repeat(3) {
      clientWith(registrySelectionStore = store, clock = { now }) { url, _ ->
        if (url.startsWith(FAST_REGISTRY)) {
          delay(10.milliseconds)
          metadata("2.0.0")
        } else {
          delay(20.milliseconds)
          metadata("1.0.0")
        }
      }.fetch(PACKAGE_NAME)
    }
    now--
    val requestedRegistries = mutableSetOf<String>()
    val restoredClient = clientWith(registrySelectionStore = store, clock = { now }) { url, _ ->
      requestedRegistries += registryOf(url)
      metadata("2.0.0")
    }

    restoredClient.fetch(PACKAGE_NAME)

    assertEquals(setOf(FAST_REGISTRY, SLOW_REGISTRY), requestedRegistries)
  }

  /** 旧 schema 即使包含完整排序也不得恢复，避免新增有效期字段后继续无限复用旧结果。 */
  @Test
  fun oldSchemaDoesNotRestorePreference() = runTest {
    val store = InMemoryNpmRegistrySelectionStore()
    store.write(
      PersistedNpmRegistrySelection(
        schemaVersion = 1,
        registryBaseUrls = listOf(FAST_REGISTRY, SLOW_REGISTRY),
        completedProbeCount = 3,
        preferredOrder = listOf(FAST_REGISTRY, SLOW_REGISTRY),
        selectedAtEpochMillis = 1_000L,
      ),
    )
    val requestedRegistries = mutableSetOf<String>()
    val client = clientWith(registrySelectionStore = store, clock = { 1_000L }) { url, _ ->
      requestedRegistries += registryOf(url)
      metadata("2.0.0")
    }

    client.fetch(PACKAGE_NAME)

    assertEquals(setOf(FAST_REGISTRY, SLOW_REGISTRY), requestedRegistries)
  }

  /** 使用两个固定源构造待测客户端，避免测试依赖真实网络。 */
  private fun clientWith(
    registrySelectionStore: NpmRegistrySelectionStore = InMemoryNpmRegistrySelectionStore(),
    registryBaseUrls: List<String> = listOf(FAST_REGISTRY, SLOW_REGISTRY),
    clock: () -> Long = { 0L },
    response: suspend (url: String, headers: Map<String, String>) -> ByteArray,
  ): NpmRegistryPackageClient {
    return NpmRegistryPackageClient(
      transport = object : NpmHttpTransport {
        override suspend fun get(url: String, headers: Map<String, String>): ByteArray {
          return response(url, headers)
        }
      },
      registryBaseUrls = registryBaseUrls,
      json = Json { ignoreUnknownKeys = true },
      registrySelectionStore = registrySelectionStore,
      clock = clock,
    )
  }

  /** 构造带有效 SRI 与 HTTPS tarball 的最小 abbreviated metadata。 */
  private fun metadata(version: String): ByteArray {
    val integrity = "sha512-${ByteArray(64).toByteString().base64()}"
    return """
      {
        "name": "$PACKAGE_NAME",
        "dist-tags": { "latest": "$version" },
        "versions": {
          "$version": {
            "name": "$PACKAGE_NAME",
            "version": "$version",
            "dist": {
              "integrity": "$integrity",
              "tarball": "https://cdn.test/package-$version.tgz"
            }
          }
        }
      }
    """.trimIndent().encodeToByteArray()
  }

  /** 从测试请求 URL 还原其 registry，避免作用域包路径编码影响断言。 */
  private fun registryOf(url: String): String {
    return listOf(FAST_REGISTRY, SLOW_REGISTRY, THIRD_REGISTRY).first(url::startsWith)
  }

  private companion object {
    const val PACKAGE_NAME = "@cyxbs-mobile/example"
    const val FAST_REGISTRY = "https://fast.registry.test"
    const val SLOW_REGISTRY = "https://slow.registry.test"
    const val THIRD_REGISTRY = "https://third.registry.test"
  }
}
