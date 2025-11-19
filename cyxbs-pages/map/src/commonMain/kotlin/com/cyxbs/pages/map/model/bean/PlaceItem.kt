package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 建筑的基本信息
 * @Author : zzx
 * @Date : 2025/11/17 18:28
 */

@Serializable
data class PlaceItem(
  @SerialName("place_name")
  val placeName: String,
  @SerialName("place_id")
  val placeId: String,
  @SerialName("place_center_x")
  val placeCenterX: Int,
  @SerialName("place_center_y")
  val placeCenterY: Int,
  @SerialName("building_list")
  val buildingList: List<PlaceBuildingItem>,
  @SerialName("tag_left")
  val tagLeft: Int,
  @SerialName("tag_right")
  val tagRight: Int,
  @SerialName("tag_top")
  val tagTop: Int,
  @SerialName("tag_bottom")
  val tagBottom: Int
)
