package com.cyxbs.components.account.provider

import com.cyxbs.components.account.AccountService
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClient
import com.cyxbs.components.utils.network.plugin.requireAccountSession
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    }.getOrNull()
  }
    private set

  private var refreshJob: Job? = null

  /** 恢复指定学号的缓存资料；历史缓存归属不符时清空，返回 null 供账户层重新请求。 */
  fun loadForAccount(stuNum: String): UserInfo? {
    val cached = value ?: return null
    if (cached.stuNum == stuNum) return cached
    clear()
    return null
  }

  /** 清空持久化资料并取消当前刷新；由账户切换统一调用。 */
  fun clear() {
    refreshJob?.cancel()
    refreshJob = null
    defaultSettings.remove(KEY)
    value = null
  }

  /**
   * 刷新当前账户资料；同一账户再次刷新时取消前一个请求。
   *
   * 沿用旧实现的直接保存流程，任务归属于当前 session，结束后不再回写旧账户数据。
   */
  fun refresh() {
    val session = AccountService.session.value
    val login = session.state as? AccountState.Login ?: return
    refreshJob?.cancel()
    refreshJob = session.accountCoroutineScope.launch {
      runCatchingCoroutine {
        HttpClient.get("/magipoke/person/info") {
          requireAccountSession(session)
        }.body<ApiWrapper<UserInfo>>().data
      }.onFailure {
        if (isDebug()) {
          toast("用户信息请求失败")
          logg("用户信息请求失败: " + it.stackTraceToString())
        }
      }.onSuccess { info ->
        // 被后续刷新取消或账户已切换时，旧请求不能覆盖当前资料。
        coroutineContext.ensureActive()
        if (AccountService.session.value !== session || login.stuNum != info.stuNum) return@onSuccess
        defaultSettings.putString(
          KEY,
          SecretTransformer.impl.secretEncrypt(defaultJson.encodeToString(info)),
        )
        value = info
        login.userInfo.value = info
      }
    }
  }
}
