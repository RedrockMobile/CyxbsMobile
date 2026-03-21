package com.cyxbs.pages.schoolcar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import com.cyxbs.pages.schoolcar.mapcompose.MapEvent
import com.cyxbs.pages.schoolcar.mapcompose.MapScope


/**
 * description ： Map的各种渲染层
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 17:22
 */

// 车站层
@Composable
fun MapScope.StationLayer(
	stationList: List<StationMarkerState>,
	selectedStationId: String?,
	currentSelectLine: Int?,
	onMapEvent: (MapEvent) -> Unit
) {
	stationList.forEach { markerState ->
		key(markerState.id) {
			Marker(markerState.position, anchor = Offset(0.5f, 1.0f), onMarkerClick = {
				onMapEvent(MapEvent.MarkerClick(markerState))
			}) {
				StationIconCompose(markerState, currentSelectLine, selectedStationId == markerState.id)
			}
		}
	}
}