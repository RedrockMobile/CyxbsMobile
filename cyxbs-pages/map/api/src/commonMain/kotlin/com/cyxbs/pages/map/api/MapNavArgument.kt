package com.cyxbs.pages.map.api

import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.appNavBackStack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * @Desc : 地图的跳转参数
 * @Author : zzx
 * @Date : 2025/11/10 14:16
 */

@Serializable
class MapNavArgument(
  @SerialName("placeSearch")
  val placeSearch: String? = null
) : AppNavArgument