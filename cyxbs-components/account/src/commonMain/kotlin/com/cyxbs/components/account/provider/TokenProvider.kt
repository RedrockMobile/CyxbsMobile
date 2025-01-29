package com.cyxbs.components.account.provider

import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.utils.secret.secretDecrypt
import com.cyxbs.components.utils.utils.secret.secretEncrypt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.days

/**
 * token 提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object TokenProvider {

  private const val KEY = "cyxbsmobile_user_v2"
  private const val KEY_TOKEN_EXPIRED = "user_token_expired_time"
  private const val KEY_REFRESH_TOKEN_EXPIRED = "user_refresh_token_expired_time"

  private val _stateFlow = MutableStateFlow(
    defaultSettings.getStringOrNull(KEY)?.let {
      runCatching {
        defaultJson.decodeFromString<TokenBean>(secretDecrypt(it))
      }.onFailure {
        defaultSettings.remove(KEY)
      }.getOrNull()
    }
  )
  val stateFlow: StateFlow<TokenBean?> get() = _stateFlow

  @Volatile
  private var tokenExpiredTime = defaultSettings.getLong(KEY_TOKEN_EXPIRED, 0)

  @Volatile
  private var refreshTokenExpiredTime = defaultSettings.getLong(KEY_REFRESH_TOKEN_EXPIRED, 0)

  fun set(tokenBean: TokenBean) {
    _stateFlow.value = tokenBean
    val json = defaultJson.encodeToString(tokenBean)
    defaultSettings.putString(KEY, secretEncrypt(json))
    val nowTime = Clock.System.now().toEpochMilliseconds()
    tokenExpiredTime = nowTime + 2.5.days.inWholeMilliseconds // 后端规定 3 天过期，客户端规定 2.5 天过期
    refreshTokenExpiredTime = nowTime + 44.days.inWholeMilliseconds // 后端规定 45 天过期，客户端规定 44 天过期
    defaultSettings.putLong(KEY_TOKEN_EXPIRED, tokenExpiredTime)
    defaultSettings.putLong(KEY_REFRESH_TOKEN_EXPIRED, refreshTokenExpiredTime)
  }

  fun clear() {
    _stateFlow.value = null
    defaultSettings.remove(KEY)
    tokenExpiredTime = 0
    refreshTokenExpiredTime = 0
    defaultSettings.putLong(KEY_TOKEN_EXPIRED, 0)
    defaultSettings.putLong(KEY_REFRESH_TOKEN_EXPIRED, 0)
  }

  // token 是否过期
  fun isTokenExpired(): Boolean {
    val curTime = Clock.System.now()
    return curTime.toEpochMilliseconds() > tokenExpiredTime
  }

  // 强制 token 过期
  fun forceTokenExpired() {
    tokenExpiredTime = 0
    defaultSettings.putLong(KEY_TOKEN_EXPIRED, 0)
  }

  // refreshToken 是否过期，过期后只能重新登录
  fun isRefreshTokenExpired(): Boolean {
    val curTime = Clock.System.now()
    return curTime.toEpochMilliseconds() > refreshTokenExpiredTime
  }

  // 强制 refreshToken 过期，过期后只能重新登录
  fun forceRefreshTokenExpired() {
    clear()
  }
}