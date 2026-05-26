package com.cyxbs.pages.notification.bean

import com.cyxbs.pages.notification.api.NoticeNavArgument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/20
 */
@Serializable
data class NoticeDialogBean(
  @SerialName("id")
  val id: String,
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("map")
  val map: Map<String, NoticeNavArgument.TextInfo> = emptyMap(),
  @SerialName("button")
  val button: NoticeNavArgument.ButtonInfo? = null,
)