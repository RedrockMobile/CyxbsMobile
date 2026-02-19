package com.cyxbs.pages.schoolcar.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * description ： 校车的位置信息
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 23:17
 */

@Serializable
data class CarLocation(
	// 默认空列表，防止 JSON 返回 null 导致奔溃
	val data: List<CarData> = emptyList()
)
@Serializable
data class CarData(

	@SerialName("lat")
	var lat: Double = 0.0,// 维度

	@SerialName("lng")
	var lng: Double = 0.0, // 经度

	@SerialName("id")
	var id: Int = 0, // id

	@SerialName("type")
	var type: Int = 0,

	@SerialName("update_at")
	var upDate: Long = 0L
)