package com.cyxbs.pages.affair.bean

import com.cyxbs.components.config.time.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/19
 */
@Serializable
class AffairBean2(
  @SerialName("id")
  val remoteId: Int,
  @SerialName("time")
  val remindTime: Int, // 提醒时间
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("when")
  val whenList: List<AffairWhenBean>, // 时间段
)

@Serializable
class AffairWhenBean(
  @SerialName("start")
  val start: Int, // 开始时间，天分钟数，小时 * 60 + 分钟，480 = 8 * 60 + 0
  @SerialName("end")
  val end: Int,   // 结束时间，天分钟数，小时 * 60 + 分钟，719 = 11 * 60 + 59
  @SerialName("date")
  val date: List<Date>, // 哪几天，格式为 yyyy-M-d，比如：2025-4-5
)