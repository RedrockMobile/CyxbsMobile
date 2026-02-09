package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 收藏返回数据类
 * @Author : zzx
 * @Date : 2025/12/4 21:43
 */

@Serializable
data class FavoritePlaceSimple(
  @SerialName("place_id")
  val placeIdList: MutableList<String>
)
