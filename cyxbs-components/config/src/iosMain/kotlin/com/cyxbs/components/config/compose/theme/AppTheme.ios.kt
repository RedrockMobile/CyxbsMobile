package com.cyxbs.components.config.compose.theme

import androidx.compose.runtime.Composable

@Composable
internal actual fun ConfigAppThemeBefore(content: @Composable () -> Unit) {
  content()
}

@Composable
internal actual fun ConfigAppThemeAfter(content: @Composable () -> Unit) {
  content()
}
