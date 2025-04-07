package com.cyxbs.pages.notification.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.notification.bean.UfieldMsgBean
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Query

/**
 * 活动消息接口
 *
 * @author 985892345
 * @date 2025/4/6
 */
interface ActivityMessageApiService {

  // 获取notification模块中的活动通知消息
  @GET("magipoke-ufield/message/list/")
  suspend fun getActivityMessage(): ApiWrapper<List<UfieldMsgBean>>

  /*
    * 改变读取活动消息的状态
    * */
  @PUT("magipoke-ufield/message/action/click/")
  suspend fun changeActivityMsgStatus(
    @Query("message_id") messageId: Int
  ): ApiStatus
}