package com.cyxbs.components.view.ui

import androidx.compose.runtime.Composable

/**
 * 窗口组件，类似于 Dialog，但全屏展示
 *
 * @author 985892345
 * @date 2025/3/24
 */

@Composable
expect fun Window(
  dismissOnBackPress: (() -> Unit)? = null, // 返回键或者 esc 键
  content: @Composable () -> Unit,
)