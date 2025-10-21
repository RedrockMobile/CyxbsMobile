package com.cyxbs.components.account

import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.pages.login.api.ILoginService
import com.g985892345.provider.api.annotation.ImplProvider
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
@ImplProvider
object TokenServiceImpl : ITokenService {

  private val requestMutex = Mutex()

  private val requestSynchronized = SynchronizedObject()

  // 是否在异步刷新 token 中
  private val isInAsyncRefresh: AtomicBoolean = atomic(false)

  override suspend fun getOrRequestToken(): String? {
    val token = TokenProvider.stateFlow.value ?: return null  // 未登录则直接返回 null
    if (checkTokenEnable(token)) {
      return token.token
    }
    return requestMutex.withLock {
      val newToken = TokenProvider.stateFlow.value
      if (newToken != null && token != newToken) {
        // 如果 token 不相等，则说明 token 已经刷新了
        newToken.token
      } else {
        // 同步等待刷新
        requestToken(token).await()
      }
    }
  }

  override fun getOrRequestToken2(runBlock: (Deferred<String>) -> String): String? {
    val token = TokenProvider.stateFlow.value ?: return null  // 未登录则直接返回 null
    if (checkTokenEnable(token)) {
      return token.token
    }
    // 同步请求 token
    return synchronized(requestSynchronized) {
      val newToken = TokenProvider.stateFlow.value
      if (newToken != null && token != newToken) {
        // 如果 token 不相等，则说明 token 已经刷新了
        newToken.token
      } else {
        // 同步等待刷新
        runBlock.invoke(requestToken(token))
      }
    }
  }

  override fun getToken(): String? {
    val token = TokenProvider.stateFlow.value ?: return null  // 未登录则直接返回 null
    if (checkTokenEnable(token)) {
      return token.token
    }
    return null
  }

  private fun checkTokenEnable(token: TokenBean): Boolean {
    val tokenRemainTime = TokenProvider.getTokenRemainTime()
    if (tokenRemainTime > 12.hours) {
      // 大于 12 小时的 token 不需要触发刷新
      return true
    } else if (tokenRemainTime > 10.minutes) {
      // 12 小时 - 10 分钟内 则异步触发 token 的刷新
      if (token == TokenProvider.stateFlow.value) { // 双检锁锁住
        if (isInAsyncRefresh.compareAndSet(expect = false, update = true)) {
          if (token == TokenProvider.stateFlow.value) {
            // 这里竞争不会很激烈，采取自旋锁
            // 这距离真正的 token 过期还存在一段时间，所以不使用 requestSynchronized 加锁
            requestToken(token).invokeOnCompletion {
              isInAsyncRefresh.lazySet(false)
            }
          } else {
            isInAsyncRefresh.lazySet(false)
          }
        }
      }
      return true
    }
    // 仅剩 10 分钟或已经过期则返回 不可用
    return false
  }

  override fun isRefreshTokenExpired(): Boolean {
    return TokenProvider.isRefreshTokenExpired()
  }

  @Volatile
  private var lastTryTokenExpiredTime = 0.milliseconds
  private val tryTokenExpiredLock = SynchronizedObject()

  override fun tryTokenExpired() {
    synchronized(tryTokenExpiredLock) {
      val now = Clock.System.now().toEpochMilliseconds().milliseconds
      if (now - lastTryTokenExpiredTime > 30.minutes) {
        lastTryTokenExpiredTime = now
        TokenProvider.forceTokenExpired()
      }
    }
  }

  @Volatile
  private var lastTryRefreshTokenExpiredTime = 0.milliseconds
  private val tryRefreshTokenExpiredLock = SynchronizedObject()

  override fun tryRefreshTokenExpired(msg: String) {
    synchronized(tryRefreshTokenExpiredLock) {
      val now = Clock.System.now().toEpochMilliseconds().milliseconds
      if (now - lastTryRefreshTokenExpiredTime > 30.minutes) {
        lastTryRefreshTokenExpiredTime = now
        toastLong("登录已过期，请重新登录\n原因：$msg")
        IAccountEditService::class.impl().onLogout()
        ILoginService::class.impl().jumpToLoginPage() // 跳转登录页
      }
    }
  }

  @Volatile
  private var requestTokenDeferred: Deferred<String>? = null
  private val deferredSynchronizedObject = SynchronizedObject()

  private fun requestToken(token: TokenBean): Deferred<String> {
    val deferred = requestTokenDeferred
    if (deferred != null) return deferred
    synchronized(deferredSynchronizedObject) {
      val deferred = requestTokenDeferred
      if (deferred != null) return deferred
      return appCoroutineScope.async {
        runCatchingCoroutine {
          HttpClientNoToken.post("/magipoke/token/refresh") {
            setBody(buildJsonObject {
              put("refreshToken", token.refreshToken)
            }.toString())
            header("STU-NUM", token.info.data.stuNum)
          }.body<ApiWrapper<TokenBean>>()
        }.mapCatching {
          it.throwApiExceptionIfFail()
          it.data
        }.onFailure {
          // token 请求失败
          onRequestTokenFailure(it)
          requestTokenDeferred = null
        }.onSuccess {
          TokenProvider.set(it)
          requestTokenDeferred = null
        }.map {
          it.token
        }.getOrThrow()
      }.also {
        requestTokenDeferred = it
      }
    }
  }

  /**
   * 1. refreshToken 失败，如果没带 STU-NUM，则直接返回 status=20004
   * 2. refreshToken 失败，如果带了 STU-NUM，则会兜底签一个 token
   *  2.1. 兜底签的 token 5 天只能使用一次，重复使用则返回 http 400，errcode=10010, errmessage=emergence refused:重复的学号
   *  2.2. 如果系统内部调用失败，则返回 http 400，errcode=10010, errmessage=find redid error
   *  2.3. 如果签发失败，则返回 http 400，errcode=10010, errmessage=sign in emerge error
   *  2.4. 签发不合法，则返回 http 400，status=20004
   */
  private suspend fun onRequestTokenFailure(throwable: Throwable) {
    when (throwable) {
      is ConnectTimeoutException, is HttpRequestTimeoutException -> {
        toastRefreshTokenFailed("refresh token 连接超时")
      }
      is ServerResponseException -> {
        toastRefreshTokenFailed("refresh token 服务器错误\nhttp status=${throwable.response.status}\nbody=${throwable.response.bodyAsText()}")
      }
      is ClientRequestException -> {
        if (throwable.response.status == HttpStatusCode.BadRequest) {
          // 在请求失败时后端会返回 http 状态码 400，这里需要单独进行解析
          val failureBean = throwable.response.body<RequestTokenFailureBean>()
          when {
            failureBean.status == 20004 -> {
              toastRefreshTokenFailed("refresh token 已失效，请重新登录")
              tryRefreshTokenExpired("refresh, status=20004")
            }
            failureBean.errcode == 10010 && failureBean.errmessage.contains("重复的学号") -> {
              toastRefreshTokenFailed("refresh token 重签失败，请重新登录")
              tryRefreshTokenExpired("refresh, emergence refused:重复的学号")
            }
            failureBean.errcode == 10010 && failureBean.errmessage.contains("find redid error") -> {
              toastRefreshTokenFailed("refresh token 系统内部调用失败")
            }
            failureBean.errcode == 10010 && failureBean.errmessage.contains("sign in emerge error") -> {
              toastRefreshTokenFailed("refresh token 签发失败")
            }
            else -> toastRefreshTokenFailed("refresh token 未知错误\nhttp status=${throwable.response.status}\nbody=${throwable.response.bodyAsText()}")
          }
        } else {
          toastRefreshTokenFailed("未知错误\nhttp status=${throwable.response.status}\nbody=${throwable.response.bodyAsText()}")
        }
      }
      else -> toastRefreshTokenFailed(throwable.message)
    }
  }

  @Serializable
  class RequestTokenFailureBean(
    @SerialName("status")
    val status: Int = 0,
    @SerialName("errcode")
    val errcode: Int = 0,
    @SerialName("errmessage")
    val errmessage: String = "",
  )

  private var lastToastRequestFailureTime = 0.milliseconds

  private fun toastRefreshTokenFailed(msg: String?) {
    if (isDebug()) {
      val nowTime = Clock.System.now().toEpochMilliseconds().milliseconds
      if (nowTime - lastToastRequestFailureTime > 1.minutes) {
        lastToastRequestFailureTime = nowTime
        toastLong(msg)
      }
    }
  }
}



