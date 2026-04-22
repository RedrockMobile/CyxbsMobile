package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
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
	val rotation: Float//旋转角度
}

/**
 * 静态的MarkerState
 */
@Stable
open class StaticMarkerState(
	override val id: Any,
	override val position: Offset,
	initialVisible: Boolean = true,
	override val rotation: Float = 0f
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
	initialVisible: Boolean = true,
	initialRotation: Float = 0f
) : MarkerState {

	private val positionAnim = Animatable(initialPosition, Offset.VectorConverter)

	private val rotationAnim = Animatable(initialRotation)


	override val rotation get() = rotationAnim.value
	override val visible = mutableStateOf(initialVisible)
	override val position get() = positionAnim.value

	val targetPosition = mutableStateOf(MarkerTargetMoveState(initialPosition, 0))
	val targetRotation = mutableStateOf(MarkerTargetRotationState(initialRotation, 0))


	fun moveToTarget(newPos: Offset, duration: Int) {
		targetPosition.value = MarkerTargetMoveState(newPos, duration)
	}

	fun rotationToTarget(newRotation: Float, duration: Int) {
		targetRotation.value = MarkerTargetRotationState(newRotation, duration)
	}

	internal suspend fun animateToTarget() {
		positionAnim.animateTo(
			targetValue = targetPosition.value.targetPosition,
			animationSpec = tween(durationMillis = targetPosition.value.duration, easing = LinearEasing)
		)
	}

	internal suspend fun animateToRotation() {
		rotationAnim.animateTo(
			targetValue = targetRotation.value.targetRotation,
			animationSpec = tween(durationMillis = targetRotation.value.duration)
		)
	}


	data class MarkerTargetMoveState(
		val targetPosition: Offset,
		val duration: Int,
	)

	data class MarkerTargetRotationState(
		val targetRotation: Float,
		val duration: Int
	)

}

