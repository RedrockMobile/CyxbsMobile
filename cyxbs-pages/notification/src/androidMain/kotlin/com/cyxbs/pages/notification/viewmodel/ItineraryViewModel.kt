package com.cyxbs.pages.notification.viewmodel

import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.model.ItineraryRepository

/**
 * ...
 * @author: Black-skyline (Hu Shujun)
 * @email: 2031649401@qq.com
 * @date: 2023/8/22
 * @Description:
 *
 */
class ItineraryViewModel : BaseViewModel() {
    // 用户已经发送的行程消息的列表（发送的行程）
    val sentItineraryList = ItineraryRepository.sentItineraryFlow

    // 用户被通知到的行程消息的列表（接收的行程）
    val receivedItineraryList = ItineraryRepository.receivedItineraryFlow

    // 取消某行程的提醒 该请求是否成功（状态）
    private val _cancelReminderIsSuccessfulState = MutableLiveData<Pair<Int, Boolean>>()
    // 取消某行程的提醒 该请求是否成功（事件）
    val cancelReminderIsSuccessfulEvent = _cancelReminderIsSuccessfulState.asShareFlow()


    // 添加某一行程到日程（添加事务） 请求是否成功（状态）
    private val _add2scheduleIsSuccessfulState = MutableLiveData<Pair<Int, Boolean>>()
    // 添加某一行程到日程（添加事务） 请求是否成功（事件）
    val add2scheduleIsSuccessfulEvent = _add2scheduleIsSuccessfulState.asShareFlow()

    /**
     * 传入行程id和该行程在rv数据中的索引，取消该行程的提醒
     */
    fun cancelItineraryReminder(itineraryId: Int, index: Int) {
        launchByViewModelScope {
            ItineraryRepository.cancelItineraryReminder(itineraryId).onFailure {
                "取消失败".toast()
                _cancelReminderIsSuccessfulState.postValue(Pair(index, false))
            }.onSuccess {
                _cancelReminderIsSuccessfulState.postValue(Pair(index, true))
            }
        }
    }

    /**
     * 改变行程已读状态
     * @param unReadIdList
     */
    fun changeItineraryReadStatus(unReadIdList: List<Int>, receivedOrSend: Boolean) {
        if (unReadIdList.isEmpty()) return
        launchByViewModelScope {
            ItineraryRepository.changeItineraryReadStatus(unReadIdList, receivedOrSend)
        }
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
        launchByViewModelScope {
            ItineraryRepository.addAffair(remindTime, info).onFailure {
                "添加失败".toast()
                _add2scheduleIsSuccessfulState.postValue(Pair(index, false))
            }.onSuccess {
                "添加成功".toast()
                _add2scheduleIsSuccessfulState.postValue(Pair(index, true))
            }.onSuccess {
                // 改变行程是否添加到了日程的状态
                ItineraryRepository.changeItineraryAddStatus(info.id)
            }
        }
    }
}