package com.cyxbs.pages.sport.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SportDetailBean(
  @SerialName("award")
  val award: Int = 0,
  @SerialName("item")
  // 后端在「无打卡记录」时会下发 item: null（而非空数组），故声明为可空，使用处 orEmpty()
  val item: List<Item>? = null,
  @SerialName("other_done")
  val otherDone: Int = 0,
  @SerialName("other_total")
  val otherTotal: Int = 0,
  @SerialName("run_done")
  val runDone: Int = 0,
  @SerialName("run_total")
  val runTotal: Int = 0,
) {
  @Serializable
  data class Item(
    @SerialName("date")
    override val date: String = "",
    @SerialName("is_award")
    override val isAward: Boolean = false,
    @SerialName("spot")
    override var spot: String = "",
    @SerialName("time")
    override val time: String = "",
    @SerialName("type")
    override val type: String = "",
    @SerialName("valid")
    override val valid: Boolean = false,
  ) : SportDetailItemData
}
