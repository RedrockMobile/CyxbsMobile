package com.cyxbs.pages.food.bean

import com.cyxbs.pages.food.widget.DiningTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * description ： 首次进入时加载的数据类
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
  @SerialName("picture")
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