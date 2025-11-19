package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

@Composable
actual fun MapImageLoad(
  inputStream: ByteArray?,
  mapWidgetState: MapWidgetState,
  onMapContainerChange: (size: IntSize) -> Unit,
  onMapWidgetStateChange: (scale: Float, offset: Offset) -> Unit,
  onClick: (offset: Offset) -> Unit,
  onDoubleClick: (offset: Offset) -> Unit,
  anchorContent: @Composable () -> Unit
) {
}