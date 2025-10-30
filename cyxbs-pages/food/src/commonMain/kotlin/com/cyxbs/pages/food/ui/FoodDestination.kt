package com.cyxbs.pages.food.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainDestination
import com.cyxbs.pages.food.api.FoodArgument
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * description ： 美食咨询处界面
 * author : HI-IR
 * email : qq2420226433@outlook.comx`
 * date : 2025/10/29 23:58
 */
@ImplProvider(clazz = MainDestination::class, name = "food")
class FoodDestination : MainDestination<FoodArgument>(FoodArgument::class) {
	@Composable
	override fun DestinationContent(parcel: DestinationParcel<FoodArgument>) {
		Text("欢迎来到美食咨询处")
	}
}