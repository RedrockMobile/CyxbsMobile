package com.cyxbs.pages.map.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * @Desc : 地图组件的状态
 * @Author : zzx
 * @Date : 2025/11/13 11:02
 */

@Stable
class MapWidgetState(
  initialScale: Float = 1f,
  initialOffset: Offset = Offset.Zero,
  initialContainer: IntSize = IntSize.Zero,
  isLock: Boolean = false
) {
  // 盒子尺寸以及中心坐标
  var container: IntSize by mutableStateOf(initialContainer)
  val center: Offset get() = Offset(container.width / 2f, container.height / 2f)
  var isLock by mutableStateOf(false)

  // 暴露给外部的scale
  val scale: Float get() = currentScale

  // 暴露给外部的offset
  val offset: Offset get() = currentOffset

  private var currentScale by mutableStateOf(initialScale)
  private var currentOffset by mutableStateOf(initialOffset)

  // Animatable动画数值包装器
  private val scaleAnim = Animatable(initialScale)
  private val offsetAnim = Animatable(initialOffset, Offset.VectorConverter)
  private var scaleAnimationJob: Job? = null
  private var offsetAnimationJob: Job? = null

  // 不带动画的设置scale
  fun setScale(scale: Float = this.scale) {
    scaleAnimationJob?.cancel()
    currentScale = scale.coerceIn(1f, 15f)
  }

  // 不带动画的设置offset
  fun setOffset(offset: Offset = this.offset) {
    offsetAnimationJob?.cancel()
    currentOffset = offset
  }

  // 停止动画
  fun stop() {
    scaleAnimationJob?.cancel()
    offsetAnimationJob?.cancel()
  }

  // 带动画的scale
  suspend fun animateScale(scale: Float = this.scale) = coroutineScope {
    scaleAnimationJob?.cancel()
    val animationJob = launch {
      // 手势直接更新状态，动画开始时从当前显示值接续。
      scaleAnim.snapTo(this@MapWidgetState.scale)
      scaleAnim.animateTo(
        targetValue = scale,
        animationSpec = tween(800, easing = LinearOutSlowInEasing)
      ) {
        currentScale = value
      }
    }
    scaleAnimationJob = animationJob
    animationJob.join()
  }

  // 带动画的offset
  suspend fun animateOffset(offset: Offset = this.offset) = coroutineScope {
    offsetAnimationJob?.cancel()
    val animationJob = launch {
      offsetAnim.snapTo(this@MapWidgetState.offset)
      offsetAnim.animateTo(
        targetValue = offset,
        animationSpec = tween(800, easing = LinearOutSlowInEasing)
      ) {
        currentOffset = value
      }
    }
    offsetAnimationJob = animationJob
    animationJob.join()
  }

}

@Composable
fun rememberMapWidgetState(
  initialScale: Float = 1f,
  initialOffset: Offset = Offset.Zero
) = remember { MapWidgetState(initialScale, initialOffset) }
