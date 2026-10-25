package com.cyxbs.components.utils.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorCannotConnectToHost

/**
 *
 *
 * @author 985892345
 * @date 2025/1/5
 */
internal actual fun createHttpClientEngine(): HttpClientEngine = Darwin.create {

}

internal actual fun HttpClientConfig<*>.platformConfigHttpClient() {
}

/** Darwin 保留 NSError；只接受明确的 URL 超时、DNS 和连接建立错误，断网及证书错误不切换。 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun Throwable.isPlatformConnectionFailure(): Boolean {
  val error = (this as? DarwinHttpRequestException)?.origin ?: return false
  return error.domain == NSURLErrorDomain && error.code in setOf(
    NSURLErrorTimedOut, NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorCannotConnectToHost,
  )
}
