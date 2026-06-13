package com.cyxbs.pages.electricity.network

import com.cyxbs.pages.electricity.bean.ElectricityInfo
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.POST

/**
 * 电费查询接口（KMP ktorfit 版）
 *
 * 业务侧通过 `ElectricityApiService::class.impl()` 获取。
 */
interface ElectricityApiService {

  /** 按宿舍号查电费 */
  @FormUrlEncoded
  @POST("magipoke-elecquery/getElectric")
  suspend fun getElectricityInfo(
    @Field("building") building: String,
    @Field("room") room: String,
  ): ElectricityInfo

  /** 不带参数查电费（基于后端记忆的上次查询，未绑定时会失败） */
  @POST("magipoke-elecquery/getElectric")
  suspend fun getLastElectricityInfo(): ElectricityInfo
}
