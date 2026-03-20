package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp


/**
 * description ： Map的各种层级
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 17:22
 */

@Composable
fun MapScope.StationLayer(
	stationList: List<MapMarkerState>,
	currentSelectLine: Int?
) {
	stationList.forEach {
		key(it.id) {
			Marker(it.position, anchor = Offset(0.5f, 1.0f)) {
				StationIconCompose(it, currentSelectLine)
			}
		}
	}

	Marker(position = Offset(1024f, 1024f)) { TestRedDot("中心点") }
}

@Composable
fun TestRedDot(name: String) {
	androidx.compose.foundation.layout.Box(
		modifier = Modifier
			.size(10.dp)
			.background(
				androidx.compose.ui.graphics.Color.Red,
				androidx.compose.foundation.shape.CircleShape
			)
	)
}