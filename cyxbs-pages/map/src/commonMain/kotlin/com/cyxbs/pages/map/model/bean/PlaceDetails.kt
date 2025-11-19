package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 建筑详细信息
 * @Author : zzx
 * @Date : 2025/11/17 18:34
 */

@Serializable
data class PlaceDetails(
  @SerialName("place_name")
  val placeName: String,
  @SerialName("place_attribute")
  val placeAttribute: List<String>?,
  val tags: List<String>?,
  val images: List<String>?
)

