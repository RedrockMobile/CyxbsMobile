package com.cyxbs.pages.notification.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.components.utils.utils.LogUtils
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import com.cyxbs.pages.notification.model.NotificationRepository

/**
 * ...
 * @author: Black-skyline (Hu Shujun)
 * @email: 2031649401@qq.com
 * @date: 2023/8/22
 * @Description:
 *
 */
class ItineraryViewModel(val hostViewModel: NotificationViewModel) : BaseViewModel() {
    // 用户已经发送的行程消息的列表（发送的行程）
    val sentItineraryList: LiveData<List<SentItineraryMsgBean>> get() = _sentItineraryList
    private val _sentItineraryList = MutableLiveData<List<SentItineraryMsgBean>>()

    // 所有未读的用户发送的行程
    val allUnReadSentItineraryIds: LiveData<List<Int>> get() = _allUnReadSentItineraryIds
    private val _allUnReadSentItineraryIds = MutableLiveData<List<Int>>()
    val newUnReadSentItineraryIds = _allUnReadSentItineraryIds.asShareFlow()

    // 获取-“已经发送的行程消息列表” 该请求是否成功（状态）
    val sentItineraryListIsSuccessfulState: LiveData<Boolean> get() = _sentItineraryListIsSuccessfulState
    private val _sentItineraryListIsSuccessfulState = MutableLiveData<Boolean>()



    // 用户被通知到的行程消息的列表（接收的行程）
    val receivedItineraryList: LiveData<List<ReceivedItineraryMsgBean>> get() = _receivedItineraryList
    private val _receivedItineraryList = MutableLiveData<List<ReceivedItineraryMsgBean>>()

    // 所有未读的用户接收的行程
    val allUnReadReceivedItineraryIds: LiveData<List<Int>> get() = _allUnReadReceivedItineraryIds
    private val _allUnReadReceivedItineraryIds = MutableLiveData<List<Int>>()
    val newUnReadReceivedItineraryIds = _allUnReadReceivedItineraryIds.asShareFlow()

    // 获取-“被通知到的行程消息列表” 该请求是否成功（状态）
    val receivedItineraryListIsSuccessfulState: LiveData<Boolean> get() = _receivedItineraryListIsSuccessfulState
    private val _receivedItineraryListIsSuccessfulState = MutableLiveData<Boolean>()



    // 取消某行程的提醒 该请求是否成功（状态）
    private val _cancelReminderIsSuccessfulState = MutableLiveData<Pair<Int, Boolean>>()
    // 取消某行程的提醒 该请求是否成功（事件）
    val cancelReminderIsSuccessfulEvent = _cancelReminderIsSuccessfulState.asShareFlow()


    // 添加某一行程到日程（添加事务） 请求是否成功（状态）
    private val _add2scheduleIsSuccessfulState = MutableLiveData<Pair<Int, Boolean>>()
    // 添加某一行程到日程（添加事务） 请求是否成功（事件）
    val add2scheduleIsSuccessfulEvent = _add2scheduleIsSuccessfulState.asShareFlow()

    // 当前行程fragment展示的哪个页面，0为接收fragment，1为发送fragment
    val currentPageIndex: LiveData<Int> get() = _currentPageIndex
    private val _currentPageIndex = MutableLiveData(0)

    /**
     * 获取已经发送的行程列表
     */
    fun getSentItinerary() {
        if (hostViewModel.getItineraryIsSuccessful) {
            _sentItineraryListIsSuccessfulState.postValue(true)
            _sentItineraryList.postValue(hostViewModel.itineraryMsg.value!!.sentItineraryList)
        } else {
            _sentItineraryListIsSuccessfulState.postValue(false)
        }
    }

    /**
     * 获取被通知到的行程列表
     */
    fun getReceivedItinerary() {
        if (hostViewModel.getItineraryIsSuccessful) {
            _receivedItineraryListIsSuccessfulState.postValue(true)
            _receivedItineraryList.postValue(hostViewModel.itineraryMsg.value!!.receivedItineraryList)
        } else {
            _receivedItineraryListIsSuccessfulState.postValue(false)
        }
    }

    /**
     * 传入行程id和该行程在rv数据中的索引，取消该行程的提醒
     */
    fun cancelItineraryReminder(itineraryId: Int, index: Int) {
        NotificationRepository.cancelItineraryReminder(itineraryId)
            .throwOrInterceptException {
                "取消失败".toast()
                _cancelReminderIsSuccessfulState.postValue(Pair(index, false))
            }
            .safeSubscribeBy (
                onError =
                {
                    it.printStackTrace()
                },
                onSuccess =
                {
                _cancelReminderIsSuccessfulState.postValue(Pair(index, true))
                })
    }

    /**
     * 改变行程已读状态
     * @param unReadIdList
     * @param status
     */
    fun changeItineraryReadStatus(unReadIdList: List<Int>,page: Int, status: Boolean = true) {
        if (unReadIdList.isEmpty()) return
        NotificationRepository.changeItineraryReadStatus(unReadIdList, status)
            .throwOrInterceptException {
                ApiException{exception ->
                    LogUtils.w("H57-changeReadStatus","response Exception status:${exception.status}")
                }
            }
            .safeSubscribeBy (
                onError = {
                    it.printStackTrace()
                },
                onSuccess = {
                LogUtils.d("H57","changeReadStatus success")
                when(page) {
                    0 -> _allUnReadReceivedItineraryIds.postValue(emptyList())
                    1 -> _allUnReadSentItineraryIds.postValue(emptyList())
                    else ->{}
                }
            })
    }


    /**
     * 改变行程是否添加到了日程的状态
     * @param itineraryId
     * @param status
     */
    private fun changeItineraryAddStatus(itineraryId: Int, status: Boolean = true) {
        NotificationRepository.changeItineraryAddStatus(itineraryId, status)
            .throwOrInterceptException {
                "与服务器君通信失败 TAT~".toast()
            }
            .safeSubscribeBy(
                onError = {
                    it.printStackTrace()
                },
                onSuccess = {
                }
            )
    }

    /**
     * 把行程添加到日程(课表事务)
     * @param index
     * @param remindTime
     * @param info
     */
    fun addItineraryToSchedule(
        index: Int,
        remindTime: Int,
        info: ReceivedItineraryMsgBean
    ) {
        NotificationRepository.addAffair(remindTime, info)
            .throwOrInterceptException {
                "添加失败".toast()
                _add2scheduleIsSuccessfulState.postValue(Pair(index, false))
            }
            .safeSubscribeBy (
                onError = {
                    it.printStackTrace()
                },
                onSuccess = {
                "添加成功".toast()
                changeItineraryAddStatus(info.id)
                _add2scheduleIsSuccessfulState.postValue(Pair(index, true))
            })
    }

    fun changeCurrentPageIndex(index: Int) {
        _currentPageIndex.value = index
    }

    fun setUnReadSentItineraryIds(list: List<Int>) {
        _allUnReadSentItineraryIds.value = list
    }

    fun setUnReadReceivedItineraryIds(list: List<Int>) {
        _allUnReadReceivedItineraryIds.value = list
    }

}