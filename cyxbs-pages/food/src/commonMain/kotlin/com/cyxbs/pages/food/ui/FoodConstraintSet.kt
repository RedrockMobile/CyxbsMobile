package com.cyxbs.pages.food.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension

/**
 * description ： 美食咨询处的约束集
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/10/30 19:03
 */

enum class FoodElement {
	WelcomePicture,
	DiningArea,
	DiningNumber,
	DiningProperty,
	MealResult,//随机结果
}

@Stable
class FoodConstraintSet(
	val scope: ConstraintSetScope,
	val windowSize: DpSize,
) {
	val welcomePicture = scope.createRefFor(FoodElement.WelcomePicture)
	val diningArea = scope.createRefFor(FoodElement.DiningArea)
	val diningNumber = scope.createRefFor(FoodElement.DiningNumber)
	val diningProperty = scope.createRefFor(FoodElement.DiningProperty)
	val mealResult = scope.createRefFor(FoodElement.MealResult)

	fun createConstrain() {

		//预留后续根据比例适配
		val ratio = windowSize.height / windowSize.width
		wh100vInfinity()
	}
}

//宽度较小，高度较大的手机屏幕
private fun FoodConstraintSet.wh100vInfinity() {
	with(scope) {
		constrain(welcomePicture) {
			top.linkTo(parent.top, 15.dp)
			linkTo(start = parent.start, end = parent.end, startMargin = 16.dp, endMargin = 16.dp)
			width = Dimension.fillToConstraints
			height = Dimension.wrapContent
		}

		constrain(diningArea) {
			top.linkTo(welcomePicture.bottom, 30.dp)
			linkTo(parent.start, parent.end, startMargin = 10.dp, endMargin = 10.dp)
			width = Dimension.fillToConstraints
			height = Dimension.wrapContent
		}

		constrain(diningNumber) {
			top.linkTo(diningArea.bottom, margin = 21.dp)
			linkTo(parent.start, parent.end, startMargin = 10.dp, endMargin = 10.dp)
			width = Dimension.fillToConstraints
			height = Dimension.wrapContent
		}

		constrain(diningProperty) {
			top.linkTo(diningNumber.bottom, margin = 21.dp)
			linkTo(parent.start, parent.end, startMargin = 10.dp, endMargin = 10.dp)
			width = Dimension.fillToConstraints
			height = Dimension.wrapContent
		}

		constrain(mealResult) {
			top.linkTo(diningProperty.bottom, margin = 32.dp)
			linkTo(parent.start, parent.end)
		}

	}
}