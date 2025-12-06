package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 地点查找数据类
 * @Author : zzx
 * @Date : 2025/12/6 11:08
 */

@Serializable
data class PlaceSearch(
  @SerialName("place_id")
  val placeId: String
)
