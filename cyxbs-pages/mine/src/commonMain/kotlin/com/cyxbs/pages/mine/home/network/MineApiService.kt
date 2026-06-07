package com.cyxbs.pages.mine.home.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.mine.home.bean.SignStatusBean
import de.jensklingenberg.ktorfit.http.POST

/**
 * 「我的」主页网络接口（KMP ktorfit 版）
 */
interface MineApiService {

  @POST("magipoke-intergral/QA/User/getScoreStatus")
  suspend fun getScoreStatus(): ApiWrapper<SignStatusBean>
}
