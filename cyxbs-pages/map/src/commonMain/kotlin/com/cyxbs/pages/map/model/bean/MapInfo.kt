package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 地图基本信息数据类
 * @Author : zzx
 * @Date : 2025/11/17 18:17
 */

@Serializable
data class MapInfo(
  @SerialName("hot_word")
  val hotWord: String, // 热词
  @SerialName("place_list")
  val placeList: List<PlaceItem>, // 地点列表
  @SerialName("map_url")
  val mapUrl: String, // 地图链接
  @SerialName("map_width")
  val mapWidth: Int, // 地图宽
  @SerialName("map_height")
  val mapHeight: Int, // 地图高
  @SerialName("map_background_color")
  val mapBackgroundColor: String, // 地图背景颜色
  @SerialName("picture_version")
  val pictureVersion: Long, // 地图版本号
  @SerialName("open_site")
  val openSiteId: String // 初始的地图点
)
