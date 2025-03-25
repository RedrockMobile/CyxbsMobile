package com.cyxbs.components.view.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 窗口组件，类似于 Dialog，但全屏展示
 *
 * @author 985892345
 * @date 2025/3/24
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Window(
  dismissOnBackPress: (() -> Unit)?,
  content: @Composable () -> Unit,
) {
  Dialog(
    onDismissRequest = { dismissOnBackPress?.invoke() },
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false,
      usePlatformInsets = false,
      useSoftwareKeyboardInset = false,
      scrimColor = Color.Transparent,
    ),
    content = content
  )
}