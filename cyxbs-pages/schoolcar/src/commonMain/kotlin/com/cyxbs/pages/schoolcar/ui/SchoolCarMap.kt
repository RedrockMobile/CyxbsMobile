package com.cyxbs.pages.schoolcar.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.schoolcar.mapcompose.MapImageContainer
import com.cyxbs.pages.schoolcar.utils.getImageFile
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel

/**  
 * description ： 校车轨轨迹的地图组件
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 21:45
 */

@Composable
fun SchoolCarMapCompose() {
	val viewModel = viewModel(SchoolCarViewModel::class)
	val imageResult by produceState<ByteArray?>(null, viewModel.downProgressDialogState.value) {
		value = getImageFile()
	}
	MapImageContainer(
		modifier = Modifier.fillMaxSize(),
		imageBytes = imageResult,
		mapState = viewModel.mapState,
		cameraEventFlow = viewModel.cameraEventFlow,
		onMapEvent = viewModel::handleMapEvent
	) {
		val selectStationId =
			remember(viewModel.btsState.selectedStationId.value) { viewModel.btsState.selectedStationId.value?.let { "station_${it}" } }
		StationLayer(
			viewModel.displayStations.value,
			selectStationId,
			viewModel.btsState.selectedLineId.value,
			viewModel::handleMapEvent)
	}
}