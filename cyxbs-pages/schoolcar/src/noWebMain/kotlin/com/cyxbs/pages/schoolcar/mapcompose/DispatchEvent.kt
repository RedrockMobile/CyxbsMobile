package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.util.fastAny
import com.jvziyaoyao.scale.zoomable.util.getMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

/**  
 * description ： 事件分发
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/21 21:57
 */

/**
 * 修改第三方库的事件分发，原第三方库没有对事件是否取消，是否被消费做判断，这里需要手动重写一下：
 * 原第三方库：在marker上滑动时，down被消费了，无法进入onGesture回调
 * 当点击marker后，onTap的未去检测事件是否被消费
 */
suspend fun PointerInputScope.detectTransformGestures(
	panZoomLock: Boolean = false,
	gestureStart: () -> Unit = {},
	gestureEnd: (Boolean) -> Unit = {},
	onTap: (Offset) -> Unit = {},
	onDoubleTap: (Offset) -> Unit = {},
	onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, event: PointerEvent) -> Boolean,
) {
	var lastReleaseTime = 0L
	var scope: CoroutineScope? = null
	awaitEachGesture {
		var rotation = 0f
		var zoom = 1f
		var pan = Offset.Zero
		var pastTouchSlop = false
		val touchSlop = viewConfiguration.touchSlop
		var lockedToPanZoom = false

		awaitFirstDown(requireUnconsumed = false)
		val t0 = getMilliseconds()
		var releasedEvent: PointerEvent? = null
		var moveCount = 0
		// 这里开始事件
		gestureStart()
		do {
			val event = awaitPointerEvent()
			if (event.type == PointerEventType.Release) releasedEvent = event
			if (event.type == PointerEventType.Move) moveCount++

			val zoomChange = event.calculateZoom()
			val rotationChange = event.calculateRotation()
			val panChange = event.calculatePan()
			if (!pastTouchSlop) {
				zoom *= zoomChange
				rotation += rotationChange
				pan += panChange

				val centroidSize = event.calculateCentroidSize(useCurrent = false)
				val zoomMotion = abs(1 - zoom) * centroidSize
				val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
				val panMotion = pan.getDistance()

				if (zoomMotion > touchSlop ||
					rotationMotion > touchSlop ||
					panMotion > touchSlop
				) {
					pastTouchSlop = true
					lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
				}
			}
			if (pastTouchSlop) {
				val centroid = event.calculateCentroid(useCurrent = false)
				val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
				if (effectiveRotation != 0f ||
					zoomChange != 1f ||
					panChange != Offset.Zero
				) {
					if (!onGesture(
							centroid,
							panChange,
							zoomChange,
							effectiveRotation,
							event
						)
					) break
				}
			}
		} while (event.changes.fastAny { it.pressed })

		var t1 = getMilliseconds()
		val dt = t1 - t0
		val dlt = t1 - lastReleaseTime
		if (moveCount == 0) releasedEvent?.let { e ->
			if (e.changes.isEmpty()) return@let
			if (e.changes.fastAny { it.isConsumed }) return@let

			val offset = e.changes.first().position
			if (dlt < 272) {
				t1 = 0L
				scope?.cancel()
				onDoubleTap(offset)
			} else if (dt < 200) {
				scope = MainScope()
				scope.launch(Dispatchers.Main) {
					delay(272)
					onTap(offset)
				}
			}
			lastReleaseTime = t1
		}

		// 这里是事件结束
		gestureEnd(moveCount != 0)
	}
}
