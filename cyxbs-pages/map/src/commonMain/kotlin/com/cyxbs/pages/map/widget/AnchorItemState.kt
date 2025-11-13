package com.cyxbs.pages.map.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * @Desc : 锚点信息的状态
 * @Author : zzx
 * @Date : 2025/11/13 20:46
 */

class AnchorItemState(
  initialPosition: Offset = Offset.Zero,
  initialSize: IntSize = IntSize.Zero,
  visible: Boolean = false
) {
  var position by mutableStateOf(initialPosition)
  var size by mutableStateOf(initialSize)
  var visible by mutableStateOf(visible)
}

@Composable
fun rememberAnchorItemState(
  initialPosition: Offset = Offset.Zero,
  initialSize: IntSize = IntSize.Zero,
  visible: Boolean = false
) = remember { AnchorItemState(initialPosition, initialSize, visible) }
