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
import kotlinx.coroutines.launch

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