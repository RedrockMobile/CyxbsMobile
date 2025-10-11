package com.cyxbs.components.account.provider

import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.defaultSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * token 提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object TokenProvider {

  private const val KEY = "cyxbsmobile_user_v2"
  private const val KEY_REFRESH_TOKEN_EXPIRED = "user_refresh_token_expired_time"

  private val _stateFlow = MutableStateFlow(
    defaultSettings.getStringOrNull(KEY)?.let {
      runCatching {
        defaultJson.decodeFromString<TokenBean>(SecretTransformer.impl.secretDecrypt(it))
      }.onFailure {
        defaultSettings.remove(KEY)
      }.getOrNull()
    }
  )

  val stateFlow: StateFlow<TokenBean?> get() = _stateFlow

  @Volatile
  private var tokenExpiredTime = (stateFlow.value?.info?.exp?.toLong() ?: 0).seconds

  @Volatile
  private var refreshTokenExpiredTime = defaultSettings.getLong(KEY_REFRESH_TOKEN_EXPIRED, 0).milliseconds

  fun set(tokenBean: TokenBean) {
    val json = defaultJson.encodeToString(tokenBean)
    defaultSettings.putString(KEY, SecretTransformer.impl.secretEncrypt(json))
    tokenExpiredTime = tokenBean.info.exp.toLong().seconds // 后端规定 3 天过期
    val nowTime = Clock.System.now().toEpochMilliseconds().milliseconds
    refreshTokenExpiredTime = nowTime + 45.days // 后端规定 45 天过期
    defaultSettings.putLong(KEY_REFRESH_TOKEN_EXPIRED, refreshTokenExpiredTime.inWholeMilliseconds)
    _stateFlow.value = tokenBean // 赋值需要放到最后
  }

  fun clear() {
    defaultSettings.remove(KEY)
    tokenExpiredTime = 0.seconds
    refreshTokenExpiredTime = 0.milliseconds
    defaultSettings.putLong(KEY_REFRESH_TOKEN_EXPIRED, 0)
    _stateFlow.value = null // 赋值需要放到最后
  }

  // 得到 token 还有效的剩余时间。如果过期，则返回负数
  fun getTokenRemainTime(): Duration {
    val curTime = Clock.System.now().toEpochMilliseconds().milliseconds
    return tokenExpiredTime - curTime
  }

  // 强制 token 过期
  fun forceTokenExpired() {
    tokenExpiredTime = 0.milliseconds
  }

  // refreshToken 是否过期，过期后只能重新登录
  fun isRefreshTokenExpired(): Boolean {
    if (stateFlow.value == null) return true
    val curTime = Clock.System.now().toEpochMilliseconds().milliseconds
    // 提前 1 天过期
    return curTime > refreshTokenExpiredTime - 1.days
  }
}