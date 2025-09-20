package com.cyxbs.pages.notification.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.notification.bean.ChangeReadStatusFromBean
import com.cyxbs.pages.notification.bean.ChangeReadStatusToBean
import com.cyxbs.pages.notification.bean.MsgBeanData
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.PUT

/**
 * 系统消息接口
 *
 * @author 985892345
 * @date 2025/4/6
 */
interface SystemMessageApiService {

  // 得到系统消息
  @GET("message-system/user/allMsg")
  suspend fun getSystemMessage(): ApiWrapper<MsgBeanData>

  /**
   * 改变已读消息状态
   */
  @PUT("message-system/user/msgHasRead")
  suspend fun changeMsgStatus(
    @Body changeBody: ChangeReadStatusToBean
  ): ApiWrapper<ChangeReadStatusFromBean>

}