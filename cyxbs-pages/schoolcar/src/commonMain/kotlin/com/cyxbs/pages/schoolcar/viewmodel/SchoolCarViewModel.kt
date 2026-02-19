package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import com.cyxbs.pages.schoolcar.model.CarDataModel
import com.cyxbs.pages.schoolcar.model.SchoolCarRepository
import com.cyxbs.pages.schoolcar.ui.SchoolCarDetailNavArgument
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsDisplayMode
import com.cyxbs.pages.schoolcar.widget.LineSelectorItem
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_0
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_1_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_2_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_3_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_4_select
import org.jetbrains.compose.resources.DrawableResource

/**
 * description ： 校车查询页的ViewModel
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 21:21
 */
class SchoolCarViewModel : BaseViewModel() {
	companion object {
		const val NETWORK_ERROR_INFO = "服务君似乎打盹了呢"
	}

	private val model by lazy {
		SchoolCarRepository
	}

	// 数据源
	private val carLineInfo: MutableState<CarLineJson?> = mutableStateOf(null)


	//====================== 底部bottomSheetState的相关状态====================
	val bottomSheetState = BottomSheetState(hideable = false)

	// 底部导航栏的线路选择器
	val lineSelectorItem = derivedStateOf {
		generateSelectorList(carLineInfo.value)
	}

	// 选中的线路 ID
	val selectedLineId: MutableState<Int?> = mutableStateOf(null)

	// 选中的站点 ID
	val selectedStationId: MutableState<Int?> = mutableStateOf(null)


	// 当前选中的线路
	val currentSelectLine = derivedStateOf {
		lineSelectorItem.value.firstOrNull { it.id == selectedLineId.value }
	}

	val displayMode = derivedStateOf {
		val info = carLineInfo.value ?: return@derivedStateOf CarInfoBtsDisplayMode.Empty

		val sId = selectedStationId.value // 站点ID
		val lId = selectedLineId.value    // 线路ID

		if (sId != null) {
			val station = info.lines.flatMap { it.stations }.find { it.id == sId }

			//如果当前选中的线路有这个站点就优先用选中line，否则就找到最近的一条
			val contextLine = info.lines.find { it.id == lId }
				?: info.lines.find { line -> line.stations.any { it.id == sId } }

			if (station != null && contextLine != null) {
				// 计算所有经过此站点的线路
				val availableLines = info.lines.filter { line ->
					line.stations.any { it.id == sId }
				}

				return@derivedStateOf CarInfoBtsDisplayMode.SiteOverView(
					site = station,
					currentLine = contextLine,
					availableLines = availableLines
				)
			}
		}

		if (lId != null) {
			val line = info.lines.find { it.id == lId }
			if (line != null) {
				return@derivedStateOf CarInfoBtsDisplayMode.LineOverview(line)
			}
		}
		//用一号线兜底
		CarInfoBtsDisplayMode.LineOverview(info.lines.first())
	}


	// 根据目前加载的CarLineJson生成选择器的源数据列表
	private fun generateSelectorList(
		info: CarLineJson?,
	): List<LineSelectorItem> {
		if (info == null) return emptyList()

		val list = mutableListOf<LineSelectorItem>()

		info.lines.forEach { line ->
			list.add(
				LineSelectorItem(
					id = line.id,
					name = line.name,
					unSelectRes = getUnselectIconResource(line.id),
					selectRes = getSelectIconResource(line.id)
				)
			)
		}

		// 添加乘车指南
		val guideId = -1
		list.add(
			LineSelectorItem(
				id = guideId,
				name = "乘车指南",
				unSelectRes = Res.drawable.schoolcar_ic_car_icon_0,
				selectRes = Res.drawable.schoolcar_ic_car_icon_0
			)
		)
		return list
	}

	init {
		initCarLineInfo()
	}

	// 初始化校车信息
	fun initCarLineInfo() {
		launchByViewModelScope {
			val localInfo = CarDataModel.getCarLine()
			if (localInfo != null) {
				carLineInfo.value = localInfo
				selectedLineId.value = localInfo.lines.first().id
			}
			val localVersion = localInfo?.busInfoVersion ?: -1

			model.getCarInfoVersion()
				.onSuccess { netVersion ->
					// 如果版本不同 或者 本地没有版本就去加载新版本
					if (netVersion != localVersion || localInfo == null) {
						fetchAndSaveCarLineInfo()
					}
				}
				.onFailure {
					log("HIIR", it.message.toString())
					if (carLineInfo.value == null) {
						toast(NETWORK_ERROR_INFO)
					} else {
						toast("网络获取失败，使用本地数据兜底")
					}
				}
		}
	}

	// 切换底部表单的选中项
	fun toggleSelectLine(line: LineSelectorItem) {
		if (line.id == -1) {
			MainNavController.navigate(SchoolCarDetailNavArgument)
			return
		}
		selectedLineId.value = line.id
	}

	fun toggleCurrentLine(availableLine: List<CarLine>) {
		selectedLineId.value = (selectedLineId.value!! + 1) / availableLine.size
	}


	private fun getUnselectIconResource(id: Int): DrawableResource {
		// id=0 对应 一号线
		return when (id + 1) {
			1 -> Res.drawable.schoolcar_ic_car_icon_1
			2 -> Res.drawable.schoolcar_ic_car_icon_2
			3 -> Res.drawable.schoolcar_ic_car_icon_3
			4 -> Res.drawable.schoolcar_ic_car_icon_4
			else -> Res.drawable.schoolcar_ic_car_icon_1
		}
	}

	private fun getSelectIconResource(id: Int): DrawableResource {
		// id=0 对应 一号线
		return when (id + 1) {
			1 -> Res.drawable.schoolcar_ic_car_icon_1_select
			2 -> Res.drawable.schoolcar_ic_car_icon_2_select
			3 -> Res.drawable.schoolcar_ic_car_icon_3_select
			4 -> Res.drawable.schoolcar_ic_car_icon_4_select
			else -> Res.drawable.schoolcar_ic_car_icon_1_select
		}
	}


	/**
	 * 加载、保存网络的线路数据
	 */
	private suspend fun fetchAndSaveCarLineInfo() {
		model.getCarLineInfo()
			.onSuccess {
				CarDataModel.saveCarLineInfo(it)
				carLineInfo.value = it
				selectedLineId.value = it.lines.first().id
				if (isDebug()) toast("线路信息更新完毕: v${it.busInfoVersion}")
			}
			.onFailure { e ->
				if (carLineInfo.value == null) {
					toast("线路信息同步失败: ${e.message}")
				}
			}
	}
}