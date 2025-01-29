package com.cyxbs.pages.sport.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.sport.model.network.SportDetailApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 写成 object，解决主页卡片与体育打卡页面跨页数据问题
 *
 * @author : why
 * @time   : 2022/8/6 10:57
 * @bless  : God bless my code
 */
object SportDetailRepository {

  /**
   * 观测体育打卡详情界面数据的LiveData
   */
  val sportData: LiveData<Result<SportDetailBean>?> get() = _sportData
  private val _sportData = MutableLiveData<Result<SportDetailBean>?>()

  private var mIsRefresh = false

  /**
   * 刷新数据，如果返回 false，则说明正在刷新中
   */
  fun refresh(): Boolean {
    if (mIsRefresh) return false
    mIsRefresh = true
    SportDetailApiService.INSTANCE
      .getSportDetailData()
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .doOnTerminate {
        mIsRefresh = false
      }
      .mapOrInterceptException {
        _sportData.postValue(Result.failure<SportDetailBean>(it))
      }
      .unsafeSubscribeBy {
        _sportData.postValue(Result.success(it))
      }
    return true
  }

  init {
    IAccountService::class.impl().state
      .onEach {
        when (it) {
          AccountState.Login -> refresh()
          AccountState.Logout -> _sportData.postValue(null)
          else -> Unit
        }
      }.launchIn(appCoroutineScope)
  }
}