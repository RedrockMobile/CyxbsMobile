package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 建筑坐标
 * @Author : zzx
 * @Date : 2025/11/17 18:30
 */

@Serializable
data class PlaceBuildingItem(
  @SerialName("building_left")
  val buildingLeft: Int,
  @SerialName("building_right")
  val buildingRight: Int,
  @SerialName("building_top")
  val buildingTop: Int,
  @SerialName("building_bottom")
  val buildingBottom: Int
)