package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**  
 * description ： 校车查询的MapCompose组件
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 14:20
 */

/**
 * 地图组件的 地图图片 层
 */
@Composable
expect fun MapImageContainer(
	modifier: Modifier,
	imageBytes: ByteArray?,
	mapState: MapState,
	onTransformChange: (offset: Offset, scale: Float, centroid: Offset,imageRatio: Float) -> Unit,
	markerContent: @Composable MapScope.() -> Unit
)
