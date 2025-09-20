package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
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
actual fun Modifier.backHandler(enabled: Boolean, onBack: () -> Unit): Modifier = this.onKeyEvent {
  if (enabled && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
    onBack()
    true
  } else false
}