package com.cyxbs.pages.notification.model

import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.pages.notification.bean.ItineraryDateBean
import com.cyxbs.pages.notification.bean.MsgBeanData
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import com.cyxbs.pages.notification.bean.toAffairDateBean
import com.cyxbs.pages.notification.network.ApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/20
 * @Description:
 *
 */
object NotificationRepository {
    private val api = ApiService.INSTANCE
    private val mGson = defaultGson


    private fun List<ItineraryDateBean>.toPostDateJson(): String {
        return mGson.toJson(toAffairDateBean())
    }
    /**
     * 获取所有的活动消息和系统消息
     */
    fun getAllSysAndActMsg(): Observable<ApiWrapper<MsgBeanData>> {
        return api.getAllMsg()
            .observeOn(Schedulers.io())
            .subscribeOn(AndroidSchedulers.mainThread())
    }

    /**
     * 获取本用户已经发送的行程
     */
    fun getSentItinerary() : Single<ApiWrapper<List<SentItineraryMsgBean>>> {
        return api.getSentItinerary()
            .observeOn(Schedulers.io())
            .subscribeOn(AndroidSchedulers.mainThread())
    }

    /**
     * 获取通知到本用户的行程
     */
    fun getReceivedItinerary() : Single<ApiWrapper<List<ReceivedItineraryMsgBean>>> {
        return api.getReceivedItinerary()
            .observeOn(Schedulers.io())
            .subscribeOn(AndroidSchedulers.mainThread())
    }

    /**
     * 取消行程的提醒
     * @param itineraryId 行程id
     */
    fun cancelItineraryReminder(itineraryId: Int) : Single<ApiStatus> {
        return api.cancelItineraryReminder(itineraryId.toString())
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
    }

    /**
     * 改变行程的已读状态（默认为将行程变为已读，即status为true时）
     * @param idList 行程id
     */
    fun changeItineraryReadStatus(idList: List<Int>, status: Boolean = true) : Single<ApiStatus> {
        return api.changeItineraryReadStatus(idList, status)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
    }

    /**
     * 改变行程的是否被添加到日程状态（默认为将行程变为已添加，即status为true时）
     * @param itineraryId 行程id
     */
    fun changeItineraryAddStatus(itineraryId: Int, status: Boolean = true) : Single<ApiStatus> {
        return api.changeItineraryAddStatus(itineraryId, status)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
    }

    /**
     * 添加行程到事务中
     */
    fun addAffair(remindTime: Int, info: ReceivedItineraryMsgBean) : Single<ApiStatus>{
        return api.addAffair(remindTime, info.title, info.content, listOf(info.dateJson).toPostDateJson())
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
    }

    /**
     * 获取未读的行程（即hasRead字段为false的行程）有多少条
     * 留着后续给api_notification模块填充
     */
    fun getNewItineraryCount(resultContainer: MutableLiveData<Int>) {
        var result = 0
        getSentItinerary()
            .mapOrInterceptException {
            }
            .flatMap {list1->
                result += list1.filter { !it.hasRead }.size
                getReceivedItinerary()
            }
            .mapOrInterceptException {  }
            .map {list2->
                list2.filter { !it.hasRead }.size
            }
            .unsafeSubscribeBy(
             onError = {
                 resultContainer.value = result
             },
             onSuccess = {
                 result += it
                 resultContainer.value = result
             })
    }
}