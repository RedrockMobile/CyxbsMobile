package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.ui.geometry.Offset

/**  
 * description ： 手势的参数类
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/4/21 22:24
 */
data class GestureParams(
	val centroid: Offset,
	val panDelta: Offset,
	val zoom: Float
)