package com.cyxbs.pages.schoolcar.bean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * description ： 获取线路信息的Bean
 * author : HI-IR
 * email : qq2420226433@outlook.com
 *  * date : 2026/2/18 22:31
 *  */
@Serializable
data class CarLineJson(
	@SerialName("bus_info_version")
	val busInfoVersion: Long, // bus信息版本
	@SerialName("lines")
	val lines: List<CarLine> //每个线路
)



