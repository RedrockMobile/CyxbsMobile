package com.cyxbs.components.utils.network.plugin

import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.network.BASE_NORMAL_BACKUP_GET
import com.cyxbs.components.utils.network.END_POINT_REDROCK_DEV
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.components.utils.network.getBaseUrl
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.get
import io.ktor.client.request.host
import io.ktor.client.request.url
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/19
 */
internal val BackupPlugin = createClientPlugin(
  "BackupPlugin",
) {
  on(Send) { request ->
    val baseUrl = getBaseUrl()
    var backupUrl = BackupUrl.get()
    if (backupUrl != null && baseUrl.endsWith(request.host)) {
      // 如果已经处于 backup，则直接使用 backupUrl
      request.host = backupUrl
      return@on proceed(request)
    }

    try {
      // 第一次正常请求
      return@on proceed(request)
    } catch (e: Exception) {
      if (!baseUrl.endsWith(request.host)) throw e
      if (backupUrl == END_POINT_REDROCK_DEV) throw e // dev 环境直接抛出异常
      if (e is ConnectTimeoutException || e is HttpRequestTimeoutException) {
        // 第一次出现请求超时，尝试切换为 backup
        backupUrl = BackupUrl.request()
        if (backupUrl != null) {
          request.url(backupUrl)
          val call = proceed(request)
          BackupUrl.enterBackup() // 在 proceed 成功后才记录 backupUrl，后续所有请求都进行切换
          return@on call
        }
      }
      throw e
    }
  }
}

private object BackupUrl {

  /**
   * 如果当前处于 backup 状态，则返回 backupUrl
   */
  fun get(): String? {
    return backupUrl
  }

  /**
   * 请求 backupUrl
   */
  suspend fun request(): String? {
    return backupUrlDeferred.await()
  }

  /**
   * 进入 backup 状态，进入后 [get] 将返回 backupUrl
   * 支持重复的多次调用
   */
  suspend fun enterBackup() {
    if (backupUrl == null) {
      mutex.withLock { // 双检锁
        if (backupUrl == null) {
          backupUrl = backupUrlDeferred.await()
          if (backupUrl != null) {
            // todo 保存进磁盘，2小时内有效
          }
        }
      }
    }
  }

  private val mutex = Mutex()

  @Volatile
  private var backupUrl: String? = null

  private val backupUrlDeferred = appCoroutineScope.async {
    try {
      // 这里使用单独的 HttpClient
      HttpClientNoToken.get(BASE_NORMAL_BACKUP_GET).body<BackupUrlStatus>().baseUrl
    } catch (e: Exception) {
      return@async null
    }
  }

  @Serializable
  private data class BackupUrlStatus(@SerialName("base_url") val baseUrl: String)
}