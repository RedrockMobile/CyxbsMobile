package com.cyxbs.components.account

import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.login.api.ILoginService
import com.g985892345.provider.api.annotation.ImplProvider
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
@ImplProvider
object TokenServiceImpl : ITokenService {

  private val mutex = Mutex()

  @Volatile
  private var requestTokenJob: Deferred<String>? = null

  override suspend fun getOrRequestToken(): String? {
    val token = TokenProvider.stateFlow.value ?: return null  // 未登录则直接返回 null
    if (!TokenProvider.isTokenExpired()) {
      // 未过期则直接返回 token
      return token.token
    }
    // token 已经过期，则挂起请求 token
    val request = requestTokenJob ?: mutex.withLock { // 双检锁
      requestTokenJob ?: requestToken(token.refreshToken).also { requestTokenJob = it }
    }
    return request.await()
  }

  override fun getToken(): String? {
    return TokenProvider.stateFlow.value?.token
  }

  override fun isRefreshTokenExpired(): Boolean {
    return TokenProvider.isRefreshTokenExpired()
  }

  // 依靠 by lazy 来实现只触发一次 forceTokenExpired
  private val tokenExpiredOnce by lazy { TokenProvider.forceTokenExpired() }
  private val refreshTokenExpiredOnce by lazy {
    TokenProvider.forceRefreshTokenExpired()
    toast("登录已过期，请重新登录")
    ILoginService::class.impl().jumpToLoginPage() // 跳转登录页
  }

  override fun tryTokenExpired() {
    tokenExpiredOnce.hashCode()
  }

  override fun tryRefreshTokenExpired() {
    refreshTokenExpiredOnce.hashCode()
  }

  private fun requestToken(refreshToken: String): Deferred<String> {
    return appCoroutineScope.async {
      runCatchingCoroutine {
        HttpClientNoToken.post("/magipoke/token/refresh") {
          setBody(buildJsonObject {
            put("refreshToken", refreshToken)
          }.toString())
          header("STU-NUM", UserInfoProvider.stateFlow.value!!.stuNum) // 这里学号正常情况下不会为 null
        }.body<ApiWrapper<TokenBean>>()
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data
      }.onFailure {
        // token 请求失败
      }.onSuccess {
        TokenProvider.set(it)
      }.map {
        it.token
      }.getOrThrow()
    }
  }
}



