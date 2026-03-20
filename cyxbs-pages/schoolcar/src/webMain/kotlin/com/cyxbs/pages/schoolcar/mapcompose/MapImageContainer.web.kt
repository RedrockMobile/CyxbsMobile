package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

@Composable
actual fun MapImageContainer(
	modifier: Modifier,
	imageBytes: ByteArray?,
	mapState: MapState,
	onTransformChange: (offset: Offset, scale: Float, centroid: Offset, imageRatio: Float) -> Unit,
	markerContent: @Composable MapScope.() -> Unit
) {
}