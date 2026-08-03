package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.NpmRegistryMismatchException
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 通过 `GET {registryBaseUrl}/{encodedPackageName}/{encodedVersion}` 校验后端锁定结果。
 *
 * 不从 registry 解析依赖、选择版本或读取 tarball 地址，因此内部源只需兼容精确版本元数据响应。
 */
internal class NpmRegistryClient(
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
   * 校验 registry 中的包名、精确版本和 SRI。
   *
   * 每次调用只发送一个精确版本 GET 请求；缓存过滤由上层下载器负责。
   *
   * DTO 故意不声明 dependencies，因此 registry 返回的依赖字段只会被 Json 的 `ignoreUnknownKeys` 忽略。
   */
  @Throws(NpmRegistryMismatchException::class, CancellationException::class)
  suspend fun verify(packageInfo: ResolvedNpmPackage) {
    val metadataUrl = buildString {
      append(registryBaseUrl)
      append('/')
      append(packageInfo.name.encodeNpmPathSegment())
      append('/')
      append(packageInfo.version.encodeNpmPathSegment())
    }
    val bytes = transport.get(metadataUrl)
    val metadata = try {
      json.decodeFromString<NpmRegistryVersionMetadata>(bytes.decodeToString())
    } catch (exception: SerializationException) {
      throw NpmRegistryMismatchException(
        "Npm registry returned invalid metadata for '${packageInfo.name}'.",
        exception,
      )
    }
    if (metadata.name != packageInfo.name) {
      throw NpmRegistryMismatchException(
        "Npm registry package name '${metadata.name}' does not match '${packageInfo.name}'.",
      )
    }
    if (metadata.version != packageInfo.version) {
      throw NpmRegistryMismatchException(
        "Npm registry version '${metadata.version}' does not match " +
          "'${packageInfo.version}' for '${packageInfo.name}'.",
      )
    }
    if (metadata.dist.integrity?.trim() != packageInfo.integrity.encoded) {
      throw NpmRegistryMismatchException(
        "Npm registry integrity does not match the release snapshot for " +
          "'${packageInfo.name}@${packageInfo.version}'.",
      )
    }
  }
}

/** npm 精确版本接口的最小响应模型；未声明的 dependencies 等字段不会参与客户端解析。 */
@Serializable
private data class NpmRegistryVersionMetadata(
  val name: String,
  val version: String,
  val dist: NpmRegistryDist,
)

@Serializable
private data class NpmRegistryDist(
  val integrity: String? = null,
)
