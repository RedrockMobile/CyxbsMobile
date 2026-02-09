package com.cyxbs.pages.map.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * @Desc : 锚点信息的状态
 * @Author : zzx
 * @Date : 2025/11/13 20:46
 */

@Stable
class AnchorItemState(
  initialPosition: Offset = Offset.Zero,
  visible: Boolean = false,
  placeId: String
) {
  var position by mutableStateOf(initialPosition)
  var visible by mutableStateOf(visible)
  var placeId by mutableStateOf(placeId)
  val scale get() = scaleAnim.value
  private val scaleAnim = Animatable(1f)

  // 单个item被点击时的动画
  suspend fun animateClick(duration: Int = 300) {
    scaleAnim.animateTo(
      targetValue = 1f,
      animationSpec = keyframes {
        durationMillis = duration
        0f at 0
        1.2f at duration / 3 using FastOutLinearInEasing
        0.8f at duration / 3 * 2
        1f at duration
      }
    )
  }

  suspend fun animateClose(duration: Int = 300) {
    scaleAnim.animateTo(
      targetValue = 0f,
      animationSpec = keyframes {
        durationMillis = duration
        1f at 0
        1.5f at duration / 4
        0f at duration / 2
        0.5f at duration / 4 * 3
        0f at duration
      },
    )
  }
}

@Composable
fun rememberAnchorItemState(
  initialPosition: Offset = Offset.Zero,
  visible: Boolean = false,
  placeId: String
) = remember { AnchorItemState(initialPosition, visible, placeId) }
