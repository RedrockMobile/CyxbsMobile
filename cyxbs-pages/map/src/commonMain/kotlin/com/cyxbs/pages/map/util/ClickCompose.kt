package com.cyxbs.pages.map.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * @Desc : compose组件添加点击效果
 * @Author : zzx
 * @Date : 2025/12/1 15:07
 */

fun Modifier.clickAnimation(
  onClick: () -> Unit = {  }
) = composed {
  val scale = remember { Animatable(1f) }
  val scope = rememberCoroutineScope()
  this
    .graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
    }
    .pointerInput(Unit) {
      detectTapGestures(
        onTap = {
          onClick()
        },
        onPress = {
          scope.launch {
            scale.animateTo(0.9f, spring(stiffness = Spring.StiffnessHigh))
          }
          tryAwaitRelease()
          scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        }
      )
    }
}

fun Modifier.clickCompass(
  rotation: Float = 17f,
  onClick: () -> Unit = {  }
) = composed {
  val rotationX = remember { Animatable(0f) }
  val rotationY = remember { Animatable(0f) }
  var size = remember { IntSize.Zero }

  val scope = rememberCoroutineScope()
  this
    .onSizeChanged { size = it }
    .graphicsLayer {
      this.rotationX = rotationX.value
      this.rotationY = rotationY.value
    }
    .pointerInput(Unit) {
      detectTapGestures(
        onTap = {
          onClick()
        },
        onPress = { offset ->
          if (size == IntSize.Zero) return@detectTapGestures
          val centerX = size.width / 2f
          val centerY = size.height / 2f
          val dx = offset.x - centerX
          val dy = offset.y - centerY
          scope.launch {
            if (abs(dx) > abs(dy)) {
              // 南北，Y轴旋转
              rotationY.animateTo(rotation * sign(dx), spring(stiffness = Spring.StiffnessHigh))
            } else {
              // 东西，X轴旋转，这里rotationX是正的上面往里倒，负的下面往里到，这里需要反一下
              rotationX.animateTo(rotation * -sign(dy), spring(stiffness = Spring.StiffnessHigh))
            }
          }
          tryAwaitRelease()
          scope.launch {
            rotationX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
          }
          scope.launch {
            rotationY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
          }
        }
      )
    }
}