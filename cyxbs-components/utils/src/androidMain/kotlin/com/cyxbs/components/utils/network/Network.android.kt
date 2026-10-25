package com.cyxbs.components.utils.network

import com.cyxbs.components.utils.utils.get.getAppVersionName
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import okhttp3.Dispatcher
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/5
 */

internal actual fun createHttpClientEngine(): HttpClientEngine = OkHttp.create {
  config {
    dispatcher(OkHttpDispatcher)
    dns(OkHttpDnsService.dns)
    ApiGenerator.networkConfigs.forEach { it.onCreateOkHttp(this) }
  }
}

internal actual fun HttpClientConfig<*>.platformConfigHttpClient() {
  install(DefaultRequest) {
    headers.append("version", getAppVersionName())
    headers.append("APPVersion", getAppVersionName())
  }
}

/** OkHttp 的 DNS、连接拒绝及路由不可达均发生在连接建立阶段，不扩大到普通 SocketException。 */
internal actual fun Throwable.isPlatformConnectionFailure(): Boolean =
  this is java.net.UnknownHostException || this is java.net.ConnectException ||
    this is java.net.NoRouteToHostException

// 手动创建 okhttp 的线程分发器，规避 协程 + Retrofit 在子线程请求被 cancel 后的异常问题
val OkHttpDispatcher = Dispatcher(
  ThreadPoolExecutor(
    0, 64, 60, TimeUnit.SECONDS,
    SynchronousQueue(),
    ThreadFactory {
      Thread(it, "Network OkHttp Dispatcher").apply {
        setUncaughtExceptionHandler(UncaughtExceptionHandler)
      }
    }
  )
)

private val UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ ->}
