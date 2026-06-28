package com.cyxbs.pages.schedule.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * todo 提醒/重复规则。
 *
 * 字段保持与后端 `remind_mode` 协议一致，展示格式与旧 Android/iOS 版本暂时继续使用
 * "yyyy年M月d日HH:mm" 这类字符串，后续 UI 迁移时再统一收敛时间模型。
 */
@Serializable
data class ScheduleRemindMode(
  /** 重复模式，取值见 [NONE]、[DAY]、[WEEK]、[MONTH]、[YEAR]。 */
  @SerialName("repeat_mode")
  val repeatMode: Int = NONE,
  /** 年重复日期列表，后端格式为 "MM.dd" 字符串；当前旧 Android 基本未使用。 */
  @SerialName("date")
  val date: List<String> = emptyList(),
  /** 周重复星期列表，沿用后端 1..7 的协议值，具体星期语义由规则层统一解释。 */
  @SerialName("week")
  val week: List<Int> = emptyList(),
  /** 月重复日期列表，例如 1、15、31。 */
  @SerialName("day")
  val day: List<Int> = emptyList(),
  /** 当前生效的提醒时间字符串；空字符串表示未设置提醒。 */
  @SerialName("notify_datetime")
  val notifyDateTime: String? = "",
) {
  companion object {
    /** 不重复。 */
    const val NONE = 0
    /** 每日重复。 */
    const val DAY = 1
    /** 每周重复。 */
    const val WEEK = 2
    /** 每月重复。 */
    const val MONTH = 3
    /** 每年重复；后端和 iOS 有字段，旧 Android 核心逻辑暂未完整支持。 */
    const val YEAR = 4
  }
}
