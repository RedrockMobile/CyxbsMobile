package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * description ： 地图的主体
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 23:07
 */

/*
 * 因为高德地图没有针对kmp适配，所以这里需要分不同的平台去实现
 */
@Composable
expect fun PlatformSchoolCarMapCompose(
	modifier: Modifier = Modifier,
	markers: List<MapMarkerState>,
	cameraState: CameraState,
	currentLine: Int?,
	onEvent: (MapEvent) -> Unit
)