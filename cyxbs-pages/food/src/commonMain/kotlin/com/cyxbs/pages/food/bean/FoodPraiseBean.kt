package com.cyxbs.pages.food.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * description ： 美食点赞的Bean
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/2 23:38
 */
@Serializable
data class FoodPraiseBean(
	@SerialName("introduce")
	val introduce: String,
	@SerialName("name")
	val name: String,
	@SerialName("picture")
	val picture: String,
	@SerialName("praise_is")
	val praiseIs: Boolean,
	@SerialName("praise_num")
	val praiseNum: Int
)