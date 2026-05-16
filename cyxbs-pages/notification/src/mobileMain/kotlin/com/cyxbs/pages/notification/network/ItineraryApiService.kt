package com.cyxbs.pages.notification.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Query

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/6
 */
interface ItineraryApiService {

  /**
   * 取消itineraryId对应的行程的提醒
   */
  @FormUrlEncoded
  @PUT("magipoke-jwzx/itinerary/cancel")
  suspend fun cancelItineraryReminder(@Field("id") id: String): ApiStatus

  /**
   * 改变行程消息的已读状态
   * @param ids           要变更的id数组
   * @param status        想让hasRead字段变成的状态
   */
  @FormUrlEncoded
  @PUT("magipoke-jwzx/itinerary/read")
  suspend fun changeItineraryReadStatus(
    @Field("id") ids: List<Int>,
    @Field("status") status: Boolean = true,
  ): ApiStatus

  /**
   * 改变行程消息的是否被添加到日程（课表事务）的状态
   * @param id            要操作的行程id
   * @param status        想让hasAdd字段变成的状态
   */
  @FormUrlEncoded
  @PUT("magipoke-jwzx/itinerary/add")
  suspend fun changeItineraryAddStatus(
    @Field("id") id: Int,
    @Field("status") status: Boolean = true,
  ): ApiStatus

  /**
   * 添加进日程
   *
   * @param time          提前提醒时间
   * @param title         事务标题
   * @param content       事务内容
   * @param dateJson      事务json
   * @return
   */
  @POST("magipoke-reminder/Person/addTransaction")
  @FormUrlEncoded
  @Headers("App-Version:74")
  suspend fun addAffair(
    @Field("time")
    time: Int,
    @Field("title")
    title: String,
    @Field("content")
    content: String,
    @Field("date")
    dateJson: String // 为 json 序列化后的 string
  ): ApiStatus
}