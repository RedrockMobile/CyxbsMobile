package com.cyxbs.pages.affair.bean

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/22
 */
@Serializable
data class AffairEntity(
  val remoteId: Int,
  val localId: String = "",
  val remindTime: Int,
  val title: String,
  val content: String,
  val whatTime: List<AffairWhatTime>,
)

@Serializable
data class AffairWhatTime(
  val timePair: MinuteTimePair,
  val date: List<Date>,
)