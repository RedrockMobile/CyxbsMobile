package com.cyxbs.pages.map.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Desc : 按钮信息
 * @Author : zzx
 * @Date : 2025/11/17 18:36
 */

@Serializable
data class ButtonInfo(
  @SerialName("button_info")
  val buttonInfo: List<ButtonInfoItem>
)

@Serializable
data class ButtonInfoItem(
  val title: String,
  @SerialName("place_id")
  val placeIdList: List<String>,
  @SerialName("is_hot")
  val isHot: Boolean
)
