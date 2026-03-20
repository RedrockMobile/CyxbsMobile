package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.schoolcar.utils.getImageFile
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import kotlinx.coroutines.launch

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
	val coroutineScope = rememberCoroutineScope()
	MapImageContainer(
		modifier = Modifier.fillMaxSize(),
		imageResult,
		viewModel.mapState,
		{ panDelta, zoomFactor, centroid, imageRatio ->
			// 虽然在这里启动协程在运行时会创建很多个协程出来，但是官方就这么写例子的
			coroutineScope.launch {
				viewModel.mapState.updateTransform(zoomFactor, panDelta, centroid, imageRatio)
			}
		}
	) {
		StationLayer(viewModel.stationList.value, 1)
	}
}