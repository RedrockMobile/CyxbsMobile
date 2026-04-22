package com.cyxbs.pages.schoolcar.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * description ： 校车信息版本
 * author : HI-IR
 * email : qq2420226433@outlook.com
 *  * date : 2026/2/18 23:12
 *  */
@Serializable
data class CarInfoVersion(
    @SerialName("bus_info_version")
    val busInfoVersion: Long // bus信息版本
)
