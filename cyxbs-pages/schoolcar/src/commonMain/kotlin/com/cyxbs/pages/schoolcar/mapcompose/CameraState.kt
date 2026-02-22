package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.runtime.Stable

/**
 * description ： 地图上相机的状态
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 23:02
 */
@Stable
data class CameraState(
	val lat: Double = 29.531876,
	val lng: Double = 106.606789,
	val zoom: Float = 17f
)