package com.cyxbs.pages.home.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.EmptyCoroutineExceptionHandler
import com.cyxbs.components.utils.network.api
import com.cyxbs.pages.home.network.NotificationApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * ...
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/14 20:50
 */
class MainViewModel : BaseViewModel() {
  /**
   * 三个状态
   * - true -> 展开
   * - false -> 折叠
   * - null -> 隐藏
   */
  val courseBottomSheetExpand: MutableLiveData<Boolean?> = MutableLiveData(false)
  val courseBottomSheetOffset = MutableLiveData<Float>()

  private val notificationApi = NotificationApiService::class.api

  /**
   * 是否有未读的消息
   */
  val hasUnReadNotification: LiveData<Boolean> get() = _hasUnReadNotification
  private val _hasUnReadNotification = MutableLiveData<Boolean>()


  /**
   * 用携程异步获取未读的(新的)notification数量
   */
  fun getNotificationUnReadStatus() {
    viewModelScope.launch(EmptyCoroutineExceptionHandler) {
      val uFieldActivityList = async(Dispatchers.IO) {
        notificationApi.getUFieldActivityList() }
      val itineraryList = listOf(
        async(Dispatchers.IO) { notificationApi.getSentItinerary() },
        async(Dispatchers.IO) { notificationApi.getReceivedItinerary() }
      )
      uFieldActivityList.await().apply {
        if (isSuccess() && data.any { !it.clicked }){
          _hasUnReadNotification.postValue(true)
          return@launch
        }
      }
      itineraryList.awaitAll().apply {
        if (this[0].isSuccess() && (this[0].data?.any { !it.hasRead } == true)) {
          _hasUnReadNotification.postValue(true)
          return@launch
        }
        if (this[1].isSuccess() && (this[1].data?.any { !it.hasRead } == true)) {
          _hasUnReadNotification.postValue(true)
          return@launch
        }
      }
      _hasUnReadNotification.postValue(false)
    }
  }
}