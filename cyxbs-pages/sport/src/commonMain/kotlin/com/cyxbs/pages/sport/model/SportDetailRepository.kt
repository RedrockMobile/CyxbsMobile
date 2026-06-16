package com.cyxbs.pages.sport.model

import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.sport.model.network.SportApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 体育打卡详情数据共享源
 *
 * 写成 object 让发现页 feed 卡片与体育打卡详情页共用同一份数据，避免重复请求。
 * 监听登录态：登录后自动刷新，退出登录置 null。
 *
 * @author : why
 * @time   : 2022/8/6 10:57
 * @bless  : God bless my code
 */
object SportDetailRepository {

  /**
   * 体育打卡详情数据。
   * - null：未登录 / 已退出登录
   * - Result：一次请求的成功或失败结果
   */
  val sportData: StateFlow<Result<SportDetailBean>?> get() = _sportData
  private val _sportData = MutableStateFlow<Result<SportDetailBean>?>(null)

  private var isRefreshing = false

  /**
   * 刷新数据，如果返回 false，则说明正在刷新中
   */
  fun refresh(): Boolean {
    if (isRefreshing) return false
    isRefreshing = true
    appCoroutineScope.launch {
      runCatchingCoroutine {
        SportApiService::class.impl().getSportDetail()
      }.mapCatching { it.data }
        .onSuccess { _sportData.value = Result.success(it) }
        .onFailure { _sportData.value = Result.failure(it) }
        .onFailure {
          logg("${it.stackTraceToString()}")
        }
      isRefreshing = false
    }
    return true
  }

  init {
    IAccountService::class.impl().state
      .onEach {
        when (it) {
          is AccountState.Login -> refresh()
          is AccountState.Logout -> _sportData.value = null
          else -> Unit
        }
      }.launchIn(appCoroutineScope)
  }
}
