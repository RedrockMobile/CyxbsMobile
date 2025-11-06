package com.cyxbs.pages.food.bean

import com.cyxbs.pages.food.widget.DiningTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * description ： 刷新餐饮特征Bean
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/2 23:42
 */
@Serializable
data class FoodRefreshBean(
	@SerialName("eat_property")
	val eatProperty: List<String>
)

fun FoodRefreshBean.eatProperty2DiningTag(): List<DiningTag> {
	return eatProperty.map {
		DiningTag(it)
	}
}