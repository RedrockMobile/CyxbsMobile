package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.NpmDownloadException
import com.cyxbs.functions.code.npm.NpmRegistryMismatchException
import com.cyxbs.functions.code.npm.NpmResolutionException
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 获取 npm 包级元数据，供客户端递归选择版本和解析依赖。
 *
 * 请求使用 npm 的 abbreviated metadata Accept 值；自建 registry 可以返回同等字段的普通 JSON。
 * 客户端只使用 dist-tags、versions、dependencies、dist.integrity 和 dist.tarball。
 */
internal class NpmRegistryPackageClient(
  private val transport: NpmHttpTransport,
  registryBaseUrl: String,
  private val json: Json,
) {
  private val registryBaseUrl = registryBaseUrl.trimEnd('/')

  init {
    val registryUrl = runCatching { Url(this.registryBaseUrl) }.getOrNull()
    require(
      registryUrl != null &&
        registryUrl.protocol == URLProtocol.HTTPS &&
        registryUrl.host.isNotBlank() &&
        registryUrl.user == null &&
        registryUrl.password == null,
    ) {
      "registryBaseUrl must be a valid HTTPS URL without user info."
    }
  }

  /**
   * 请求一个包的版本目录。
   *
   * @throws NpmDownloadException HTTP 或响应解码失败。
   * @throws NpmRegistryMismatchException registry 返回了其他包的元数据。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmDownloadException::class,
    NpmRegistryMismatchException::class,
    CancellationException::class,
  )
  suspend fun fetch(packageName: String): NpmRegistryPackageMetadata {
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
        id = com.cyxbs.functions.code.npm.NpmPackageId(packageName, versionText),
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
  val id: com.cyxbs.functions.code.npm.NpmPackageId,
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
