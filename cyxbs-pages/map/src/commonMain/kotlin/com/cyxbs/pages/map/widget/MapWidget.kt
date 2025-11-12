package com.cyxbs.pages.map.widget

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * @Desc : Map主体
 * @Author : zzx
 * @Date : 2025/11/12 17:23
 */

@Composable
fun MapWidget(inputStream: ByteArray?) {
  var isClick by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(
    targetValue = if (isClick) 5f else 1f,
    animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
  )
  Box(
    modifier = Modifier
      .scale(scale)
  ) {
    MapImageLoad(inputStream) {

    }
  }
}