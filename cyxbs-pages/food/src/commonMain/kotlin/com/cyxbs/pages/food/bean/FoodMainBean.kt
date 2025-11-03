package com.cyxbs.pages.food.bean

import com.cyxbs.pages.food.widget.DiningTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * description ： TODO:类的作用
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/2 22:11
 */
@Serializable
data class FoodMainBean(
	@SerialName("eat_area")
	val eatArea: List<String>,
	@SerialName("eat_num")
	val eatNum: List<String>,
	@SerialName("eat_property")
	val eatProperty: List<String>,
	val picture: String
)


fun FoodMainBean.eatArea2DiningTag(): List<DiningTag> {
	return eatArea.map {
		DiningTag(it)
	}
}

fun FoodMainBean.eatNum2DiningTag(): List<DiningTag> {
	return eatNum.map {
		DiningTag(it)
	}
}

fun FoodMainBean.eatProperty2DiningTag(): List<DiningTag> {
	return eatProperty.map {
		DiningTag(it)
	}
}