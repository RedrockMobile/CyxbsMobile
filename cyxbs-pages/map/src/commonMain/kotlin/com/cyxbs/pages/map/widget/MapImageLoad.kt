package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset

/**
 * @Desc : 加载Map组件
 * @Author : zzx
 * @Date : 2025/11/11 21:18
 */

@Composable
expect fun MapImageLoad(
  inputStream: ByteArray?,
  mapWidgetState: MapWidgetState,
  onMapWidgetStateChange: (scale: Float, offset: Offset) -> Unit,
  onClick: (offset: Offset) -> Unit,
  onDoubleClick: (offset: Offset) -> Unit,
  anchorContent: @Composable () -> Unit
)