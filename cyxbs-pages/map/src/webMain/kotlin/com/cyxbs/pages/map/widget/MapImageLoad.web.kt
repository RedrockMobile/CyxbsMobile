package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

@Composable
actual fun MapImageLoad(
  inputStream: ByteArray?,
  mapWidgetState: MapWidgetState,
  onMapContainerChange: (IntSize) -> Unit,
  onMapWidgetStateChange: (Float, Offset) -> Unit,
  onClick: (Offset) -> Unit,
  onDoubleClick: (Offset) -> Unit,
  anchorContent: @Composable (() -> Unit)
) {
}