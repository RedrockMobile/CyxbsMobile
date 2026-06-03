package com.cyxbs.pages.mine.user.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 签到状态（commonMain ktorfit 版）
 *
 * 由原 androidMain [com.cyxbs.pages.mine.network.model.ScoreStatus] 精简而来，
 * 只保留「我的」主页展示用到的两个字段。
 */
@Serializable
data class SignStatusBean(
  // 连续签到天数
  @SerialName("check_in_days")
  val serialDays: Int = 0,
  // 今天是否已签到，1 为是，0 为否
  @SerialName("is_check_today")
  val checkSign: Int = 0,
) {
  val isChecked: Boolean get() = checkSign == 1
}
