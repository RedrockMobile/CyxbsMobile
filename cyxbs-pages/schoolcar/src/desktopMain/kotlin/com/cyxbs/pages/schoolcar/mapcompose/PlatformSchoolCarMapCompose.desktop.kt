package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

@androidx.compose.runtime.Composable
actual fun PlatformSchoolCarMapCompose(
	modifier: Modifier,
	markers: List<MapMarkerState>,
	cameraEventFlow: Flow<CameraEvent>,
	currentLine: Int?,
	selectSiteId: Int?,
	onEvent: (MapEvent) -> Unit
) {
}