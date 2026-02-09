package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * @Desc : 加载Map组件
 * @Author : zzx
 * @Date : 2025/11/11 21:18
 */

/**
 * 地图大图加载的主要组件
 * @param inputStream 图片的ByteArray对象
 * @param mapWidgetState 地图状态的统一管理处
 * @param onMapWidgetStateChange 地图状态改变
 * @param onClick 点击回调
 * @param onDoubleClick 双击回调
 * @param anchorContent 锚点信息的Composable
 */
@Composable
expect fun MapImageLoad(
  inputStream: ByteArray?,
  mapWidgetState: MapWidgetState,
  onMapContainerChange: (size: IntSize) -> Unit,
  onMapWidgetStateChange: (scale: Float, offset: Offset) -> Unit,
  onClick: (offset: Offset) -> Unit,
  onDoubleClick: (offset: Offset) -> Unit,
  anchorContent: @Composable () -> Unit
)