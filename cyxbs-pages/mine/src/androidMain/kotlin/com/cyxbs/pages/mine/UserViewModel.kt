package com.cyxbs.pages.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.utils.extensions.EmptyCoroutineExceptionHandler
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.mine.network.model.ItineraryMsgBean
import com.cyxbs.pages.mine.network.model.ScoreStatus
import com.cyxbs.pages.mine.network.model.UfieldMsgBean
import com.cyxbs.pages.mine.util.apiService
import com.mredrock.cyxbs.common.utils.extensions.doOnErrorWithDefaultErrorHandler
import com.mredrock.cyxbs.common.utils.extensions.mapOrThrowApiException
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch


/**
 * Created by zia on 2018/8/26.
 */
class UserViewModel : BaseViewModel() {

    private val _status = MutableLiveData<ScoreStatus>()//签到状态
    val status: LiveData<ScoreStatus>
        get() = _status

    /**
     * ”新“通知消息（状态为未读的）的数量
     */
    val newNotificationCount: LiveData<Int> get() = _newNotificationCount
    private val _newNotificationCount = MutableLiveData<Int>()



    init {
        getNewNotificationCount()
    }

    /**
     * 用携程异步获取未读的notification数量
     */
    fun getNewNotificationCount() {
        viewModelScope.launch(EmptyCoroutineExceptionHandler) {
            val uFieldActivityList = async(Dispatchers.IO) { apiService.getUFieldActivityList() }
            val itineraryList = listOf(
                async(Dispatchers.IO) { apiService.getSentItinerary() },
                async(Dispatchers.IO) { apiService.getReceivedItinerary() }
            )
            val newUFieldActivityCount = async(Dispatchers.Default) {
                getNewActivityCount(uFieldActivityList.await())
            }
            val newItineraryCount = async(Dispatchers.Default) {
                getNewItineraryCount(itineraryList.awaitAll())
            }
            _newNotificationCount.value = (newUFieldActivityCount.await() + newItineraryCount.await())
        }
    }

    /**
     * 获取“新”活动通知的数量
     * @param response
     */
    private fun getNewActivityCount(response: ApiWrapper<List<UfieldMsgBean>>) : Int{
        return if (response.isSuccess()) {
            val list = response.data.filter { !it.clicked }
            list.size
        } else
            0
    }

    /**
     * 获取“新”行程通知的数量
     * @param response
     */
    private fun getNewItineraryCount(response: List<ApiWrapper<List<ItineraryMsgBean>?>>): Int{
        val receivedCount: Int = if (response[1].isSuccess() && !response[1].data.isNullOrEmpty()) {
            val list = response[1].data!!.filter { !it.hasRead }
            list.size
        } else 0
        val sentCount: Int = if (response[0].isSuccess() && !response[0].data.isNullOrEmpty()) {
            val list = response[0].data!!.filter { !it.hasRead }
            list.size
        } else 0
        return receivedCount + sentCount
    }


    fun getScoreStatus() {
        apiService.getScoreStatus()
            .mapOrThrowApiException()
            .setSchedulers()
            .doOnErrorWithDefaultErrorHandler { true }
            .unsafeSubscribeBy(
                onNext = {
                    _status.postValue(it)
                }
            )
            .lifeCycle()
    }
}