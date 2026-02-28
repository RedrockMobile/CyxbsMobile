package com.cyxbs.pages.schoolcar.widget

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
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
 * description ： CarInfoBts的状态
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/28 23:36
 */
@Stable
class CarInfoBtsState(val peekHeight: Dp, private val carLineInfo: State<CarLineJson?>) {
	var isStateChanging = mutableStateOf(false)
	val bottomSheetState by lazy {
		BottomSheetState()
	}

	// 底部导航栏的线路选择器的列表
	val lineSelectorItem = derivedStateOf {
		generateSelectorList(carLineInfo.value)
	}

	/**
	 * 选中的线路Id!=null + selectedSiteId!= null 则为站点模式
	 * 选中的线路Id!=null + selectedSiteId == null 则为线路模式
	 * 关于模式的切换后面给了3个方法
	 */
	// 选中的线路 ID
	val selectedLineId: MutableState<Int?> = mutableStateOf(null)

	// 选中的站点 ID
	val selectedSiteId: MutableState<Int?> = mutableStateOf(null)


	val displayMode = derivedStateOf {
		val info = carLineInfo.value ?: return@derivedStateOf CarInfoBtsDisplayMode.ErrorOverView

		val sId = selectedSiteId.value // 站点ID
		val lId = selectedLineId.value    // 线路ID

		if (sId != null) {
			val station = info.lines.flatMap { it.stations }.find { it.id == sId }

			//如果当前选中的线路有这个站点就优先用选中line，否则就找到有该站点line的一条
			val contextLine = info.lines.find { it.id == lId }

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

		CarInfoBtsDisplayMode.Empty
	}

	fun changeToEmptyMode() {
		selectedLineId.value = null
		selectedSiteId.value = null
	}

	fun changeToLineMode(lineId: Int) {
		selectedLineId.value = lineId
		selectedSiteId.value = null
	}

	// 根据目前加载的CarLineJson生成选择器的源数据列表
	private fun generateSelectorList(
		info: CarLineJson?,
	): List<LineSelectorItem> {
		if (info == null) return emptyList()
		return buildList {
			info.lines.forEach { line ->
				add(
					LineSelectorItem.LineSelectorItemLine(
						id = line.id,
						name = line.name,
						unSelectRes = getUnselectIconResource(line.id),
						selectRes = getSelectIconResource(line.id)
					)
				)
			}
			add(LineSelectorItem.Guide)
		}
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

}