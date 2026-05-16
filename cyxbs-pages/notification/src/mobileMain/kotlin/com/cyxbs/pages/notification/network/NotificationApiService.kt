package com.cyxbs.pages.notification.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import com.cyxbs.pages.notification.bean.UfieldMsgBean
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/**
 * ...
 * @author: Black-skyline (Hu Shujun)
 * @email: 2031649401@qq.com
 * @date: 2023/9/5
 * @Description: 获取消息中心的消息
 *
 */
interface NotificationApiService {

    // 获取notification模块中的发送的行程
    @GET("magipoke-jwzx/itinerary/allMsg")
    suspend fun getSentItinerary(
        @Query("typ") type: String = "sent"
    ): ApiWrapper<List<SentItineraryMsgBean>>

    // 获取notification模块中的接收的行程
    @GET("magipoke-jwzx/itinerary/allMsg")
    suspend fun getReceivedItinerary(
        @Query("typ") type: String = "received"
    ): ApiWrapper<List<ReceivedItineraryMsgBean>>

}