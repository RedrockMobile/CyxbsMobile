package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

@Composable
actual fun PlatformSchoolCarMapCompose(
	modifier: androidx.compose.ui.Modifier,
	markers: List<MapMarkerState>,
	cameraEventFlow: Flow<CameraEvent>,
	currentLine: Int?,
	selectSiteId: Int?,
	onEvent: (MapEvent) -> Unit
) {
}