package com.cyxbs.functions.code.npm.transport

import com.cyxbs.functions.code.npm.NpmDownloadException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.coroutines.CancellationException

/**
 * 复用调用方 [HttpClient] 的 Ktor HTTP 实现。
 *
 * 调用方负责在 HttpClient 中配置平台 engine、超时、代理和证书策略；该实现只统一检查状态码并
 * 将响应读取为字节数组，包体大小由 npm 包发布流程保证。
 */
class KtorNpmHttpTransport(
  private val httpClient: HttpClient,
) : NpmHttpTransport {

  override suspend fun get(url: String): ByteArray {
    return get(url, emptyMap())
  }

  /** 将调用方声明的元数据协议请求头转发给 npm registry。 */
  override suspend fun get(url: String, headers: Map<String, String>): ByteArray {
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
