package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent

@Composable
actual fun MapImageLoad(
  inputStream: ByteArray?,
  mapWidgetState: MapWidgetState,
  anchorItemState: List<AnchorItemState>,
  onMapWidgetStateChange: (scale: Float, offset: Offset) -> Unit,
  onClick: (offset: Offset) -> Unit,
  onDoubleClick: (offset: Offset) -> Unit
) {
}