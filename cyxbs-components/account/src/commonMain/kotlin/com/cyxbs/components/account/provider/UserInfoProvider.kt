package com.cyxbs.components.account.provider

import com.cyxbs.components.account.AccountService
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 用户信息提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object UserInfoProvider {

  private const val KEY = "cyxbsmobile_user_info"

  var value = defaultSettings.getStringOrNull(KEY)?.let {
    runCatching {
      defaultJson.decodeFromString<UserInfo>(SecretTransformer.impl.secretDecrypt(it))
    }.onFailure {
      defaultSettings.remove(KEY)
    }.onFailure {
      refresh() // 本地保存的数据有误时触发一次刷新
    }.getOrNull()
  }
    private set

  fun clear() {
    refreshJob?.cancel()
    refreshJob = null
    defaultSettings.remove(KEY)
    value = null
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
        defaultSettings.putString(
          KEY,
          SecretTransformer.impl.secretEncrypt(defaultJson.encodeToString(it))
        )
        value = it
        val state = AccountService.state.value
        if (state is AccountState.Login && state.stuNum == it.stuNum) {
          state.userInfo.value = it
        }
      }
      refreshJob = null
    }
  }
}