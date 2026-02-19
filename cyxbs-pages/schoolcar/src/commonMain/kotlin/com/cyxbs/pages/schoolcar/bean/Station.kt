package com.cyxbs.pages.schoolcar.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**  
* description ： 站点信息
* author : HI-IR
* email : qq2420226433@outlook.com
* date : 2026/2/18 22:44 
*/
@Serializable
data class CarStation(
	@SerialName("id")
	val id: Int,//站点id
	@SerialName("lat")
	val lat: Double,//纬度
	@SerialName("lng")
	val lng: Double, //经度
	@SerialName("name")
	val name: String //站点名字
)