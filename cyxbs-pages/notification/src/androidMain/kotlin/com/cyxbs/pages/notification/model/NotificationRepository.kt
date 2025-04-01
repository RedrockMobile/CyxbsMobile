package com.cyxbs.pages.notification.model

import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.pages.notification.bean.ItineraryDateBean
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.bean.toAffairDateBean
import com.cyxbs.pages.notification.network.ApiService
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
     * todo 这里后续改成直接调用 affair 模块暴露的方法，否则需要下次打开课表才会生效
     */
    fun addAffair(remindTime: Int, info: ReceivedItineraryMsgBean) : Single<ApiStatus>{
        return api.addAffair(remindTime, info.title, info.content, listOf(info.dateJson).toPostDateJson())
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
    }
}