package com.cyxbs.pages.affair.bean

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/22
 */
@Serializable
data class AffairEntity(
  @SerialName("remoteId")
  val remoteId: Int,
  @SerialName("localId")
  val localId: String,
  @SerialName("remindTime")
  val remindTime: Int,
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("whatTime")
  val whatTime: List<AffairWhatTime>,
)

@Serializable
data class AffairWhatTime(
  @SerialName("timePair")
  val timePair: MinuteTimePair,
  @SerialName("date")
  val date: List<Date>,
)