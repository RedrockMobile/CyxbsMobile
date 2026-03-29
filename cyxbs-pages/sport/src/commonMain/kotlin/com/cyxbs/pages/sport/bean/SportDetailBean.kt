package com.cyxbs.pages.sport.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SportDetailBean (
    @SerialName("award")
    val award : Int,
    @SerialName("item")
    val item: List<Item>,
    @SerialName("other_done")
    val otherDone: Int,
    @SerialName("other_total")
    val otherTotal: Int,
    @SerialName("run_done")
    val runDone: Int,
    @SerialName("run_total")
    val runTotal: Int
) {
    @Serializable
    data class Item(
        @SerialName("date")
        override val date: String,
        @SerialName("is_award")
        override val isAward: Boolean,
        @SerialName("spot")
        override var spot: String,
        @SerialName("time")
        override val time: String,
        @SerialName("type")
        override val type: String,
        @SerialName("valid")
        override val valid: Boolean
    ) : SportDetailItemData
}