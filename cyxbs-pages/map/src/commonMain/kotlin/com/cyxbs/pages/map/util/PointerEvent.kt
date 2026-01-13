package com.cyxbs.pages.map.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Clock

/**
 * @Desc : 对三方库的手势重写
 * @Author : zzx
 * @Date : 2025/11/25 17:11
 */

/**
 * 用于地图的手势检测方法
 * 三方库的没有检测当前手势是否被消费而直接开始了事件
 * 而为了检测多种手势正常开启了requireUnconsumed = false，从而如果点击anchor时也会触发地图底层的点击
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

    val down = awaitFirstDown(requireUnconsumed = false)
    // 这里进行了判断，如果子布局消费了则直接返回
    if (down.isConsumed) {
      return@awaitEachGesture
    }
    val t0 = getMilliseconds()
    var releasedEvent: PointerEvent? = null
    var moveCount = 0
    // 这里开始事件
    gestureStart()
    do {
      val event = awaitPointerEvent()
      if (event.type == PointerEventType.Release) releasedEvent = event
      if (event.type == PointerEventType.Move) moveCount++
      val canceled = event.changes.fastAny { it.isConsumed }
      if (!canceled) {
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
      }
    } while (!canceled && event.changes.fastAny { it.pressed })

    var t1 = getMilliseconds()
    val dt = t1 - t0
    val dlt = t1 - lastReleaseTime

    if (moveCount == 0) releasedEvent?.let { e ->
      if (e.changes.isEmpty()) return@let
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

fun getMilliseconds(): Long {
  return Clock.System.now().toEpochMilliseconds()
}

/**
 * awaitFirstDown去掉了滚轮事件
 * 这里需要单独判断一下，否则桌面端无法实现放缩
 */
suspend fun PointerInputScope.detectScrollZoomGestures(
  zoomMultiplier: Float = 1.05f,
  onZoom: (centroid: Offset, zoom: Float) -> Unit
) {
  awaitEachGesture {
    while (true) {
      val event = awaitPointerEvent()
      if (event.type == PointerEventType.Scroll) {
        val change = event.changes.firstOrNull() ?: continue
        // 如果Ctrl没有被按下则返回
        if (!event.keyboardModifiers.isCtrlPressed) continue

        val scrollDelta = change.scrollDelta
        if (scrollDelta.y == 0f) continue
        val position = change.position
        // 负值向上滚动放大
        val zoom = zoomMultiplier.pow(-scrollDelta.y)
        onZoom(position, zoom)

        change.consume()
      }
    }
  }
}