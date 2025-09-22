package com.cyxbs.pages.discover.pages.discover

import android.os.Parcelable
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.mapOrThrowApiException
import com.cyxbs.pages.discover.bean.NewsListItem
import com.cyxbs.pages.discover.network.ApiServices
import com.cyxbs.pages.discover.network.RollerViewInfo

/**
 * @author zixuan
 * 2019/11/20
 */
class DiscoverHomeViewModel : BaseViewModel() {
  val viewPagerInfo = MutableLiveData<List<RollerViewInfo>>()
  val jwNews = MutableLiveData<List<NewsListItem>>()
  var functionRvState: Parcelable? = null
  private val apiServices: ApiServices by lazy {
    ApiGenerator.getApiService(ApiServices::class.java)
  }
  
  init {
    getRollInfo()
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

  private fun getJwNews() {
    apiServices.getNewsList(1)
      .mapOrThrowApiException()
      .setSchedulers()
      .safeSubscribeBy {
        jwNews.value = it
      }
  }
}
