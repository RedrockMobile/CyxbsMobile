package com.cyxbs.pages.food.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.pages.food.bean.FoodResultBeanItem
import com.cyxbs.pages.food.bean.eatArea2DiningTag
import com.cyxbs.pages.food.bean.eatNum2DiningTag
import com.cyxbs.pages.food.bean.eatProperty2DiningTag
import com.cyxbs.pages.food.model.FoodRepository
import com.cyxbs.pages.food.widget.DiningTag
import kotlinx.coroutines.launch

/**
 * description ： 美食咨询处的ViewModel
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/10/30 18:29
 */

class FoodViewModel() : BaseViewModel() {
	private val model by lazy {
		FoodRepository
	}

	init {
		requestFoodMain()
	}

	val diningArea = mutableStateListOf<DiningTag>()

	val diningNumber = mutableStateListOf<DiningTag>()

	val diningProperty = mutableStateListOf<DiningTag>()

	/**
	 * 	是否显示描述页
	 * 	没招了ChooseDialogCompose需要传入一个State
	 */

	val showDescribe = mutableStateOf(false)

	fun openDescribe() {
		showDescribe.value = true
	}

	fun closeDescribe() {
		showDescribe.value = false
	}

	/**
	 * 显示温馨提示(尝试选择更多关键词)
	 */
	val showWarn = mutableStateOf(false)

	fun openWarn() {
		showWarn.value = true
	}

	fun closeWarn() {
		showWarn.value = false
	}

	/**
	 * 提示选择更多标签的Dialog
	 */
	val showTips = mutableStateOf(false)
	fun openTips() {
		showTips.value = true
	}

	fun closeTips() {
		showTips.value = false
	}

	var showDetail = mutableStateOf(false)

	fun openDetail() {
		if (current.value == null) {
			return
		}
		showDetail.value = true
	}

	fun closeDetail() {
		showDetail.value = false
	}

	private val selectedDiningArea = mutableSetOf<String>()
	private val selectedDiningProperty = mutableSetOf<String>()
	private var selectedDiningNumber: String? = null


	// 是否需要重新请求网络
	private var needRequest = false

	// 标记是否初始化完成
	private var initialized = false

	/**
	 * 网络获取的结果列表
	 */
	val resultList = mutableStateListOf<FoodResultBeanItem>()


	// 当前显示的索引
	val currentIndex = mutableStateOf(0)

	// 当前显示的 item
	val current = derivedStateOf {
		resultList.getOrNull(currentIndex.value)
	}

	//设置数据
	fun setData(newList: List<FoodResultBeanItem>) {
		resultList.clear()
		resultList.addAll(newList)
		currentIndex.value = 0
	}


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
		if (initialized) {
			needRequest = true
		}
	}

	//改变DinningProperty的选中
	fun toggleDiningPropertySelect(clickTag: DiningTag) {
		val idx = diningProperty.indexOfFirst { it.name == clickTag.name }
		if (idx == -1) return
		val newTag = diningProperty[idx].copy(isSelected = !diningProperty[idx].isSelected)
		diningProperty[idx] = newTag

		if (newTag.isSelected) {
			selectedDiningProperty.add(newTag.name)
		} else {
			selectedDiningProperty.remove(newTag.name)
		}
		if (initialized) {
			needRequest = true
		}
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

			if (initialized) {
				needRequest = true
			}
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

		if (initialized) {
			needRequest = true
		}
	}

	/**
	 * 随机生成美食
	 */
	fun doRandomGenerated() {
		//先检查选择项
		if (!checkTagSelected()) {
			openTips()
			return
		}
		viewModelScope.launch {
			val requestResult = model.requestRandomGenerate(
				eatArea = selectedDiningArea.toList(),
				eatNumber = selectedDiningNumber!!,
				eatProperty = selectedDiningProperty.toList()
			)
			val data = requestResult.getOrElse { throwable ->
				if (throwable is ApiException && throwable.status == 10100) {
					openWarn()
				} else {
					toast("网络错误")
				}
				//已经处理完异常了，还是返回一个null方便下面逻辑
				null
			}
			data?.let {
				setData(it)
			}
			//如果是第一次，则完成初始化
			if (!initialized) {
				initialized = true
			}
		}
	}

	/**
	 * 换一换
	 */
	fun doChange() {
		if (needRequest) {
			// 用户修改了Tag，需要重新请求网络
			doRandomGenerated()
			needRequest = false //重置
			return
		}

		//检查是否还有下一条
		if (currentIndex.value < resultList.size - 1) {
			currentIndex.value += 1
		} else {
			// 已经走完了，提示
			openWarn()
		}
	}

	/**
	 * 检查标签选择是否合规
	 * @return 选择是否合规
	 */
	private fun checkTagSelected(): Boolean {

		val diningNumberVerified = selectedDiningNumber != null

		val diningAreaVerified = selectedDiningArea.isNotEmpty()

		val diningPropertyVerified = selectedDiningProperty.isNotEmpty()

		return diningAreaVerified && diningPropertyVerified && diningNumberVerified
	}


	//首次进入时加载
	private fun requestFoodMain() {
		viewModelScope.launch {
			val result = model.requestFoodMain()
			val data = result.getOrNull()
			if (data == null) {
				toast("网络错误")
				return@launch
			}
			diningNumber.addAll(data.eatNum2DiningTag())
			diningProperty.addAll(data.eatProperty2DiningTag())
			diningArea.addAll(data.eatArea2DiningTag())
		}
	}

	fun doPraiseFood() {
		val currentFood = current.value
		if (currentFood == null) {
			return
		}
		viewModelScope.launch {
			val result = model.doPraiseFood(currentFood.name)
			val data = result.getOrNull()
			if (data == null) {
				toast("网络错误")
				return@launch
			}

			//触发重组
			resultList[currentIndex.value] = currentFood.copy(
				praiseIs = data.praiseIs, praiseNum = data.praiseNum
			)
		}
	}

	fun refreshFoodProperty() {
		viewModelScope.launch {
			val result = model.refreshProperty(selectedDiningArea.toList(), selectedDiningNumber ?: "")
			val data = result.getOrNull()
			if (data == null) {
				toast("网络错误")
				return@launch
			}

			//后端给的数据有可能重复，这里去重一下
			val distinctData = data.eatProperty2DiningTag().distinctBy { it.name }
			selectedDiningProperty.clear()
			diningProperty.clear()
			diningProperty.addAll(distinctData)


		}
	}

}