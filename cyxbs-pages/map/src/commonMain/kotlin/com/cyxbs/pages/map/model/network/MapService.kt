package com.cyxbs.pages.map.model.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.map.model.bean.ButtonInfo
import com.cyxbs.pages.map.model.bean.FavoritePlaceSimple
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceDetails
import com.cyxbs.pages.map.model.bean.PlaceSearch
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import io.ktor.client.request.forms.MultiPartFormDataContent

/**
 * @Desc : 地图模块网络请求API
 * @Author : zzx
 * @Date : 2025/11/17 18:15
 */

interface MapService {

  @GET("magipoke-stumap/basic")
  suspend fun getMapInfo(): ApiWrapper<MapInfo> // 获取地图基本信息

  @FormUrlEncoded
  @POST("magipoke-stumap/detailsite")
  suspend fun getPlaceDetails(@Field("place_id") placeId: String): ApiWrapper<PlaceDetails> // 获取建筑的详细信息

  @GET("magipoke-stumap/button")
  suspend fun getButtonInfo(): ApiWrapper<ButtonInfo> // 获取按钮信息

  @FormUrlEncoded
  @POST("magipoke-stumap/addhot")
  suspend fun addHot(@Field("id") placeId: String): ApiStatus // 添加热词

  @GET("magipoke-stumap/rockmap/collect")
  suspend fun getCollect(): ApiWrapper<FavoritePlaceSimple> // 得到收藏

  @FormUrlEncoded
  @PATCH("magipoke-stumap/rockmap/addkeep")
  suspend fun addCollect(@Field("place_id") placeId: String): ApiStatus // 添加收藏

  @HTTP(method = "DELETE", path = "magipoke-stumap/rockmap/deletekeep", hasBody = true)
  suspend fun deleteCollect(@Body body: MultiPartFormDataContent): ApiStatus // 删除收藏

  @FormUrlEncoded
  @POST("magipoke-stumap/placesearch")
  suspend fun placeSearch(@Field("place_search") placeSearch: String): ApiWrapper<PlaceSearch> // 搜索地点

  @POST("magipoke-stumap/rockmap/upload")
  suspend fun uploadPhoto(@Body body: MultiPartFormDataContent): ApiStatus // 上传图片
}