package com.cyxbs.pages.electricity.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 电费查询接口外层包装
 *
 * 后端在「账号未绑定寝室 / 服务异常」等场景下不会返回 `elec_inf` 字段，
 * 这里声明成 nullable + 默认 null，调用方据此判断是否需要提示用户先选寝室。
 */
@Serializable
data class ElectricityInfo(
  @SerialName("elec_inf")
  val elecInf: ElecInf? = null,
)

/**
 * 电费查询返回的具体信息字段
 */
@Serializable
data class ElecInf(
  @SerialName("elec_end")
  val elecEnd: String = "",
  @SerialName("elec_start")
  val elecStart: String = "",
  @SerialName("elec_free")
  val elecFree: String = "",
  @SerialName("elec_spend")
  val elecSpend: String = "",
  @SerialName("elec_cost")
  val elecCost: List<String> = listOf("0", "0"),
  @SerialName("record_time")
  val recordTime: String = "",
  @SerialName("elec_month")
  val elecMonth: String = "",
) {
  /**
   * 后端把元和角分两段返回，前端拼接成 "x.y" 的形式
   */
  fun getEleCost(): String = "${elecCost.getOrElse(0) { "0" }}.${elecCost.getOrElse(1) { "0" }}"

  /** 简单判断抄表日期是否为空来判断是不是返回的空数据 */
  fun isEmpty(): Boolean = recordTime.isEmpty()
}
