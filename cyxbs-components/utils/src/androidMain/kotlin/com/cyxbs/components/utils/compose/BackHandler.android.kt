package com.cyxbs.components.utils.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/29
 */
@Stable
actual fun Modifier.backHandler(enabled: Boolean, onBack: () -> Unit): Modifier = this.composed { 
  BackHandler(enabled, onBack)
  this
}.onKeyEvent {
  // 这里存疑，不知道 Android 按下 ESC 键会不会一起在 BackHandler 中触发，如果会的话后续可以删掉
  if (enabled && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
    onBack()
    true
  } else false
}