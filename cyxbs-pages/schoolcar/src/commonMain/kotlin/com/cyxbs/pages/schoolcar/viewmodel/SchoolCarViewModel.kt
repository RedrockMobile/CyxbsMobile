package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.isDebug
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import com.cyxbs.pages.schoolcar.bean.CarLocation
import com.cyxbs.pages.schoolcar.bean.CarStation
import com.cyxbs.pages.schoolcar.mapcompose.BaseMarkerState
import com.cyxbs.pages.schoolcar.mapcompose.CameraEvent
import com.cyxbs.pages.schoolcar.mapcompose.MapEvent
import com.cyxbs.pages.schoolcar.mapcompose.MapState
import com.cyxbs.pages.schoolcar.model.CarDataModel
import com.cyxbs.pages.schoolcar.model.SchoolCarRepository
import com.cyxbs.pages.schoolcar.ui.StationMarkerState
import com.cyxbs.pages.schoolcar.utils.downloadMapImage
import com.cyxbs.pages.schoolcar.utils.isFileExist
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsState
import com.cyxbs.pages.schoolcar.widget.LineSelectorItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * description ： 校车查询页的ViewModel
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 21:21
 */
expect class SchoolCarViewModel() : CommonSchoolCarViewModel {
	// 返回null表示没有最近的站点
	override fun getClosedSite(): CarStation?
}

abstract class CommonSchoolCarViewModel : BaseViewModel() {
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


	// bts的State
	val btsState = CarInfoBtsState(100.dp, carLineInfo)

	// downloadingDialog
	val downProgressDialogState = mutableStateOf(!isFileExist())
	val downProgress = mutableStateOf(0f)

	// map的state
	val mapState = MapState()


	//================== 关于地图的一些数据==================================
	// 轮询查询校车位置的Job
	private var carLocationJob: Job? = null
	private val _realtimeCarLocations = mutableStateOf<List<CarLocation>>(emptyList())

	// 站点数据
	private val allStationMarkers = derivedStateOf {
		// stationId -> setOf(lineId)
		val stationMap = mutableMapOf<Int, MutableSet<Int>>()
		carLineInfo.value?.let { json ->
			// 组装stationMap
			json.lines.forEach { line ->
				line.stations.forEach { station ->
					stationMap.getOrPut(station.id) { mutableSetOf() }.add(line.id)
				}
			}
			json.lines.flatMap {
				it.stations
			}.distinctBy { it.id }.map {
				StationMarkerState(
					id = "station_${it.id}",
					name = it.name,
					lineIds = stationMap[it.id]?.toSet() ?: emptySet(),
					position = Offset(it.px.toFloat(), it.py.toFloat()),
					visible = true
				)
			}
		} ?: emptyList()

	}


	// 摄像头数据流
	private val cameraEventFlowInternal =
		Channel<CameraEvent>(Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	val cameraEventFlow = cameraEventFlowInternal.receiveAsFlow()


	// 显示站点
	val displayStations = derivedStateOf {
		val selectedLineId =
			btsState.selectedLineId.value ?: return@derivedStateOf allStationMarkers.value

		allStationMarkers.value.filter {
			it.lineIds.contains(selectedLineId)
		}
	}

	init {
		initCarLineInfo()
		checkAndDownloadMap()
		startPollingLocation()
	}

	// 初始化校车信息
	private fun initCarLineInfo() {
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
					if (carLineInfo.value == null) {
						toast(NETWORK_ERROR_INFO)
					} else {
						toast("网络获取失败，使用本地数据兜底")
					}
				}
		}
	}

	private fun checkAndDownloadMap() {
		if (isFileExist()) {
			closeDownLoadProgressDialog()
			return
		}
		launchByViewModelScope {
			model.getMapConfig().onSuccess { mapStatic ->
				openDownLoadProgressDialog()
				try {
					downloadMapImage(mapStatic.mapUrl) { current, total ->
						downProgress.value = if (total > 0) current.toFloat() / total.toFloat() else 0f
					}
					closeDownLoadProgressDialog()
				} catch (_: Exception) {
					closeDownLoadProgressDialog()
					toast("地图下载失败，请检查网络")
				}
			}.onFailure {
				toast("无法获取地图配置")
			}
		}
	}


	// 切换底部表单的选中项
	fun toggleSelectLine(line: LineSelectorItem) {
		if (line.id == -1) {
			schoolCarPage.value = 1
			stopPollingLocation()
			return
		}
		if (line.id == btsState.selectedLineId.value) {
			changeToEmptyMode()
			return
		}
		// 恢复线路模式
		changeToLineMode(line.id)
	}

	// 站点模式下切换线路
	fun toggleCurrentLine(availableLine: List<CarLine>) {
		val currentId = btsState.selectedLineId.value ?: return
		val currentIndex = availableLine.indexOfFirst { it.id == currentId }
		if (currentIndex == -1) return
		val nextIndex = (currentIndex + 1) % availableLine.size
		btsState.selectedLineId.value = availableLine[nextIndex].id
	}

	fun selectSite(stationId: Int) {
		val info = carLineInfo.value ?: return
		val availableLines = info.lines.filter { line ->
			line.stations.any { it.id == stationId }
		}
		if (availableLines.isEmpty()) return

		val currentLId = btsState.selectedLineId.value
		val isCurrentLineValid = availableLines.any { it.id == currentLId }

		val targetLineId = if (!isCurrentLineValid) availableLines.first().id else currentLId

		btsState.selectedLineId.value = targetLineId
		btsState.selectedStationId.value = stationId
	}


	// 从乘车指南页返回
	fun backFromDetail() {
		schoolCarPage.value = 0
		startPollingLocation()
	}

	/**
	 * 处理地图事件
	 */
	fun handleMapEvent(event: MapEvent) {
		when (event) {
			is MapEvent.MapClick -> {
				changeToEmptyMode()
			}

			is MapEvent.MarkerClick -> {
				handleMarkerClick(event.marker)
			}
		}
	}

	private fun handleMarkerClick(marker: BaseMarkerState) {
		when (marker) {
			is StationMarkerState -> {
				changeToSiteMode(
					StationMarkerState.getStationIdByString(marker.id)!!,
					offset = marker.position
				)
			}
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

	fun zoomExpand() {
		cameraEventFlowInternal.trySend(CameraEvent.ZoomExpand)
	}

	fun zoomOut() {
		cameraEventFlowInternal.trySend(CameraEvent.ZoomOut)
	}

	fun focusOnPoint(offset: Offset) {
		cameraEventFlowInternal.trySend(CameraEvent.Focus(offset.x, offset.y, 6f))
	}

	fun positioning() {
		cameraEventFlowInternal.trySend(CameraEvent.Positioning)
	}


	private fun cameraRecover() {
		cameraEventFlowInternal.trySend(CameraEvent.Recover)
	}

	// 对显示模式切换的封装，用这来控制显示模式的切换，以防状态错误
	private fun changeToLineMode(lineId: Int) {
		btsState.changeToLineMode(lineId)
		cameraRecover()
	}

	private fun changeToSiteMode(siteId: Int, offset: Offset) {
		selectSite(siteId)
		focusOnPoint(offset)
	}

	private fun changeToEmptyMode() {
		btsState.changeToEmptyMode()
		cameraRecover()
	}

	private fun startPollingLocation(interval: Long = 2000, reLocation: Long = 5000) {
		carLocationJob?.cancel()

		carLocationJob = launchByViewModelScope {
			while (true) {
				ensureActive()
				val result = model.getCarLocation().getOrNull()

				if (result != null) {
					_realtimeCarLocations.value = result.data
					delay(interval)
				} else {
					delay(reLocation)
				}
			}
		}
	}

	fun stopPollingLocation() {
		carLocationJob?.cancel()
		carLocationJob = null
	}

	fun selectClosedSite() {
		val closedSite = getClosedSite() ?: return
		changeToSiteMode(closedSite.id, Offset(closedSite.px.toFloat(), closedSite.py.toFloat()))
	}

	fun openDownLoadProgressDialog() {
		downProgressDialogState.value = true
	}

	fun closeDownLoadProgressDialog() {
		downProgressDialogState.value = false
	}

	abstract fun getClosedSite(): CarStation?
}