package com.cyxbs.pages.sport.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 体育打卡信息说明的单条内容（后端下发，展示在 feed 的说明弹窗里）
 */
@Serializable
data class NoticeItem(
  @SerialName("title") val title: String = "",
  @SerialName("content") val content: String = "",
)
