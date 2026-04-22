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
data class CarLocationJson(
	// 默认空列表，防止 JSON 返回 null 导致奔溃
	val data: List<CarLocation> = emptyList()
)
@Serializable
data class CarLocation(

	@SerialName("lat")
	var lat: Double = 0.0,// 维度

	@SerialName("lng")
	var lng: Double = 0.0, // 经度

	@SerialName("id")
	var id: Int = 0, // 唯一id

	@SerialName("type")
	var type: Int = 0, // type 为线路id

	@SerialName("update_at")
	var upDate: Long = 0L,
	@SerialName("px")
	val px: Int, // 图片的偏移x
	@SerialName("py")
	val py: Int // 图片的偏移y
)