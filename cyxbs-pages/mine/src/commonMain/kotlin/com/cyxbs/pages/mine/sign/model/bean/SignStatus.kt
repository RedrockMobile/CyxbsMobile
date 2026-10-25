package com.cyxbs.pages.mine.sign.model.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**  
 * description: 签到数据类
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 13:35
 */
@Serializable
data class SignStatus(
  val integral: Int,
  @SerialName("check_in_days")
  val serialDays: Int, // 已连续打卡X天
  @SerialName("is_check_today")
  val checkSign: Int, // 是否签到
  @SerialName("week_info")
  val weekInfo: String, // 七天签到状态；接口顺序为周日→周一，页面会反转为周一→周日
  val rank: Int, // 已签到时显示今日排名
  val percent: String, // 显示超过的用户比例
  @SerialName("can_check_in")
  val canCheckIn: Boolean // 控制寒暑假提示和按钮可用性
) {
  val isChecked get() = checkSign == 1
}
