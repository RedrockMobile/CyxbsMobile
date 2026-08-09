package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.model.NpmDownloadException
import com.cyxbs.functions.code.npm.diagnostic.NpmPackageMetadataTiming
import com.cyxbs.functions.code.npm.diagnostic.NpmPackagePoolMetrics
import com.cyxbs.functions.code.npm.diagnostic.NpmRegistrySourceOutcome
import com.cyxbs.functions.code.npm.diagnostic.NpmRegistrySourceTiming
import com.cyxbs.functions.code.npm.model.NpmPackageId
import com.cyxbs.functions.code.npm.model.NpmRegistryMismatchException
import com.cyxbs.functions.code.npm.model.NpmResolutionException
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * 获取 npm 包级元数据，供客户端递归选择版本和解析依赖。
 *
 * 请求使用 npm 的 abbreviated metadata Accept 值；自建 registry 可以返回同等字段的普通 JSON。
 * 客户端只使用 dist-tags、versions、dependencies、dist.integrity 和 dist.tarball。
 */
internal class NpmRegistryPackageClient(
  private val transport: NpmHttpTransport,
  registryBaseUrls: List<String>,
  private val json: Json,
  registrySelectionStore: NpmRegistrySelectionStore = InMemoryNpmRegistrySelectionStore(),
  clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
  private val registryBaseUrls = registryBaseUrls
    .map(String::trim)
    .map { it.trimEnd('/') }
    .distinct()
  private val registrySelector = NpmRegistrySelector(
    registryBaseUrls = this.registryBaseUrls,
    store = registrySelectionStore,
    clock = clock,
  )

  init {
    require(this.registryBaseUrls.isNotEmpty()) { "registryBaseUrls must not be empty." }
    this.registryBaseUrls.forEach { registryBaseUrl ->
      val registryUrl = runCatching { Url(registryBaseUrl) }.getOrNull()
      require(
        registryUrl != null &&
          registryUrl.protocol == URLProtocol.HTTPS &&
          registryUrl.host.isNotBlank() &&
          registryUrl.user == null &&
          registryUrl.password == null,
      ) {
        "registryBaseUrls must contain valid HTTPS URLs without user info."
      }
    }
  }

  /**
   * 请求一个包的版本目录。
   *
   * @param packageName 完整 npm 包名。
   * @param metrics 可选的单次入口准备指标；记录包总耗时及各 registry 的竞速结果。
   * @throws NpmDownloadException HTTP 或响应解码失败。
   * @throws NpmRegistryMismatchException registry 返回了其他包的元数据。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmDownloadException::class,
    NpmRegistryMismatchException::class,
    CancellationException::class,
  )
  suspend fun fetch(
    packageName: String,
    metrics: NpmPackagePoolMetrics? = null,
  ): NpmRegistryPackageMetadata {
    val packageStartedAt = metrics?.let { TimeSource.Monotonic.markNow() }
    var succeeded = false
    try {
      val metadata = when {
        registryBaseUrls.size == 1 -> fetchFromSingleRegistry(packageName, metrics)
        else -> when (val plan = registrySelector.requestPlan()) {
          is NpmRegistryRequestPlan.ProbeAll -> try {
            fetchFromFastestRegistry(
              packageName = packageName,
              probeReservationId = plan.reservationId,
              metrics = metrics,
            )
          } finally {
            // 父协程取消时仍需归还探测名额，否则同进程后续请求可能永久等待。
            withContext(NonCancellable) {
              registrySelector.releaseProbe(plan.reservationId)
            }
          }
          is NpmRegistryRequestPlan.Preferred -> fetchFromPreferredRegistries(
            packageName = packageName,
            registryBaseUrls = plan.registryBaseUrls,
            metrics = metrics,
          )
        }
      }
      succeeded = true
      return metadata
    } finally {
      packageStartedAt?.let { startedAt ->
        metrics.recordMetadata(
          NpmPackageMetadataTiming(packageName, startedAt.elapsedNow(), succeeded),
        )
      }
    }
  }

  /**
   * 固定阶段只请求已选主源；主源失败后才按排名顺序尝试备用源，避免正常请求制造取消流量。
   */
  private suspend fun fetchFromPreferredRegistries(
    packageName: String,
    registryBaseUrls: List<String>,
    metrics: NpmPackagePoolMetrics?,
  ): NpmRegistryPackageMetadata {
    val failures = mutableListOf<Throwable>()
    registryBaseUrls.forEachIndexed { index, registryBaseUrl ->
      val startedAt = TimeSource.Monotonic.markNow()
      try {
        val metadata = fetchFromRegistry(registryBaseUrl, packageName)
        metrics?.recordRegistrySource(
          NpmRegistrySourceTiming(
            packageName = packageName,
            registryBaseUrl = registryBaseUrl,
            duration = startedAt.elapsedNow(),
            outcome = NpmRegistrySourceOutcome.SUCCEEDED,
            selected = true,
          ),
        )
        registrySelector.recordPreferredResult(primarySucceeded = index == 0)
        return metadata
      } catch (exception: CancellationException) {
        metrics?.recordRegistrySource(
          NpmRegistrySourceTiming(
            packageName = packageName,
            registryBaseUrl = registryBaseUrl,
            duration = startedAt.elapsedNow(),
            outcome = NpmRegistrySourceOutcome.CANCELLED,
            selected = false,
          ),
        )
        throw exception
      } catch (throwable: Throwable) {
        failures += throwable
        metrics?.recordRegistrySource(
          NpmRegistrySourceTiming(
            packageName = packageName,
            registryBaseUrl = registryBaseUrl,
            duration = startedAt.elapsedNow(),
            outcome = NpmRegistrySourceOutcome.FAILED,
            selected = false,
          ),
        )
      }
    }

    registrySelector.recordPreferredResult(primarySucceeded = false)
    val primary = failures.first()
    failures.drop(1).forEach(primary::addSuppressed)
    throw primary
  }

  /** 单源配置不创建竞速协程，但仍输出与多源一致的诊断记录。 */
  private suspend fun fetchFromSingleRegistry(
    packageName: String,
    metrics: NpmPackagePoolMetrics?,
  ): NpmRegistryPackageMetadata {
    val registryBaseUrl = registryBaseUrls.single()
    val startedAt = metrics?.let { TimeSource.Monotonic.markNow() }
    var outcome = NpmRegistrySourceOutcome.FAILED
    try {
      return fetchFromRegistry(registryBaseUrl, packageName).also {
        outcome = NpmRegistrySourceOutcome.SUCCEEDED
      }
    } catch (exception: CancellationException) {
      outcome = NpmRegistrySourceOutcome.CANCELLED
      throw exception
    } finally {
      startedAt?.let {
        metrics.recordRegistrySource(
          NpmRegistrySourceTiming(
            packageName = packageName,
            registryBaseUrl = registryBaseUrl,
            duration = it.elapsedNow(),
            outcome = outcome,
            selected = outcome == NpmRegistrySourceOutcome.SUCCEEDED,
          ),
        )
      }
    }
  }

  /** 并发请求全部 registry，首个完成完整校验的响应获胜并取消其余请求。 */
  private suspend fun fetchFromFastestRegistry(
    packageName: String,
    probeReservationId: Long,
    metrics: NpmPackagePoolMetrics?,
  ): NpmRegistryPackageMetadata {
    return supervisorScope {
      val results = Channel<RegistryFetchResult>(capacity = registryBaseUrls.size)
      val observedResults = mutableListOf<RegistryFetchResult>()
      val jobs = registryBaseUrls.mapIndexed { sourceIndex, registryBaseUrl ->
        launch {
          val startedAt = TimeSource.Monotonic.markNow()
          val result = try {
            val metadata = fetchFromRegistry(registryBaseUrl, packageName)
            RegistryFetchResult.Success(
              sourceIndex = sourceIndex,
              registryBaseUrl = registryBaseUrl,
              duration = startedAt.elapsedNow(),
              metadata = metadata,
            )
          } catch (exception: CancellationException) {
            // 父调用或竞速胜出触发的取消必须继续传播；源自身的取消则只视为该源失败。
            if (!currentCoroutineContext().isActive) {
              results.trySend(
                RegistryFetchResult.Cancelled(sourceIndex, registryBaseUrl, startedAt.elapsedNow()),
              )
              throw exception
            }
            RegistryFetchResult.Failure(
              sourceIndex,
              registryBaseUrl,
              startedAt.elapsedNow(),
              exception,
            )
          } catch (throwable: Throwable) {
            RegistryFetchResult.Failure(
              sourceIndex,
              registryBaseUrl,
              startedAt.elapsedNow(),
              throwable,
            )
          }
          results.send(result)
        }
      }
      try {
        val failures = arrayOfNulls<Throwable>(registryBaseUrls.size)
        repeat(registryBaseUrls.size) {
          when (val result = results.receive()) {
            is RegistryFetchResult.Success -> {
              observedResults += result
              // “最快”以完整解析和身份、SRI、tarball 校验结束为准，不能让无效的快速响应获胜。
              metrics.recordRegistryResult(packageName, result, selected = true)
              jobs.forEach { it.cancel() }
              jobs.joinAll()
              while (true) {
                val pending = results.tryReceive().getOrNull() ?: break
                observedResults += pending
                metrics.recordRegistryResult(packageName, pending, selected = false)
              }
              registrySelector.recordProbe(
                reservationId = probeReservationId,
                winnerBaseUrl = result.registryBaseUrl,
                winnerDuration = result.duration,
                failedBaseUrls = observedResults
                  .filterIsInstance<RegistryFetchResult.Failure>()
                  .mapTo(mutableSetOf()) { it.registryBaseUrl },
              )
              return@supervisorScope result.metadata
            }
            is RegistryFetchResult.Failure -> {
              observedResults += result
              metrics.recordRegistryResult(packageName, result, selected = false)
              failures[result.sourceIndex] = result.throwable
            }
            is RegistryFetchResult.Cancelled -> {
              observedResults += result
              metrics.recordRegistryResult(packageName, result, selected = false)
              failures[result.sourceIndex] = CancellationException("Npm registry request cancelled.")
            }
          }
        }
        registrySelector.recordProbe(
          reservationId = probeReservationId,
          winnerBaseUrl = null,
          winnerDuration = null,
          failedBaseUrls = observedResults
            .filterIsInstance<RegistryFetchResult.Failure>()
            .mapTo(mutableSetOf()) { it.registryBaseUrl },
        )
        // 所有源都失败时按配置顺序选择主异常，使结果不受网络完成顺序影响。
        val primary = checkNotNull(failures.firstOrNull { it != null })
        failures.forEach { failure ->
          if (failure != null && failure !== primary) primary.addSuppressed(failure)
        }
        throw primary
      } finally {
        jobs.forEach { it.cancel() }
      }
    }
  }

  /**
   * 从单个 registry 获取并完整校验包元数据；只有该方法成功返回的响应才可参与多源竞速。
   */
  private suspend fun fetchFromRegistry(
    registryBaseUrl: String,
    packageName: String,
  ): NpmRegistryPackageMetadata {
    val url = "$registryBaseUrl/${packageName.encodeNpmPathSegment()}"
    val bytes = transport.get(
      url = url,
      headers = mapOf("Accept" to ABBREVIATED_METADATA_ACCEPT),
    )
    val response = try {
      json.decodeFromString<NpmRegistryPackageResponse>(bytes.decodeToString())
    } catch (exception: SerializationException) {
      throw NpmDownloadException("Npm registry returned invalid package metadata.", exception)
    }
    if (response.name != packageName) {
      throw NpmRegistryMismatchException(
        "Npm registry package name '${response.name}' does not match '$packageName'.",
      )
    }
    val versions = response.versions.mapNotNull { (versionText, item) ->
      val version = NpmSemver.parseOrNull(versionText) ?: return@mapNotNull null
      if (item.version != versionText || item.name?.let { it != packageName } == true) {
        throw NpmRegistryMismatchException(
          "Npm registry contains inconsistent metadata for '$packageName@$versionText'.",
        )
      }
      val integrityText = item.dist.integrity?.trim()
        ?: throw NpmRegistryMismatchException(
          "Npm registry has no SRI for '$packageName@$versionText'.",
        )
      val integrity = NpmIntegrity.parse(integrityText, packageName)
      val tarball = item.dist.tarball?.trim().orEmpty()
      if (!isValidTarballUrl(tarball)) {
        throw NpmRegistryMismatchException(
          "Npm registry has an invalid tarball URL for '$packageName@$versionText'.",
        )
      }
      NpmRegistryVersion(
        id = NpmPackageId(packageName, versionText),
        semver = version,
        integrity = integrity,
        dependencies = item.dependencies,
        tarballUrl = tarball,
      )
    }.associateBy { it.id.version }
    if (versions.isEmpty()) {
      throw NpmRegistryMismatchException("Npm registry has no usable versions for '$packageName'.")
    }
    return NpmRegistryPackageMetadata(
      name = packageName,
      distTags = response.distTags,
      versions = versions,
    )
  }

  /** tarball 必须是无用户信息的 HTTP(S) 地址，避免本地路径或凭据被当作下载源。 */
  private fun isValidTarballUrl(value: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return (url.protocol == URLProtocol.HTTPS || url.protocol == URLProtocol.HTTP) &&
      url.host.isNotBlank() && url.user == null && url.password == null
  }

  private companion object {
    const val ABBREVIATED_METADATA_ACCEPT = "application/vnd.npm.install-v1+json"
  }
}

/** 多 registry 请求的内部完成事件，失败项保留配置下标以稳定最终异常。 */
private sealed interface RegistryFetchResult {
  val sourceIndex: Int
  val registryBaseUrl: String
  val duration: Duration

  data class Success(
    override val sourceIndex: Int,
    override val registryBaseUrl: String,
    override val duration: Duration,
    val metadata: NpmRegistryPackageMetadata,
  ) : RegistryFetchResult

  data class Failure(
    override val sourceIndex: Int,
    override val registryBaseUrl: String,
    override val duration: Duration,
    val throwable: Throwable,
  ) : RegistryFetchResult

  data class Cancelled(
    override val sourceIndex: Int,
    override val registryBaseUrl: String,
    override val duration: Duration,
  ) : RegistryFetchResult
}

/** 将内部竞速结果转换为公开诊断记录；只由父协程串行调用。 */
private fun NpmPackagePoolMetrics?.recordRegistryResult(
  packageName: String,
  result: RegistryFetchResult,
  selected: Boolean,
) {
  this ?: return
  val outcome = when (result) {
    is RegistryFetchResult.Success -> NpmRegistrySourceOutcome.SUCCEEDED
    is RegistryFetchResult.Failure -> NpmRegistrySourceOutcome.FAILED
    is RegistryFetchResult.Cancelled -> NpmRegistrySourceOutcome.CANCELLED
  }
  recordRegistrySource(
    NpmRegistrySourceTiming(
      packageName = packageName,
      registryBaseUrl = result.registryBaseUrl,
      duration = result.duration,
      outcome = outcome,
      selected = selected,
    ),
  )
}

/** 单个包从 registry 获得的可选版本集合。 */
internal data class NpmRegistryPackageMetadata(
  val name: String,
  val distTags: Map<String, String>,
  val versions: Map<String, NpmRegistryVersion>,
) {
  /** 按请求选择最高匹配版本；dist-tag 先转换为精确版本。 */
  fun select(versionSpec: String): NpmRegistryVersion {
    distTags[versionSpec]?.let { taggedVersion ->
      return versions[taggedVersion]
        ?: throw NpmRegistryMismatchException(
          "Npm dist-tag '$versionSpec' points to missing version '$taggedVersion' for '$name'.",
        )
    }
    val range = NpmVersionRange.parse(versionSpec)
    return versions.values
      .asSequence()
      .filter { range.matches(it.semver) }
      .maxByOrNull(NpmRegistryVersion::semver)
      ?: throw NpmResolutionException(
        "Npm package '$name' has no version matching '$versionSpec'.",
      )
  }
}

/** registry 中已经校验、可直接参与依赖图解析的具体版本。 */
internal data class NpmRegistryVersion(
  val id: NpmPackageId,
  val semver: NpmSemver,
  val integrity: NpmIntegrity,
  val dependencies: Map<String, String>,
  val tarballUrl: String,
)

@Serializable
private data class NpmRegistryPackageResponse(
  val name: String,
  @SerialName("dist-tags") val distTags: Map<String, String> = emptyMap(),
  val versions: Map<String, NpmRegistryVersionResponse> = emptyMap(),
)

@Serializable
private data class NpmRegistryVersionResponse(
  val name: String? = null,
  val version: String,
  val dependencies: Map<String, String> = emptyMap(),
  val dist: NpmRegistryPackageDist,
)

@Serializable
private data class NpmRegistryPackageDist(
  val integrity: String? = null,
  val tarball: String? = null,
)
