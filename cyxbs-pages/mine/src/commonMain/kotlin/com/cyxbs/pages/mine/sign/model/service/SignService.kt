package com.cyxbs.pages.mine.sign.model.service

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.mine.sign.model.bean.SignStatus
import de.jensklingenberg.ktorfit.http.POST

/**  
 * description: 网络请求接口
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 13:44
 */
interface SignService {

  /**
   * 获取签到状态
   */
  @POST("magipoke-intergral/QA/User/getScoreStatus")
  suspend fun getSignStatus(): ApiWrapper<SignStatus>

  /**
   * 签到操作
   */
  @POST("magipoke-intergral/QA/Integral/checkIn")
  suspend fun checkIn(): ApiStatus

}