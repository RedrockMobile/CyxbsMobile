package com.cyxbs.functions.code.npm.transport

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.functions.code.npm.internal.DebugNpmPackageSource
import com.cyxbs.functions.code.npm.model.NpmDownloadException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.coroutines.CancellationException

/**
 * 复用调用方 [HttpClient] 的 Ktor HTTP 实现。
 *
 * 调用方负责在 HttpClient 中配置平台 engine、超时、代理和证书策略；该实现只统一检查状态码并
 * 将响应读取为字节数组，包体大小由 npm 包发布流程保证。Debug 构建会在 metadata 与 tgz 请求
 * 进入网络前按需检查 App 私有版本目录；本地包仍通过 metadata、SRI 和依赖图的完整链路进入包池，
 * 同一包的多个 debug 版本可以并存，Release 构建完全不访问该目录。
 */
class KtorNpmHttpTransport private constructor(
  httpClientProvider: () -> HttpClient,
  private val debugEnabled: () -> Boolean,
  private val debugPackageSource: DebugNpmPackageSource,
) : NpmHttpTransport {

  /** 使用项目默认无 Token Client，并延迟到首次网络请求再初始化。 */
  constructor() : this({ HttpClientNoToken }, ::isDebug, DebugNpmPackageSource())

  /** 复用调用方提供的 [httpClient]。 */
  constructor(httpClient: HttpClient) : this({ httpClient }, ::isDebug, DebugNpmPackageSource())

  // 默认包池可在网络与 KtProvider 初始化前创建，因此不能在构造 Transport 时立即读取默认 Client。
  private val httpClient by lazy(httpClientProvider)

  /** 将调用方声明的元数据协议请求头转发给 npm registry。 */
  override suspend fun get(url: String, headers: Map<String, String>): ByteArray {
    if (debugEnabled()) {
      debugPackageSource.packageFromLocalTarballUrl(url)?.let { coordinate ->
        return debugPackageSource.read(coordinate.name, coordinate.version)?.archiveBytes
          ?: throw NpmDownloadException(
            "Local debug npm package '${coordinate.name}@${coordinate.version}' is missing.",
          )
      }
      if (debugPackageSource.isMetadataRequest(headers)) {
        val packageName = debugPackageSource.packageNameFromMetadataUrl(url)
        val localPackages = packageName?.let(debugPackageSource::readAll).orEmpty()
        if (localPackages.isNotEmpty()) {
          // 本地版本存在时仍尝试读取线上 metadata，让更高的正式版本自然覆盖 debug 预发布版。
          val remoteMetadata = try {
            getFromNetwork(url, headers)
          } catch (exception: CancellationException) {
            throw exception
          } catch (_: NpmDownloadException) {
            null
          }
          return debugPackageSource.mergeMetadata(localPackages, remoteMetadata)
        }
      }
    }
    return getFromNetwork(url, headers)
  }

  /** 执行不带 debug 劫持的真实 HTTP 请求，并保持原有异常映射。 */
  private suspend fun getFromNetwork(url: String, headers: Map<String, String>): ByteArray {
    return try {
      val response = httpClient.get(url) {
        headers {
          headers.forEach { (name, value) -> append(name, value) }
        }
      }
      if (response.status.value !in 200..299) {
        throw NpmDownloadException(
          "Npm HTTP request returned status ${response.status.value}.",
        )
      }
      response.body()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: NpmDownloadException) {
      throw exception
    } catch (throwable: Throwable) {
      // 下载地址可能带签名参数，异常消息不回显 URL，避免日志泄露完整凭据。
      throw NpmDownloadException("Npm HTTP request failed.", throwable)
    }
  }
}
