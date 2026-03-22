package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**  
 * description ： 地图组件的Marker的State
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/22 18:40
 */
@Stable
interface MarkerState {
	val id: Any
	val position: Offset
	val visible: MutableState<Boolean>
}

/**
 * 静态的MarkerState
 */
@Stable
open class StaticMarkerState(
	override val id: Any,
	override val position: Offset,
	initialVisible: Boolean = true
) : MarkerState {
	override val visible = mutableStateOf(initialVisible)
}

/**
 * 可移动的Marker的State
 */
@Stable
open class MovableMarkerState(
	override val id: Any,
	initialPosition: Offset,
	initialVisible: Boolean = true
) : MarkerState {
	override val visible = mutableStateOf(initialVisible)
	override val position get() = positionAnim.value

	// 目标信息
	var moveInfo by mutableStateOf(MarkerMoveInfo(initialPosition, 0))
		private set


	private val positionAnim = Animatable(initialPosition, Offset.VectorConverter)
	fun moveToTarget(newPos: Offset, duration: Int) {
		moveInfo = MarkerMoveInfo(newPos, duration)
	}

	// 平滑移动
	internal suspend fun animateToTarget() {
		positionAnim.animateTo(
			targetValue = moveInfo.targetPosition,
			animationSpec = tween(durationMillis = moveInfo.duration, easing = LinearEasing)
		)
	}

}

data class MarkerMoveInfo(val targetPosition: Offset, val duration: Int)