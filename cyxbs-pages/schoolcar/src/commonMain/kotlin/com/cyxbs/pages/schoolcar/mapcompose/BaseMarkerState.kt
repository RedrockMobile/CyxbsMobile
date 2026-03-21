package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset

/**
 * description ： 地图上的Marker State
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/21 22:18
 */
@Stable
open class BaseMarkerState(
	val id: String,// 这个会作为key来区分每个marker
	initialPosition: Offset = Offset.Zero,
	initialVisible: Boolean = true,
) {
	val position get() = positionAnim.value
	private val positionAnim = Animatable(initialPosition, Offset.VectorConverter)
	val visible = mutableStateOf(initialVisible)

	// 平滑移动
	suspend fun moveToPosition(newPosition: Offset) {
		positionAnim.animateTo(
			targetValue = newPosition,
			animationSpec = tween(1000)
		)
	}

	fun invisible(){
		visible.value = false
	}

	fun visible(){
		visible.value = false
	}
}