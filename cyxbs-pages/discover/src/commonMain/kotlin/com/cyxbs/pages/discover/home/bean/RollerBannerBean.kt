package com.cyxbs.pages.discover.home.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 首页 banner 一条数据
 *
 * 后端返回字段 picture_url / picture_goto_url / keyword
 */
@Serializable
data class RollerBannerBean(
  @SerialName("picture_url")
  val pictureUrl: String = "",
  @SerialName("picture_goto_url")
  val pictureGotoUrl: String = "",
  @SerialName("keyword")
  val keyword: String = "",
)
