package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import com.cyxbs.pages.schoolcar.bean.CarLocation
import com.cyxbs.pages.schoolcar.mapcompose.CameraState
import com.cyxbs.pages.schoolcar.mapcompose.MapMarkerState
import com.cyxbs.pages.schoolcar.mapcompose.MarkerType
import com.cyxbs.pages.schoolcar.model.CarDataModel
import com.cyxbs.pages.schoolcar.model.SchoolCarRepository
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsDisplayMode
import com.cyxbs.pages.schoolcar.widget.LineSelectorItem
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_1_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_2_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_3_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_4_select
import kotlinx.coroutines.Job
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
	val carLineInfo: MutableState<CarLineJson?> = mutableStateOf(null)

	// 校车轨迹的页面状态，0表示地图页，1表示乘车指南页
	val schoolCarPage = mutableStateOf(0)

	// 定位按钮在Y上的偏移
	val offsetYRadio: MutableFloatState = mutableFloatStateOf(0F)

	//================== 关于地图的一些数据==================================
	private val _realtimeCarLocations = mutableStateListOf<CarLocation>()

	// 轮询查询校车位置的Job
	private var pollingJob: Job? = null

	val cameraState = mutableStateOf(CameraState())


	val markers = derivedStateOf {
		val list = mutableListOf<MapMarkerState>()
		val info = carLineInfo.value ?: return@derivedStateOf list

		val currentLId = selectedLineId.value
		if (currentLId == null) {
			// 没有选中线路的时候，先展示所有的站点
			val allStations = info.lines.flatMap { it.stations }.distinctBy { it.id }
			allStations.forEach { station ->
				list.add(
					MapMarkerState(
						uid = "site_${station.id}",
						type = MarkerType.Site(station.id),
						lat = station.lat,
						lng = station.lng,
						rotation = 0f
					)
				)
			}
		} else {
			// 如果有选中路线，就展示当前路线的marker
			val line = info.lines.find { it.id == currentLId }
			line?.stations?.forEach { station ->
				list.add(
					MapMarkerState(
						uid = "site_${station.id}",
						type = MarkerType.Site(station.id),
						lat = station.lat,
						lng = station.lng,
						rotation = 0f
					)
				)
			}
		}


		if (schoolCarPage.value == 0) {
			_realtimeCarLocations.forEach { car ->
				if (currentLId != null && car.type != currentLId) return@forEach
				list.add(
					MapMarkerState(
						uid = "car_${car.type}_${car.id}",
						type = MarkerType.Car(car.id, car.type),
						lat = car.lat,
						lng = car.lng,
					)
				)
			}
		}

		list
	}


	//====================== 底部bottomSheetState的相关状态====================
	val bottomSheetState by lazy {
		BottomSheetState()
	}

	val peekHeight = mutableStateOf(94.dp)

	// 底部导航栏的线路选择器的列表
	val lineSelectorItem = derivedStateOf {
		generateSelectorList(carLineInfo.value)
	}

	/**
	 * 选中的线路Id!=null + selectedStationId!= null 则为站点模式
	 * 选中的线路Id!=null + selectedStationId == null 则为线路模式
	 */
	// 选中的线路 ID
	val selectedLineId: MutableState<Int?> = mutableStateOf(null)

	// 选中的站点 ID
	val selectedStationId: MutableState<Int?> = mutableStateOf(null)

	val displayMode = derivedStateOf {
		val info = carLineInfo.value ?: return@derivedStateOf CarInfoBtsDisplayMode.ErrorOverView

		val sId = selectedStationId.value // 站点ID
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


	// 根据目前加载的CarLineJson生成选择器的源数据列表
	private fun generateSelectorList(
		info: CarLineJson?,
	): List<LineSelectorItem> {
		if (info == null) return emptyList()

		val list = mutableListOf<LineSelectorItem>()

		info.lines.forEach { line ->
			list.add(
				LineSelectorItem.LineSelectorItemLine(
					id = line.id,
					name = line.name,
					unSelectRes = getUnselectIconResource(line.id),
					selectRes = getSelectIconResource(line.id)
				)
			)
		}

		// 添加乘车指南
		list.add(LineSelectorItem.Guide)
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
			schoolCarPage.value = 1
			return
		}
		// 恢复线路模式
		selectedStationId.value = null
		selectedLineId.value = line.id
	}

	// 站点模式下切换线路
	fun toggleCurrentLine(availableLine: List<CarLine>) {
		val currentId = selectedLineId.value ?: return
		val currentIndex = availableLine.indexOfFirst { it.id == currentId }
		if (currentIndex == -1) return
		val nextIndex = (currentIndex + 1) % availableLine.size
		selectedLineId.value = availableLine[nextIndex].id
	}

	fun selectStation(stationId: Int) {
		val info = carLineInfo.value ?: return

		val availableLines = info.lines.filter { line ->
			line.stations.any { it.id == stationId }
		}

		if (availableLines.isEmpty()) return
		val currentLId = selectedLineId.value
		val isCurrentLineValid = availableLines.any { it.id == currentLId }

		// 如果当前选择的线路里面没有这个站点，我们就要切换线路了
		if (!isCurrentLineValid) {
			selectedLineId.value = availableLines.first().id
		}

		selectedStationId.value = stationId
	}


	// 从乘车指南页返回
	fun backFromDetail() {
		schoolCarPage.value = 0
	}

	// 从地图返回
	fun backFromMap() {

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
				if (isDebug()) toast("线路信息更新完毕: v${it.busInfoVersion}")
			}
			.onFailure { e ->
				if (carLineInfo.value == null) {
					toast("线路信息同步失败: ${e.message}")
				}
			}
	}
}