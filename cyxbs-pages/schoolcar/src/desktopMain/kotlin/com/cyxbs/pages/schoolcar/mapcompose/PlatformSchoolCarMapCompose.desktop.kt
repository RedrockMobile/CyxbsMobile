package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.ui.Modifier

@androidx.compose.runtime.Composable
actual fun PlatformSchoolCarMapCompose(
	modifier: Modifier,
	markers: List<MapMarkerState>,
	cameraState: CameraState,
	currentLine: Int?,
	selectSiteId: Int?,
	onEvent: (MapEvent) -> Unit
) {
}