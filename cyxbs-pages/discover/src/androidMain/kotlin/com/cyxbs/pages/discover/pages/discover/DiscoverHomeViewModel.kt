package com.cyxbs.pages.discover.pages.discover

import android.os.Parcelable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.EmptyCoroutineExceptionHandler
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.mapOrThrowApiException
import com.cyxbs.pages.discover.bean.NewsListItem
import com.cyxbs.pages.discover.network.ApiServices
import com.cyxbs.pages.discover.network.RollerViewInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * @author zixuan
 * 2019/11/20
 */
class DiscoverHomeViewModel : BaseViewModel() {
  val viewPagerInfo = MutableLiveData<List<RollerViewInfo>>()
  val jwNews = MutableLiveData<List<NewsListItem>>()
  val hasUnread = MutableLiveData<Boolean>()
  var functionRvState: Parcelable? = null
  private val apiServices: ApiServices by lazy {
    ApiGenerator.createSelfRetrofit(okHttpClientConfig = {
      it.apply {
        callTimeout(10, TimeUnit.SECONDS)
        readTimeout(2, TimeUnit.SECONDS)
        writeTimeout(2, TimeUnit.SECONDS)
        retryOnConnectionFailure(true)
      }
    }, tokenNeeded = true).create(ApiServices::class.java)
  }
  
  init {
    getRollInfo()
//    getHasUnread()
    getNotificationUnReadStatus()
    getJwNews()
  }
  
  private fun getRollInfo() {
    apiServices.getRollerViewInfo()
      .mapOrThrowApiException()
      .setSchedulers()
      .safeSubscribeBy {
        viewPagerInfo.value = it
      }
  }

  /**
   * 该网络请求的接口好像还在用，暂时不动它
   */
  private fun getHasUnread() {
    apiServices.getHashUnreadMsg()
      .mapOrThrowApiException()
      .setSchedulers()
      .safeSubscribeBy {
        hasUnread.value = it.has
      }
  }
  
  private fun getJwNews() {
    apiServices.getNewsList(1)
      .mapOrThrowApiException()
      .setSchedulers()
      .safeSubscribeBy {
        jwNews.value = it
      }
  }

  /**
   * 用携程异步获取未读的(新的)notification数量
   */
  fun getNotificationUnReadStatus() {
    viewModelScope.launch(EmptyCoroutineExceptionHandler) {
      val uFieldActivityList = async(Dispatchers.IO) {
        apiServices.getUFieldActivityList() }
      val itineraryList = listOf(
        async(Dispatchers.IO) { apiServices.getSentItinerary() },
        async(Dispatchers.IO) { apiServices.getReceivedItinerary() }
      )
      uFieldActivityList.await().apply {
        if (isSuccess() && data.any { !it.clicked }){
          hasUnread.value = true
          return@launch
        }
      }
      itineraryList.awaitAll().apply {
        if (this[0].isSuccess() && (this[0].data?.any { !it.hasRead } == true)) {
          hasUnread.value = true
          return@launch
        }
        if (this[1].isSuccess() && (this[1].data?.any { !it.hasRead } == true)) {
          hasUnread.value = true
          return@launch
        }
      }
      hasUnread.value = false
    }
  }
}
