package com.cyxbs.components.account.provider

import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClient
import com.cyxbs.components.utils.utils.secret.secretDecrypt
import com.cyxbs.components.utils.utils.secret.secretEncrypt
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * 用户信息提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object UserInfoProvider {

  private const val KEY = "cyxbsmobile_user_info"

  private val _stateFlow = MutableStateFlow(
    defaultSettings.getStringOrNull(KEY)?.let {
      runCatching {
        defaultJson.decodeFromString<UserInfo>(secretDecrypt(it))
      }.onFailure {
        defaultSettings.remove(KEY)
      }.getOrNull()
    }
  )
  val stateFlow: StateFlow<UserInfo?> = _stateFlow

  fun clear() {
    _stateFlow.value = null
    refreshJob?.cancel()
    refreshJob = null
    defaultSettings.remove(KEY)
  }

  private var refreshJob: Job? = null

  fun refresh() {
    refreshJob?.cancel()
    refreshJob = appCoroutineScope.launch {
      runCatchingCoroutine {
        HttpClient.get("/magipoke/person/info").body<ApiWrapper<UserInfo>>()
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data
      }.onFailure {
        if (isDebug()) {
          toast("用户信息请求失败")
          logg("用户信息请求失败: " + it.stackTraceToString())
        }
      }.onSuccess {
        _stateFlow.value = it
        defaultSettings.putString(KEY, secretEncrypt(defaultJson.encodeToString(it)))
      }
    }
  }
}