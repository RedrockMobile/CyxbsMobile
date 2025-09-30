package com.cyxbs.components.utils.network

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.network.plugin.BackupPlugin
import com.cyxbs.components.utils.network.plugin.TokenPlugin
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.addDefaultResponseValidation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration.Companion.seconds

/**
 *
 *
 * @author 985892345
 * @date 2025/1/5
 */

/**
 * 网络请求 Ktorfit
 *
 * # 使用方式
 * - 模块 build.gradle.kts 添加以下内容
 * ```
 * useNetwork() // 网络请求
 * ```
 *
 * - 在 network 文件夹下创建网络请求接口，注意返回类型应为 ApiWrapper<> 或者 ApiStatue
 * ```
 * interface ApiService {
 *   // 注意 @GET 接口中路径不能以 斜杆/ 开头
 *   @GET("magipoke-jwzx/kebiao/relevance/")
 *   suspend fun getLinkStudent(): ApiWrapper<LinkStuBean>
 * }
 * ```
 * 
 * - 使用处，推荐使用 .impl() 进行获取
 * ```
 * // 推荐使用 .impl() 进行获取
 * val apiService = ApiService::class.impl()
 *
 * // Ktorfit 官方用法 (需要手动触发 KSP 才能引用，不推荐)
 * val apiService = Network.createApiService()
 * ```
 */
val Network by lazy {
  Ktorfit.Builder()
    .httpClient(HttpClient)
    .baseUrl(getBaseUrl().let { if (it.endsWith("/")) it else "$it/" })
    .build()
}

val HttpClient = createHttpClient(true)
val HttpClientNoToken = createHttpClient(false)

fun createHttpClient(
  needToken: Boolean,
): HttpClient {
  return HttpClient(createHttpClientEngine()) {
    // json 反序列化处理 https://ktor.io/docs/client-serialization.html#serialization_dependency
    install(ContentNegotiation) {
      json(json = defaultJson)
    }
    // 设置请求默认 baseUrl https://ktor.io/docs/client-default-request.html
    install(DefaultRequest) {
      url(getBaseUrl())
    }
    // 登录信息 token 处理
    if (needToken) {
      install(TokenPlugin)
    }
    // 网校备用域名容灾处理，需要在 HttpTimeout 之前
    install(BackupPlugin)
    // 超时处理 https://ktor.io/docs/client-timeout.html#configure_plugin
    // 超时会抛出 HttpRequestTimeoutException
    install(HttpTimeout) {
      requestTimeoutMillis = 10.seconds.inWholeMilliseconds
      connectTimeoutMillis = 10.seconds.inWholeMilliseconds
      socketTimeoutMillis = 10.seconds.inWholeMilliseconds
    }
    // http 状态码处理，3XX 抛出 RedirectResponseException，4XX 抛出 ClientRequestException，5XX 抛出 ServerResponseException
    expectSuccess = true
    addDefaultResponseValidation()
    // 平台相关配置
    platformConfigHttpClient()
  }
}

// https://ktor.io/docs/client-engines.html
internal expect fun createHttpClientEngine(): HttpClientEngine
internal expect fun HttpClientConfig<*>.platformConfigHttpClient()

