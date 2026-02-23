package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Stable

/**
 * description ： 地图上相机的状态
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 23:02
 */
// 这个object类是用来初始化摄像头位置的，后续的摄像头事件应该用channel来发送事件
@Stable
object  CameraStateDefault{
	const val lat: Double = 29.530040
	const val lng: Double = 106.606789
	const val zoom: Float = 16.05f
}


@Stable
sealed interface CameraEvent {
	data class Focus(
		val lat: Double = 29.530040,
		val lng: Double = 106.606789,
		val zoom: Float = 16.05f
	) : CameraEvent

	object ZoomExpand : CameraEvent // 放大事件
	object ZoomOut : CameraEvent // 缩小事件
}