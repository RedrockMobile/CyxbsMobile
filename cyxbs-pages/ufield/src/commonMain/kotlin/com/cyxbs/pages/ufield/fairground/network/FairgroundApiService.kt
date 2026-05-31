package com.cyxbs.pages.ufield.fairground.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.ufield.fairground.bean.DaysBean
import de.jensklingenberg.ktorfit.http.GET

/**
 * 邮乐园入口页网络接口（KMP ktorfit 版）
 *
 * 业务侧通过 `FairgroundApiService::class.impl()` 获取。
 */
interface FairgroundApiService {

  @GET("magipoke-playground/center/days")
  suspend fun getDays(): ApiWrapper<DaysBean>
}
