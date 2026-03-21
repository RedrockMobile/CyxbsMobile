package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Stable

/**
 * description ： 地图上相机的状态
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 23:02
 */
@Stable
sealed interface CameraEvent {
	data class Focus(
		val x: Float,
		val y: Float,
		val zoom: Float
	) : CameraEvent
	object ZoomExpand : CameraEvent // 放大事件
	object ZoomOut : CameraEvent // 缩小事件
	object Positioning : CameraEvent // 回到自己的位置
	object Recover: CameraEvent
}