package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.Flow

@Composable
actual fun MapImageContainer(
	modifier: Modifier,
	imageBytes: ByteArray?,
	mapState: MapState,
	cameraEventFlow: Flow<CameraEvent>,
	onMapEvent: (MapEvent) -> Unit,
	markerContent: @Composable MapScope.() -> Unit
) {
}