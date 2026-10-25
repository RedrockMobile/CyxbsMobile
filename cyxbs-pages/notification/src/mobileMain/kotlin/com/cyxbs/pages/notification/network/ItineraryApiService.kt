package com.cyxbs.pages.notification.network

import com.cyxbs.components.utils.network.ApiStatus
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.PUT

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
}