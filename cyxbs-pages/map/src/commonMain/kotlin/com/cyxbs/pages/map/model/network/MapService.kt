package com.cyxbs.pages.map.model.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.map.model.bean.ButtonInfo
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceDetails
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST

/**
 * @Desc : 地图模块网络请求API
 * @Author : zzx
 * @Date : 2025/11/17 18:15
 */

interface MapService {

  @GET("magipoke-stumap/basic")
  suspend fun getMapInfo() : ApiWrapper<MapInfo> // 获取地图基本信息

  @FormUrlEncoded
  @POST("magipoke-stumap/detailsite")
  suspend fun getPlaceDetails(@Field("place_id") placeId: String) : ApiWrapper<PlaceDetails> // 获取建筑的详细信息

  @GET("magipoke-stumap/button")
  suspend fun getButtonInfo() : ApiWrapper<ButtonInfo> // 获取按钮信息
}