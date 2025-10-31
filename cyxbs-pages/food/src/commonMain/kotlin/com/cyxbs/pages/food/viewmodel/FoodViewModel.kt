package com.cyxbs.pages.food.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.food.widget.DiningTag

/**
 * description ： 美食咨询处的ViewModel
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/10/30 18:29
 */

class FoodViewModel() : BaseViewModel() {
	val diningArea = mutableStateListOf(
		DiningTag("千喜鹤"),
		DiningTag("樱花"),
		DiningTag("衍生"),
		DiningTag("中心"),
		DiningTag("兴业苑"),
		DiningTag("莘莘"),
		DiningTag("滨湖"),
		DiningTag(""),
	)

	val diningNumber = mutableStateListOf(
		DiningTag("1-2人"),
		DiningTag("3-4人"),
		DiningTag("4人以上"),
		DiningTag(""),
	)

	val diningFeature = mutableStateListOf(
		DiningTag("甜品"),
		DiningTag("便宜实惠"),
		DiningTag("清爽"),
		DiningTag("没胃口"),
		DiningTag("甜品2"),
		DiningTag("便宜实3"),
		DiningTag("清1爽"),
		DiningTag("没胃2口"),
	)

	private val selectedDiningArea = mutableSetOf<String>()
	private val selectedDiningFeature = mutableSetOf<String>()
	private var selectedDiningNumber: String? = null

	var result by mutableStateOf("")


	//改变DinningArea的选中
	fun toggleDiningAreaSelect(clickTag: DiningTag) {

		val idx = diningArea.indexOfFirst { it.name == clickTag.name }
		if (idx == -1) return
		val newTag = diningArea[idx].copy(isSelected = !diningArea[idx].isSelected)
		diningArea[idx] = newTag

		if (newTag.isSelected) {
			selectedDiningArea.add(newTag.name)
		} else {
			selectedDiningArea.remove(newTag.name)
		}
		logg(selectedDiningArea)
	}

	//改变DinningFeature的选中
	fun toggleDiningFeatureSelect(clickTag: DiningTag) {
		val idx = diningFeature.indexOfFirst { it.name == clickTag.name }
		if (idx == -1) return
		val newTag = diningFeature[idx].copy(isSelected = !diningFeature[idx].isSelected)
		diningFeature[idx] = newTag

		if (newTag.isSelected) {
			selectedDiningFeature.add(newTag.name)
		} else {
			selectedDiningFeature.remove(newTag.name)
		}
		logg(selectedDiningFeature)

	}

	//改变DinningNumbers的选中
	fun toggleDiningNumberSelect(clickTag: DiningTag) {
		//拿到点击的idx
		val idx = diningNumber.indexOfFirst { it.name == clickTag.name }
		if (idx == -1) return

		val currentlySelected = selectedDiningNumber
		// 如果点击的是当前已选 -> 取消
		if (currentlySelected == clickTag.name) {
			diningNumber[idx] = diningNumber[idx].copy(isSelected = false)
			selectedDiningNumber = null

			logg(selectedDiningNumber)

			return
		}

		// 取消之前选中的项
		currentlySelected?.let { prevName ->
			val prevIdx = diningNumber.indexOfFirst { it.name == currentlySelected }
			if (prevIdx != -1) {
				diningNumber[prevIdx] = diningNumber[prevIdx].copy(isSelected = false)
			}
		}
		// 选中当前
		diningNumber[idx] = diningNumber[idx].copy(isSelected = true)
		selectedDiningNumber = clickTag.name

		logg(selectedDiningNumber)
	}

}