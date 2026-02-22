package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformSchoolCarMapCompose(
	modifier: androidx.compose.ui.Modifier,
	markers: List<MapMarkerState>,
	cameraState: CameraState,
	currentLine: Int?,
	selectSiteId: Int?,
	onEvent: (MapEvent) -> Unit
) {
}