package com.cyxbs.pages.ufield.fairground.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 邮乐园「来到的天数」数据
 *
 * 对应接口：/magipoke-playground/center/days
 */
@Serializable
data class DaysBean(
  @SerialName("days")
  val days: String,
)
