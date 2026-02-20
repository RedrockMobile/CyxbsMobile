package com.cyxbs.pages.schoolcar.bean

import androidx.compose.runtime.Stable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**  
* description ： TODO:类的作用
* author : HI-IR
* email : qq2420226433@outlook.com
* date : 2026/2/18 22:44 
*/
@Stable
@Serializable
data class CarLine(
	@SerialName("id")
	val id: Int, // 线路id
	@SerialName("name")
	val name: String, //线路名字
	@SerialName("run_time")
	val runTime: String, // 线路运行时间
	@SerialName("run_type")
	val runType: String, // 线路运行方式 往返
	@SerialName("send_type")
	val sendType: String, // 线路发车方式(双向发车)
	@SerialName("stations")
	val stations: List<CarStation> //站点
)