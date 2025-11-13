package com.cyxbs.pages.map.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * @Desc : 地图组件的状态
 * @Author : zzx
 * @Date : 2025/11/13 11:02
 */

class MapWidgetState(
  initialScale: Float = 1f,
  initialOffset: Offset = Offset.Zero,
  initialContainer: IntSize = IntSize.Zero
) {
  // 盒子尺寸以及中心坐标
  var container: IntSize by mutableStateOf(initialContainer)
  val center: Offset get() = Offset(container.width / 2f, container.height / 2f)

  // 暴露给外部的scale
  val scale: Float get() = scaleAnim.value

  // 暴露给外部的offset
  val offset: Offset get() = offsetAnim.value

  // Animatable动画数值包装器
  private val scaleAnim = Animatable(initialScale)
  private val offsetAnim = Animatable(initialOffset, Offset.VectorConverter)

  // 不带动画的设置scale
  suspend fun setScale(scale: Float = this.scale) {
    scaleAnim.snapTo(scale.coerceIn(1f, 15f))
  }

  // 不带动画的设置offset
  suspend fun setOffset(offset: Offset = this.offset) {
    offsetAnim.snapTo(offset)
  }

  // 停止动画
  suspend fun stop() {
    scaleAnim.stop()
    offsetAnim.stop()
  }

  // 带动画的scale
  suspend fun animateScale(scale: Float = this.scale) {
    scaleAnim.animateTo(
      targetValue = scale,
      animationSpec = tween(600, easing = LinearOutSlowInEasing)
    )
  }

  // 带动画的offset
  suspend fun animateOffset(offset: Offset = this.offset) {
    offsetAnim.animateTo(
      targetValue = offset,
      animationSpec = tween(600, easing = LinearOutSlowInEasing)
    )
  }

}

@Composable
fun rememberMapWidgetState(
  initialScale: Float = 1f,
  initialOffset: Offset = Offset.Zero
) = remember { MapWidgetState(initialScale, initialOffset) }