package com.cyxbs.pages.food.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * description ： 随机美食的数据类
 *
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/2 23:33
 */
@Serializable
data class FoodResultBeanItem(
	@SerialName("Introduce")
	val introduce: String,
	@SerialName("FoodName")
	val name: String,
	@SerialName("Picture")
	val picture: String,
	@SerialName("PraiseIs")
	var praiseIs: Boolean,
	@SerialName("PraiseNum")
	var praiseNum: Int
)