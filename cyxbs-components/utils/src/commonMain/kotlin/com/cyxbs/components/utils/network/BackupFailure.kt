package com.cyxbs.components.utils.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

/**
 * 判断是否适合尝试备用域名，Ktor 与旧版 OkHttp 共用此规则。
 *
 * 仅接受超时、域名解析和连接建立失败；不沿 cause 查找，避免把认证、业务或取消异常
 * 内部的网络原因当成原接口故障。连接重置、证书失败和普通 IOException 不在名单内。
 * 超时可能发生在服务端已处理请求之后，调用方仍需保证写接口的幂等性。
 */
internal fun Throwable.shouldTryBackup(): Boolean = when (this) {
  is CancellationException -> false
  is ConnectTimeoutException, is HttpRequestTimeoutException, is SocketTimeoutException -> true
  else -> isPlatformConnectionFailure()
}

/** 识别当前引擎提供的 DNS 与连接建立失败；无法明确分类的平台返回 false。 */
internal expect fun Throwable.isPlatformConnectionFailure(): Boolean
