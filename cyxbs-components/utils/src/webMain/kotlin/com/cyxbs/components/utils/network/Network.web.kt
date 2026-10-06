package com.cyxbs.components.utils.network

import io.ktor.client.HttpClientConfig

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/5
 */
internal actual fun HttpClientConfig<*>.platformConfigHttpClient() {
}

/** 浏览器隐藏 DNS、连接及 CORS 的具体原因，不依据模糊的 fetch 错误触发域名切换。 */
internal actual fun Throwable.isPlatformConnectionFailure(): Boolean = false
